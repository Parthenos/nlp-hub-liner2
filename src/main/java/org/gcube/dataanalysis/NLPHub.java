package org.gcube.dataanalysis;

import java.io.IOException;
import java.net.URL;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * Servlet implementation class NLPHub
 */
@WebServlet(asyncSupported = true, name = "NLPServlet", urlPatterns = {"/nlphub-servlet"})
public class NLPHub extends HttpServlet {
	private Logger _log = Logger.getLogger(NLPHub.class.getSimpleName());
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public NLPHub() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		_log.debug("Got uri in doGet: " + request.getParameter("uri"));
		URL url = new URL(request.getParameter("uri"));
		Scanner sc = new Scanner(url.openStream());
		StringBuilder toReturn = new StringBuilder();
		while (sc.hasNext()) {
			toReturn.append(sc.nextLine());
        }
		sc.close(); // End of program
		// CZ THis seems to be necessary to deal with proper UTF-8 encodings.
		response.setHeader("Content-Type", "application/octet-stream; charset=UTF-8");
		response.setHeader("Content-Disposition","attachment;filename=myFile.aaa");
		response.setCharacterEncoding("UTF-8");
		_log.debug("Got uri in doGet, having set headers: " + toReturn.toString());
		response.getWriter().append(toReturn.toString());
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

	}

}
