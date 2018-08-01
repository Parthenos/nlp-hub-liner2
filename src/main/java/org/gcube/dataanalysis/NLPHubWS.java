package org.gcube.dataanalysis;

import static org.gcube.common.authorization.client.Constants.authorizationService;
import static org.gcube.resources.discovery.icclient.ICFactory.clientFor;
import static org.gcube.resources.discovery.icclient.ICFactory.queryFor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.gcube.common.authorization.library.AuthorizationEntry;
import org.gcube.common.authorization.library.provider.SecurityTokenProvider;
import org.gcube.common.resources.gcore.ServiceEndpoint;
import org.gcube.common.resources.gcore.utils.XPathHelper;
import org.gcube.common.scope.api.ScopeProvider;
import org.gcube.resources.discovery.client.api.DiscoveryClient;
import org.gcube.resources.discovery.client.queries.api.SimpleQuery;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;


@ServerEndpoint(value = "/websocketendpoint")
public class NLPHubWS {
	private static Logger _log = Logger.getLogger(NLPHubWS.class.getSimpleName());

	private static final String USER_AGENT = "Mozilla/5.0";
	private static String SERVICE_ENDPOINT_CATEGORY = "DataAnalysis";
	private static String SERVICE_ENDPOINT_NAME = "DataMiner";
	private static String STATUS_COMPUTED = "computed";
	private static String STATUS_ERROR = "error";
	private static String XML_RESULT_ROOT_EXCEPTION = "ExceptionReport";
	private static final String GCUBE_TOKEN = "gcube-token";
	private static final String WORKSPACE_PROD_ENDPOINT = "https://workspace-repository.d4science.org/home-library-webapp";
	private static final String NLPHUB_FOLDER_NAME = "NLPHUb";
	private static final int RETRY_NO = 3;


	private static Set<Session> peers = Collections.synchronizedSet( new HashSet<Session>() );
	private JSONParser parser = new JSONParser();

	@OnOpen
	public void onOpen( final Session session )	{
		_log.trace( "onOpen(" + session.getId() + ")" );
		peers.add( session );
	}

	@OnClose
	public void onClose( final Session session ) {
		_log.trace( "onClose(" + session.getId() + ")" );
		peers.remove( session );
	}

	@SuppressWarnings("unchecked")
	@OnMessage
	public void onMessage(final Session session, final String message) {
		_log.info( "onMessage(" + message + "," + session.getId() + ")" );
		for ( final Session peer : peers )	{
			if ( peer.getId().equals( session.getId() ) ) {
				Object obj = null;
				try {
					obj = parser.parse(message);
				} catch (ParseException e) {
					JSONObject objToReturn = new JSONObject();
					objToReturn.put("response", "error parsing JSON");
					peer.getAsyncRemote().sendText(objToReturn.toJSONString());
				}			
				JSONObject jsonObject = (JSONObject) obj;
				String token = jsonObject.get("token").toString();
				String action = jsonObject.get("action").toString();
				String inputText = jsonObject.get("text").toString();
				switch (action) {
				case "start":
					AuthorizationEntry aEntry = validateToken(peer, token);
					if (aEntry == null)
						return;
					_log.debug("Context="+aEntry.getContext());
					String options = jsonObject.get("options").toString();
					if (options == null || options.equals(""))
						options = "default";
					else
						try {
							options = URLEncoder.encode(options, "UTF-8");
						} catch (UnsupportedEncodingException e) {
							_log.error("Could not encode options");
						}
					String result = doWPSCallToDataMiner(peer, aEntry, token, inputText, options);
					//seomthing went wrong
					if (result == null)
						break;
					else {
						_log.debug("Result seems ok, sending back to client");
					}
					send2Client(peer, "response", STATUS_COMPUTED, "value", result);
					break;
				default:
					break;
				}
				//peer.getAsyncRemote().sendText("action=" + action);
			}
		}
	}	
	/**
	 * performs tha actual call to the service
	 * @param peer session 
	 * @param entry authEntry	
	 * @param token token 
	 * @param username username
	 * @param inputText
	 * @param options
	 * @return
	 */
	private String doWPSCallToDataMiner(Session peer, AuthorizationEntry entry, String token, String inputText, String options) {
		_log.debug("doWPSCallToDataMiner for="+entry.getContext());
		String toReturn = "";
		List<ServiceEndpoint> dms = null;
		try {
			dms = getDataMinerInstance(entry.getContext());
			if (dms == null || dms.isEmpty()) {
				send2Client(peer, "response", STATUS_ERROR, "value", "There is no DataMiner cluster in this VRE (" 
						+ entry.getContext()+ ")" 
						+ ", please report this issue at www.d4science.org/contact-us");
				_log.warn("Error, no DataMiner cluster in this VRE");
				return null;
			}				
			ServiceEndpoint se = dms.get(0);
			ServiceEndpoint.AccessPoint ap = se.profile().accessPoints().asCollection().iterator().next();
			_log.info("got DataMiner instance="+ap.address());
			//String apAddress = ap.address().startsWith("https") ? ap.address() : ap.address().replace("http", "https");
			//TODO in https -> error= Certificate for <dataminer-cloud1.d4science.org> doesn't match any of the subject alternative names: [dataminer-lb.garr.d4science.org, dataminer.garr.d4science.org]
			String apAddress = ap.address(); 
			_log.info("Uploading file to WS ...");
			String inputFileLink = uploadInputTextFileToWS(inputText, token, entry.getClientInfo().getId());
			_log.info("inputFileLink Uploaded OK: " + inputFileLink);
			String wpsParams = getWPSCallURLParameters(token, inputFileLink, options);
			String responseXML = sendGet(apAddress, wpsParams, token);
			_log.info("response: " + responseXML);
			toReturn = parseResult(responseXML);
			_log.info("PARSED OK: " + toReturn);
			if (!toReturn.startsWith("http")) {
				send2Client(peer, "response", STATUS_ERROR, "value", "There was a problem executing this method, see exception returned: \n" + toReturn);
				return null;
			}
		} catch (Exception e) {
			send2Client(peer, "response", STATUS_ERROR, "value", "There was a problem contacting the DataMiner cluster in this VRE (" 
					+ entry.getContext()+ ")" 
					+ "), please report this issue at www.d4science.org/contact-us");
			return null;
		}
		return toReturn;
	}
	/**
	 * 
	 * @param token the token
	 * @param inputFileLink the http  of the input file
	 * @param options (not necessary for Jan's tool)
	 * @return the url paramters for WPS Call to perform
	 */
	private String getWPSCallURLParameters(String token, String inputFileLink, String encodedOptions) {
		StringBuilder sb = new StringBuilder("request=Execute&service=WPS&Version=1.0.0")
				.append("&gcube-token=")
				.append(token)
				.append("&lang=en-US")
				.append("&Identifier=org.gcube.dataanalysis.wps.statisticalmanager.synchserver.mappedclasses.transducerers")
				.append(".NER_LINER2_POLISH")
				.append("&DataInputs=ClassToRun=pl.clarin.nlp.worker.client.NERLiner2PolishWrapper;")
				.append("FileInput=")
				.append(inputFileLink)
				.append(";");
		return sb.toString();
	}

	/* for Jan's NER Liner, need to produce
	request=Execute&service=WPS&Version=1.0.0
	        &gcube-token=950c1194-bf7e-466a-8e28-80c97ac0388c-843339462
	        &lang=en-US
	        &Identifier=org.gcube.dataanalysis.wps.statisticalmanager.synchserver.mappedclasses.transducerers.NER_LINER2_POLISH
	        &DataInputs=ClassToRun=pl.clarin.nlp.worker.client.NERLiner2PolishWrapper;FileInput=http%3A%2F%2Fdata.d4science.org%2FTERTWWZyVU9mRmxqZ05NT2tBYlhiV3NaMlEzV2kyeEZHbWJQNStIS0N6Yz0;

	 */
	/**
	 * 
	 * @param peer
	 * @param token
	 * @return
	 */
	private AuthorizationEntry validateToken(Session peer, String token) {
		_log.debug("validateToken token for="+token);
		AuthorizationEntry toReturn = null;
		if (token==null || token.equals("")) {
			send2Client(peer, "response", STATUS_ERROR, "value", "You must supply a token");
			_log.warn("Error, no token provided");
			return null;
		}
		try {
			ScopeProvider.instance.set("/d4science.research-infrastructures.eu");
			SecurityTokenProvider.instance.set(token);
			AuthorizationEntry entry = authorizationService().get(token);
			toReturn = entry;

			_log.info("serving token for="+toReturn.getClientInfo().getId());
		} catch (Exception e) {
			send2Client(peer, "response", STATUS_ERROR, "value", "You must supply a valid token");
			_log.warn("Error, token not valid");
			return null;
		}
		return toReturn;
	}
	/**
	 * 
	 * @return the http public link of the uploaded file
	 */
	private String uploadInputTextFileToWS(String text, String token, String username) {
		byte[] str = text.getBytes();
		SecurityTokenProvider.instance.set(token);
		String path = "/Home/"+username+"/Workspace/";
		String fileName = UUID.randomUUID().toString()+".txt";
		try {
			return uploadFile(str, fileName, "Automatically uploaded", path, token);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}


	// HTTP POST request
	private String sendGet(String url, String urlParameters, String token) {
		StringBuilder toReturn = new StringBuilder();
		try {
			int i = 1;
			while (i <= RETRY_NO) {
				CloseableHttpClient client = HttpClients.createDefault();
				HttpGet request = new HttpGet(url+"?"+urlParameters);
				_log.info("request to="+request.toString());
				// add header
				request.setHeader("User-Agent", USER_AGENT);
				request.setHeader("Accept-Encoding","UTF-8");
				request.setHeader("Accept-Charset","UTF-8");
				request.setHeader("Content-Encoding","UTF-8");

				HttpResponse response = client.execute(request);

				// Get the response
				BufferedReader rd = new BufferedReader
						(new InputStreamReader(
								response.getEntity().getContent()));

				String line = "";
				while ((line = rd.readLine()) != null) {
					toReturn.append(line);
				}
				i++;
				if (toReturn.toString().compareTo("") == 0 || toReturn.toString().startsWith("Error")) {
					_log.warn("response from Dataminer is empty or an error occurred, retry tentative: " + i + " of " + RETRY_NO);
					_log.error("here is the faulty response from Dataminer="+toReturn.toString());
				} else {
					_log.debug("response from Dataminer="+toReturn.toString());
					break;
				}
			} 
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return toReturn.toString();
	}

	private String parseResult(String xml) throws Exception {
		String elem = removeXmlStringNamespaceAndPreamble(xml);
		DocumentBuilder docBuilder =  DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Node node = docBuilder.parse(new InputSource(new StringReader(elem))).getDocumentElement();
		String rootElement =  node.getNodeName();
		XPathHelper helper = new XPathHelper(node);
		List<String> currValue = null;
		if (rootElement.compareTo(XML_RESULT_ROOT_EXCEPTION) == 0) {
			currValue = helper.evaluate("/ExceptionReport");
			if (currValue != null && currValue.size() > 0) 
				return currValue.get(0);
		}
		else {
			currValue = helper.evaluate("//ProcessOutputs/Output/Data/ComplexData/FeatureCollection/featureMember/Result[2]/Data/text()");
			if (currValue != null && currValue.size() > 0) 
				return currValue.get(0);
		}
		return null;
	}

	//this remove all the namespaces causing parsing errors as i don't have time to deal with this.
	private static String removeXmlStringNamespaceAndPreamble(String xmlString) {
		return xmlString.replaceAll("(<\\?[^<]*\\?>)?", "") /* remove preamble */
				.replaceAll("xmlns.*?(\"|\').*?(\"|\')", "") /* remove xmlns declaration */
				.replaceAll("(<)(\\w+:)(.*?>)", "$1$3") /* remove opening tag prefix */
				.replaceAll("(</)(\\w+:)(.*?>)", "$1$3")
				.replaceAll("wps:", "")
				.replaceAll("xsi:", "")
				.replaceAll("ogr:",""); /* remove closing tags prefix */
	}

	private static String uploadFile(byte[] in, String name, String description, String parentPath, String token) throws Exception {
		//create folder
		try {
			getHTML(WORKSPACE_PROD_ENDPOINT+"/rest/CreateFolder?name="+NLPHUB_FOLDER_NAME+"&description=desc&parentPath="+parentPath, token);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		parentPath += NLPHUB_FOLDER_NAME;		
		String uri = WORKSPACE_PROD_ENDPOINT + "/rest/Upload?" + "name=" + name+ "&description=" + URLEncoder.encode(description, "UTF-8") + 
				"&parentPath=" + URLEncoder.encode(parentPath, "UTF-8");

		URL url = new URL(uri);
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setRequestProperty(GCUBE_TOKEN, SecurityTokenProvider.instance.get());
		connection.setDoInput(true);
		connection.setDoOutput(true);
		connection.setUseCaches(false);
		connection.setRequestProperty("Content-Type", "image/jpeg");
		connection.setRequestMethod("POST");

		// Write file to response.
		OutputStream output = connection.getOutputStream();
		output.write(in);
		output.close();

		BufferedReader r = new BufferedReader(new  InputStreamReader(connection.getInputStream()));

		StringBuffer response = new StringBuffer();
		String inputLine;
		while ((inputLine = r.readLine()) != null) {
			response.append(inputLine);
		}

		String xmlOut = response.toString();
		xmlOut = xmlOut.replace("<string>", "");
		xmlOut = xmlOut.replace("</string>", "");
		String publicLink = getHTML(WORKSPACE_PROD_ENDPOINT+"/rest/GetPublicLink?absPath="+xmlOut+"&shortUrl=false", token);
		publicLink = publicLink.replace("<string>", "");
		publicLink = publicLink.replace("</string>", "");
		publicLink = URLEncoder.encode(publicLink, "UTF-8");
		return publicLink;
	}


	public static String getHTML(String urlToRead, String token) throws Exception {
		StringBuilder result = new StringBuilder();
		URL url = new URL(urlToRead);
		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
		conn.setRequestProperty(GCUBE_TOKEN, token);
		conn.setRequestMethod("GET");
		BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String line;
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		rd.close();
		return result.toString();
	}

	private List<ServiceEndpoint> getDataMinerInstance(String scope) throws Exception  {
		String currScope = 	ScopeProvider.instance.get();
		ScopeProvider.instance.set(scope);
		SimpleQuery query = queryFor(ServiceEndpoint.class);
		query.addCondition("$resource/Profile/Category/text() eq '"+ SERVICE_ENDPOINT_CATEGORY +"'");
		query.addCondition("$resource/Profile/Name/text() eq '"+ SERVICE_ENDPOINT_NAME +"'");
		DiscoveryClient<ServiceEndpoint> client = clientFor(ServiceEndpoint.class);
		List<ServiceEndpoint> toReturn = client.submit(query);
		ScopeProvider.instance.set(currScope);
		return toReturn;
	}	

	@SuppressWarnings("unchecked")
	private static void send2Client(Session peer, String attr1, String value1, String attr2, String value2) {
		JSONObject objToReturn = new JSONObject();
		objToReturn.put(attr1, value1);
		objToReturn.put(attr2, value2);
		synchronized (peer) {
			if (peer.isOpen()) {
				peer.getAsyncRemote().sendText(objToReturn.toJSONString());
			}
		}
	}

	@OnError
	public void onError(Session session, Throwable ex) {
		_log.debug("Error " +ex.getMessage());
	}
}