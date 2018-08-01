<!DOCTYPE html>
<html>
<head>
<!-- input=http%3A%2F%2Fws1-clarind.esc.rzg.mpg.de%2Fdrop-off%2Fstorage%2F1513257926038.txt&lang=en&analysis=const-parsing -->
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>NLPHub v1.2</title>
<link href="https://fonts.googleapis.com/icon?family=Material+Icons"
	rel="stylesheet" />
<!--Import materialize.css-->
<link type="text/css" rel="stylesheet" href="css/materialize.min.css"
	media="screen,projection" />
<link type="text/css" rel="stylesheet" href="css/custom.css" />
<!-- jQuery library -->
<script
	src="https://ajax.googleapis.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>
<script type="text/javascript" src="js/materialize.min.js"></script>
<script type="text/javascript" src="js/jquery.simple.websocket.min.js"></script>
<script type="text/javascript" src="js/main.js"></script>

</head>
<body style="padding: 0 15px;">
	<h3>Liner2: Named Entity and Temporal Expression Recognizer for Polish</h3>
	<p class="flow-text">Identify names of persons, locations,
		organizations, as well as money amounts, time and date expressions in
		Polish texts automatically.</p>
	<div class="row">
		<div class="input-field col s12">
			<textarea id="input-textarea" class="my-textarea" rows="8"
				placeholder="paste your text here"></textarea>
		</div>
	</div>
	<%--<div>
		<p>Default Annotations:</p>
		<input type="checkbox" id="address-checkbox" value="Address"
			checked="checked"></input> <label for="address-checkbox">Address</label>
		<input type="checkbox" id="date-checkbox" value="Date"
			checked="checked"></input> <label for="date-checkbox">Date</label> <input
			type="checkbox" id="location-checkbox" value="Location"
			checked="checked"></input> <label for="location-checkbox">Location</label>
		<input type="checkbox" id="organization-checkbox" value="Organization"
			checked="checked"></input> <label for="organization-checkbox">Organization</label>
	</div>
	<div>
		<p>Additional Annotations:</p>
		<input type="checkbox" id="person-checkbox" value="Person"></input><label
			for="person-checkbox">Person</label> <input type="checkbox"
			id="money-checkbox" value="Money"></input> <label
			for="money-checkbox">Money</label> <input type="checkbox"
			id="percent-checkbox" value="Percent"></input> <label
			for="percent-checkbox">Percent</label> <input type="checkbox"
			id="token-checkbox" value="Token"></input> <label
			for="token-checkbox">Token</label> <input type="checkbox"
			id="SpaceToken-checkbox" value="SpaceToken"></input> <label
			for="SpaceToken-checkbox">SpaceToken</label> <input type="checkbox"
			id="sentence-checkbox" value="Sentence"></input> <label
			for="sentence-checkbox">Sentence</label>
	</div>--%>
	<div class="progress" id="progressBar" style="visibility: hidden;">
		<div class="indeterminate"></div>
	</div>
	<div style="text-align: center; padding: 5px;">
		<a class="waves-effect waves-light btn green darken-1"
			id="execute-button">Execute</a>
	</div>
	<div id="result-container" style="margin-top: -20px;">
		<div id="downloadLink" style="display: inline;"></div>
		<pre id="result">
	</pre>
	</div>
	<!-- scripting code -->
	<script type="text/javascript">
		$("#input-textarea").text("Pobieranie tekstu wejściowego ... proszę czekać");
		doCheckInputText = function(uri) {
			$.ajax({
				url : "nlphub-servlet",
				type : 'GET',
				datatype : 'text', //json
                //contentType: "application/x-www-form-urlencoded;charset=UTF-8", // default
				data : {
					uri : uri
				},
				success : function(data) {
					$("#input-textarea").text(data);
				}
			});
		}
		var inputEncodedURL = getUrlParameter("input") == null ? ""
				: getUrlParameter("input");
		if (inputEncodedURL != "")
			doCheckInputText(inputEncodedURL);
		//instantiate web socket
		var protocol = (location.protocol == 'https:') ? 'wss://' : 'ws://';
		var webSocket = $.simpleWebSocket({
			url : protocol + location.hostname
					+ (location.port ? ':' + location.port : '')
					+ '/nlp-hub-liner2/websocketendpoint'
		});
		// reconnected listening
		webSocket.listen(function(message) {
			doHandleResponse(message);
		});
	</script>
</body>
</html>