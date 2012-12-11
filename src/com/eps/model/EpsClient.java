package com.eps.model;

/*
 * 4/4/2011 -- RE-WRITE WITHOUT OILP.exe
 *
 */

import java.net.URLEncoder;
import java.sql.ResultSet;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.ederbase.model.EbDynamic;
import com.ederbase.model.EbEnterprise;

/**
 * 
 * @author Rob Eder
 */
public class EpsClient {
	public String stVersion = "EPPORA Version: 11 December 2012";
	private int iUserId = -1;
	private int nmPrivUser = 0;
	private String stAction = "";
	private String stTrace = "";
	private EbEnterprise ebEnt = null;
	private String stError = "";
	public EpsUserData epsUd = null;

	public EpsClient(EbEnterprise ebEnt, String stDbDyn) {
		this.ebEnt = ebEnt;
		if (this.ebEnt.ebDyn == null) {
			this.ebEnt.ebDyn = new EbDynamic(this.ebEnt);
		}
		this.ebEnt.ebDyn.assertDb(stDbDyn);
		if (this.ebEnt.ebUd != null && this.ebEnt.ebUd.request != null) {
			String stTemp = this.ebEnt.ebUd.request.getParameter("commtrace");
			if (stTemp != null && stTemp.length() > 0 && stTemp.equals("d")) {
				this.ebEnt.iDebugLevel = 1;
				this.ebEnt.dbCommon.setDebugLevel(1);
				this.ebEnt.dbDyn.setDebugLevel(1);
				this.ebEnt.dbEb.setDebugLevel(1);
				this.ebEnt.dbEnterprise.setDebugLevel(1);
			}
		}
		this.epsUd = new EpsUserData();
		this.epsUd.setEbEnt(ebEnt, this.stVersion);
		this.epsUd.epsEf.setEbEnt(ebEnt, this);
		this.epsUd.setUser(iUserId, 0);
	}

	public String getEpsPage() {
		String stReturn = "";
		Map<String, String> recentURLs = (Map<String, String>) this.ebEnt.ebUd.request
		    .getSession().getAttribute("recentURLs");
		if (recentURLs == null)
			recentURLs = new LinkedHashMap<String, String>();

		String stSql = "SELECT * FROM X25RefContent where nmRefPageId=1 order by nmUserId";
		try {

			iUserId = this.ebEnt.ebUd.getLoginId();
			nmPrivUser = this.ebEnt.ebUd.getLoginPersonFlags();

			stAction = this.ebEnt.ebUd.request.getParameter("stAction");
			if (stAction == null || stAction.length() <= 0)
				stAction = "home";

			String stPopupMessage = this.ebEnt.ebUd.request
			    .getParameter("popupmessage");
			if (stPopupMessage != null && stPopupMessage.length() > 0)
				ebEnt.ebUd.setPopupMessage(stPopupMessage);

			String stLookup = this.ebEnt.ebUd.request.getParameter("h");
			if (stLookup != null && stLookup.equals("n")) { // Load
				                                              // Framework.Masterpage
				                                              // for popup menus,
				                                              // without TOP and
				                                              // NAVIGATION
				stSql = "SELECT * FROM X25RefContent where nmContentId in (1,5,6)  order by nmUserId;";
			}
			ResultSet rs = this.ebEnt.dbEnterprise.ExecuteSql(stSql);
			if (rs != null) {
				rs.last();
				int iMax = rs.getRow();
				for (int iC = 1; iC <= iMax; iC++) {
					rs.absolute(iC);
					if ((rs.getInt("nmFlag") & 0x01) != 0) // Active
					{
						stReturn += processRules(rs.getInt("nmFlag"),
						    rs.getString("stContent"));
					}
				}
			}
			this.ebEnt.ebDyn.setPopupMessage("alert",
			    this.ebEnt.ebUd.getPopupMessage());
			stReturn += this.epsUd.getValitation();
			if (stTrace.length() > 0) {
				stReturn += "<hr>Trace:<br>" + stTrace + "</td></tr></table><hr>";
			}

			String stPageTitle = this.epsUd.getPageTitle();
			stPageTitle = stPageTitle != null ? stPageTitle.trim() : "";
			// Replace global tags with real values
			stReturn = stReturn.replace("~~stPageTitle~", stPageTitle);

			String stA = this.ebEnt.ebUd.request.getParameter("a");
			if (stA == null || !stA.equals("28")) {
				double pageWidth = this.epsUd.rsMyDiv.getDouble("PageWidthPx");
				stReturn = stReturn.replaceAll("~PageWidthPx~", (pageWidth > 0 ? ""+pageWidth : "auto"));
			}
			if (iUserId > 0) {
				stReturn = stReturn
				    .replace(
				        "~~stWelcome~",
				        "<div id='gen3'>"
				            + "<form method=post id=loginout name=loginout>"
				            + "<font class=medium>Welcome: </font><font class=mediumbold><a class='welcome-name' href='./?stAction=admin&t=9&do=edit&pk="
				            + this.iUserId
				            + "'>"
				            + this.ebEnt.dbDyn
				                .ExecuteSql1("select concat(FirstName,' ',LastName) from Users where nmUserId="
				                    + this.iUserId)
				            + "</a></font>"
				            + "<input type=hidden name=Logout value=Logout onClick=\"setSubmitId(9998);\">"
				            + "<div class='vsplitter'><span/></div>"
				            + "<span onClick='setSubmitId(9998);document.loginout.submit();' style='cursor:pointer;'><b>Sign Out</b></span>"
				            + "</form></div><div class='clr'><span/></div>");
				String stClass = "";
				Enumeration<String> paramNames = this.ebEnt.ebUd.request
				    .getParameterNames();
				String queryString = this.ebEnt.ebUd.request.getQueryString();
				if (queryString != null && queryString.trim().length() > 0) {
					while (paramNames.hasMoreElements()) {
						String paramName = (String) paramNames.nextElement();
						if (queryString.contains(paramName + "=")) {
							stClass += " " + paramName + "_"
							    + this.ebEnt.ebUd.request.getParameter(paramName);
						}
					}
				}
				if (stClass.trim().length() == 0) stClass = "stAction_home";
				stReturn = stReturn.replaceAll("~BodyStyleClass~", "body " + stClass);
			} else {
				stReturn = stReturn.replace("~~stWelcome~", "");
				stReturn = stReturn.replaceAll("~BodyStyleClass~", "body-login");
			}

			// add recent URL
			Set<String> keySet = recentURLs.keySet();
			if (!stAction.equals("home")
			    && !this.ebEnt.ebUd.request.getQueryString().contains("delete")) {
				if (stPageTitle != null && stPageTitle.trim().length() > 0) {
					Iterator<String> keyIterator = keySet.iterator();
					while (keyIterator.hasNext()) {
						String key = keyIterator.next();
						if (recentURLs.get(key).equals(this.ebEnt.ebUd.request.getQueryString())) {
							recentURLs.clear();
							break;
						}
					}
					if (recentURLs.containsKey(stPageTitle.toLowerCase())) {
						recentURLs.remove(stPageTitle.toLowerCase());
					}
					if (recentURLs.size() > 30) {
						String[] keyArr = new String[recentURLs.keySet().size()];
						recentURLs.keySet().toArray(keyArr);
						recentURLs.remove(keyArr[0]);
					}
					recentURLs.put(stPageTitle.toLowerCase(),
							this.ebEnt.ebUd.request.getQueryString());
					this.ebEnt.ebUd.request.getSession().setAttribute("recentURLs",
					    recentURLs);
				}
			}
			
			String stRecents = "";
			if (keySet != null && keySet.size() > 0) {
				stRecents += "<ul>";
				for (String key : keySet) {
					stRecents += "<li><a href='./?" + recentURLs.get(key) + "'>" + key + "</a></li>";
				}
				stRecents += "</ul>";
			}
			
			stReturn += "<script>" +
					"$(document).ready(function() {$('#topNavItems .nav-home').append(\""+stRecents+"\")});</script>";
		} catch (Exception e) {
			e.printStackTrace();
			this.stError += "<BR>ERROR getEpsPage: " + e;
		}
		return stReturn;
	}

	public String processRules(int nmFlag, String stContent) {
		String stReturn = "";
		if ((nmFlag & 0x04) != 0) {
			int iPos1 = 0;
			int iPos2 = 0;
			int iPos3 = 0; // last iPos2
			while ((iPos1 = stContent.indexOf("~~", iPos2)) >= 0) {
				iPos3 = iPos2;
				iPos2 = stContent.indexOf("~", iPos1 + 2);
				if (iPos2 > iPos1) {
					iPos2++; // advance past this character.
					stReturn += stContent.substring(iPos3, iPos1);
					String stCommand = stContent.substring(iPos1, iPos2);
					String[] asFields = stCommand.split("\\|");
					if (asFields.length >= 2) {
						if (asFields[0].equals("~~stPageTitle")) {
							stReturn += "~~stPageTitle~";
						} else if (asFields[0].equals("~~stWelcome")) {
							stReturn += "~~stWelcome~";
						} else if (asFields[0].equals("~~Version")) {
							stReturn += this.stVersion;
						} else if (asFields[0].equals("~~MenuBar")) {
							// Create Menu Bar.
							if (iUserId > 0) {
								stReturn += this.makeMenuBar();
							} else {
								stReturn += "&nbsp;";
							}
						} else if (asFields[0].equals("~~MainBody")) {
							if (iUserId <= 0) {
								this.epsUd.setPageTitle("Login");
								stReturn += this.epsUd.getLoginPage();
							} else {
								String stA = this.ebEnt.ebUd.request.getParameter("a");
								if (stA != null && stA.equals("28")) {
									this.epsUd.setPageTitle("Super User - Table Edit");
									stReturn += ebEnt.ebAdmin
									    .getTableEdit(this.ebEnt.ebUd.request);
								} else {
									String stReturn1 = this.epsUd.getActionPage(stAction);
									String stChild = this.ebEnt.ebUd.getXlsProcess();
									if (stChild != null && stChild.length() > 0) {
										EpsXlsProject epsProject = new EpsXlsProject();
										epsProject.setEpsXlsProject(this.ebEnt, this.epsUd);
										if (stChild.equals("19"))
											stReturn1 = epsProject.xlsRequirementsEdit(stChild);
										else if (stChild.equals("21"))
											stReturn1 = epsProject.xlsSchedulesEdit(stChild);
										else if (stChild.equals("34"))
											stReturn1 = epsProject.xlsTestEdit(stChild);
										else if (stChild.equals("46"))
											stReturn1 = epsProject.xlsAnalyze();
										else if (stChild.equals("26"))
											stReturn1 = epsProject.xlsBaseline(stChild);
										else
											stError += "<BR>ERROR child not implemented " + stChild;
										this.stError += epsProject.getError();
									}
									stReturn += stReturn1;
								}
							}
						}
					} else {
						stError += "<BR>ERROR: process command not implemented: "
						    + stCommand;
					}
				} else {
					break;
				}
			} // while
			if (iPos2 < stContent.length()) {
				stReturn += stContent.substring(iPos2); // copy remainder.
			}
		} else {
			stReturn = stContent;
		}
		return stReturn;
	}

	public String makeMenuBar() {
		String stReturn = "";
		stReturn += "<li class='top-nav nav-home'><a class='topnav' href='./?stAction=home'>Home</a>";
		Map<String, String> recentURLs = (Map<String, String>) this.ebEnt.ebUd.request
		    .getSession().getAttribute("recentURLs");
//		if (recentURLs != null && recentURLs.size() > 0) {
//			stReturn += "<ul>";
//			String[] keys = new String[recentURLs.keySet().size()];
//			keys = recentURLs.keySet().toArray(keys);
//			for (int i = keys.length - 1; i >= 0; i--) {
//				stReturn += "<li><a href='./?" + recentURLs.get(keys[i]) + "'>"
//				    + keys[i] + "</a></li>";
//			}
//			stReturn += "</ul>";
//		}
		stReturn += "</li>";
		try {
			String stSql = "SELECT * FROM teb_table where nmTableType > 0 order by stTableName";
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			String stReport = "";
			String stAdmin = "";
			String stProject = "";
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				if ((rs.getInt("nmProjectPriv") & nmPrivUser) != 0) {
					stProject += "<li><a href='./?stAction=admin&t=12&do=insert'>Create Project</a></li>";
					stProject += "<li><a href='./?stAction=projects&c=allocate'>Allocate</a></li>";
					// stProject +=
					// "<li><a href='./?stAction=projects&c=analprj'>Analyze Project</a></li>";
					// stProject +=
					// "<li><a href='./?stAction=projects&c=analreq'>Analyze Requirements</a></li>";
					// stProject +=
					// "<li><a href='./?stAction=projects&c=analsch'>Analyze Schedule</a></li>";
					// stProject +=
					// "<li><a href='./?stAction=projects&c=apprprj'>Approve Project</a></li>";
					// stProject +=
					// "<li><a href='./?stAction=projects&c=apprreq'>Approve Requirement</a></li>";
					// stProject +=
					// "<li><a href='./?stAction=projects&c=apprsch'>Approve Schedule</a></li>";
					// stProject +=
					// "<li><a href='./?stAction=projects&c=critscor'>Criterion Scoring</a></li>";
					stProject += "<li><a href='./?stAction=projects&t="
					    + rs.getInt("nmTableId") + "'>" + rs.getString("stTableName")
					    + "</a></li>";
					stProject += "<li><a href='javascript:void(0);'>Template</a></li>";
					if (epsUd.stPrj != null && epsUd.stPrj.length() > 0
					    && !epsUd.stPrj.equals("0")) {
						ResultSet rsProject = this.ebEnt.dbDyn
						    .ExecuteSql("SELECT * FROM Projects WHERE RecId=" + epsUd.stPrj);
						if (rsProject.next()) {
							String stPrjLink = "./?stAction=projects&t=12&pk="
							    + this.epsUd.stPrj;
							stProject += "<li><a href='javascript:void(0);'>Current Project</a>"
							    + "	<li><a class='sub-item' href='"
							    + stPrjLink
							    + "&do=xls&child=46'>Analyze Project</a></li>"
							    + "	<li><a class='sub-item' href='"
							    + stPrjLink
							    + "&do=xls&child=26&stType=Approve&nmFrom="
							    + rsProject.getInt("CurrentBaseline")
							    + "&submit="
							    + URLEncoder.encode("Create Baseline", "UTF-8")
							    + "'>Approve</a></li>"
							    + "	<li><a class='sub-item' href='"
							    + stPrjLink
							    + "&do=xls&child=26'>Baseline</a></li>"
							    + "	<li><a class='sub-item' href='./?stAction=projects&c=critscor'>Criterion Score</a></li>"
							    + "	<li><a class='sub-item' href='"
							    + stPrjLink
							    + "&do=edit'>Project Attributes</a></li>"
							    + "	<li><a class='sub-item' href='"
							    + stPrjLink
							    + "&do=xls&child=19'>Requirements</a></li>"
							    + "	<li><a class='sub-item' href='"
							    + stPrjLink
							    + "&do=xls&child=21'>Schedule</a></li>"
							    + "	<li><a class='sub-item' href='"
							    + stPrjLink
							    + "&do=xls&child=34'>Test</a></li>"
							    + "	<li><a class='sub-item' href='javascript:void(0);'>WBS</a></li>"
							    + "</li>";
						}
					}
				}
				if ((rs.getInt("nmReportPriv") & nmPrivUser) != 0) {
					String stLink = "./?stAction=reports&t=" + rs.getInt("nmTableId");
					String stViewLink = stLink + "&submit2="
					    + URLEncoder.encode("View Saved Reports", "UTF-8");
					String stRunLink = stLink + "&submit2="
					    + URLEncoder.encode("Run/Execute Report", "UTF-8");
					String stCustomLink = stLink + "&submit2="
					    + URLEncoder.encode("Custom Report Designer", "UTF-8");
					ResultSet rsR = epsUd.ebEnt.dbDyn
					    .ExecuteSql("SELECT * FROM teb_customreport where stReportType = '"
					        + rs.getString("nmTableId")
					        + "' order by nmDefaultReport DESC,stReportName limit 0,30");

					String stRun = "";
					String stCustom = "";
					while (rsR.next()) {
						stRun += "<li><a href='" + stRunLink + "&customreport="
						    + rsR.getString("RecId") + "'>" + rsR.getString("stReportName")
						    + "</a></li>";
						stCustom += "<li><a href='" + stCustomLink + "&customreport="
								+ rsR.getString("RecId") + "'>" + rsR.getString("stReportName")
								+ "</a></li>";
					}
					stReport += "<li><a class='collapsed' href='"
					    + stLink
					    + "'>"
					    + rs.getString("stTableName")
					    + "</a>"
					    + "<ul>"
					    + "<li><a class='collapsed' href='javascript:void(0);'>Custom Report Designer</a><ul>" +
					    stCustom +
					    "</ul></li>"
					    + "<li><a class='collapsed' href='javascript:void(0);'>Run/Execute Report</a><ul>"
					    + stRun + "</ul></li>" 
					    + "<li><a href='" + stViewLink
					    + "'>View Saved Reports</a></li>" + "</ul>" + "</li>";
				}
				if ((rs.getInt("nmAccessPriv") & nmPrivUser) != 0) {
					if (stAdmin.length() <= 0 && (nmPrivUser & 0x220) != 0) // Ex and PPM
						stAdmin += "<li><a href='./?stAction=admin&c=appr'>Approve Special Days</a></li>";

					if (rs.getInt("nmTableId") == 15) // Options
						stAdmin += "<li><a href='./?stAction=admin&t="
						    + rs.getInt("nmTableId") + "&pk=1&do=edit'>"
						    + rs.getString("stTableName") + "</a></li>";
					else if (rs.getInt("nmTableId") == 9) // Users
					{
						stAdmin += "<li><a href='./?stAction=admin&t="
						    + rs.getInt("nmTableId") + "&do=users'>"
						    + rs.getString("stTableName") + "</a></li>";
					} else {
						stAdmin += "<li><a href='./?stAction=admin&t="
						    + rs.getInt("nmTableId") + "'>" + rs.getString("stTableName")
						    + "</a></li>";
					}
				}
			}
			if (stReport.length() > 0)
				stReturn += "<li class='top-nav nav-reports'><a class='topnav' href='#'>Reports</a><ul>"
				    + stReport + "</ul></li>";

			if (stProject.length() > 0)
				stReturn += "<li class='top-nav nav-projects'><a class='topnav' href='#'>Project</a><ul>"
				    + stProject + "</ul></li>";

			if (stAdmin.length() > 0)
				stReturn += "<li class='top-nav nav-admin'><a class='topnav' href='#'>Administration</a><ul>"
				    + stAdmin + "</ul></li>";
			stReturn += "<li class='top-nav nav-edit'><a class='topnav' href='#'>Edit</a><ul id='edit-menu'>edit-menu-content</ul></li>";

			stReturn += "<li class='top-nav nav-help'><a class='topnav' href='#'>Help</a><ul>";
			stReturn += "<li><a href='./?stAction=help&i=about'>About</a></li>";
			/* AS -- 28Sept2011 -- Issue #66 */
			// stReturn +=
			// "<li><a href='./?stAction=help&i=content'>Help Contents</a></li></ul></li>";
			stReturn += "<li><a href='./?stAction=help&i=content'>Contents</a></li></ul></li>";

			if ((this.ebEnt.ebUd.getLoginPersonFlags() & 0x800) != 0) { // Super User
				                                                          // only
				stReturn += "<li class='top-nav nav-system'><a class='topnav' href='#'>System Admin</a><ul>";
				// stReturn +=
				// "<li><a href='./?a=28&tb=1.d.teb_division'>Division Setup</a></li>";
				stReturn += "<li><a href='./?stAction=tablefield&rebuild=tblcol'>Table/Field Manager</a></li>";
				// stReturn +=
				// "<li><a href='./?stAction=mspmload'>MS Project Load</a></li>";
				stReturn += "<li><a href='./?stAction=tcimport&id2=di'>Data Import</a></li>";
				// stReturn +=
				// "<li><a href='./?stAction=specialdays'>Load Site Special Days</a></li>";
				// stReturn +=
				// "<li><a target=\"_blank\" href='./?b=2002'>QA Admin</a></li>";
				stReturn += "<li><a target=\"_blank\" href='./?b=2001'>QA Manager</a></li>";
				stReturn += "<li><a href='./?a=28&tb=1.e.X25RefContent'>Edit Content</a></li>";
				stReturn += "<li><a href='./?stAction=rescyneb'>Resync EB</a></li>";
				stReturn += "<li><a href='./?stAction=d50d53&commtrace=i'>Full D50/D53</a></li>";
				// stReturn +=
				// "<li><a href='./?stAction=runeob&commtrace=i'>Run EOB now</a></li>";
				stReturn += "<li><a href='./?stAction=loadpage&m="
				    + URLEncoder.encode("End-of-Business Processing", "UTF-8") + "&r="
				    + URLEncoder.encode("./?stAction=runeob&commtrace=i", "UTF-8")
				    + "'>Run EOB now</a></li>";
				stReturn += "</ul></li>";
			}
		} catch (Exception e) {
			this.stError += "<BR> makeMenuBar " + e;
		}
		return stReturn;
	}

	public String getError() {
		if (this.epsUd != null)
			this.stError += this.epsUd.getError();

		return this.stError;
	}
}
