/*
 */
package org.mypackage.eps;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ederbase.model.EbDynamic;
import com.ederbase.model.EbEnterprise;
import com.ederbase.model.QaManager;
import com.eps.model.EpsClient;
import com.eps.model.EpsReport;

/**
 * @author Robert Eder Feb 2010 to ... Oct 2010 This file controls all dynamic
 *         pages for EPPORA/EPS
 **/
public class EpsController extends HttpServlet {

	protected void processRequest(HttpServletRequest request,
	    HttpServletResponse response) throws ServletException, IOException {
		long startTime = System.nanoTime();
		long dbTime = 0;
		int iSqlCount = 0;
		String stHTML = "";
		String stReportId = "";
		String stError = "";
		int iDebugLevel = 0;
		EbEnterprise ebEnt = null;
		EpsClient epsClient = null;
		try {
			try {
				// SkinId=1, Language=26 English
				// figure out the db
				String stDb = request.getRequestURI().replace("index.jsp", "")
				    .replace("/", "");
				// stDb="eps";
				if (stDb.length() == 3)
					stDb += "01";

				ebEnt = new EbEnterprise(1, 26, "ederbase", "eb" + stDb, "common");
				if (ebEnt.ebDyn == null) {
					ebEnt.ebDyn = new EbDynamic(ebEnt);
				}
				ebEnt.ebUd.setRequest(request, response);

				epsClient = new EpsClient(ebEnt, "1|db" + stDb);
				epsClient.epsUd.processSubmit(request, response);

				int iB = 1004;

				try {
					iB = Integer.parseInt(request.getParameter("b"));
				} catch (Exception e) {
				}

				QaManager qa = null;
				switch (iB) {
				default:
				case 1004:
					stReportId = request.getParameter("reportid");
					if (stReportId != null && stReportId.length() > 0) {
						EpsReport epsReport = new EpsReport();
						stHTML = epsReport.epsFormatSavedReports(stReportId, ebEnt);
						if (epsReport.getError().length() > 0)
							stHTML += "<hr><font color=red>" + epsReport.getError();
						response.reset();
					} else {
						stHTML = epsClient.getEpsPage();
					}
					break;

				case 2001:
					qa = new QaManager(ebEnt);
					stHTML += qa.getQaManager(ebEnt);
					break;

				case 2002:
					qa = new QaManager(ebEnt);
					stHTML += qa.getQaAdmin(ebEnt);
					break;
				}

			} catch (Exception e) {
				stError += "<br>ERROR EbController: " + e;
			}
		} finally {
			if (stHTML != null && stHTML.length() > 6) {
				if (stHTML.substring(0, 2).equals("~~")) {
					response
					    .sendRedirect(response.encodeRedirectURL(stHTML.substring(2)));
					return;
				}
			}
			if (ebEnt != null) {
				stError += epsClient.getError();

				stError += ebEnt.getAllErrors();
				if (ebEnt.iDebugLevel > 0) {
					iDebugLevel = ebEnt.iDebugLevel;
					stError += "<br><table border=1  style='background-color: white;'>";
					stError += ebEnt.dbCommon.getDebugTrace();
					stError += ebEnt.dbDyn.getDebugTrace();
					stError += ebEnt.dbEb.getDebugTrace();
					stError += ebEnt.dbEnterprise.getDebugTrace();
					stError += "</table><br>";
				}
				if (ebEnt.ebUd.getLoginId() == 1 && stError.trim().length() > 0) {
					stError += ebEnt.ebUd.dumpRequest();
				} else {
					if (ebEnt.ebUd.getRedirect().length() > 0) {
						String stRedir = ebEnt.ebUd.getRedirect();
						if (ebEnt.ebUd.getPopupMessage().length() > 0) {
							int iPos = stRedir.indexOf("#");
							if (iPos > 0)
								stRedir = stRedir.substring(0, iPos);
							stRedir += (stRedir.indexOf("?") >= 0 ? "" : "?");
							stRedir += "&popupmessage="
							    + URLEncoder.encode(ebEnt.ebUd.getPopupMessage(), "UTF-8");
						}
						response.sendRedirect(response.encodeRedirectURL(stRedir));
						return;
					}
				}
				dbTime += ebEnt.dbCommon.getExecuteTime();
				iSqlCount += ebEnt.dbCommon.getSqlCount();
				dbTime += ebEnt.dbDyn.getExecuteTime();
				iSqlCount += ebEnt.dbDyn.getSqlCount();
				dbTime += ebEnt.dbEb.getExecuteTime();
				iSqlCount += ebEnt.dbCommon.getSqlCount();
				dbTime += ebEnt.dbEb.getExecuteTime();
				iSqlCount += ebEnt.dbEnterprise.getSqlCount();
			}
			String stFileName = "";
			String stFormat = "";
			String stContentType = "text/html;charset=UTF-8";
			if (stReportId != null && stReportId.length() > 0) {
				stFormat = request.getParameter("format");
				if (stFormat != null && stFormat.length() > 0) {
					if (stFormat.equals("excel")) {
						stContentType = "application/vnd.ms-excel";
						stFileName = "Report_" + stReportId + ".xls";
					}
				} else
					stFormat = "";
			}
			response.setContentType(stContentType);
			if (stFileName.length() > 0) {
				response.setHeader("Content-Length", String.valueOf(stHTML.length()));
				response.setHeader("Content-Disposition", "attachment; filename= \""
				    + stFileName + "\"");
			}
			PrintWriter out = response.getWriter();
			out.println(stHTML);
			if (stFileName.length() == 0) {
				if (!stError.equals("")) {
					out.println("<hr><font color=red>");
					out.println(stError);
				}
			}
			String stTemp = request.getParameter("commtrace");
			if (iDebugLevel > 0
			    || (stTemp != null && stTemp.length() > 0 && (stTemp.equals("d")
			        || stTemp.equals("t") || stTemp.equals("i")))) {
				long endTime = System.nanoTime();
				long elapsedTime = endTime - startTime;
				double seconds = elapsedTime / 1.0E09;
				out.println((dbTime / 1.0E09) + "/" + iSqlCount + " " + seconds
				    + " sec");
			}
			out.close();
			ebEnt.ebClose();
		}
	}

	/**
	 * Handles the HTTP <code>GET</code> method.
	 * 
	 * @param request
	 *          servlet request
	 * @param response
	 *          servlet response
	 * @throws ServletException
	 *           if a servlet-specific error occurs
	 * @throws IOException
	 *           if an I/O error occurs
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
	    throws ServletException, IOException {
		processRequest(request, response);
	}

	/**
	 * Handles the HTTP <code>POST</code> method.
	 * 
	 * @param request
	 *          servlet request
	 * @param response
	 *          servlet response
	 * @throws ServletException
	 *           if a servlet-specific error occurs
	 * @throws IOException
	 *           if an I/O error occurs
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	    throws ServletException, IOException {
		processRequest(request, response);
	}

	/**
	 * Returns a short description of the servlet.
	 * 
	 * @return a String containing servlet description
	 */
	@Override
	public String getServletInfo() {
		return "EPS/EPPORA Server";
	}
}
