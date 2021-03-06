/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eps.model;

import it.sauronsoftware.cron4j.Scheduler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ederbase.model.EbDatabase;
import com.ederbase.model.EbEnterprise;
import com.ederbase.model.EbMail;
import com.ederbase.model.EbStatic;

class DailyEOBTask implements Runnable {
	public static EpsUserData epsud;
	boolean autoEOB = false;

	public DailyEOBTask(EpsUserData ud) {
		epsud = ud;
		try {
			InputStream is = this.getClass().getClassLoader()
					.getResourceAsStream("config.properties");
			Properties props = new Properties();
			props.load(is);
			autoEOB = Boolean.parseBoolean(props.getProperty("autoEOB"));
		} catch (IOException e) {
			autoEOB = false;
		}
	}

	@Override
	public void run() {
		if (autoEOB)
			epsud.runEOB();
	}
}

class MonthlyEOBTask implements Runnable {
	public static EpsUserData epsud;

	public MonthlyEOBTask(EpsUserData ud) {
		epsud = ud;
	}

	@Override
	public void run() {

		try {
			String stSql = "SELECT RecId FROM Projects";
			ResultSet rs = epsud.ebEnt.dbDyn.ExecuteSql(stSql);
			String pids = "";
			while (rs.next()) {
				pids += rs.getString("RecId") + ",";
			}
			if (pids.length() > 0)
				pids = pids.substring(0, pids.length() - 1);
			rs.close();
			// clean up for deleted projects -- activity #55
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM requirements WHERE nmProjectId NOT IN ("
							+ pids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM schedule WHERE nmProjectId NOT IN ("
							+ pids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM teb_baseline WHERE nmProjectId NOT IN ("
							+ pids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM teb_link WHERE nmProjectId NOT IN ("
							+ pids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM teb_project WHERE nmProjectId NOT IN ("
							+ pids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM teb_refinventory WHERE nmProjectId NOT IN ("
							+ pids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM test WHERE nmProjectId NOT IN ("
							+ pids + ")");
			epsud.ebEnt.dbEnterprise
					.ExecuteUpdate("DELETE FROM x25audittrail WHERE nmProject NOT IN ("
							+ pids + ")");

			// clean up deleted user -- activity #56
			stSql = "SELECT nmUserId FROM users";
			rs = epsud.ebEnt.dbDyn.ExecuteSql(stSql);
			String uids = "";
			while (rs.next()) {
				uids += rs.getString("nmUserId") + ",";
			}
			if (uids.length() > 0)
				uids = uids.substring(0, uids.length() - 1);
			rs.close();
			// clean up for deleted users
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("UPDATE projects SET nmLockUserId=0 WHERE nmLockUserId NOT IN ("
							+ uids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("UPDATE teb_project SET nmUserId=0 WHERE nmUserId NOT IN ("
							+ uids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM teb_allocate WHERE nmUserId NOT IN ("
							+ uids + ")");
			epsud.ebEnt.dbDyn
					.ExecuteUpdate("DELETE FROM v_allocate WHERE nmUserId NOT IN ("
							+ uids + ")");
			epsud.ebEnt.dbEnterprise
					.ExecuteUpdate("DELETE FROM x25audittrail WHERE nmUserId NOT IN ("
							+ uids + ")");
			epsud.ebEnt.dbEnterprise
					.ExecuteUpdate("DELETE FROM x25user WHERE RecId NOT IN ("
							+ uids + ")");
			epsud.ebEnt.dbEnterprise
					.ExecuteUpdate("DELETE FROM x25refuser WHERE nmUserId NOT IN ("
							+ uids + ")");
			// epsud.ebEnt.dbEnterprise.ExecuteUpdate("UPDATE x25refuser SET nmApproveUserId=0 WHERE nmApproveUserId NOT IN ("+uids+")");
			System.out.println("Finish Monthly EOB");
		} catch (SQLException e) {
			System.out.println("Finish Monthly EOB with ERROR: "
					+ e.getMessage());
		}
	}
}

class AnnualEOBTask implements Runnable {
	public static EbDatabase ebd1 = null;

	AnnualEOBTask(EbDatabase ebd) {
		ebd1 = ebd;
	}

	@Override
	public void run() {
		// System.out.println("Current system time: " + new Date());
		// System.out.println("Another minute ticked away...");

		invoke(ebd1);
	}

	public void invoke(EbDatabase ebd) {
		// activity #59: update LC hours already done with daily EOB
		// reset sick days of all user -- activity #60
		String stSql = "update Users set SickDaysToDate=0";
		ebd.ExecuteUpdate(stSql);

		try {
			stSql = "SELECT nmRefId,nmLaborCategoryId,nmActualHours,nmEstimatedHours FROM teb_reflaborcategory rlc, laborcategory lc"
					+ " WHERE rlc.nmRefType=42 and rlc.nmLaborCategoryId = lc.nmLcId and nmActualHours>nmEstimatedHours order by LaborCategory";
			ResultSet rsC = ebd.ExecuteSql(stSql);

			while (rsC.next()) {
				double newExpendedHours = 50;
				double newEstimatedHours = 0;

				String refId = rsC.getString("nmRefId");
				String nmLcId = rsC.getString("nmLaborCategoryId");
				double oldExpendedHours = rsC.getDouble("nmActualHours");
				double oldEstimatedHours = rsC.getDouble("nmEstimatedHours");

				if (oldEstimatedHours <= 0)
					newEstimatedHours = newExpendedHours;
				else if (oldExpendedHours <= 0)
					newEstimatedHours = oldEstimatedHours;
				else
					newEstimatedHours = newExpendedHours * oldEstimatedHours
							/ oldExpendedHours;

				// reset hours -- activity #57,#58
				stSql = "update teb_reflaborcategory set nmActualHours="
						+ newExpendedHours + ",nmEstimatedHours="
						+ newEstimatedHours
						+ " where nmRefType=42 and nmRefId=" + refId
						+ " and nmLaborCategoryId=" + nmLcId;
				ebd.ExecuteUpdate(stSql);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			ebd.ebClose();
		}
	}

}

public class EpsUserData {

	protected ResultSet rsMyDiv = null;
	protected int nmCustomReportId;
	protected int nmTableId;
	protected String stHelp1 = "";
	protected EbEnterprise ebEnt = null;
	protected String stError = "";
	protected EpsEditField epsEf = null;
	private String stChild = "";
	private String stPageTitle = "";
	private String stVersion = "";
	public String stPrj = "";
	public EpsXlsProject epsProject = null;
	public String stUsersDivision = "";
	private int iD50Flags = 0;
	private int iD53Flags = 0;

	public void EbUserData() {
		this.ebEnt = null;
	}

	public void setEbEnt(EbEnterprise ebEnt, String stVersion) {
		this.ebEnt = ebEnt;
		this.epsEf = new EpsEditField();
		this.stVersion = stVersion;
	}

	public void processSubmit(HttpServletRequest request,
			HttpServletResponse response) {
		ebEnt.ebUd.clearPopupMessage();

		// Check for exchange rates
		// Creates the scheduler.
		Scheduler scheduler = null;
		Object sch = request.getSession().getServletContext()
				.getAttribute("scheduler");
		if (sch == null) {
			scheduler = new Scheduler();

			String timeString = "0 0";
			try {
				String dailyTime = rsMyDiv.getString("EndofBusinessDay");
				if (dailyTime != null && dailyTime.length() > 0) {
					String[] tpart = dailyTime.split(":");
					timeString = tpart[1] + " " + tpart[0];
				}
			} catch (SQLException e) {
			}

			DailyEOBTask dailyTask = new DailyEOBTask(this);
			scheduler.schedule(timeString + " * * *", dailyTask);

			MonthlyEOBTask monthlyTask = new MonthlyEOBTask(this);
			scheduler.schedule("1 0 1 * *", monthlyTask);

			AnnualEOBTask annualTask = new AnnualEOBTask(this.ebEnt.dbDyn);
			scheduler.schedule("0 0 1 1 *", annualTask);

			request.getSession().getServletContext()
					.setAttribute("scheduler", scheduler);
		} else {
			scheduler = (Scheduler) sch;
		}

		// Starts the scheduler.
		if (!scheduler.isStarted())
			scheduler.start();

		String stTemp = "";
		stTemp = this.ebEnt.ebUd.request.getLocalAddr();
		int iRecId = 0; // only local here for dynamic
		int iSeqNr = 0;
		this.ebEnt.ebUd.request = request;
		try {
			this.ebEnt.ebUd.aLogin = new String[5];
			for (int i = 0; i < this.ebEnt.ebUd.aLogin.length; i++) {
				this.ebEnt.ebUd.aLogin[i] = "";
			}
			this.ebEnt.ebUd.aCookies = this.ebEnt.ebUd.request.getCookies();
			if (this.ebEnt.ebUd.aCookies != null) {
				for (int i = 0; i < this.ebEnt.ebUd.aCookies.length; i++) {
					Cookie c = this.ebEnt.ebUd.aCookies[i];
					if (c.getName().equals("ui")) {
						// this.ebEnt.ebUd.aLogin[0] =
						// this.ebEnt.EBDecrypt(c.getValue());
						this.ebEnt.ebUd.aLogin[0] = c.getValue();
					}
					if (c.getName().equals("ut")) {
						this.ebEnt.ebUd.aLogin[1] = c.getValue();
					}
					if (c.getName().equals("email")) {
						this.ebEnt.ebUd.aLogin[2] = c.getValue().replace("%40",
								"@");
					}
					if (c.getName().equals("sid")) {
						// this.ebEnt.ebUd.aLogin[4] =
						// this.ebEnt.EBDecrypt(c.getValue());
						this.ebEnt.ebUd.aLogin[4] = c.getValue();
					}
				}
			}
			this.setUser(-1, 0);
			String stLogout = this.ebEnt.ebUd.request.getParameter("Logout");
			if (stLogout != null && stLogout.equals("Logout")) {

				// this.ebEnt.dbEnterprise.ExecuteUpdate("update X25User set nmLastLoginTime=0 where RecId = "
				// + this.ebEnt.ebUd.aLogin[0]);
				@SuppressWarnings("unchecked")
				Set<Integer> ids = (Set<Integer>) this.ebEnt.ebUd.request
						.getSession().getServletContext()
						.getAttribute("LOG_IN_USERS");
				Object userId = this.ebEnt.ebUd.request.getSession()
						.getAttribute("userId");
				if (ids != null && userId != null && ids.contains(userId)) {
					ids.remove(userId);
				}
				this.ebEnt.ebUd.request.getSession().getServletContext()
						.setAttribute("LOG_IN_USERS", ids);
				this.ebEnt.ebUd.request.getSession().invalidate();
				this.ebEnt.ebUd.aLogin[1] = "0";
				Cookie userCookie2 = new Cookie("ut", this.ebEnt.ebUd.aLogin[1]);
				response.addCookie(userCookie2);
				this.ebEnt.ebUd.setRedirect("./");
			} else {
				stTemp = this.ebEnt.ebUd.request.getParameter("Login");
				if (stTemp != null && stTemp.startsWith("Forgot")) {
					ebEnt.ebUd.setRedirect("./?");

					String stEmail = this.ebEnt.ebUd.request.getParameter("f5");

					if (stEmail == null || stEmail.isEmpty())
						return;
					ResultSet rsUser = this.ebEnt.dbDyn
							.ExecuteSql("select * from Users u, ebeps01.X25User xu where "
									+ "u.nmUserId=xu.RecId and stEmail = '"
									+ stEmail + "'");
					if (rsUser == null || !rsUser.next()) {
						return;
					}

					String stPattern = "!@#$%^&*+_.";
					String stPass = "EPPORA";
					stPass += stPattern.charAt(Math.round((float) Math.random()
							* (stPattern.length() - 1)));
					stPass += stPattern.charAt(Math.round((float) Math.random()
							* (stPattern.length() - 1)));
					stPass += stPattern.charAt(Math.round((float) Math.random()
							* (stPattern.length() - 1)));
					stPass += System.currentTimeMillis();

					this.ebEnt.dbEnterprise
							.ExecuteUpdate("update x25user set stPassword=password('"
									+ stPass
									+ "') where RecId="
									+ rsUser.getString("RecId"));

					String stContent = "Your password has been changed to \""
							+ stPass
							+ "\" temporarily. "
							+ "It is recommended that when you log back in you change your password to something that will be easy "
							+ "for you to remember and contains at least one non-alphanumeric character.";

					EbMail ebM = new EbMail(this.ebEnt);
					ebM.setProduction(1);
					ebM.setEbEmail("smtp.gmail.com", "true",
							"EPPORA Do Not Reply",
							"donotreply.eppora@gmail.com",
							"donotreply.eppora@gmail.com", "epsdonotreply");
					iRecId = ebM.sendMail(
							stEmail,
							rsUser.getString("FirstName") + " "
									+ rsUser.getString("LastName"),
							"Password Recovery", stContent, 1);
					if (iRecId > 0) {
						ebEnt.ebUd
								.setPopupMessage("Password is sent to your email address.");
					}
				} else {
					if (stTemp != null && !stTemp.equals("")) {
						this.ebEnt.ebUd.aLogin[0] = "";
						this.ebEnt.ebUd.aLogin[1] = "";
						this.ebEnt.ebUd.aLogin[2] = this.ebEnt.ebUd.request
								.getParameter("f5");
						this.ebEnt.ebUd.aLogin[3] = this.ebEnt.ebUd.request
								.getParameter("f3");
					}

					iRecId = this.ebEnt.ebUd.checkLogin();
					if (iRecId > 0) {
						this.ebEnt.ebUd.request.getSession().setAttribute(
								"userId", iRecId);
						@SuppressWarnings("unchecked")
						Set<Integer> ids = (Set<Integer>) this.ebEnt.ebUd.request
								.getSession().getServletContext()
								.getAttribute("LOG_IN_USERS");
						if (ids == null)
							ids = new HashSet<Integer>();
						ids.add(iRecId);
						this.ebEnt.ebUd.request.getSession()
								.getServletContext()
								.setAttribute("LOG_IN_USERS", ids);

						if (stTemp != null && !stTemp.equals("")) {
							if (this.rsMyDiv != null
									&& this.rsMyDiv
											.getInt("UserQuestionsOnOff") > 0
									&& ebEnt.ebUd.request.getParameterMap()
											.containsKey("f4")) {
								String[] aV = this.ebEnt.dbDyn
										.ExecuteSql1(
												"select Answers from Users where nmUserId="
														+ this.ebEnt.ebUd.aLogin[0])
										.replace(",", "\n").replace("~", "\n")
										.split("\\\n");
								String stAnswer = this.ebEnt.ebUd.request
										.getParameter("f4").trim();
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f6");
								int iIndex = Integer.parseInt(stTemp);
								if (aV != null && aV.length > iIndex) {
									if (!aV[iIndex].trim().equals(stAnswer)) {
										this.ebEnt.ebUd.aLogin[0] = "0";
										this.ebEnt.ebUd.aLogin[1] = "0";
										this.setUser(-1, 0);
										this.ebEnt.ebUd
												.setPopupMessage("Invalid Answer");
										makeTask(5, "Invalid Answer / Email: "
												+ this.ebEnt.ebUd.aLogin[2]); // 5
																				// 1
																				// Failed
																				// Login
																				// Enabled
																				// Yes
									}
								} else {
									this.ebEnt.ebUd.aLogin[0] = "0";
									this.ebEnt.ebUd.aLogin[1] = "0";
									this.setUser(-1, 0);
									this.ebEnt.ebUd
											.setPopupMessage("Answer not found");
									makeTask(5, "Answer not found / Email: "
											+ this.ebEnt.ebUd.aLogin[2]); // 5
																			// 1
																			// Failed
																			// Login
																			// Enabled
																			// Yes
								}
							}
							this.ebEnt.ebUd.setCookie("ui",
									this.ebEnt.ebUd.aLogin[0], 30);
							this.ebEnt.ebUd.setCookie("ut",
									this.ebEnt.ebUd.aLogin[1], 30);
							this.ebEnt.ebUd.setCookie("email",
									this.ebEnt.ebUd.aLogin[2], 30);
							this.ebEnt.ebUd.setCookie("sid",
									this.ebEnt.ebUd.aLogin[4], 30);

							this.epsEf.addAuditTrail(5, "0",
									this.ebEnt.ebUd.aLogin[2]); // This
																// is
																// to
																// LOG
																// the
																// LOGIN
																// event
																// for
																// reporting
						}
					} else {
						if (iRecId == -5) {
							this.ebEnt.ebUd
									.setPopupMessage("E-Mail/Password is being used. \nPlease try again after "
											+ (request.getSession()
													.getMaxInactiveInterval() / 60)
											+ " minutes.");
						} else if (stTemp != null && !stTemp.equals("")) {
							this.ebEnt.ebUd
									.setPopupMessage("E-Mail/Password not found.");
							makeTask(5, "E-Mail/Password not found "
									+ this.ebEnt.ebUd.aLogin[2]); // 5
																	// 1
																	// Failed
																	// Login
																	// Enabled
																	// Yes
							makeMessage("All",
									getAllUsers(getPriviledge("ad")),
									"Illegal Login",
									"Illegal Login with email \""
											+ this.ebEnt.ebUd.aLogin[2]
											+ "\" and password \""
											+ this.ebEnt.ebUd.aLogin[3] + "\"",
									new SimpleDateFormat("MM/dd/yyyy")
											.format(Calendar.getInstance()
													.getTime()));
							iRecId = this.ebEnt.dbEnterprise
									.ExecuteSql1n("select RecId from X25User where stEMail='"
											+ this.ebEnt.ebUd.aLogin[2] + "'");
							if (iRecId > 0) {
								makeMessage("All", "" + iRecId,
										"Illegal Login",
										"Illegal Login with email \""
												+ this.ebEnt.ebUd.aLogin[2]
												+ "\" and password \""
												+ this.ebEnt.ebUd.aLogin[3]
												+ "\"", new SimpleDateFormat(
												"MM/dd/yyyy").format(Calendar
												.getInstance().getTime()));
							}
						}
					}
				}
				// rest moved to savedata section !!
			}
			stPrj = this.ebEnt.ebUd.request.getParameter("f8");
			if (stPrj == null || stPrj.length() <= 0) {
				stPrj = this.ebEnt.ebUd.getCookieValue("f8");
				if (stPrj == null) {
					stPrj = "";
				}
			} else {
				this.ebEnt.ebUd.setCookie("f8", stPrj, 30);
			}
		} catch (Exception e) {
			stError += "processSubmit Exception: " + e;
		}
	}

	private String makeDbField(ResultSet rsF) {
		String stReturn = "";
		int nmDbType = EpsStatic.ciDatabaseTypeMysql;
		try {
			int nmFlags = rsF.getInt("nmFlags");
			int iMax = rsF.getInt("nmMaxBytes");
			String stDefault = rsF.getString("stDefaultValue");
			String stNull = rsF.getString("stNull");
			String stExtra = rsF.getString("stExtra");
			if (stDefault == null) {
				stDefault = "";
			} else {
				stDefault = stDefault.trim();
			}
			if (stNull == null) {
				stNull = "";
			}
			if (stExtra == null) {
				stExtra = "";
			}
			stReturn += ",\n" + rsF.getString("stDbFieldName").replace("'", "")
					+ " ";
			switch (rsF.getInt("nmDataType")) {
			case 1: // Int
				stReturn += "int ";
				if (stDefault.length() <= 0) {
					stDefault = "0";
				}
				break;
			case 9: // choice
				if ((nmFlags & 0x44000080) == 0) {
					stReturn += " int ";
					if (stDefault.length() <= 0) {
						stDefault = "0";
					}
				} else if ((nmFlags & 0x80) != 0) { // MULTI CHOICE
					if (nmDbType == EpsStatic.ciDatabaseTypeMysql) {
						stReturn += "MEDIUMTEXT ";
						stDefault = null; // MySQL can't have default in
											// blob/text
					} else {
						stReturn += " text ";
					}
				} else { // Fraction and non int choices go here
					stReturn += " varchar(255) ";
				}
				break;
			case 4: // Long Text
			case 32: // Long Text
			case 39: // Tilde List
			case 40: // Special Days
				if (stDefault.length() <= 0) {
					stDefault = null;
				}
				if (nmDbType == EpsStatic.ciDatabaseTypeMysql) {
					stReturn += "longtext ";
				} else {
					stReturn += "text ";
				}
				break;
			case 6: // binary blob
				stDefault = null;
				if (nmDbType == EpsStatic.ciDatabaseTypeMysql) {
					stReturn += "longblob ";
				} else {
					stReturn += "image ";
				}
				break;
			case 5: // MONEY
			case 28: // MONEY
			case 29: // MONEY
			case 30: // MONEY
			case 31: // decimal number
				stReturn += "double ";
				if (stDefault.length() <= 0) {
					stDefault = "0";
				}
				break;
			case 20: // date only
				stDefault = null;
				stReturn += "date ";
				break;
			case 8: // datetime
				stDefault = null;
				stReturn += "datetime ";
				break;
			case 3:
			default:
				if (iMax <= 0) {
					iMax = 255;
				}
				stReturn += "varchar(" + iMax + ")";
				break;
			}
			if (stDefault != null) {
				stReturn += " not null default '" + stDefault.replace("'", "")
						+ "' ";
			} else {
				if (stNull.length() > 0
						&& stNull.toLowerCase().substring(0, 1).equals("n")) {
					stReturn += " not null ";
				} else {
					stReturn += " null ";
				}
			}
			if (stExtra.toLowerCase().equals("auto_increment")) {
				stReturn += " AUTO_INCREMENT ";
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR: makeDbField " + e;
		}
		return stReturn;
	}

	public String epsLoadSpecialDays() {
		String stReturn = "<h1>Resetting Calendar</h1>";
		String stEvent = "";
		String stTemp = "";
		String stSql = "";
		int iL = 0;
		int iD = 0;
		String[] aLines;
		try {
			String stTa = this.ebEnt.ebUd.request.getParameter("taNOTUSED");
			// stTa = "";
			this.ebEnt.dbDyn
					.ExecuteUpdate("delete from Calendar where stType='Holiday' ");
			if (stTa != null) {
				this.ebEnt.dbDyn.ExecuteUpdate("truncate table Calendar ");
				stTa = stTa.trim().replace("st ", " ");
				stTa = stTa.replace("nd ", " ");
				stTa = stTa.replace("rd ", " ");
				stTa = stTa.replace("th ", " ");
				aLines = stTa.split("\n");
				for (iL = 0; iL < aLines.length; iL++) {
					if (aLines[iL].contains("Easter Sunday")) {
						stEvent = "Easter Sunday";
					} else {
						String[] aV = aLines[iL].split("  ");
						for (int i = 0; i < aV.length; i++) {
							stTemp = aV[i].trim();
							if (stTemp.length() > 0) {
								String[] v = stTemp.split(" ");
								stSql = "replace into Calendar (dtDay,stEvent,stDivisions) values(\""
										+ v[2]
										+ "-"
										+ EpsStatic.getMonth(v[1])
										+ "-"
										+ v[0]
										+ "\",\""
										+ stEvent
										+ "\",\"0\" ); ";
								this.ebEnt.dbDyn.ExecuteUpdate(stSql);
							}
						}
					}
				}
				ResultSet rsCal = this.ebEnt.dbDyn
						.ExecuteSql("SELECT * FROM teb_division");
				rsCal.last();
				int iMax = rsCal.getRow();
				int iDay = 0;
				int iMonth = 0;
				int iWeekday = 0;
				for (int iYear = 2000; iYear < 2099; iYear++) {
					for (iD = 1; iD <= iMax; iD++) {
						rsCal.absolute(iD);
						stTa = rsCal.getString("stHolidays");
						aLines = stTa.trim().split("\n");
						for (iL = 0; iL < aLines.length; iL++) {
							if (aLines[iL].trim().length() <= 0) {
								continue; // skipe empty lines
							}
							String[] aV = aLines[iL].trim().split("-");
							if (aV.length > 0) {
								stTemp = aV[1].trim();
							} else {
								stTemp = "";
							}
							if (stTemp.contains(" of ")
									|| stTemp.contains(" in ")) {
								Calendar calendar = Calendar.getInstance();
								calendar.set(Calendar.YEAR, iYear);
								if (stTemp.contains("3rd Monday of January")) {
									iMonth = 1;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 1);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									// SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
									// THURSDAY, FRIDAY, and
									// SATURDAY.
									// 1 2 3 4 5 6 7
									if (iWeekday != 2) {
										if (iWeekday > 2) {
											iDay = (7 - iWeekday) + 3;
										} else {
											iDay = 2;
										}
									} else {
										iDay = 1;
									}
									iDay += 14;
								} else if (stTemp
										.contains("3rd Monday of February")
										|| stTemp
												.contains("Third Monday in February")) {
									iMonth = 2;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 1);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									if (iWeekday != 2) {
										if (iWeekday > 2) {
											iDay = (7 - iWeekday) + 3;
										} else {
											iDay = 2;
										}
									} else {
										iDay = 1;
									}
									iDay += 14;
								} else if (stTemp
										.contains("4th Thursday of November")) {
									iMonth = 11;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 1);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									// SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
									// THURSDAY, FRIDAY, and
									// SATURDAY.
									// 1 2 3 4 5 6 7
									if (iWeekday != 5) {
										if (iWeekday > 5) {
											iDay = (7 - iWeekday) + 6;
										} else {
											iDay = (5 - iWeekday) + 1;
										}
									} else {
										iDay = 1;
									}
									iDay += 21;
								} else if (stTemp
										.contains("2nd Monday of October")
										|| stTemp
												.contains("Second Monday in October")) {
									iMonth = 10;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 1);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									if (iWeekday != 2) {
										if (iWeekday > 2) {
											iDay = (7 - iWeekday) + 3;
										} else {
											iDay = 2;
										}
									} else {
										iDay = 1;
									}
									iDay += 7;
								} else if (stTemp
										.contains("first Monday of September")
										|| stTemp
												.contains("First Monday in September")) {
									iMonth = 9;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 1);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									if (iWeekday != 2) {
										if (iWeekday > 2) {
											iDay = (7 - iWeekday) + 3;
										} else {
											iDay = 2;
										}
									} else {
										iDay = 1;
									}
								} else if (stTemp.contains("2nd Sunday of May")) {
									// SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
									// THURSDAY, FRIDAY, and
									// SATURDAY.
									// 1 2 3 4 5 6 7
									iMonth = 5;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 1);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									if (iWeekday != 1) {
										if (iWeekday > 1) {
											iDay = (7 - iWeekday) + 1;
										}
									} else {
										iDay = 1;
									}
									iDay += 7;
								} else if (stTemp
										.contains("3rd Sunday of June")) {
									// SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
									// THURSDAY, FRIDAY, and
									// SATURDAY.
									// 1 2 3 4 5 6 7
									iMonth = 6;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 1);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									if (iWeekday != 1) {
										if (iWeekday > 1) {
											iDay = (7 - iWeekday) + 1;
										}
									} else {
										iDay = 1;
									}
									iDay += 14;
								} else if (stTemp
										.contains("last Monday of May")) {
									// SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
									// THURSDAY, FRIDAY, and
									// SATURDAY.
									// 1 2 3 4 5 6 7
									iMonth = 5;
									calendar.set(Calendar.MONTH, iMonth - 1);
									calendar.set(Calendar.DAY_OF_MONTH, 31);
									iWeekday = calendar
											.get(Calendar.DAY_OF_WEEK);
									if (iWeekday != 2) {
										if (iWeekday > 2) {
											iDay = 31 - (iWeekday - 2);
										} else {
											iDay = 31 - 6;
										}
									} else {
										iDay = 31;
									}
								} else {
									stError += "<BR> " + stTemp;
								}
							} else {
								try {
									String[] v = aV[0].split(" ");
									iDay = Integer.parseInt(v[0]);
									iMonth = EpsStatic.getMonth(v[1].trim());
								} catch (Exception e) {
									iMonth = -1;
								}
							}
							if (stTemp.length() > 0) {
								String[] v = stTemp.split("\\(");
								stEvent = v[0].trim();
							} else {
								stEvent = "??";
							}
							if (iMonth > 0) {
								stSql = "replace into Calendar (dtDay,stEvent,nmDivision) values(\""
										+ iYear
										+ "-"
										+ iMonth
										+ "-"
										+ iDay
										+ "\",\""
										+ stEvent
										+ "\",\""
										+ rsCal.getString("nmDivision")
										+ "\" ); ";
								this.ebEnt.dbDyn.ExecuteUpdate(stSql);
							}
						}
					}
				}
				stReturn += "<br>END: LoadSpecialDays ";
			} else {
				stReturn += "</form><form method=post><table><tr><td><textarea name=ta rows=20 cols=100></textarea></td></tr>";
				stReturn += "<tr><td><input type=submit name=submit value=Submit></td></tr>";
				stReturn += "</table></form>";
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR: epsLoadSpecialDays " + e;
		}
		return stReturn;
	}

	public String getActionPage(String stAction) {
		if (stAction.equals("loadpage")) {
			return this.loadingPage();
		}
		if (stAction.equals("runeob")) {
			return this.runEOB();
		} else if (stAction.equals("d50d53")) {
			return this.runD50D53();
		} else if (stAction.equals("rescyneb")) {
			return this.ebEnt.ebAdmin.getResetEb();
		} else if (stAction.equals("tcimport")) {
			epsProject = new EpsXlsProject();
			epsProject.setEpsXlsProject(this.ebEnt, this);
			return epsProject.epsLoadImport();
		} else if (stAction.equals("specialdays")) {
			return epsLoadSpecialDays();
		} else if (stAction.equals("tablefield")) {
			return epsTableField();
		} else if (stAction.equals("admin")) {
			return epsTable("Administration");
		} else if (stAction.equals("projects")) {
			return epsTable("Projects");
		} else if (stAction.equals("reports")) {
			return epsReport();
		} else if (stAction.equals("help")) {
			return epsHelp();
		} else if (stAction.equals("home")) {
			return makeHomePage();
		} else {
			return "NOT YET IMPLEMENTED: stAction=" + stAction;
		}
	}

	private String epsHelp() {
		String stReturn = "<center><table border=1 width='100%'><tr><td>";
		String stHelp = "";
		String stInfo = this.ebEnt.ebUd.request.getParameter("i");
		if (stInfo == null || stInfo.equals("about")) {
			this.setPageTitle("Help About");
			// This version, V1.5, was released 1 August 2011.
			stHelp = "<h1>Help About</h1><p>Enterprise Project Portfolio Optimized Resource Allocation (EPPORA) is an "
					+ "Enterprise Portfolio Software, Inc. (EPS) product.  This "
					+ stVersion
					+ ".  "
					+ "EPPORA&apos;s functionality is described in the EPPORA User&apos;s Guide, which can be accessed using the Help "
					+ "tab &quot;Content&quot; command.  For any questions contact EPS at 310-287-0800 or email EPS at:<br><br><table>";
			stHelp += "<tr><td>&nbsp;&nbsp;</td><td>Technical</td><td><a href='mailto:t@EPPORA.com'>t@EPPORA.com</a></td></tr>";
			stHelp += "<tr><td>&nbsp;&nbsp;</td><td>Marketing</td><td><a href='mailto:m@EPPORA.com'>m@EPPORA.com</a></td></tr>";
			stHelp += "<tr><td>&nbsp;&nbsp;</td><td>Sales</td><td><a href='mailto:s@EPPORA.com'>s@EPPORA.com</a></td></tr>";
			stHelp += "<tr><td>&nbsp;&nbsp;</td><td>Employment</td><td><a href='mailto:e@EPPORA.com'>e@EPPORA.com</a></td></tr></table><br>&nbsp;";
		} else {
			// openurl|./common/help/index.html
			this.setPageTitle("Help Contents");
			stHelp = this.ebEnt.ebUd.UrlReader("./common/help/index.html");
		}
		return stReturn + stHelp + "</td></tr></table><br/>";
	}

	private String epsTableField() {
		String stReturn = "<center><h1>Table Setup</h1>";
		String stSql = "";
		String stValue = "";
		String stBottom = "";
		String stFields = "";
		String stTop = "";
		String stPk = "";
		stReturn += "<table><tr><td colspan=3>Raw Edit tables: ";
		stReturn += "&nbsp;&nbsp;&nbsp;&nbsp;<a href='./?a=28&tb=1.d.teb_table'>teb_table</a>";
		stReturn += "&nbsp;&nbsp;&nbsp;&nbsp;<a href='./?a=28&tb=1.d.teb_fields'>teb_fields</a>";
		stReturn += "&nbsp;&nbsp;&nbsp;&nbsp;<a href='./?a=28&tb=1.d.teb_epsfields'>teb_epsfields</a><br /></td></tr>";
		stReturn += "<tr><th class=l1th>Step</th><th class=l1th>Action</th><th class=l1th>Description</th></tr>";
		stReturn += "<tr><td>1</td><td><a href='./?stAction=tablefield&rebuild=schema'>Rebuild Table Schema</a></td><td>"
				+ "Will build the table schema and store in teb_table.  Will NOT touch existing tables.</td></tr>";
		stReturn += "<tr><td>2</td><td><a href='./?stAction=tablefield&rebuild=table'>Rebuild Tables</a></td><td>"
				+ "<font color=red>DESTRUCTIVE: Will DELETE ALL TABLES AND CONTENTS and then rebuild them EMPTY</font></td></tr>";
		stReturn += "<tr><td>3</td><td><a href='./?stAction=tablefield&loadold=y'>Load (old): Labor Categories, Criteria etc.</a></td><td>"
				+ "Will load initial values in tables.  You can change the contents.</td></tr>";
		stReturn += "<tr><td>4</td><td><a href='./?stAction=tablefield&loadold=users'>Load Test Users</a></td><td>"
				+ "Will load 19,338 Test Users. First/Last initials are set as: A D (46) Admin, B A (22), E E (14) Exec, S U (6), "
				+ "P M (82), P P (26) rest as PTM. Will go in circular mode through all divisions per pair. Emails are set "
				+ "as aa01@eppora.com to zz99@eppora.com (takes abmout 2mins to run)</td></tr>";
		stReturn += "</table>";
		stReturn += "<br />";
		int iMax = 0;
		int iR = 0;
		ResultSet rs = null;
		try {
			String stAction = this.ebEnt.ebUd.request.getParameter("rebuild");
			if (stAction != null && stAction.equals("schema")) {
				stReturn += "<h1>Rebuilding Schema</h1>";
				stSql = "select * from teb_table order by stTableName";
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.last();
				iMax = rs.getRow();
				for (iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					stValue = rs.getString("stDbTableName");
					if (stValue == null || stValue.trim().length() <= 0) {
						this.ebEnt.dbDyn
								.ExecuteUpdate("update teb_table set stDbTableName = \""
										+ rs.getString("stTableName").trim()
												.replace(" ", "_")
										+ "\" where nmTableId="
										+ rs.getString("nmTableId"));
					}
				}
				stSql = "select * from teb_fields";
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.last();
				iMax = rs.getRow();
				for (iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					stValue = rs.getString("stDbFieldName");
					String stValueOrig = stValue;
					stValue = stValue.replace("'", "");
					stValue = stValue.replace("(", "");
					stValue = stValue.replace(")", "");
					stValue = stValue.replace("-", "");
					stValue = stValue.replace("%", "");
					stValue = stValue.replace("__", "_");
					stValue = stValue.replace("__", "_");
					if (!stValue.equals(stValueOrig)) {
						this.ebEnt.dbDyn
								.ExecuteUpdate("update teb_fields set stDbFieldName = \""
										+ stValue
										+ "\" where stDbFieldName = \""
										+ stValueOrig + "\" ");
					}
				}
				stSql = "select * from teb_table where nmTableType > 0 ";
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.last();
				iMax = rs.getRow();
				for (iR = 1; iR <= iMax; iR++) {
					stFields = stTop = stBottom = stPk = "";
					rs.absolute(iR);
					stTop = rs.getString("stTop");
					stBottom = rs.getString("stBottom");
					stPk = rs.getString("stPk");
					if (rs.getInt("nmTableType") > 0) {
						if (rs.getInt("nmTableType") != 3) {
							stSql = "select * from teb_fields f, teb_epsfields ef where f.nmForeignId = ef.nmForeignId "
									+ " and f.nmTabId in ("
									+ rs.getString("stTabList") + ") ";
							ResultSet rsF = this.ebEnt.dbDyn.ExecuteSql(stSql);
							rsF.last();
							int iFieldMax = rsF.getRow();
							for (int iF = 1; iF <= iFieldMax; iF++) {
								rsF.absolute(iF);
								stFields += makeDbField(rsF);
							}
						} else {
							stFields = rs.getString("stFields");
						}
						switch (rs.getInt("nmTableType")) {
						case 1: // User table
							stReturn += "<br> building user "
									+ rs.getString("stDbTableName");
							if (stTop.length() <= 0) {
								stTop = "\n nmUserId int not null, \n nmDivision int not null default 0";
							}
							if (stPk.length() <= 0) {
								stPk = "nmUserId";
							}
							if (stBottom.length() <= 0) {
								stBottom = ",\n PRIMARY KEY (" + stPk + ")";
							}
							break;
						case 2: // Labor Category, etc. Simple tables
							stReturn += "<br> building normal table: "
									+ rs.getString("stDbTableName");
							if (stTop.length() <= 0) {
								stTop = "\n RecId int not null auto_increment";
							}
							if (stPk.length() <= 0) {
								stPk = "RecId";
							}
							if (stBottom.length() <= 0) {
								stBottom = ",\n PRIMARY KEY (" + stPk + ")";
							}
							break;
						case 3: // from table only. use this after making
								// manuall changes to
								// schema.
							break;
						default:
							this.stError += "<BR>ERROR: Rebuilding Schema nmTableType="
									+ rs.getInt("nmTableType")
									+ " not implemented. ";
							break;
						}
						this.ebEnt.dbDyn
								.ExecuteUpdate("update teb_table set stTop=\""
										+ stTop + "\",stPk=\"" + stPk
										+ "\",stBottom=\"" + stBottom
										+ "\",stFields=\"" + stFields
										+ "\" where nmTableId="
										+ rs.getString("nmTableId"));
					}
				}
			} else if (stAction != null && stAction.equals("table")) {
				stSql = "select * from teb_table where nmTableType > 0 ";
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.last();
				iMax = rs.getRow();
				for (iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					stSql = "DROP TABLE IF EXISTS "
							+ rs.getString("stDbTableName");
					this.ebEnt.dbDyn.ExecuteUpdate(stSql);
					stSql = "CREATE TABLE " + rs.getString("stDbTableName")
							+ "(";
					stSql += rs.getString("stTop");
					stSql += rs.getString("stFields");
					stSql += rs.getString("stBottom");
					stSql += ");";
					this.ebEnt.dbDyn.ExecuteUpdate(stSql);
				} // good house keeping
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("delete from X25User where RecId > 1000");
				// this.ebEnt.dbEnterprise.ExecuteUpdate("truncate table X25User");
				// this.ebEnt.dbEnterprise.ExecuteUpdate("replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(1,'roberteder@myinfo.com',65535,password('EPS###'))");
				/* Start of change AS -- 26Sept2011 -- Issue#2 */
				/*
				 * this.ebEnt.dbEnterprise.ExecuteUpdate(
				 * "replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(2,'joeledfleiss@yahoo.com',65535,password('EPS###'))"
				 * ); this.ebEnt.dbEnterprise.ExecuteUpdate(
				 * "replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(3,'jf@eppora.com',65535,password('EPS###'))"
				 * ); this.ebEnt.dbEnterprise.ExecuteUpdate(
				 * "replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(3,'jf@eppora.com',65535,password('EPS###'))"
				 * ); this.ebEnt.dbEnterprise.ExecuteUpdate(
				 * "replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(5,'eob@eppora.com',65535,password('EPS###'))"
				 * ); this.ebEnt.dbEnterprise.ExecuteUpdate(
				 * "replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(6,'ll@eppora.com',65535,password('EPS###'))"
				 * );
				 */
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(1,'xancar1986@gmail.com',65535,password('ABCs1234'))");
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(2,'joeledfleiss@yahoo.com',65535,password('ABCs1234'))");
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(3,'devonkim@yahoo.com',65535,password('ABCs1234'))");
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(5,'eob@eppora.com',65535,password('ABCs1234'))");
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("replace into X25User (RecId,stEMail,nmPriviledge,stPassword) values(6,'ll@eppora.com',65535,password('ABCs1234'))");
				/* End of change AS -- 26Sept2011 -- Issue#2 */
				this.ebEnt.dbDyn
						.ExecuteUpdate("truncate table teb_reflaborcategory");
				this.ebEnt.dbDyn
						.ExecuteUpdate("truncate table teb_refdivision");
				this.ebEnt.dbDyn.ExecuteUpdate("truncate table Users");
				/* Start of change AS -- 26Sept2011 -- Issue#4 */
				/*
				 * this.ebEnt.dbDyn.ExecuteUpdate(
				 * "replace into Users (nmUserId,FirstName,LastName,Answers) values(1,'Rob','Eder','Tucker~Lakers~Blue')"
				 * ); this.ebEnt.dbDyn.ExecuteUpdate(
				 * "replace into Users (nmUserId,FirstName,LastName,Answers) values(2,'Joel','Fleiss','Tucker~Lakers~Blue')"
				 * ); this.ebEnt.dbDyn.ExecuteUpdate(
				 * "replace into Users (nmUserId,FirstName,LastName,Answers) values(3,'Joel E','Fleiss','Tucker~Lakers~Blue')"
				 * );
				 */
				this.ebEnt.dbDyn
						.ExecuteUpdate("replace into Users (nmUserId,FirstName,LastName,Answers) values(1,'Arun','Shankar','Tucker~Lakers~Blue')");
				this.ebEnt.dbDyn
						.ExecuteUpdate("replace into Users (nmUserId,FirstName,LastName,Answers) values(2,'Joel','Fleiss','Tucker~Lakers~Blue')");
				this.ebEnt.dbDyn
						.ExecuteUpdate("replace into Users (nmUserId,FirstName,LastName,Answers) values(3,'Devon','Kim','Tucker~Lakers~Blue')");
				/* End of change AS -- 26Sept2011 -- Issue#4 */

				this.ebEnt.dbDyn
						.ExecuteUpdate("replace into Users (nmUserId,FirstName,LastName,Answers) values(5,'','EOB','Tucker~Lakers~Blue')");
				this.ebEnt.dbDyn
						.ExecuteUpdate("replace into Users (nmUserId,FirstName,LastName,Answers) values(6,'Lawrence','Le Blanc','Tucker~Lakers~Blue')");
				this.ebEnt.dbDyn
						.ExecuteUpdate("update teb_fields set nmCols = nmMaxBytes  where nmCols=0 and nmTabId > 0 and nmMaxBytes > 0");
				this.ebEnt.dbDyn.ExecuteUpdate("truncate table teb_reports");
				this.ebEnt.dbDyn
						.ExecuteUpdate("truncate table teb_reportcolumns");
				this.ebEnt.dbDyn
						.ExecuteUpdate("truncate table teb_customreport");
				this.ebEnt.dbDyn
						.ExecuteUpdate("insert into teb_customreport (RecId,stReportType,stReportName) values( 1,19,'Requirements')");
				this.ebEnt.dbDyn
						.ExecuteUpdate("insert into teb_customreport (RecId,stReportType,stReportName) values( 2,21,'Schedules')");
				this.ebEnt.dbDyn
						.ExecuteUpdate("insert into teb_customreport (RecId,stReportType,stReportName) values( 3,34,'Test')");
				this.ebEnt.dbDyn
						.ExecuteUpdate("insert into teb_reportcolumns SELECT * FROM tmpreportcolumns");
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("truncate table X25RefTask");
				this.ebEnt.dbEnterprise.ExecuteUpdate("truncate table X25Task");
			} else if (stAction != null && stAction.equals("tblcol2")) {
				String stId = this.ebEnt.ebUd.request.getParameter("t");
				stSql = "select f.*,ef.nmOrderDisplay,ef.nmPriv,ef.stHandler from teb_fields f left join teb_epsfields ef on f.nmForeignId=ef.nmForeignId where f.nmTabId="
						+ stId + " order by ef.nmOrderDisplay,f.nmForeignId";
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.last();
				iMax = rs.getRow();
				stReturn += "</form><form method=post><table><tr><th class=l1th>ID</th><th class=l1th>Ord</th><th class=l1th>DT</th>"
						+ "<th class=l1th>Flags</th><th class=l1th>T</th><th class=l1th>DbFldNm</th><th class=l1th>Label</th>"
						+ "<th class=l1th>Mn</th><th class=l1th>Mx</th><th class=l1th>Rw</th><th class=l1th>Cl</th><th class=l1th>Validation</th><th class=l1th>Priv</th>"
						+ "<th class=l1th>Hdr</th><th class=l1th>Srch</th><th class=l1th>Default</th><th class=l1th>Handler</th></tr>";
				int nmOrderDisplay = 0;
				int iRecId2 = 0;
				int iRecId = 0;
				String stSave = this.ebEnt.ebUd.request.getParameter("save");
				for (iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					String stTemp = rs.getString("nmOrderDisplay");
					if (stTemp == null) {
						this.ebEnt.dbDyn
								.ExecuteUpdate("insert into teb_epsfields (nmForeignId) values("
										+ rs.getString("nmForeignId") + ") ");
					}
					iRecId = iRecId2 = rs.getInt("nmForeignId");
					/* TODO: Insert */
					stReturn += "<tr>";
					stReturn += "<td align=right>"
							+ rs.getString("nmForeignId") + "</td>";
					if (stSave != null && stSave.length() > 0) {
						stSql = "update teb_fields set " + "nmDataType=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmDataType_" + iRecId)
								+ "\","
								+ "nmTabId=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmTabId_" + iRecId)
								+ "\","
								+ "nmFlags="
								+ this.ebEnt.ebUd.request
										.getParameter("nmFlags_" + iRecId)
								+ ","
								+ "stDbFieldName=\""
								+ this.ebEnt.ebUd.request
										.getParameter("stDbFieldName_" + iRecId)
								+ "\","
								+ "stLabel=\""
								+ this.ebEnt.ebUd.request
										.getParameter("stLabel_" + iRecId)
								+ "\","
								+ "nmMinBytes=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmMinBytes_" + iRecId)
								+ "\","
								+ "nmMaxBytes=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmMaxBytes_" + iRecId)
								+ "\","
								+ "nmRows=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmRows_" + iRecId)
								+ "\","
								+ "nmCols=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmCols_" + iRecId)
								+ "\","
								+ "stValidation=\""
								+ this.ebEnt.ebUd.request
										.getParameter("stValidation_" + iRecId)
								+ "\","
								+ "nmHeaderOrder=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmHeaderOrder_" + iRecId)
								+ "\","
								+ "stDefaultValue=\""
								+ this.ebEnt.ebUd.request
										.getParameter("stDefaultValue_"
												+ iRecId)
								+ "\","
								+ "nmOrder2=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmOrder2_" + iRecId)
								+ "\"" + " where nmForeignId=" + iRecId2;
						stReturn += "<td align=left class=small>" + stSql
								+ "</td>";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
						stSql = "update teb_epsfields set "
								+ "nmOrderDisplay=\""
								+ this.ebEnt.ebUd.request
										.getParameter("nmOrderDisplay_"
												+ iRecId)
								+ "\","
								+ "stHandler=\""
								+ this.ebEnt.ebUd.request
										.getParameter("stHandler_" + iRecId)
								+ "\","
								+ "nmPriv="
								+ this.ebEnt.ebUd.request
										.getParameter("nmPriv_" + iRecId) + ""
								+ " where nmForeignId=" + iRecId2;
						stReturn += "<td align=left class=small>" + stSql
								+ "</td>";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
					} else {
						iRecId = -1;
						nmOrderDisplay += 10;
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmOrderDisplay_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ nmOrderDisplay + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmDataType_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmDataType") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:30px' type=text name=nmFlags_"
								+ rs.getString("nmForeignId")
								+ " value=\"0x"
								+ Long.toHexString(rs.getInt("nmFlags"))
								+ "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmTabId_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmTabId") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:160px' type=text name=stDbFieldName_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("stDbFieldName") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:160px' type=text name=stLabel_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("stLabel") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmMinBytes_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmMinBytes") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmMaxBytes_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmMaxBytes") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmRows_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmRows") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmCols_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmCols") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:100px' type=text name=stValidation_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("stValidation") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:30px' type=text name=nmPriv_"
								+ rs.getString("nmForeignId")
								+ " value=\"0x"
								+ Long.toHexString(rs.getInt("nmPriv"))
								+ "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmHeaderOrder_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmHeaderOrder") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmOrder2_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("nmOrder2") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:100px' type=text name=stDefaultValue_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("stDefaultValue") + "\"></td>";
						stReturn += "<td align=right><input class=small style='width:100px' type=text name=stHandler_"
								+ rs.getString("nmForeignId")
								+ " value=\""
								+ rs.getString("stHandler") + "\"></td>";
					}
					stReturn += "<tr>";
				}
				if (stSave != null && stSave.length() > 0) {
					stReturn += "<tr><th colspan=13 align=center>Columns have been updated</th></tr>";
				} else {
					stReturn += "<tr>";
					stReturn += "<td align=right>New</td>";
					nmOrderDisplay += 10;
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmOrderDisplay_-1 value=\""
							+ nmOrderDisplay + "\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmDataType_-1 value=\"3\"></td>";
					stReturn += "<td align=right><input class=small style='width:30px' type=text name=nmFlags_-1 value=\"0x1\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmTabId_-1 value=\"\"></td>";
					stReturn += "<td align=right><input class=small style='width:160px' type=text name=stDbFieldName_-1 value=\"\"></td>";
					stReturn += "<td align=right><input class=small style='width:160px' type=text name=stLabel_-1 value=\"\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmMinBytes_-1 value=\"0\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmMaxBytes_-1 value=\"32\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmRows_-1 value=\"0\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmCols_-1 value=\"0\"></td>";
					stReturn += "<td align=right><input class=small style='width:100px' type=text name=stValidation_-1 value=\"\"></td>";
					stReturn += "<td align=right><input class=small style='width:30px' type=text name=nmPriv_-1 value=\"0x0\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmHeaderOrder_-1 value=\"0\"></td>";
					stReturn += "<td align=right><input class=small style='width:20px' type=text name=nmOrder2_-1 value=\"0\"></td>";
					stReturn += "<td align=right><input class=small style='width:100px' type=text name=stDefaultValue_-1 value=\"\"></td>";
					stReturn += "<td align=right><input class=small style='width:100px' type=text name=stHandler_-1 value=\"\"></td>";
					stReturn += "<tr><th colspan=13 align=center><input type=submit name=save value='Save'></th></tr>";
				}
				stReturn += "</table>";
			} else if (stAction != null && stAction.equals("tblcol")) {
				stSql = "select * from teb_table where nmTableType > 0 order by stTableName";
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.last();
				iMax = rs.getRow();
				stReturn += "<table class=l1table><tr><th class=l1th>Priv</th><th class=l1th>Table Name</th><th class=l1th>Data Columns</th><th class=l1th>Header Columns</th><th class=l1th>Search Columns</th></tr>";
				for (iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					stReturn += "<tr>";
					stReturn += "<td class=l1td align=right>";
					if (rs.getInt("nmAccessPriv") != 0) {
						stReturn += "A: 0x"
								+ Long.toHexString(rs.getInt("nmAccessPriv"));
					}
					if (rs.getInt("nmReportPriv") != 0) {
						stReturn += " R: 0x"
								+ Long.toHexString(rs.getInt("nmReportPriv"));
					}
					if (rs.getInt("nmProjectPriv") != 0) {
						stReturn += " P: 0x"
								+ Long.toHexString(rs.getInt("nmProjectPriv"));
					}
					stReturn += "<br>C: 0x"
							+ Long.toHexString(rs.getInt("nmCreatePriv"));
					stReturn += "<br>E: 0x"
							+ Long.toHexString(rs.getInt("nmEditPriv"));
					stReturn += "<br>D: 0x"
							+ Long.toHexString(rs.getInt("nmDeletePriv"));
					stReturn += "</td><td class=l1td><a href='./?a=28&tb=1.d.teb_table&tid="
							+ rs.getString("nmTableId")
							+ "'>"
							+ rs.getString("stTableName")
							+ "</a>"
							+ "<br><a href='./?stAction=tablefield&rebuild=tblcol2&t="
							+ rs.getString("nmTableId")
							+ "'>Columns</a>"
							+ "<br>" + rs.getString("stDbTableName") + "</td>";
					stSql = "select * from teb_fields f, teb_epsfields ef where f.nmForeignId=ef.nmForeignId and f.nmTabId in ("
							+ rs.getString("stTabList")
							+ ") order by ef.nmOrderDisplay,f.stDbFieldName";
					ResultSet rsF = this.ebEnt.dbDyn.ExecuteSql(stSql);
					rsF.last();
					int iMaxF = rsF.getRow();
					stReturn += "<td class=l1td><table border=0>";
					int nmOrderDisplay = 0;
					for (int iRF = 1; iRF <= iMaxF; iRF++) {
						rsF.absolute(iRF);
						stReturn += "<tr>";
						stReturn += "<td align=right>0x"
								+ Long.toHexString(rsF.getInt("nmPriv"))
								+ "</td>";
						stReturn += "<td><a href='./?a=28&tb=1.d.teb_fields&tid="
								+ rsF.getString("nmForeignId")
								+ "'>"
								+ rsF.getString("nmDataType") + "</a></td>";
						stReturn += "<td><a href='./?a=28&tb=1.d.teb_epsfields&tid="
								+ rsF.getString("nmForeignId")
								+ "'>"
								+ rsF.getString("stLabel") + "</a></td>";
						nmOrderDisplay += 10; // reset order
						if (nmOrderDisplay != rsF.getInt("nmOrderDisplay")) // only
																			// update
																			// if
																			// different
						{
							this.ebEnt.dbDyn
									.ExecuteUpdate("update teb_epsfields set nmOrderDisplay="
											+ nmOrderDisplay
											+ " where nmForeignId="
											+ rsF.getString("nmForeignId"));
						}
						stReturn += "<tr>";
					}
					stReturn += "</table></td>";
					stSql = "select * from teb_fields f, teb_epsfields ef where f.nmForeignId=ef.nmForeignId and f.nmTabId in ("
							+ rs.getString("stTabList")
							+ ") and f.nmHeaderOrder > 0  order by f.nmHeaderOrder,f.stDbFieldName";
					rsF = this.ebEnt.dbDyn.ExecuteSql(stSql);
					rsF.last();
					iMaxF = rsF.getRow();
					stReturn += "<td class=l1td>";
					for (int iRF = 1; iRF <= iMaxF; iRF++) {
						rsF.absolute(iRF);
						if (iRF > 1) {
							stReturn += "<br>";
						}
						stReturn += rsF.getString("stLabel");
					}
					stReturn += "</td>";
					stSql = "select * from teb_fields f, teb_epsfields ef where f.nmForeignId=ef.nmForeignId and f.nmTabId in ("
							+ rs.getString("stTabList")
							+ ") and f.nmOrder2 > 0  order by f.nmOrder2,f.stDbFieldName";
					rsF = this.ebEnt.dbDyn.ExecuteSql(stSql);
					rsF.last();
					iMaxF = rsF.getRow();
					stReturn += "<td class=l1td>";
					for (int iRF = 1; iRF <= iMaxF; iRF++) {
						rsF.absolute(iRF);
						if (iRF > 1) {
							stReturn += "<br>";
						}
						stReturn += rsF.getString("stLabel");
					}
					stReturn += "</td>";
					stReturn += "</tr>";
				}
				stReturn += "</table>Access/Edit/Create/Delete/Report 0x1=PTM 0x20=PPM 0x40=PM  0x80=BA  "
						+ "0x200=Exec  0x400=Administrator  0x800=Super User 0x40000000=OWN/SELF "
						+ "<br><a href='./?stAction=tablefield&rebuild=fieldpriv'>Load Field Priv</a>"
						+ "&nbsp;&nbsp;&nbsp;&nbsp;<a href='./?stAction=tablefield&rebuild=reqspec'>Load Requirement Spec</a>"
						+ "&nbsp;&nbsp;&nbsp;&nbsp;<a href='./?stAction=tablefield&rebuild=rprtprivfld'>Set Report Priv and Field</a>";
			} else if (stAction != null && stAction.equals("fieldpriv")) {
				String stTa = this.ebEnt.ebUd.request.getParameter("ta");
				if (stTa == null || stTa.trim().length() <= 0) {
					stReturn += "</form><form method=post><br>Load Field Privilege Values from XLS<br><textarea name=ta rows=10 cols=100>";
					stReturn += "</textarea><br /><input type=submit name=submit value=LOAD></form>";
				} else {
					stReturn += "<br>Field Privs have been loaded";
					String[] aLines;
					aLines = stTa.trim().split("\n");
					int nmPriv = 0;
					int nmFieldId = 0;
					for (int iL = 1; iL < aLines.length; iL++) {
						nmFieldId = nmPriv = 0;
						String[] aFields = aLines[iL].trim().toLowerCase()
								.split("\t");
						if (aFields.length > 10) {
							if (aFields[0].length() > 0) {
								nmFieldId = Integer.parseInt(aFields[0]);
								if (nmFieldId == 135) {
									nmFieldId = 135;
								}
							} else {
								nmFieldId = -1;
							}
							if (aFields[5].equals("y")) {
								nmPriv |= 0x400; /* Admin */
							}
							if (aFields[6].equals("y")) {
								nmPriv |= 0x80; /* BA */
							}
							if (aFields[7].equals("y")) {
								nmPriv |= 0x200; /* Exec */
							}
							if (aFields[8].equals("y")) {
								nmPriv |= 0x40; /* PM */
							}
							if (aFields[9].equals("y")) {
								nmPriv |= 0x20; /* PPM */
							}
							if (aFields[10].equals("y")) {
								nmPriv |= 0x01; /* PTM */
							}
							if (nmFieldId > 0) {
								this.ebEnt.dbDyn
										.ExecuteUpdate("update teb_epsfields set nmPriv="
												+ nmPriv
												+ " where nmForeignId="
												+ nmFieldId);
							}
						}
					}
				}
			} else if (stAction != null && stAction.equals("reqspec")) {
				String stTa = this.ebEnt.ebUd.request.getParameter("ta");
				if (stTa == null || stTa.trim().length() <= 0) {
					stReturn += "</form><form method=post><br>Load Requirement Specification"
							+ "&nbsp;&nbsp;<select name=type>"
							+ "<option value=parse>Parse only</option>"
							+ "<option value=insert>Insert/Update</option>"
							+ "<br><textarea name=ta rows=10 cols=100>";
					stReturn += "</textarea><br /><input type=submit name=submit value=LOAD></form>";
				} else {
					/*
					 * 6.29 Report--Budget (D29) The purpose of the Budget
					 * Report is to provide management a capsule view of how
					 * their projects are performing financially. Reqâ€™t ID
					 * Title Level Description 1 Access 0 2 Project Manager 1
					 * Project Managers shall be able to access the â€œBudget
					 * Reportâ€� for their project.
					 */
					stReturn += "<br>Requirement Spec";
					String[] aLines;
					aLines = stTa.trim().split("\n");
					String stTemp = "";
					int iState = 0;
					int nmDeliveryId = 0;
					String stDescription = "";
					this.ebEnt.dbDyn
							.ExecuteUpdate("truncate table teb_reqspec ");
					this.ebEnt.dbDyn
							.ExecuteUpdate("truncate table teb_reqlist ");
					for (int iL = 1; iL < aLines.length; iL++) {
						if (aLines[iL].trim().contains("4.0	Deliverables")) {
							iState = 4;
						}
						if (aLines[iL].trim().contains("5.0	Users")) {
							iState = 5;
						}
						if (aLines[iL].trim().contains(
								"6 	EPPORA Functional Components")) {
							iState = 6;
						}
						if (aLines[iL].trim().contains("7.0	Assumptions")) {
							iState = 7;
						}
						if (iState == 6 && aLines[iL].trim().endsWith(")")) {
							try {
								int iPos = aLines[iL].trim().indexOf("(");
								iPos++;
								stTemp = aLines[iL].trim().substring(iPos,
										iPos + 3);
								nmDeliveryId = this.ebEnt.dbDyn
										.ExecuteSql1n("select RecId from teb_reqspec where stDeliverable=\""
												+ stTemp + "\" ");
							} catch (Exception e) {
								nmDeliveryId = -1;
								this.stError += "<BR>ERROR: from teb_reqspec line "
										+ iL + " " + e;
							}
							continue;
						}
						String[] aFields = aLines[iL].trim().split("\t");
						if (aFields.length > 2) {
							if (iState == 4
									&& aFields[0].trim().toUpperCase()
											.startsWith("D")
									&& aFields[0].trim().length() < 5) {
								this.ebEnt.dbDyn
										.ExecuteUpdate("replace into teb_reqspec (stDeliverable,stTitle) "
												+ "values(\""
												+ aFields[0].trim()
														.toUpperCase()
												+ "\",\""
												+ aFields[1].trim()
												+ "\")");
							}
							if (iState == 6
									&& !aFields[0].trim().toUpperCase()
											.subSequence(0, 1).equals("R")) {
								try {
									stDescription = "";
									if (aFields.length > 3) {
										stDescription = aFields[3].trim();
									}
									this.ebEnt.dbDyn
											.ExecuteUpdate("replace into teb_reqlist (nmDeliveryId,nmRequId,stTitle,nmLevel,stDescription) "
													+ "values("
													+ nmDeliveryId
													+ ",\""
													+ aFields[0].trim()
													+ "\",\""
													+ aFields[1].trim()
													+ "\","
													+ ""
													+ this.ebEnt.dbDyn
															.fmtDbString(aFields[2]
																	.trim())
													+ ","
													+ this.ebEnt.dbDyn
															.fmtDbString(stDescription)
													+ ")");
								} catch (Exception e) {
									this.stError += "<BR>ERROR: into teb_reqlist "
											+ iL + " " + e;
								}
							}
						}
					}
				}
			} else if (stAction != null && stAction.equals("rprtprivfld")) {
				stSql = "select t.nmTableId,t.stTableName,t.stDeliveryId,rl.* from teb_table t, teb_reqspec rs, teb_reqlist rl  where t.stDeliveryId=rs.stDeliverable and rs.RecId=rl.nmDeliveryId and t.stTableName like '%report%';";
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.last();
				iMax = rs.getRow();
				int nmPriviledge = 0;
				String stFieldList = "";
				int iState = 0;
				for (iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					stValue = rs.getString("stTitle").trim();
					if (stValue.startsWith("Access")) {
						iState = 1;
						if (nmTableId > 0 && stFieldList.length() > 0) {
							this.ebEnt.dbDyn
									.ExecuteUpdate("update teb_table set nmReportPriv ="
											+ nmPriviledge
											+ ", stReportFieldList="
											+ this.ebEnt.dbDyn
													.fmtDbString(stFieldList)
											+ " where nmTableId="
											+ rs.getString("nmTableId"));
						}
						stFieldList = "";
						nmPriviledge = 0;
						nmTableId = rs.getInt("nmTableId");
					} else if (stValue.startsWith("Heading")) {
						iState = 2;
					} else if (stValue.startsWith("Content")) {
						iState = 3;
					} else {
						if (iState == 3) {
							int nmFieldId = this.ebEnt.dbDyn
									.ExecuteSql1n("select nmForeignId from teb_fields where stLabel = \""
											+ stValue + "\" ");
							if (nmFieldId <= 0) {
								nmFieldId = this.ebEnt.dbDyn
										.ExecuteSql1n("select max(nmForeignId) from teb_fields ");
								nmFieldId++;
								String stDbFieldName = stValue.trim().replace(
										" ", "_");
								stDbFieldName = stDbFieldName.trim().replace(
										"'", "");
								this.ebEnt.dbDyn
										.ExecuteUpdate("insert into teb_fields "
												+ "(nmForeignId,stDbFieldName,stLabel,nmDataType,nmFlags,nmMinBytes,nmMaxBytes,nmCols,nmTabId) "
												+ "values("
												+ nmFieldId
												+ ",\""
												+ stDbFieldName
												+ "\",\""
												+ stValue.trim()
												+ "\",3,1,0,255,64,"
												+ rs.getInt("nmTableId") + ") ");
								this.ebEnt.dbDyn
										.ExecuteUpdate("insert into teb_epsfields (nmForeignId) values("
												+ nmFieldId + ")");
							}
							stFieldList += "~" + nmFieldId;
						}
						if (iState == 1) {
							if (stValue.equals("Administrator")
									|| stValue.equals("Administrators")) {
								nmPriviledge |= 0x400;
							} else if (stValue.equals("Business Analyst")) {
								nmPriviledge |= 0x80;
							} else if (stValue.equals("Executive")) {
								nmPriviledge |= 0x200;
							} else if (stValue.equals("Super User")) {
								nmPriviledge |= 0x800;
							} else if (stValue.equals("Project Manager")) {
								nmPriviledge |= 0x40;
							} else if (stValue
									.equals("Project Portfolio Manager")
									|| stValue
											.equals("Project Portfolio Managers")) {
								nmPriviledge |= 0x20;
							} else if (stValue.equals("Project Team Member")
									|| stValue.equals("Project Team Members")) {
								nmPriviledge |= 0x1;
							} else {
								stError += "<BR>MISSING PRIV: " + stValue;
							}
						}
					}
				}
				if (nmTableId > 0 && stFieldList.length() > 0) {
					this.ebEnt.dbDyn
							.ExecuteUpdate("update teb_table set nmReportPriv ="
									+ nmPriviledge
									+ ", stReportFieldList="
									+ this.ebEnt.dbDyn.fmtDbString(stFieldList)
									+ " where nmTableId="
									+ rs.getString("nmTableId"));
				}
			}
			stAction = this.ebEnt.ebUd.request.getParameter("loadold");
			if (stAction != null && stAction.equals("y")) {
				stAction = this.ebEnt.ebUd.request.getParameter("submit");
				if (stAction != null && stAction.equals("LOAD")) {
					stReturn += "<br>Loaded";
					String[] aLines;
					String stTa = this.ebEnt.ebUd.request.getParameter("ta");
					if (stTa != null) {
						stTa = stTa.trim();
						aLines = stTa.split("\n");
						int iTable = 0;
						this.ebEnt.dbDyn
								.ExecuteUpdate("truncate table teb_division");
						this.ebEnt.dbDyn
								.ExecuteUpdate("insert into teb_division "
										+ "(nmDivision, nmExchangeRate, stCountry, stHolidays, stMoneySymbol, nmBurdenFactor, stDivisionName, dtExchangeRate, stCurrency)"
										+ "select nmDivision, stExchangeRage, stCountry, stHolidays, stMoneySymbol, nmBurdenFactor, stDivisionName, dtExchangeRate, stCurrency from tmp_demodiv");
						// Table must be loaded before we read it.
						ResultSet rsD = this.ebEnt.dbDyn
								.ExecuteSql("select * from teb_division");
						rsD.last();
						int iMaxD = rsD.getRow();
						for (int iL = 0; iL < aLines.length; iL++) {
							if (aLines[iL].trim().equals("::TRIGGERS")) {
								iTable = 4;
							} else if (aLines[iL].trim().length() > 0) {
								if (iTable == 4) {
									this.ebEnt.dbDyn
											.ExecuteUpdate("replace into Triggers (TriggerName) values(\""
													+ aLines[iL].trim()
													+ "\") ");
								}
							}
						}
						this.ebEnt.dbDyn
								.ExecuteUpdate("update Triggers set nmTaskType=1 where RecId in (10,11,12,13,14,19)");

						this.ebEnt.dbDyn
								.ExecuteUpdate("truncate table teb_dictionary");
						this.ebEnt.dbDyn
								.ExecuteUpdate("truncate table Options");
						stSql = "insert into Options (RecId) values( 1 )";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
						this.ebEnt.dbDyn
								.ExecuteUpdate("update Options set UserQuestions ='What is the name of your favorite pet?~What is the name of your favorite sports team?~What is your favorite color?' ");
						this.ebEnt.dbDyn
								.ExecuteUpdate("insert into teb_dictionary select * from common.teb_dictionary");
						this.ebEnt.dbDyn
								.ExecuteUpdate("update teb_fields set nmMaxBytes=255, nmMinBytes=2 where stValidation = 'd22'");
						stSql = "insert into Users (nmUserId, LastName,Answers)"
								+ " select xu.RecId as nmUserId, xu.stEMail as stLastName, 'Tucker~Lakers~Blue' as Answers"
								+ " from "
								+ this.ebEnt.dbEnterprise.getDbName()
								+ ".X25User xu" + " where xu.RecId < 1000";
						stSql = "select RecId from X25User ";
						ResultSet rsU = this.ebEnt.dbEnterprise
								.ExecuteSql(stSql);
						rsU.last();
						int iMaxU = rsU.getRow();
						for (int iRU = 1; iRU <= iMaxU; iRU++) {
							rsU.absolute(iRU);
							stSql = "replace into teb_refdivision (nmDivision,nmRefType,nmRefId) "
									+ "values (1,42,"
									+ rsU.getString("RecId")
									+ ") ";
							this.ebEnt.dbDyn.ExecuteUpdate(stSql);
						}
						epsLoadSpecialDays();
					}
					this.epsEf.rebuildChoices();
				} else {
					stReturn += "</form><form method=post><br>Load Default Values (Triggers, Options)<br><textarea name=ta rows=10 cols=100>";
					stReturn += "\n\n::TRIGGERS\nCompleted Deliverable\nCompleted Milestone\nCompleted Project\nDeleted Requirement\nFailed Login\nInserted Requirement\nLate Deliverable\nLate Milestone\nLong Duration Task\nMissing Inventory Resource\nMissing Labor Resource\nMissing Project Manager\nMissing Project Portfolio Manager\nMissing Sponsor\nModified Project Schedule\nNew Project Schedule\nNew User\nNo Initial Verb Task\nSpecial Days Approval\nTimely Updated Schedule\nUpdated Requirements\nUpdated User";
					stReturn += "</textarea><br /><input type=submit name=submit value=LOAD></form>";
				}
			} else if (stAction != null && stAction.equals("users")) {
				stReturn += "<br>Loading Test Users";
				stSql = "select * from tmpepsusers order by stFirstName, stLastName";
				ResultSet rsU = this.ebEnt.dbEnterprise.ExecuteSql(stSql);
				rsU.last();
				int iMaxU = rsU.getRow();
				String stFN = "";
				String stLN = "";
				String stPhone = "";
				int[][] aCount = new int[26][26]; // Two rows and four columns
				int i1 = 0;
				int i2 = 0;
				for (i1 = 0; i1 < 26; i1++) {
					for (i2 = 0; i2 < 26; i2++) {
						aCount[i1][i2] = 0; // initialize it
					}
				}
				byte[] bChar;
				String stEMail = "";
				int iUserId = 1000;
				int nmPriviledge = 0;
				ResultSet rsDiv = this.ebEnt.dbDyn
						.ExecuteSql("select * from teb_division");
				rsDiv.last();
				int iMaxDiv = rsDiv.getRow();
				ResultSet rsLC = this.ebEnt.dbDyn
						.ExecuteSql("select * from LaborCategory");
				rsLC.last();
				int iMaxLC = rsLC.getRow();
				for (int iRU = 1; iRU <= iMaxU; iRU++) {
					rsU.absolute(iRU);
					try {
						nmPriviledge = 0;
						stFN = rsU.getString("stFirstName");
						stLN = rsU.getString("stLastName");
						bChar = stFN.toLowerCase().substring(0, 1).getBytes();
						i1 = bChar[0] - 97;
						bChar = stLN.toLowerCase().substring(0, 1).getBytes();
						i2 = bChar[0] - 97;
						rsDiv.absolute((aCount[i1][i2] % iMaxDiv) + 1);
						rsLC.absolute((aCount[i1][i2] % iMaxLC) + 1);
						aCount[i1][i2]++;
						iUserId++;
						stEMail = stFN.toLowerCase().substring(0, 1)
								+ stLN.toLowerCase().substring(0, 1);
						if (stEMail.equals("ad")) {
							nmPriviledge |= 0x400;
						} else if (stEMail.equals("ba")) {
							nmPriviledge |= 0x80;
						} else if (stEMail.equals("ee")) {
							nmPriviledge |= 0x200;
						} else if (stEMail.equals("su")) {
							nmPriviledge |= 0x800;
						} else if (stEMail.equals("pm")) {
							nmPriviledge |= 0x40;
						} else if (stEMail.equals("pp")) {
							nmPriviledge |= 0x20;
						} else {
							nmPriviledge |= 0x1;
						}
						stEMail += aCount[i1][i2] + "@eppora.com";
						stSql = "replace into X25User (RecId,stEMail,stPassword,nmPriviledge) values("
								+ iUserId
								+ ",\""
								+ stEMail
								+ "\",password('EPS###'),"
								+ nmPriviledge
								+ ") ";
						this.ebEnt.dbEnterprise.ExecuteUpdate(stSql);
						stSql = "replace into Users (nmUserId,FirstName,LastName,Answers,Telephone) "
								+ "values("
								+ iUserId
								+ ","
								+ this.ebEnt.dbDyn.fmtDbString(stFN)
								+ ","
								+ this.ebEnt.dbDyn.fmtDbString(stLN)
								+ ",'Tucker~Lakers~Blue',"
								+ this.ebEnt.dbDyn.fmtDbString(stPhone) + ") ";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
						stSql = "replace into teb_refdivision (nmDivision,nmRefType,nmRefId) "
								+ "values ("
								+ rsDiv.getString("nmDivision")
								+ ",42," + iUserId + ") ";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
						stSql = "replace into teb_reflaborcategory (nmLaborCategoryId,nmRefType,nmRefId) "
								+ "values ("
								+ rsLC.getString("nmLcId")
								+ ",42," + iUserId + ") ";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
					} catch (Exception e) {
					}
				}
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR: epsTableField max " + iMax + " r=" + iR
					+ " sql=" + stSql + " " + e;
		}
		stReturn += "</center>";
		return stReturn;
	}

	private void updateScheduleTask(String param) {
		try {
			String stPrjId = this.ebEnt.ebUd.request.getParameter("nmPrjId");
			String stBaseline = this.ebEnt.ebUd.request
					.getParameter("nmBaseline");
			String stSchId = this.ebEnt.ebUd.request.getParameter("nmSchId");
			String stWfStatus = this.ebEnt.ebUd.request
					.getParameter("wfStatus");
			int nmWfStatus = Integer.parseInt(stWfStatus);

			String stEstimatedHours = this.ebEnt.ebUd.request
					.getParameter(param + "Estimated-" + stPrjId + "_"
							+ stBaseline + "_" + stSchId);
			String stExpendedHours = this.ebEnt.ebUd.request.getParameter(param
					+ "Expended-" + stPrjId + "_" + stBaseline + "_" + stSchId);
			String stStatus = this.ebEnt.ebUd.request.getParameter(param
					+ "Status-" + stPrjId + "_" + stBaseline + "_" + stSchId);

			SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy");
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DATE, 10); // number of days to add
			String dateEnd = fmt.format(c.getTime());

			try {
				Double.parseDouble(stEstimatedHours);
			} catch (Exception e) {
				this.ebEnt.ebUd
						.setPopupMessage("Estimated Hours must be a number");
				return;
			}

			try {
				Double.parseDouble(stExpendedHours);
			} catch (Exception e) {
				this.ebEnt.ebUd
						.setPopupMessage("Expended Hours must be a number");
				return;
			}
			// validateEffort(null, stSchId, stExpendedHours, stPrjId,
			// Integer.parseInt(stBaseline));
			Double newEffort = Double.parseDouble(stExpendedHours);
			ResultSet rsCheck = this.ebEnt.dbDyn
					.ExecuteSql("SELECT p.*, s.lowlvl FROM Schedule s, Projects p where s.nmProjectId=p.RecId and s.nmBaseline=p.CurrentBaseline and (SchFlags & 0x10 ) != 0 and s.nmProjectId="
							+ stPrjId + " and s.RecId=" + stSchId);

			if (rsCheck.next()) {
				int lowlvl = rsCheck.getInt("lowlvl");
				if (lowlvl > 0
						&& newEffort > this.rsMyDiv
								.getDouble("MaximumTaskHours")) {
					this.ebEnt.ebUd.setPopupMessage("[ID:" + stSchId
							+ "] Effort may not exceed "
							+ rsMyDiv.getDouble("MaximumTaskHours") + " hours");
					makeMessage(
							rsCheck.getString("ProjectName"),
							this.ebEnt.ebUd.getLoginId()
									+ ","
									+ rsCheck
											.getString("ProjectManagerAssignment")
									+ ","
									+ rsCheck
											.getString("ProjectPortfolioManagerAssignment"),
							"Schedule Warning",
							"Schedule low-level task ID "
									+ stSchId
									+ " has a resource assigned over the maximum recommended hours ("
									+ rsMyDiv.getDouble("MaximumTaskHours")
									+ ")", dateEnd);
				}
			}

			String stWhere = " WHERE nmProjectId=" + stPrjId
					+ " AND nmBaseline=" + stBaseline + " AND RecId=" + stSchId;

			if (nmWfStatus == 0) {
				// this.ebEnt.dbDyn.ExecuteUpdate("INSERT INTO teb_workflow2 SELECT * FROM Schedule WHERE nmProjectId="
				// + stPrjId
				// + " AND nmBaseline="
				// + stBaseline
				// + " AND RecId=" + stSchId);
				ResultSet rsSchedule = this.ebEnt.dbDyn
						.ExecuteSql("SELECT * FROM Schedule" + stWhere);
				if (rsSchedule.next()) {
					String stCols = "";
					String stValues = "";
					ResultSetMetaData rsmd = rsSchedule.getMetaData();
					for (int iCol = 1; iCol <= rsmd.getColumnCount(); iCol++) {
						if (iCol > 1) {
							stCols += ",";
							stValues += ",";
						}
						stCols += rsmd.getColumnName(iCol);
						stValues += rsSchedule.getString(iCol) == null ? "NULL"
								: this.ebEnt.dbDyn.fmtDbString(rsSchedule
										.getString(iCol));
					}
					this.ebEnt.dbDyn
							.ExecuteUpdate("REPLACE INTO teb_workflow2("
									+ stCols + ",nmUserId) VALUE (" + stValues
									+ "," + this.ebEnt.ebUd.getLoginId() + ")");
					this.ebEnt.dbDyn
							.ExecuteUpdate("UPDATE teb_workflow2 SET SchEfforttoDate="
									+ stExpendedHours
									+ ", SchEstimatedEffort="
									+ stEstimatedHours
									+ ", SchStatus="
									+ this.ebEnt.dbDyn.fmtDbString(stStatus)
									+ stWhere);
				}
				ResultSet rsP = this.ebEnt.dbDyn
						.ExecuteSql("SELECT * FROM Projects WHERE RecId="
								+ stPrjId);
				if (rsP.next()) {
					String msg = "Task " + stSchId + ": Estimated \""
							+ stEstimatedHours + "\", Expended \""
							+ stExpendedHours + "\", Status \"" + stStatus
							+ "\"";
					makeMessage(
							rsP.getString("ProjectName"),
							rsP.getString("ProjectManagerAssignment")
									+ ","
									+ rsP.getString("ProjectPortfolioManagerAssignment"),
							"A Workflow Task is updated", msg, dateEnd);
				}
			} else if (nmWfStatus == -1) {
				String nmUserId = this.ebEnt.dbDyn
						.ExecuteSql1("SELECT nmUserId FROM teb_workflow2"
								+ stWhere);
				ResultSet rsP = this.ebEnt.dbDyn
						.ExecuteSql("SELECT * FROM Projects WHERE RecId="
								+ stPrjId);
				if (rsP.next()) {
					String msg = "Task " + stSchId + ": Estimated \""
							+ stEstimatedHours + "\", Expended \""
							+ stExpendedHours + "\", Status \"" + stStatus
							+ "\"";
					makeMessage(rsP.getString("ProjectName"), nmUserId,
							"A Workflow Task is rejected", msg, dateEnd);
				}
				this.ebEnt.dbDyn.ExecuteUpdate("DELETE FROM teb_workflow2"
						+ stWhere);
			} else if (nmWfStatus == 1) {
				this.ebEnt.dbDyn
						.ExecuteUpdate("UPDATE teb_workflow2 SET iHandleFlags=1, SchStatus="
								+ this.ebEnt.dbDyn.fmtDbString(stStatus)
								+ ", SchEstimatedEffort="
								+ stEstimatedHours
								+ ", SchEfforttoDate="
								+ stExpendedHours
								+ stWhere);
				this.ebEnt.dbDyn.ExecuteUpdate("UPDATE Schedule set SchStatus="
						+ this.ebEnt.dbDyn.fmtDbString(stStatus)
						+ ", SchEstimatedEffort=" + stEstimatedHours
						+ ", SchEfforttoDate=" + stExpendedHours
						+ ", dtSchLastUpdate=now()" + stWhere);
				calculateScheduleCost(stWhere);
				EpsXlsProject epsXlsProject = new EpsXlsProject();
				epsXlsProject.setEpsXlsProject(this.ebEnt, this);
				epsXlsProject.nmBaseline = Integer.parseInt(stBaseline);
				epsXlsProject.stPk = stPrjId;
				epsXlsProject.processScheduleRequirementCost(Integer
						.parseInt(stSchId));
				epsXlsProject.processCriticalPath();
				makeTask(15, this.epsEf.getScheduleName("" + stSchId, stPrjId,
						Integer.parseInt(stBaseline)));
			}
		} catch (Exception e) {
			this.stError += "<br>ERROR updateScheduleTask " + e;
		}
	}

	public List<Integer> getScheduleLaborCategoryIds(String lc) {
		List<Integer> ids = new ArrayList<Integer>();
		String[] stLCs = lc.split("\\|");
		for (int i = 0; i < stLCs.length; i++) {
			String[] stLcParts = stLCs[i].split("~");
			if (stLcParts.length > 0) {
				ids.add(Integer.parseInt(stLcParts[0]));
			}
		}
		return ids;
	}

	public String makeHomePage() {
		this.setPageTitle("Home");
		String stReturn = "";
		String stProjects = "";
		String stSql = "";
		try {
			Enumeration paramNames = this.ebEnt.ebUd.request
					.getParameterNames();

			while (paramNames.hasMoreElements()) {
				String paramName = (String) paramNames.nextElement();
				if (paramName != null
						&& (paramName.startsWith("actual_") || paramName
								.startsWith("approve_"))) {
					String[] paramValues = this.ebEnt.ebUd.request
							.getParameterValues(paramName);
					if (paramValues.length == 1) {
						String paramValue = paramValues[0];
						String[] aV = paramName.split("_");
						if (paramName.startsWith("actual_")) {
							try {
								int iH = Integer.parseInt(paramValue);
								stSql = "update teb_allocateprj set nmActual='"
										+ paramValue + "' where "
										+ "dtDatePrj='" + aV[1]
										+ "' and nmUserId='" + aV[2]
										+ "' and nmPrjId='" + aV[3]
										+ "' and nmTaskId='" + aV[4] + "'";
								this.ebEnt.dbDyn.ExecuteUpdate(stSql);
							} catch (Exception e) {
								this.ebEnt.ebUd
										.setPopupMessage("Actual Hours must be a number");
							}
						} else {
							try {
								int iH = Integer.parseInt(paramValue);
								stSql = "update teb_allocateprj set nmActualApproved='"
										+ paramValue
										+ "' where "
										+ "dtDatePrj='"
										+ aV[1]
										+ "' and nmUserId='"
										+ aV[2]
										+ "' and nmPrjId='"
										+ aV[3]
										+ "' and nmTaskId='" + aV[4] + "'";
								this.ebEnt.dbDyn.ExecuteUpdate(stSql);
							} catch (Exception e) {
								this.ebEnt.ebUd
										.setPopupMessage("Approved Hours must be a number");
							}
						}
					}
				}

				if (paramName != null && "workflowtype".equals(paramName)) {
					String param = this.ebEnt.ebUd.request
							.getParameter(paramName);
					// update workflow
					if ("wf".equals(param) || "wfPTM".equals(param)) {
						updateScheduleTask(param);
					}
				}
			}
			ResultSet rsP = this.ebEnt.dbDyn
					.ExecuteSql("select * from Projects order by ProjectName");
			int iMax = 0;
			if (rsP != null) {
				rsP.last();
				iMax = rsP.getRow();
				stProjects += this.ebEnt.ebUd.addOption2(
						"-- Select Default Project --", "0", stPrj);
				for (int iP = 1; iP <= iMax; iP++) {
					rsP.absolute(iP);
					stProjects += this.ebEnt.ebUd.addOption2(
							rsP.getString("ProjectName"),
							rsP.getString("RecId"), stPrj);
				}
			}
			if (iMax == 0) {
				stReturn += "No Projects";
			} else {
				stReturn += "<form name='form4' id='form4' onsubmit='return myValidation(this)' method='post'>";
			}
			String stUser = "";

			SimpleDateFormat dateFormatter = new SimpleDateFormat("ddMMMyy");
			SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");

			if ((this.ebEnt.ebUd.getLoginPersonFlags() & 0x20) == 0)
				stUser = " and u.nmUserId=" + this.ebEnt.ebUd.getLoginId()
						+ " ";
			ResultSet rsActual = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_allocateprj ap, Users u, Projects p, Schedule s"
							+ " where ap.nmUserId=u.nmUserId and ap.nmPrjId=p.RecId and p.CurrentBaseline=s.nmBaseline and p.RecId=s.nmProjectId"
							+ " and s.RecId=ap.nmTaskId "
							+ stUser
							+ " and ap.dtDatePrj <= DATE_ADD(curdate(),INTERVAL 2 DAY)  and nmActualApproved <= 0 "
							+ " order by p.ProjectName,s.SchTitle,u.LastName,u.FirstName,ap.dtDatePrj");
			rsActual.last();
			int iMaxActual = rsActual.getRow();
			if (iMaxActual > 0) {
				stReturn += "<center><h1>Enter Actual Hours</h1><table class=l1tablenarrow>"
						+ "<tr><th class=l1th>Project/Task</th><th class=l1th>Name</th><th class=l1th>Date</th>"
						+ "<th class=l1th>Allocated</th><th class=l1th>Actual</th>";
				if ((this.ebEnt.ebUd.getLoginPersonFlags() & 0x20) != 0)
					stReturn += "<th class=l1th>Approved</th>";
				stReturn += "</tr>";
				for (int iA = 1; iA <= iMaxActual; iA++) { // addOption(String
															// stLabel,
															// String stId,
															// String
															// stCurrent)
					rsActual.absolute(iA);
					stReturn += "<tr><td class=l1td>"
							+ rsActual.getString("ProjectName") + " - ";
					stReturn += rsActual.getString("SchTitle") + "</td>";
					stReturn += "<td class=l1td>"
							+ rsActual.getString("FirstName") + " "
							+ rsActual.getString("LastName") + "</td>";
					stReturn += "<td class=l1td>"
							+ this.ebEnt.ebUd.fmtDateFromDb(rsActual
									.getString("dtDatePrj")) + "</td>";
					stReturn += "<td class=l1td align=right>"
							+ rsActual.getString("nmAllocated") + "</td>";
					stReturn += "<td class=l1td><input type=text"
							+ " name='actual_"
							+ rsActual.getString("dtDatePrj").substring(0, 10)
							+ "_" + rsActual.getString("nmUserId") + "_"
							+ rsActual.getString("nmPrjId") + "_"
							+ rsActual.getString("nmTaskId") + "'"
							+ " size=2 style='text-align:right;' value='"
							+ rsActual.getString("nmActual") + "'></td>";

					if ((this.ebEnt.ebUd.getLoginPersonFlags() & 0x20) != 0) {
						stReturn += "<td class=l1td><input type=text"
								+ " name='approve_"
								+ rsActual.getString("dtDatePrj").substring(0,
										10) + "_"
								+ rsActual.getString("nmUserId") + "_"
								+ rsActual.getString("nmPrjId") + "_"
								+ rsActual.getString("nmTaskId") + "'"
								+ " size=2 style='text-align:right;' value='"
								+ rsActual.getString("nmActualApproved")
								+ "'></td>";
					}
					stReturn += "</tr>";
				}
				stReturn += "<tr><td class=l1td colspan=4>&nbsp;</td><td class=l1td colspan=2>"
						+ "<input type=submit name=saveactual value='Save'></td></tr>"
						+ "</table></center><br />";
			}

			ResultSet rs = null;
			int iCount = 0;

			stReturn += "<input type=hidden name=workflowtype value=''><input type=hidden name=nmPrjId value='' /><input type=hidden name=nmBaseline value='' /><input type=hidden name=nmSchId value='' /><input type=hidden name=wfStatus value='' />";

			// accordion
			stReturn += "<div id='home-accordion'>";
			int nmPriviledge = this.ebEnt.dbEnterprise
					.ExecuteSql1n("select nmPriviledge from x25user where RecId="
							+ this.ebEnt.ebUd.getLoginId());
			String stPriviledge = getPriviledgeTypes(nmPriviledge);

			String stFrom = this.ebEnt.ebUd.request.getParameter("from");
			if (stFrom == null || stFrom.isEmpty()
					|| Integer.parseInt(stFrom) < 0)
				stFrom = "0";
			int iFrom = Integer.parseInt(stFrom);
			String stDisplay = this.ebEnt.ebUd.request.getParameter("display");
			if (stDisplay == null || stDisplay.isEmpty()
					|| Integer.parseInt(stDisplay) <= 0)
				stDisplay = rsMyDiv.getString("MaxRecords");
			int iDisplay = Integer.parseInt(stDisplay);
			int iTotalRecords = 0;

			// work flow
			iCount = 0;
			stReturn += "<div><a href='./?stAction=home&part=0&display="
					+ iDisplay
					+ "'>Work Flow</a>"
					+ "<span class='project-selector'>Project Name: <select name=f8 id=f8  onChange=\"document.form4.submit();\" onclick=\"event.cancelBubble=true\">"
					+ stProjects
					+ "</select></span></div>"
					+ "<div id='wf-content'><table class=l1tablenarrow>"
					+ "<tr><th class='l1th th0'>Action</th><th class='l1th th1'>Project Name</th><th class='l1th th2'>Task ID</th><th class='l1th th3'>Expended Hours</th><th class='l1th th4'>Estimated Hours</th><th class='l1th th5'>Status</th><th class='l1th th6'>Description</th></tr>";
			String stPrjIds = "";
			iMax = rsP.getRow();
			for (int i = 1; i <= iMax; i++) {
				rsP.absolute(i);
				if (i > 1)
					stPrjIds += ",";
				stPrjIds += rsP.getString("RecId");
			}
			iTotalRecords = this.ebEnt.dbDyn
					.ExecuteSql1n("select count(*),"
							+ "(select count(*) from teb_workflow2 wf"
							+ " where wf.nmBaseline=s.nmBaseline and wf.nmProjectId=s.nmProjectId and wf.RecId=s.RecId"
							+ " and (wf.iHandleFlags=0 OR (wf.SchStatus!='Not Started' AND wf.SchStatus!='In Progress'))) AS cnt "
							+ " from Projects p, Schedule s"
							+ " LEFT JOIN teb_reflaborcategory rlc ON rlc.nmLaborCategoryId = SUBSTRING_INDEX( s.SchLaborCategories, '~', 1 )"
							+ " LEFT JOIN users u ON rlc.nmRefId=u.nmUserId"
							+ " where p.CurrentBaseline=s.nmBaseline and p.RecId=s.nmProjectId and p.ProjectStatus=1 and s.lowlvl=1"
							+ " 	and u.nmUserId="
							+ this.ebEnt.ebUd.getLoginId()
							+ " and p.RecId IN (" + stPrjIds + ") having cnt=0"
							+ " order by p.ProjectName,s.RecId");
			rs = this.ebEnt.dbDyn
					.ExecuteSql("select p.ProjectName, p.RecId AS ProjectId, s.*,"
							+ "  (select count(*) from teb_workflow2 wf"
							+ " where wf.nmBaseline=s.nmBaseline and wf.nmProjectId=s.nmProjectId and wf.RecId=s.RecId"
							+ " and (wf.iHandleFlags=0 OR (wf.SchStatus!='Not Started' AND wf.SchStatus!='In Progress'))) AS cnt "
							+ " from Projects p, Schedule s"
							+ " LEFT JOIN teb_reflaborcategory rlc ON rlc.nmLaborCategoryId = SUBSTRING_INDEX( s.SchLaborCategories, '~', 1 )"
							+ " LEFT JOIN users u ON rlc.nmRefId=u.nmUserId"
							+ " where p.CurrentBaseline=s.nmBaseline and p.RecId=s.nmProjectId and p.ProjectStatus=1 and s.lowlvl=1"
							+ " 	and u.nmUserId="
							+ this.ebEnt.ebUd.getLoginId()
							+ " and p.RecId IN ("
							+ stPrjIds
							+ ") having cnt=0"
							+ " order by p.ProjectName,s.RecId"
							+ " LIMIT "
							+ iFrom + "," + iDisplay);
			while (rs.next()) {
				iCount++;
				String recId = rs.getString("RecId");
				String stPrjId = rs.getString("ProjectId");
				String stBaseLine = rs.getString("nmBaseline");
				stReturn += "<tr>";
				stReturn += "<td class='l1td td0'><span class=hiddenfield><input type='submit' value='Send' "
						+ "onclick='document.form4.workflowtype.value=\"wf\"; document.form4.nmPrjId.value=\""
						+ stPrjId
						+ "\"; document.form4.nmBaseline.value=\""
						+ stBaseLine
						+ "\"; document.form4.nmSchId.value=\""
						+ recId
						+ "\";document.form4.wfStatus.value=0;' /><input type=button class='cancel-button' value='Cancel' /></span>"
						+ "<span class=showfield>"
						+ "<a title='Edit' href='javascript:void(0);'><img class='edit-button' src='./common/b_edit.png' /></a>&nbsp;"
						+ "<a title='Info' href='javascript:void(0);'><img src='./common/b_info.png' onclick='window.open(\"./?stAction=admin&t=0&h=n&do=scheduledetail&listm=1&lists="
						+ recId
						+ "&listp="
						+ stPrjId
						+ "&listb="
						+ stBaseLine
						+ "\")' /></a>" + "</span></td>";
				stReturn += "<td class='l1td td1'>"
						+ rs.getString("ProjectName") + "</td>";
				stReturn += "<td class='l1td td2'>" + recId + "</td>";
				stReturn += "<td class='l1td td3'>"
						+ "<span class=hiddenfield><input type=text name='wfExpended-"
						+ stPrjId + "_" + stBaseLine + "_" + recId
						+ "' style='text-align:right;' value='"
						+ rs.getString("SchEfforttoDate")
						+ "' /></span><span class=showfield data-value='"
						+ rs.getString("SchEfforttoDate") + "'>"
						+ rs.getString("SchEfforttoDate") + "</span></td>";
				stReturn += "<td class='l1td td4'>"
						+ "<span class=hiddenfield><input type=text name='wfEstimated-"
						+ stPrjId + "_" + stBaseLine + "_" + recId
						+ "' style='text-align:right;' value='"
						+ rs.getString("SchEstimatedEffort")
						+ "' /></span><span data-value='"
						+ rs.getString("SchEstimatedEffort") + "'>"
						+ rs.getString("SchEstimatedEffort") + "</span></td>";
				String stStatus = rs.getString("SchStatus");
				stReturn += "<td class='l1td td5'>"
						+ "<span class=hiddenfield><select name='wfStatus-"
						+ stPrjId
						+ "_"
						+ stBaseLine
						+ "_"
						+ recId
						+ "'>"
						+ this.ebEnt.ebUd.addOption("Not Started",
								"Not Started", stStatus)
						+ this.ebEnt.ebUd.addOption("In Progress",
								"In Progress", stStatus)
						+ this.ebEnt.ebUd.addOption("Done", "Done", stStatus)
						+ "</select></span>" + "</select></span>"
						+ "<span class=showfield data-value='" + stStatus
						+ "'>" + stStatus + "</span>" + "</td>";
				stReturn += "<td class='l1td td6'>"
						+ rs.getString("SchDescription") + "</td>";
				stReturn += "</tr>";
			}
			if (iCount == 0) {
				stReturn += "<tr><td class=l1td colspan=7>No Tasks</td></tr>";
			}
			stReturn += "</table>"
					+ (iCount > 0 ? makeToolbar(false, iTotalRecords, iFrom,
							iDisplay, "./?stAction=home&part=0") : "")
					+ "</div>";
			rs.close();

			stReturn += "<div><a href='./?stAction=home&part=1&display="
					+ iDisplay
					+ "'>Messages</a></div>"
					+ "<div id='msg-content'><table class=l1tablenarrow>"
					+ "<tr><th class='l1th th0'>Action</th><th class='l1th th1'>Project Name</th><th class='l1th th2'>Message</th><th class='l1th th3'>Description</th></tr>";

			// message
			iTotalRecords = this.ebEnt.dbEnterprise
					.ExecuteSql1n("select count(*) from X25RefTask rt, X25Task t"
							+ " where t.RecId=rt.nmTaskId and rt.nmRefType=42 and (t.nmTaskFlag=1 OR (t.nmTaskFlag=2  and dtStart >= DATE_ADD(curdate(),INTERVAL -10 DAY)))"
							+ " and rt.nmRefId=" + this.ebEnt.ebUd.getLoginId());
			rs = this.ebEnt.dbEnterprise
					.ExecuteSql("select t.* from X25RefTask rt, X25Task t"
							+ " where t.RecId=rt.nmTaskId and rt.nmRefType=42 and (t.nmTaskFlag=1 OR (t.nmTaskFlag=2  and dtStart >= DATE_ADD(curdate(),INTERVAL -10 DAY)))"
							+ " and rt.nmRefId=" + this.ebEnt.ebUd.getLoginId()
							+ " ORDER BY t.dtStart DESC" + " LIMIT " + iFrom
							+ "," + iDisplay);
			// + " order by dtAssignStart desc limit 30");
			// + " order by dtStart desc limit 100");
			rs.last();
			iMax = rs.getRow();
			iCount = 0;
			if (iMax > 0) {
				for (int iR = 1; iR <= iMax; iR++) { // addOption(String
														// stLabel, String
														// stId, String
														// stCurrent)
					rs.absolute(iR);
					int nmTaskFlag = rs.getInt("nmTaskFlag");
					stReturn += "<tr>"
							+ "<td class='l1td td0'><a title='Info' href='javascript:void(0);'><img src='./common/b_info.png' onclick='window.open(\"./?stAction=admin&t=0&do=taskdetail&h=n&list="
							+ rs.getString("RecId")
							+ "\")' /></a>&nbsp;"
							+ "<a title='Delete' href='./?stAction=admin&t=0&do=taskdelete&h=n&list="
							+ rs.getString("RecId")
							+ "'><img src='./common/b_drop.png' /></a></td>";
					if (nmTaskFlag == 2) {
						String[] prjArr = rs.getString("stDescription").split(
								" - ");

						iCount++;

						if (prjArr.length > 1) {
							stReturn += "<td class='l1td td1'>"
									+ (prjArr[0].equals("All") ? "" : prjArr[0])
									+ "</td>" + "<td class='l1td td2'>"
									+ rs.getString("stTitle") + "</td>"
									+ "<td class='l1td td3'>" + prjArr[1]
									+ "</td>";
						} else {
							stReturn += "<td class='l1td td1'>&nbsp;</td>"
									+ "<td class='l1td td2'>"
									+ rs.getString("stTitle") + "</td>"
									+ "<td class='l1td td3'>" + prjArr[0]
									+ "</td>";
						}
					} else if (nmTaskFlag == 1) {
						iCount++;
						stReturn += "<td class='l1td td1'>&nbsp;</td>"
								+ "<td class='l1td td2'>"
								+ rs.getString("stTitle") + "</td>"
								+ "<td class='l1td td3'>"
								+ rs.getString("stDescription") + "</td>";
					}
					stReturn += "</tr>";
				}
			}

			if (iCount == 0) {
				stReturn += "<tr><td class=l1td colspan=5>No Messages</td></tr>";
			}
			stReturn += "</table>"
					+ (iCount > 0 ? makeToolbar(false, iTotalRecords, iFrom,
							iDisplay, "./?stAction=home&part=1") : "")
					+ "</div>";

			// work flow project team members
			iTotalRecords = this.ebEnt.dbDyn
					.ExecuteSql1n("select count(*) "
							+ " from Projects p, Schedule s, teb_workflow2 wf, Users u"
							+ " where p.CurrentBaseline=s.nmBaseline and p.RecId=s.nmProjectId and p.ProjectStatus=1 and s.lowlvl=1"
							+ " and wf.nmProjectId=s.nmProjectId and wf.nmBaseline=s.nmBaseline and wf.RecId=s.RecId and wf.nmUserId=u.nmUserId and wf.iHandleFlags=0"
							+ " and (p.ProjectManagerAssignment="
							+ this.ebEnt.ebUd.getLoginId()
							+ " or p.ProjectPortfolioManagerAssignment="
							+ this.ebEnt.ebUd.getLoginId() + ")"
							+ " order by p.ProjectName,s.RecId");
			rs = this.ebEnt.dbDyn
					.ExecuteSql("select p.ProjectName, p.RecId AS ProjectId, s.RecId, s.SchDescription, s.nmBaseline,wf.SchEstimatedEffort, wf.SchEfforttoDate, wf.SchStatus,CONCAT(u.FirstName, ' ', u.LastName) AS UserName"
							+ " from Projects p, Schedule s, teb_workflow2 wf, Users u"
							+ " where p.CurrentBaseline=s.nmBaseline and p.RecId=s.nmProjectId and p.ProjectStatus=1 and s.lowlvl=1"
							+ " and wf.nmProjectId=s.nmProjectId and wf.nmBaseline=s.nmBaseline and wf.RecId=s.RecId and wf.nmUserId=u.nmUserId and wf.iHandleFlags=0"
							+ " and (p.ProjectManagerAssignment="
							+ this.ebEnt.ebUd.getLoginId()
							+ " or p.ProjectPortfolioManagerAssignment="
							+ this.ebEnt.ebUd.getLoginId()
							+ ")"
							+ " order by p.ProjectName,s.RecId"
							+ " LIMIT "
							+ iFrom + "," + iDisplay);

			rs.last();
			iCount = 0;
			iMax = rs.getRow();
			stReturn += "<div><a href='./?stAction=home&part=2&display="
					+ iDisplay
					+ "'>Work Flow of Project Team Members</a></div>"
					+ "<div id='wfptm-content'><table class=l1tablenarrow>"
					+ "<tr><th class='l1th th0'>Action</th><th class='l1th th1'>Project Name</th><th class='l1th th2'>Task ID</th><th class='l1th th3'>Project Team Member</th><th class='l1th th4'>Expended Hours</th><th class='l1th th5'>Estimated Hours</th><th class='l1th th6'>Status</th><th class='l1th th7'>Description</th></tr>";
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				iCount++;
				String stPrjId = rs.getString("ProjectId");
				String recId = rs.getString("RecId");
				String stBaseLine = rs.getString("nmBaseline");
				stReturn += "<tr>";
				stReturn += "<td class='l1td td0'><span class=hiddenfield>"
						+ "<input type='submit' value='Update' onclick='document.form4.workflowtype.value=\"wfPTM\"; document.form4.nmPrjId.value=\""
						+ stPrjId
						+ "\"; document.form4.nmBaseline.value=\""
						+ stBaseLine
						+ "\"; document.form4.nmSchId.value=\""
						+ recId
						+ "\";document.form4.wfStatus.value=1' />"
						+ "<input type='submit' value='Reject'  onclick='document.form4.workflowtype.value=\"wfPTM\"; document.form4.nmPrjId.value=\""
						+ stPrjId
						+ "\"; document.form4.nmBaseline.value=\""
						+ stBaseLine
						+ "\"; document.form4.nmSchId.value=\""
						+ recId
						+ "\";document.form4.wfStatus.value=\"-1\"'/>"
						+ "<input type=button class='cancel-button' value='Cancel' /></span>"
						+ "<span class=showfield>"
						+ "<a title='Edit' href='javascript:void(0);'><img class='edit-button' src='./common/b_edit.png' /></a>&nbsp;"
						+ "<a title='Info' href='javascript:void(0);'><img src='./common/b_info.png' onclick='window.open(\"./?stAction=admin&t=0&h=n&do=scheduledetail&listm=1&lists="
						+ recId
						+ "&listp="
						+ stPrjId
						+ "&listb="
						+ stBaseLine
						+ "\")' /></a>" + "</span></td>";
				stReturn += "<td class='l1td td1'>"
						+ rs.getString("ProjectName") + "</td>";
				stReturn += "<td class='l1td td2'>" + rs.getString("RecId")
						+ "</td>";
				stReturn += "<td class='l1td td3'>" + rs.getString("UserName")
						+ "</td>";
				stReturn += "<td class='l1td td5'>"
						+ "<span class=hiddenfield><input type=text name='wfPTMExpended-"
						+ stPrjId + "_" + stBaseLine + "_" + recId
						+ "' style='text-align:right;' value='"
						+ rs.getString("SchEfforttoDate")
						+ "' /></span><span class=showfield data-value='"
						+ rs.getString("SchEfforttoDate") + "'>"
						+ rs.getString("SchEfforttoDate") + "</span></td>";
				stReturn += "<td class='l1td td4'>"
						+ "<span class=hiddenfield><input type=text name='wfPTMEstimated-"
						+ stPrjId + "_" + stBaseLine + "_" + recId
						+ "' style='text-align:right;' value='"
						+ rs.getString("SchEstimatedEffort")
						+ "' /></span><span class=showfield data-value='"
						+ rs.getString("SchEstimatedEffort") + "'>"
						+ rs.getString("SchEstimatedEffort") + "</span></td>";

				String stStatus = rs.getString("SchStatus");
				stReturn += "<td class='l1td td6'>"
						+ "<span class=hiddenfield><select name='wfPTMStatus-"
						+ stPrjId
						+ "_"
						+ stBaseLine
						+ "_"
						+ recId
						+ "'>"
						+ this.ebEnt.ebUd.addOption("Not Started",
								"Not Started", stStatus)
						+ this.ebEnt.ebUd.addOption("In Progress",
								"In Progress", stStatus)
						+ this.ebEnt.ebUd.addOption("Done", "Done", stStatus)
						+ this.ebEnt.ebUd.addOption("Approved", "Approved",
								stStatus)
						+ this.ebEnt.ebUd.addOption("Suspended", "Suspended",
								stStatus) + "</select></span>"
						+ "<span class=showfield data-value='" + stStatus
						+ "'>" + stStatus + "</span>" + "</td>";
				stReturn += "<td class='l1td td7'>"
						+ rs.getString("SchDescription") + "</td>";

				stReturn += "</tr>";
			}
			if (iCount == 0) {
				stReturn += "<tr><td class=l1td colspan=8>No Tasks</td></tr>";
			}
			stReturn += "</table>"
					+ (iCount > 0 ? makeToolbar(false, iTotalRecords, iFrom,
							iDisplay, "./?stAction=home&part=2") : "")
					+ "</div>";
			rs.close();

			// approved project dashboard
			if (stPriviledge.contains("Ex") || stPriviledge.contains("Ppm")) {
				iTotalRecords = this.ebEnt.dbDyn
						.ExecuteSql1n("select count(*) from Projects p"
								+ " where p.ProjectStatus=1 order by p.ProjectName");
				rs = this.ebEnt.dbDyn.ExecuteSql("select * from Projects p"
						+ " where p.ProjectStatus=1 order by p.ProjectName"
						+ " LIMIT " + iFrom + "," + iDisplay);

				rs.last();
				iCount = 0;
				iMax = rs.getRow();
				stReturn += "<div><a href='./?stAction=home&part=3&display="
						+ iDisplay
						+ "'>Executive Project Portfolio Dashboard</a></div>"
						+ "<div id='prj-content'><table class=l1tablenarrow>"
						+ "<tr><th class=l1th>Project Name</th><th class=l1th>Start Date</th><th class=l1th>End Date</th><th class=l1th>Estimated Hours</th><th class=l1th>Expended Hours</th><th class=l1th>Estimated Cost</th><th class=l1th>Expended to 	Date</th>"
						+ "<th class='l1th spi-header'><table><tr><td colspan=3 align=center>SPI</td></tr><tr><td class=redcolor><div>&nbsp;</div>&lt;0.85</td><td class=yellowcolor><div>&nbsp;</div>0.85-1</td><td class=greencolor><div>&nbsp;</div>&gt;1</td></tr></table></th>"
						+ "<th class='l1th cpi-header'><table><tr><td colspan=3 align=center>CPI</td></tr><tr><td class=redcolor><div>&nbsp;</div>&lt;0.85</td><td class=yellowcolor><div>&nbsp;</div>0.85-1</td><td class=greencolor><div>&nbsp;</div>&gt;1</td></tr></table></th></tr>";
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					iCount++;

					int iFirstApprove = this.ebEnt.dbDyn
							.ExecuteSql1n("SELECT min(nmBaseline)"
									+ " FROM teb_baseline where nmProjectId="
									+ rs.getString("RecId")
									+ " and stType='Approve'");
					// int iIniticalBaseline = this.ebEnt.dbDyn
					// .ExecuteSql1n("SELECT max(nmBaseline)"
					// + " FROM teb_baseline where nmProjectId="
					// + rs.getString("RecId")
					// + " and nmBaseline < " + iFirstApprove);
					int iIniticalBaseline = rs.getInt("CurrentBaseline");

					Date fixedStartDate = rs.getDate("FixedProjectStartDate");
					Date fixedEndDate = rs.getDate("FixedProjectEndDate");

					double CPI = 0;
					double SPI = 0;

					String stTemp = this.ebEnt.dbDyn
							.ExecuteSql1("select sum(nmExpenditureToDate) from Schedule"
									+ " where nmProjectId="
									+ rs.getInt("RecId")
									+ " and nmBaseline="
									+ iIniticalBaseline
									+ " and SchStatus!='Not Started' and SchStatus!='In Progress' and lowlvl=1");

					double CPIExpended = (stTemp != null && "".equals(stTemp
							.trim())) ? Double.parseDouble(stTemp) : 0;
					stTemp = this.ebEnt.dbDyn
							.ExecuteSql1("select sum(SchCost) from Schedule"
									+ " where nmProjectId="
									+ rs.getInt("RecId")
									+ " and nmBaseline="
									+ iIniticalBaseline
									+ " and SchStatus!='Not Started' and SchStatus!='In Progress' and lowlvl=1");
					double CPIEstimated = (stTemp != null && "".equals(stTemp
							.trim())) ? Double.parseDouble(stTemp) : 0;
					if (CPIExpended != 0) {
						CPI = 1.0 * Math
								.round(CPIEstimated * 100 / CPIExpended) / 100;
					}

					// stTemp =
					// this.ebEnt.dbDyn.ExecuteSql1("select sum(nmExpenditureToDate + SchRemainingHours) from Schedule"
					// + " where nmProjectId="
					// + rs.getInt("RecId") + " and nmBaseline=" +
					// iIniticalBaseline +
					// " and SchEfforttoDate>0 and SchStatus!='Not Started' and lowlvl=1");
					// double SPIExpended = (stTemp != null &&
					// "".equals(stTemp.trim())) ? Double.parseDouble(stTemp) :
					// 0;

					ResultSet rsSchedule = this.ebEnt.dbDyn
							.ExecuteSql("select * from Schedule where nmProjectId="
									+ rs.getInt("RecId")
									+ " and nmBaseline="
									+ iIniticalBaseline
									+ " and SchStatus!='Not Started' and lowlvl=1");
					double SPIExpended = 0;
					while (rsSchedule.next()) {
						String stValue = rsSchedule
								.getString("SchLaborCategories");
						if (stValue != null && stValue.length() > 0) {
							String[] aRecords = stValue.split("\\|", -1);
							String[] aFields = aRecords[0].split("~", -1);
							double dAvg = this.ebEnt.dbDyn
									.ExecuteSql1n("select sum(HourlyRate)/count(*) from LaborCategory l,"
											+ " Users u, teb_reflaborcategory rlc where  l.nmLcId="
											+ aFields[0]
											+ " and u.nmUserId=rlc.nmRefId and rlc.nmRefType=42 and rlc.nmLaborCategoryId=l.nmLcId");
							double dHours = Double.parseDouble(aFields[2]);
							SPIExpended += rsSchedule
									.getDouble("nmExpenditureToDate")
									+ dAvg
									* dHours;
						}
					}
					stTemp = this.ebEnt.dbDyn
							.ExecuteSql1("select sum(SchCost) from Schedule"
									+ " where nmProjectId="
									+ rs.getInt("RecId")
									+ " and nmBaseline="
									+ iIniticalBaseline
									+ " and SchStatus!='Not Started' and lowlvl=1");
					double SPIEstimated = (stTemp != null && "".equals(stTemp
							.trim())) ? Double.parseDouble(stTemp) : 0;

					if (SPIExpended != 0) {
						SPI = 1.0 * Math
								.round(SPIEstimated * 100 / SPIExpended) / 100;
					}

					System.out.println("Project ID " + rs.getInt("RecId")
							+ " Baseline: " + iIniticalBaseline + " CPIPV: "
							+ CPIEstimated + " CPIAV: " + CPIExpended
							+ " SPIPV: " + SPIEstimated + " SPIAV: "
							+ SPIExpended + " ====> CPI:" + CPI + " SPI: "
							+ SPI);

					stReturn += "<tr>";
					stReturn += "<td class=l1td> <a title='Edit' class='edit-button' href='.?stAction=projects&t=12&pk="
							+ rs.getString("RecId")
							+ "&do=edit'><img src='./common/b_edit.png'></a>"
							+ rs.getString("ProjectName") + "</td>";
					stReturn += "<td class=l1td>"
							+ (fixedStartDate != null ? dateFormatter
									.format(fixedStartDate) : "") + "</td>";
					stReturn += "<td class=l1td>"
							+ (fixedEndDate != null ? dateFormatter
									.format(fixedEndDate) : "") + "</td>";
					stReturn += "<td class=l1td align=right>"
							+ rs.getString("ProjectEstimatedHours") + "</td>";
					stReturn += "<td class=l1td align=right>"
							+ rs.getString("ProjectEstimatedHours") + "</td>";
					stReturn += "<td class=l1td align=right>&#36; "
							+ SPIEstimated + "</td>";
					stReturn += "<td class=l1td align=right>&#36; "
							+ SPIExpended + "</td>";
					stReturn += "<td class='l1td spi-content'>"
							+ "<table><tr class=spibg><td colspan=4><div class=spiline data-val='"
							+ SPI
							+ "'/></td></tr>"
							+ "<tr class=spinumber><td>0</td><td>1</td><td>2</td><td>3</td></tr></table>"
							+ "</td>";
					stReturn += "<td class='l1td spi-content'>"
							+ "<table><tr class=spibg><td colspan=4><div class=spiline data-val='"
							+ CPI
							+ "'/></td></tr>"
							+ "<tr class=spinumber><td>0</td><td>1</td><td>2</td><td>3</td></tr></table>"
							+ "</td>";

					stReturn += "</tr>";
				}
				if (iCount == 0) {
					stReturn += "<tr><td class=l1td colspan=9>No Projects</td></tr>";
				}
				stReturn += "</table>"
						+ (iCount > 0 ? makeToolbar(false, iTotalRecords,
								iFrom, iDisplay, "./?stAction=home&part=3")
								: "") + "</div>";
				rs.close();
			}

		} catch (Exception e) {
			stError += "<br>ERROR makeHomePage " + e;
		}
		String stPart = this.ebEnt.ebUd.request.getParameter("part");
		// if (stPart == null || stPart.isEmpty())
		// stPart = "0";
		stReturn += "</div></form>" + "<script>$(document).ready(winReady("
				+ stPart + "));</script>";
		return stReturn;
	}

	private String epsTable(String stAction) { // http://localhost:8084/eps/?stAction=admin&t=40
		String stT = this.ebEnt.ebUd.request.getParameter("t");
		String stA = this.ebEnt.ebUd.request.getParameter("a");
		String stReturn = "<center"
				+ (stT != null && !stT.isEmpty() ? " class=t" + stT : "") + ">";
		int iState = 0;
		// this.setPageTitle(stAction + ": ");
		try {
			if (stT == null) {
				String stC = this.ebEnt.ebUd.request.getParameter("c");
				if (stC != null && stC.equals("critscor")) {
					stReturn += processCritScor();
				} else if (stC != null && stC.equals("appr")) {
					this.setPageTitle("Approve Special Days");
					stReturn += processApprove();
				} else if (stC != null && stC.equals("allocate")) {
					this.setPageTitle("Allocate");
					stReturn += this.epsEf.processAllocate(this, 0);
				} else {
					stError += "<BR>Not a valid command: " + stC;
				}
			} else {
				String stCh = this.ebEnt.ebUd.request.getParameter("stChild");
				if (stCh != null && stCh.length() > 0) {
					stT = stCh; // Child has higher priority.
				}
				String stSql = "SELECT * FROM teb_table where nmTableId=" + stT;
				ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs.absolute(1);
				String stTemp = this.ebEnt.ebUd.request.getParameter("do");
				if (stTemp != null && stTemp.equals("taskdetail")) {
					stReturn += this.ebEnt.dbEnterprise
							.ExecuteSql1("SELECT stDescription FROM x25task where RecId="
									+ this.ebEnt.ebUd.request
											.getParameter("list"));
				} else if (stTemp != null && stTemp.equals("taskdelete")) {
					this.ebEnt.dbEnterprise
							.ExecuteUpdate("DELETE FROM x25task where RecId="
									+ this.ebEnt.ebUd.request
											.getParameter("list"));
					this.ebEnt.ebUd.setRedirect("./?stAction=home&part=1");
				} else if (stTemp != null && stTemp.equals("scheduledetail")) {
					String nmProjectId = this.ebEnt.ebUd.request
							.getParameter("listp");
					String nmBaseLineId = this.ebEnt.ebUd.request
							.getParameter("listb");
					String nmScheduleId = this.ebEnt.ebUd.request
							.getParameter("lists");
					String nmMessage = this.ebEnt.ebUd.request
							.getParameter("listm");
					ResultSet rsSch = this.ebEnt.dbDyn
							.ExecuteSql("SELECT s.*,p.ProjectName,d.stDivisionName FROM schedule s, projects p, teb_division d WHERE d.nmDivision=p.nmDivision AND p.RecId=s.nmProjectId AND s.nmProjectId="
									+ nmProjectId
									+ " AND s.nmBaseLine="
									+ nmBaseLineId
									+ " AND s.recID="
									+ nmScheduleId);
					if (rsSch.next()) {
						String stDesc = "Task may not exceed "
								+ rsMyDiv.getDouble("MaximumTaskHours")
								+ " hours";
						try {
							int iM = Integer.parseInt(nmMessage);
							if (iM > 0) {
								stDesc = "";
								if ("Y".equals(rsSch.getString("SchDone"))) {
									stDesc += "Status: Done<br>";
									stDesc += "Estimated Hours: "
											+ rsSch.getString("SchEstimatedEffort")
											+ "<br>"
											+ "Expended Hours: "
											+ rsSch.getString("SchEfforttoDate");
								} else {
									stDesc = rsSch.getString("SchDescription");
								}

							}
						} catch (Exception e) {
						}
						Date aDate = rsSch.getDate("dtSchLastUpdate");
						SimpleDateFormat formatter = new SimpleDateFormat(
								"ddMMMyy HH:mm");
						stReturn += "<h2>&quot;" + rsSch.getString("SchTitle")
								+ "&quot; per Project Schedule</h2>";
						stReturn += "<table border='1'>"
								+ "<tr><th>Project Name</th><th>Task ID</th><th>Level</th><th>Division</th><th>Time</th><th>Description</th></tr>"
								+ "<tr>"
								+ "	<td>"
								+ rsSch.getString("ProjectName")
								+ "</td>"
								+ "	<td>"
								+ rsSch.getString("RecId")
								+ "</td>"
								+ "	<td>"
								+ rsSch.getString("SchLevel")
								+ "</td>"
								+ "	<td>"
								+ rsSch.getString("stDivisionName")
								+ "</td>"
								+ "	<td>"
								+ (aDate == null ? "&nbsp;" : formatter
										.format(aDate)) + "</td>" + "	<td>"
								+ stDesc + "</td>" + "</tr>" + "</table>";
					}
				} else {
					if (rs.getInt("nmTableId") == 12 && stTemp != null
							&& stTemp.equals("edit")) {
						stPageTitle += " Project Attributes";
					} else if (rs.getInt("nmTableId") == 9) {
						if ("edit".equals(stTemp)) {
							stPageTitle += " User Attributes";
						} else {
							stPageTitle += " User Search";
						}
					} else {
						stTemp = this.ebEnt.ebUd.request.getParameter("child");
						if (stTemp != null && "19".equals(stTemp)) {
							if ("editfull".equals(stA)) {
								stPageTitle += " Requirement Attributes";
							} else {
								if ("1".equals(this.ebEnt.ebUd.request
										.getParameter("edit")))
									stPageTitle += " Inline Edit Requirement";
								else
									stPageTitle += " Requirements";
							}
						} else if (stTemp != null && stTemp.equals("21")) {
							if ("editfull".equals(stA)) {
								stPageTitle += " Schedule Task Attributes";
							} else {
								if ("1".equals(this.ebEnt.ebUd.request
										.getParameter("edit")))
									stPageTitle += " Inline Edit Schedule Task";
								else
									stPageTitle += " Schedule Tasks";
							}
						} else if (stTemp != null && stTemp.equals("34")) {
							stPageTitle += " Test";
						} else {
							stPageTitle += " " + rs.getString("stTableName");
						}
					}
					switch (iState) {
					case 0:
						stReturn += listTable(rs);
						break;
					}
				}
			}
		} catch (Exception e) {
			this.stError += "<br>ERROR: epsTable " + e;
		}
		stReturn += "</center>";
		return stReturn;
	}

	public String editTable(ResultSet rsTable, String stPk) {
		String stReturn = "";
		String stSql = "";
		String stTemp = "";
		ResultSet rsD = null;
		int iColumnCount = 0;
		int iEnable = 1;
		try {
			this.nmTableId = rsTable.getInt("nmTableId");
			if (this.nmTableId == 12 && stPk != null) // Projects
			{
				if (Integer.parseInt(stPk) <= 0) {
					stReturn += "<center><h2>No Selected Project</h2></center>";
					return stReturn;
				}
				// Must Check Lock Flag
				ResultSet rsP = this.ebEnt.dbDyn
						.ExecuteSql("select * from Projects where RecId="
								+ stPk);
				rsP.absolute(1);
				if (rsP.getInt("nmLockFlags") != 0) {
					String stUnlock = this.ebEnt.ebUd.request
							.getParameter("unlock");
					if (stUnlock != null && stUnlock.length() > 0) {
						this.ebEnt.dbDyn
								.ExecuteUpdate("update Projects set nmLockFlags=0 where RecId="
										+ stPk);
						stReturn += "<br>Project <b>"
								+ rsP.getString("ProjectName")
								+ "</b> has been unlocked";
						return stReturn; // -------------------------------?
					} else if (rsP.getInt("nmLockUserId") != this.ebEnt.ebUd
							.getLoginId()) {
						stReturn += "</form><form method=post><br>Sorry, this project <b>"
								+ rsP.getString("ProjectName")
								+ "</b> "
								+ "is currently used by user <b>"
								+ getUserName(rsP.getInt("nmLockUserId"))
								+ "</b> "
								+ "on "
								+ rsP.getString("dtLockStart")
								+ "<br>&nbsp;<h2>The following users can unlock this Project</h2><table border=1>"
								+ "<tr><th colspan=2>Name</th><th>Phone</th><th>Cell</th><th>Email</th><th>&nbsp</th></tr><tr>";
						ResultSet rsUser = this.ebEnt.dbDyn
								.ExecuteSql("select * from Users u, ebeps01.X25User xu where "
										+ "u.nmUserId=xu.RecId and u.nmUserId="
										+ rsP.getInt("nmLockUserId"));
						rsUser.absolute(1);
						stReturn += "<td><b>" + rsUser.getString("FirstName")
								+ "</b></td>";
						stReturn += "<td><b>" + rsUser.getString("LastName")
								+ "</b></td>";
						stReturn += "<td>" + rsUser.getString("Telephone")
								+ "</td>";
						stReturn += "<td>" + rsUser.getString("CellPhone")
								+ "</td>";
						stReturn += "<td><a href=\"mailto:"
								+ rsUser.getString("stEMail") + "\">"
								+ rsUser.getString("stEMail") + "</a></td>";
						stReturn += "<td>User must login, select this project and <b>click on: 'Save' or 'Cancel'</b> button respectively</td>";
						stReturn += "</tr>";
						rsUser.close();
						stSql = "select * from Users u, teb_refdivision td, ebeps01.X25User xu where td.nmDivision=1 and td.nmRefType=42 "
								+ "and td.nmRefId=1 and u.nmUserId=xu.RecId and (xu.nmPriviledge & 0x20) != 0 and u.nmUserId != "
								+ rsP.getInt("nmLockUserId")
								+ " order by u.LastName,u.FirstName";
						rsUser = this.ebEnt.dbDyn.ExecuteSql(stSql);
						rsUser.last();
						int iMaxU = rsUser.getRow();
						for (int iU = 1; iU <= iMaxU; iU++) {

							rsUser.absolute(iU);
							stReturn += "<td><b>"
									+ rsUser.getString("FirstName")
									+ "</b></td>";
							stReturn += "<td><b>"
									+ rsUser.getString("LastName")
									+ "</b></td>";
							stReturn += "<td>" + rsUser.getString("Telephone")
									+ "</td>";
							stReturn += "<td>" + rsUser.getString("CellPhone")
									+ "</td>";
							stReturn += "<td><a href=\"mailto:"
									+ rsUser.getString("stEMail") + "\">"
									+ rsUser.getString("stEMail") + "</a></td>";
							if (rsUser.getInt("nmUserId") == this.ebEnt.ebUd
									.getLoginId()) {
								stReturn += "<td><input type=hidden name='unlockprj' value='"
										+ stPk
										+ "'>"
										+ "<input type=submit name='unlock' value='Unlock Project'></td>";
							} else {
								stReturn += "<td>&nbsp;</td>";
							}
							stReturn += "</tr>";
						}
						stReturn += "</table>";
						rsUser.close();
						return stReturn; // -------------------------------?
					}
				}
				this.ebEnt.dbDyn
						.ExecuteUpdate("update Projects set nmLockFlags = 1,"
								+ "nmLockUserId= "
								+ this.ebEnt.ebUd.getLoginId()
								+ ", dtLockStart=now() where RecId=" + stPk);
			}

			if (this.stChild.length() > 0) {
				// Need to change table context.
				rsTable = this.ebEnt.dbDyn
						.ExecuteSql("select * from teb_table where nmTableId="
								+ stChild);
				rsTable.absolute(1);
				int iCount = this.ebEnt.dbDyn
						.ExecuteSql1n("select count(*) from "
								+ rsTable.getString("stDbTableName")
								+ " where " + rsTable.getString("stPk")
								+ " = \"" + stPk + "\"");
				if (iCount <= 0) {
					this.ebEnt.dbDyn.ExecuteUpdate("replace into "
							+ rsTable.getString("stDbTableName") + " ("
							+ rsTable.getString("stPk") + ") values( \"" + stPk
							+ "\")");
				}
			}
			stReturn = "</center></form><form name='form"
					+ rsTable.getString("nmTableId")
					+ "' id='form"
					+ rsTable.getString("nmTableId")
					+ "' onsubmit='return myValidation(this)' method='post' action='#next'>"
					+ "<div id='loadingDiv' style='display:none;z-index:-1;'></div>"
					+ "<table align=center valign=top width='100%' border=0 id='fieldtb'>";
			iColumnCount++;
			stReturn += "<tr>";
			if (this.nmTableId == 3) // Login
			{
				stReturn += "<td valign=top width=100% align=center><table>";
			} else {
				stReturn += "<td valign=top><table style='text-align:left;'>";
			}
			ResultSet rsF = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_fields f, teb_epsfields ef where f.nmForeignId=ef.nmForeignId and f.nmTabId in ("
							+ rsTable.getString("stTabList")
							+ ") order by ef.nmOrderDisplay;");
			rsF.last();
			int iMax = rsF.getRow();
			if (this.nmTableId == 9) {
				if (stPk != null && stPk.equals("-1")) // insert NEW USER:
				{
					int nmUserId = this.ebEnt.dbEnterprise
							.ExecuteSql1n("select max(RecId) from X25User");
					nmUserId++;
					this.ebEnt.dbEnterprise
							.ExecuteUpdate("replace into X25User (RecId) values("
									+ nmUserId + ") ");
					this.ebEnt.dbDyn
							.ExecuteUpdate("replace into Users (nmUserId,Answers,StartDate,SpecialDays) values("
									+ nmUserId + ",'',now(),'') ");
					makeTask(17, "Added new user");
					this.ebEnt.ebUd
							.setRedirect("./?stAction=admin&t=9&do=edit&requestedPk=-1&pk="
									+ nmUserId);
					return ""; // ----------------------------->
				}
			}
			if (stPk != null && stPk.length() > 0) // primarily Login has no
													// data
			{
				if (stChild != null
						&& (stChild.equals("21") || stChild.equals("19"))) {
					stTemp = this.ebEnt.ebUd.request.getParameter("pk");
					String stBaseline = this.ebEnt.dbDyn
							.ExecuteSql1("select CurrentBaseline from Projects where RecId="
									+ stTemp);
					stSql = "SELECT * FROM "
							+ rsTable.getString("stDbTableName")
							+ " where nmProjectId=" + stTemp + " "
							+ "and nmBaseline=" + stBaseline + " and "
							+ rsTable.getString("stPk") + " = \"" + stPk + "\"";
				} else {
					stSql = "SELECT * FROM "
							+ rsTable.getString("stDbTableName") + " where "
							+ rsTable.getString("stPk") + " = \"" + stPk + "\"";
				}
				rsD = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rsD.absolute(1);
			}
			String stTable2 = "";
			if (rsTable.getInt("nmTableId") == 9) // Users
			{
				ResultSet rsU = this.ebEnt.dbEnterprise
						.ExecuteSql("select * from X25User where RecId=" + stPk);
				rsU.absolute(1);
				stTemp = this.ebEnt.ebUd.request.getParameter("poperr");
				if (stTemp != null && stTemp.length() > 0) {
					stTemp = "<center><font color=red size = +1>" + stTemp
							+ "</font></center><br/>";
				} else {
					stTemp = "";
				}
				stTable2 += "<table border=0>";
				stTemp = rsU.getString("stEMail");
				if (stTemp == null || stTemp.length() <= 0
						|| !stTemp.contains("@")) {
					stTable2 += "<tr><td class='' valign=top>Email: </td><td align=left ><input type=text name=stEMail value=\"\" size=40></td></tr>";
				} else {
					stTable2 += "<tr><td class=''>Email: </td><td align=left style='background-color:#dddddd;'>"
							+ rsU.getString("stEMail") + "</td></tr>";
				}
				stTable2 += "<tr><td class='' valign=top>Password: </td><td align=left ><input type=password name=stPassword value=\"\" size=40> <font class=small>(Only enter to change password)</td></tr>";
				stTable2 += "<tr><td class='' valign=top>User Type: </td><td align=left >"
						+ editUserType(rsU.getInt("nmPriviledge"), 1)
						+ "</td></tr>";
				stTable2 += "<tr><td class='' valign=top>Division: </td><td align=left >"
						+ makeList(460, 2, rsU.getInt("RecId")) + "</td></tr>";
				stTable2 += "<tr><td class='' valign=top>Labor Categories: </td><td align=left >"
						+ makeList(97, 1, rsU.getInt("RecId")) + "</td></tr>";
				stTable2 += "<tr><td class='' valign=top colspan=2>Supported Labor Categories: </td></tr>";
				stTable2 += "<tr><td class='' valign=top colspan=2>"
						+ makeSupportedLC(rsU.getInt("RecId")) + "</td></tr>";
				stTable2 += "</table>";
				stReturn += "<tr><td valign=top><table border=0>";
				this.epsEf.addValidation(97);
				this.epsEf.addValidation(460);
			} else {
				if ((rsTable.getInt("nmTableFlags") & 0x400) != 0) // Show
																	// Divsion
				{
					stReturn += editDivision(rsTable, rsD);
				}
			}
			int iColspan = 0;
			String stLabel = "";
			int iValue = 0;
			for (int iF = 1; iF <= iMax; iF++) {
				rsF.absolute(iF);
				if (this.ebEnt.ebUd.getLoginId() > 0) {
					int nmPriv = rsF.getInt("nmPriv"); // only can check for
														// logged in
														// users
					iValue = (this.ebEnt.ebUd.getLoginPersonFlags() & nmPriv);
					if (iValue == 0) {
						continue; // --------------------> Not allowed, so
									// ignore this
									// field.
					}
				} else {
					iValue = 1;
				}

				if ((rsF.getInt("nmFlags") & 0x400) != 0) // Hide
				{
					continue; // Ignore/hide this field
				}
				if ((rsF.getInt("nmFlags") & 0x800) != 0) // Start at NEW column
															// in edit
				{
					iColumnCount++;
					stReturn += "</table></td><td valign=top><table style='text-align:left;'>";
				}
				String stClass = "";
				stLabel = rsF.getString("stLabel");
				switch (rsF.getInt("nmForeignId")) {
				case 4: // User question - RESPONSE
					Map<String, String> paramMap = ebEnt.ebUd.request
							.getParameterMap();
					boolean isLoggingIn = (paramMap != null)
							&& (paramMap.get("Login") != null);
					if (this.rsMyDiv.getInt("UserQuestionsOnOff") > 0
							&& isLoggingIn) {
						String[] aV = rsMyDiv.getString("UserQuestions")
								.replace("~", "\n").split("\\\n", -1);
						if (aV != null && aV.length >= this.ebEnt.ebUd.iLic) {
							stLabel = aV[this.ebEnt.ebUd.iLic];
						}
					} else {
						iValue = 0;
					}
					break;
				}
				if (iValue == 0) {
					continue; // --------------------> Not allowed, so ignore
								// this field.
				}
				iColspan++;
				if (rsF.getString("stSpecial") != null
						&& rsF.getString("stSpecial").indexOf("<select1>") >= 0) {
					stReturn += "<tr>";
					stReturn += "<td colspan=2 class=\""
							+ stClass
							+ "\">"
							+ this.epsEf.editField(rsMyDiv, rsTable, rsF, rsD,
									null, iEnable, stLabel) + "</td>";
					stReturn += "</tr>";
				} else {
					stReturn += "<tr>";
					int iColSpan = 1;
					stClass = "";
					if ((rsF.getInt("nmFlags") & 16) != 0) {
						iColSpan = 2;
						stClass = "table1";
					} else {
						if (nmTableId == 3) {// Login
							stClass = "login-label";
						}
						if (rsF.getInt("nmForeignId") == 4) {
							stReturn += "<td class=\"" + stClass + "\">"
									+ stLabel + " </td>";
						} else {
							stReturn += "<td class=\"" + stClass + "\">"
									+ stLabel + ": </td>";
						}
					}
					stReturn += "<td colspan="
							+ iColSpan
							+ " class=\""
							+ stClass
							+ "\">"
							+ this.epsEf.editField(rsMyDiv, rsTable, rsF, rsD,
									null, iEnable, stLabel) + "</td>";
					stReturn += "</tr>";
					if (rsF.getString("stSpecial") != null
							&& rsF.getString("stSpecial").indexOf("<hr>") >= 0) {
						stReturn += "<tr><td colspan=2 align=center><hr></td></tr>";
					}
				}
			}
			if (rsTable.getInt("nmTableId") == 9) // Users
			{
				stReturn += "</table><td valign=top>" + stTable2
						+ "</td></tr></table>";
			}
			stReturn += "<tr><td colspan=2 align=center>";
			String stChildren = "";
			stReturn += "</table></td></tr><tr><td align=center colspan="
					+ iColumnCount + " >";
			if (rsTable.getString("stChildren").trim().length() > 0) { // show
																		// attributes
				String[] aV = rsTable.getString("stChildren").trim().split(",");
				for (int i = 0; i < aV.length; i++) {
					ResultSet rsC = this.ebEnt.dbDyn
							.ExecuteSql("select * from  teb_table where nmTableId ="
									+ aV[i]);
					if (rsC != null) {
						rsC.absolute(1);
						if ((rsC.getInt("nmChildPriv") & this.ebEnt.ebUd
								.getLoginPersonFlags()) != 0) {
							if (stChildren.length() > 0) {
								stChildren += ",";
							}
							stChildren += aV[i];

							if (aV[i].equals("46")) { // analyze project button
								stReturn += "<input type=submit name=child"
										+ aV[i]
										+ " value=\""
										+ rsC.getString("stTableName")
										+ "\" onClick=\"if (!this.form.submitting) {this.form.submitting=true;return setSubmitId4(9990);} else {return false;}\">&nbsp;&nbsp;";
							} else
								stReturn += "<input type=submit name=child"
										+ aV[i]
										+ " value=\""
										+ rsC.getString("stTableName")
										+ "\" onClick=\"if (!this.form.submitting) {this.form.submitting=true;return setSubmitId(9990);} else {return false;}\">&nbsp;&nbsp;";
						}
					}
				}
			}
			if (this.stChild.length() > 0) {
				stReturn += "<input type=hidden name=stChild value=\""
						+ stChild + "\">";
				stReturn += "<input type=hidden name=stPk value=\"" + stPk
						+ "\">";
			}
			if (rsTable.getInt("nmTableId") == 3) // Login
			{
				stReturn += "<input type=hidden name=f6 id=f6 value=\""
						+ this.ebEnt.ebUd.iLic
						+ "\">"
						+ "<input type=submit name=Login value='Login' onClick=\"setSubmitId(9997);\">&nbsp;"
						+ "<input type=submit name=Login value='Forgot Password' onClick=\"setSubmitId(9993);\">"
						+ "</td></tr>";
			} else {
				stReturn += "<input type=hidden name=stChildren value=\""
						+ stChildren
						+ "\">"
						+ "<input type=submit name=savedata id=savedata value='Save' onClick=\"if (!this.form.submitting) {this.form.submitting=true;return setSubmitId(9999);} else {return false;}\">&nbsp;&nbsp;<input type=submit name=cancel id=cancel value='Cancel' onClick=\"if (!this.form.submitting) {this.form.submitting=true;return setSubmitId(8888);} else {return false;}\"></td></tr>";
			}
			stReturn += "<input type=hidden name=giSubmitId id=giSubmitId value=\"0\">";
			stReturn += "</td></tr></table>";

			if (stChild != null && (stChild.equals("21"))) {
				// change remaining cost to starting cost if not started yet #25
				stReturn += "<script type='text/javascript'>if(isBeforeDate(document.getElementById('f827').value)) document.getElementById('f824').value = document.getElementById('f811').value;</script>";
			}
		} catch (Exception e) {
			this.stError += "<br>ERROR: editTable " + e;
		}
		return stReturn;
	}

	private String editDivision(ResultSet rsTable, ResultSet rsD) {
		String stReturn = "<tr><td>Division: </td><td>";
		String stDivision = "";
		try {
			ResultSet rsDiv = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_division");
			rsDiv.last();
			int iMaxDiv = rsDiv.getRow();
			stReturn += "<select name=nmDivision>";
			try {
				stDivision = rsD.getString("nmDivision");
			} catch (Exception e) {
				stDivision = this.ebEnt.dbDyn
						.ExecuteSql1("select min(rd.nmDivision) from Users u, teb_refdivision rd where u.nmUserId=rd.nmRefId and rd.nmRefType=42 and rd.nmFlags=1 and rd.nmRefId="
								+ this.ebEnt.ebUd.getLoginId());
			}
			for (int iD = 1; iD <= iMaxDiv; iD++) {
				rsDiv.absolute(iD);
				int i = this.ebEnt.dbDyn
						.ExecuteSql1n("select count(*) from Users u, teb_refdivision rd where u.nmUserId=rd.nmRefId and rd.nmRefType=42 and rd.nmRefId="
								+ this.ebEnt.ebUd.getLoginId()
								+ " and rd.nmDivision="
								+ rsDiv.getInt("nmDivision"));
				stReturn += this.ebEnt.ebUd.addOption4(
						rsDiv.getString("stDivisionName"),
						rsDiv.getString("nmDivision"), stDivision, i);
			}
			stReturn += "</select>";
		} catch (Exception e) {
			this.stError += "<br>ERROR: editDivision " + e;
		}
		stReturn += "</td></tr>";
		return stReturn;
	}

	public String selectDivision(String stName) {
		String stReturn = "";
		String stDivision = "";
		try {
			stUsersDivision = "";
			ResultSet rsDiv = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_division");
			rsDiv.last();
			int iMaxDiv = rsDiv.getRow();
			stReturn += "<select name=" + stName + ">";
			stDivision = this.ebEnt.dbDyn
					.ExecuteSql1("select min(rd.nmDivision) from Users u, teb_refdivision rd where u.nmUserId=rd.nmRefId and rd.nmRefType=42 and rd.nmFlags=1 and rd.nmRefId="
							+ this.ebEnt.ebUd.getLoginId());
			for (int iD = 1; iD <= iMaxDiv; iD++) {
				rsDiv.absolute(iD);
				int i = this.ebEnt.dbDyn
						.ExecuteSql1n("select count(*) from Users u, teb_refdivision rd where u.nmUserId=rd.nmRefId and rd.nmRefType=42 and rd.nmRefId="
								+ this.ebEnt.ebUd.getLoginId()
								+ " and rd.nmDivision="
								+ rsDiv.getInt("nmDivision"));
				stReturn += this.ebEnt.ebUd.addOption4(
						rsDiv.getString("stDivisionName"),
						rsDiv.getString("nmDivision"), stDivision, i);
				if (i > 0) {
					if (stUsersDivision.length() > 0) {
						stUsersDivision += ",";
					}
					stUsersDivision += rsDiv.getString("nmDivision");
				}
			}
			stReturn += "</select>";
		} catch (Exception e) {
			this.stError += "<br>ERROR: selectDivision " + e;
		}
		return stReturn;
	}

	private String listTable(ResultSet rsTable) {
		String stReturn = "";
		String stSql = "";
		int iFieldMax = 0;
		String stLink2 = "";
		String stValue = "";
		String stAlign = "";
		String stTemp = "";
		int iFrom = 0;
		int iPk = 0;
		int iPkFrom = 0;
		stReturn += "<table class=l1tableb>";
		String stPk = this.ebEnt.ebUd.request.getParameter("pk");
		try {
			// Housekeeping
			if (rsTable.getInt("nmTableId") == 12) {
				analyzePrjSchTotals();
			}
			int nmTableFlags = rsTable.getInt("nmTableFlags");
			ResultSet rsF = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_fields f, teb_epsfields ef where f.nmForeignId=ef.nmForeignId and f.nmTabId in ("
							+ rsTable.getString("stTabList")
							+ ") and f.nmHeaderOrder > 0 order by f.nmHeaderOrder");
			rsF.last();
			int iMaxF = rsF.getRow();
			String stDo = this.ebEnt.ebUd.request.getParameter("do");
			String stSave = this.ebEnt.ebUd.request.getParameter("savedata");
			String stCancel = this.ebEnt.ebUd.request.getParameter("cancel");
			if (stDo != null) {
				if (stDo.equals("specialday")) {
					return specialDay(rsTable); // ----------------------------------------->
				} else if (stDo.equals("users")) {
					return adminUsers(rsTable, stDo); // ----------------------------------------->
				} else if (stDo.equals("del")) {
					String username = null;
					if (rsTable.getString("stDbTableName").equals("Users")) {
						username = ebEnt.dbDyn
								.ExecuteSql1("SELECT CONCAT(FirstName,' ', LastName) as FullName FROM Users WHERE "
										+ rsTable.getString("stPk")
										+ " = \""
										+ stPk + "\"");
					}
					stSql = "delete FROM " + rsTable.getString("stDbTableName")
							+ " where " + rsTable.getString("stPk") + " = \""
							+ stPk + "\"";
					this.ebEnt.dbDyn.ExecuteUpdate(stSql);
					if (rsTable.getString("stDbTableName").equals("Users")) {
						stSql = "delete FROM "
								+ this.ebEnt.dbEnterprise.getDbName()
								+ ".X25User where RecId = \"" + stPk + "\"";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);

						// make message to ppm
						ResultSet rsLC = ebEnt.dbDyn
								.ExecuteSql("select lc.LaborCategory from LaborCategory lc, teb_reflaborcategory rlc"
										+ " where rlc.nmLaborCategoryId=lc.nmLcId and nmRefType=42 and nmRefId="
										+ stPk);
						rsLC.last();
						int iLC = rsLC.getRow();
						String msg = "";
						if (iLC > 0) {
							for (int i = 1; i <= iLC; i++) {
								rsLC.absolute(i);
								msg += "<tr><td>"
										+ rsLC.getString("LaborCategory")
										+ "</td></tr>";
							}
						} else {
							msg += "<tr><td>No Labor Category</td></tr>";
						}
						if (username == null)
							username = "";
						msg = "Deleted User: "
								+ username
								+ "<br><table border=1><tr><th>Labor Category Support</th></tr>"
								+ msg + "</table>";
						makeMessage("All", getAllUsers(getPriviledge("ppm")),
								"Deleted User", msg, new SimpleDateFormat(
										"MM/dd/yyyy").format(Calendar
										.getInstance().getTime()));
						return adminUsers(rsTable, "users"); // ----------------------------------------->
					}
					if (rsTable.getString("stDbTableName").equals("Projects")) {
						stSql = "delete FROM teb_baseline where nmProjectId = \""
								+ stPk + "\"";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
					}
				} else {
					this.stChild = "";
					if (stSave == null || stSave.length() <= 0) {
						String stChildren = this.ebEnt.ebUd.request
								.getParameter("stChildren");
						if (stChildren != null && stChildren.length() > 0) {
							String[] aV = stChildren.split(",");
							for (int i = 0; i < aV.length; i++) {
								stSave = this.ebEnt.ebUd.request
										.getParameter("child" + aV[i]);
								if (stSave != null) {
									this.stChild = aV[i];
									break;
								}
							}
						}
					}
					if (stDo.equals("xls")) {
						this.stChild = this.ebEnt.ebUd.request
								.getParameter("child");
						if (stChild == null) {
							stChild = "";
						}
						this.ebEnt.ebUd.setXlsProcess(stChild);
						return "";
					}
					if (rsTable.getString("stDbTableName").equals("Projects")
							&& stDo.equals("approve")) {
						ebEnt.dbDyn
								.ExecuteUpdate("update Projects set ProjectStatus=1 where RecId="
										+ stPk);
						ebEnt.dbDyn
								.ExecuteUpdate("update teb_baseline b, Projects p set b.stType='Approve' where b.nmBaseline=p.CurrentBaseline and b.nmProjectId=p.RecId and p.RecId="
										+ stPk);
					}
					if (stSave != null || stCancel != null) { // DO SAVEDATA()
						if (rsTable.getInt("nmTableId") == 12) // Projects
						{ // Must Uncheck Lock Flag
							if (stPk != null
									&& ((stSave != null && stSave
											.equals("Save")) || stCancel != null)) {
								ResultSet rsP = this.ebEnt.dbDyn
										.ExecuteSql("select * from Projects where RecId="
												+ stPk);
								rsP.absolute(1);
								if (rsP.getInt("nmLockFlags") != 0) {
									if (rsP.getInt("nmLockUserId") == this.ebEnt.ebUd
											.getLoginId()) {
										this.ebEnt.dbDyn
												.ExecuteUpdate("update Projects set nmLockFlags = 0,"
														+ "nmLockUserId= "
														+ this.ebEnt.ebUd
																.getLoginId()
														+ ", dtLockStart=now() where RecId="
														+ stPk);
									}
								}
							}
						}
						rsF = this.ebEnt.dbDyn
								.ExecuteSql("select * from teb_fields f, teb_epsfields ef where f.nmForeignId=ef.nmForeignId and f.nmTabId in ("
										+ rsTable.getString("stTabList")
										+ ") order by f.nmHeaderOrder, f.nmForeignId ");
						rsF.last();
						iMaxF = rsF.getRow();
						// stError += this.ebEnt.ebUd.dumpRequest();
						if (stPk == null) {
							stPk = this.ebEnt.ebUd.request.getParameter("stPk");
						}
						if (stPk == null && stSave != null
								&& stSave.length() > 0) // Insert
						{
							iPk = this.ebEnt.dbDyn.ExecuteSql1n("select max("
									+ rsTable.getString("stPk") + ") from "
									+ rsTable.getString("stDbTableName"));
							iPk++;
							iPkFrom = iPk;
							stPk = "" + iPk;
							switch (rsTable.getInt("nmTableId")) {
							case 27: // Criteria
								ResultSet rsDiv = this.ebEnt.dbDyn
										.ExecuteSql("select * from teb_division");
								rsDiv.last();
								int iMaxDiv = rsDiv.getRow();
								for (int iD = 1; iD <= iMaxDiv; iD++) {
									rsDiv.absolute(iD);
									this.ebEnt.dbDyn
											.ExecuteUpdate("insert into "
													+ rsTable
															.getString("stDbTableName")
													+ " ("
													+ rsTable.getString("stPk")
													+ ",nmDivision,nmFlags) values("
													+ iPk
													+ ","
													+ rsDiv.getString("nmDivision")
													+ ",0) ");
									iPk++;
								}
								break;
							case 12: // Projects
								this.ebEnt.dbDyn.ExecuteUpdate("insert into "
										+ rsTable.getString("stDbTableName")
										+ " ("
										+ rsTable.getString("stPk")
										+ ",nmDivision) values("
										+ stPk
										+ ","
										+ this.ebEnt.ebUd.request
												.getParameter("nmDivision")
										+ ") ");
								this.ebEnt.dbDyn
										.ExecuteUpdate("INSERT INTO teb_baseline (nmProjectId,nmBaseline,dtEntered,nmUserEntered) "
												+ "values("
												+ stPk
												+ ",1,now(),"
												+ this.ebEnt.ebUd.getLoginId()
												+ ") ");
								break;
							default:
								this.ebEnt.dbDyn.ExecuteUpdate("insert into "
										+ rsTable.getString("stDbTableName")
										+ " (" + rsTable.getString("stPk")
										+ ") values(" + stPk + ") ");
								break;
							}
						}
						if (stSave != null && stSave.length() > 0) {
							int iCount = saveTable(rsTable, rsF, iMaxF, stPk,
									iPk, iPkFrom);
							if (rsTable.getString("stDbTableName").equals(
									"Options")) {
								this.ebEnt.ebUd.setRedirect("./");
							}
							if (rsTable.getString("stDbTableName").equals(
									"Users")) {
								stTemp = this.ebEnt.ebUd.request
										.getParameter("stEMail");
								if (stTemp != null && stTemp.length() > 0) {
									if (EbStatic.isEmail(stTemp)) {
										iCount = this.ebEnt.dbEnterprise
												.ExecuteSql1n("select count(*) from X25User where stEMail = \""
														+ stTemp + "\"");
										if (iCount > 0) {
											stError += "<br>Email is already in db: "
													+ stTemp;
										} else {
											this.ebEnt.dbEnterprise
													.ExecuteUpdate("update X25User set stEMail= \""
															+ stTemp
															+ "\" where RecId="
															+ stPk);
										}
									} else {
										stError += "<BR>Invalid Email "
												+ stTemp;
									}
								}
								if (stError.length() <= 0) {
									stTemp = this.ebEnt.dbEnterprise
											.ExecuteSql1("select stEMail from X25User where RecId ="
													+ stPk);
									if (stTemp == null || stTemp.length() < 5
											|| !EbStatic.isEmail(stTemp)) {
										stError += "<BR>Invalid or missing email";
									}
								}
								// OLD REDIR
								String[] aV = this.ebEnt.ebUd.request
										.getParameterValues("f460_selected");
								String stPrimary = this.ebEnt.ebUd.request
										.getParameter("f460_dcvalue");
								// this.ebEnt.dbDyn.ExecuteUpdate("delete from teb_refdivision where nmRefType=42 and nmRefId="
								// + stPk);
								this.ebEnt.dbDyn
										.ExecuteUpdate("update teb_refdivision set nmFlags=-2 where nmRefType=42 and nmRefId="
												+ stPk);
								if (aV != null && aV.length > 0) {
									for (int i = 0; i < aV.length; i++) {
										if (aV[i] != null
												&& aV[i].trim().length() > 0) {
											if (stPrimary.length() <= 0) {
												stPrimary = aV[i]; // No primary
																	// set, make
																	// 1st one
																	// the
																	// primary.
											}
											this.ebEnt.dbDyn
													.ExecuteUpdate("replace into teb_refdivision (nmRefType,nmRefId,nmDivision,nmFlags) "
															+ "values(42,"
															+ stPk
															+ ","
															+ aV[i] + ",0) ");
										}
									}
								}
								if (stPrimary != null
										&& stPrimary.trim().length() > 0) {
									this.ebEnt.dbDyn
											.ExecuteUpdate("replace into teb_refdivision (nmRefType,nmRefId,nmDivision,nmFlags) "
													+ "values(42,"
													+ stPk
													+ ","
													+ stPrimary + ",1) ");
								}
								this.ebEnt.dbDyn
										.ExecuteUpdate("delete from teb_refdivision where nmFlags=-2 and nmRefType=42 and nmRefId="
												+ stPk);
								// /////////////
								aV = this.ebEnt.ebUd.request
										.getParameterValues("f97_selected");
								// this.ebEnt.dbDyn.ExecuteUpdate("delete from teb_reflaborcategory where nmRefType=42 and nmRefId="
								// + stPk);
								this.ebEnt.dbDyn
										.ExecuteUpdate("update teb_reflaborcategory set nmFlags=-2 where nmRefType=42 and nmRefId="
												+ stPk);
								if (aV != null && aV.length > 0) {
									for (int i = 0; i < aV.length; i++) {
										if (aV[i] != null
												&& aV[i].trim().length() > 0) {
											this.ebEnt.dbDyn
													.ExecuteUpdate("replace into teb_reflaborcategory (nmRefType,nmRefId,nmLaborCategoryId,nmFlags) "
															+ "values(42,"
															+ stPk
															+ ","
															+ aV[i] + ",0) ");
										}
									}
								}
								this.ebEnt.dbDyn
										.ExecuteUpdate("delete from teb_reflaborcategory where nmFlags=-2 and nmRefType=42 and nmRefId="
												+ stPk);
								// ////
								String[] aType = this.ebEnt.ebUd.request
										.getParameterValues("nmType");
								int iTemp = 0;
								int nmType = 0;
								if (aType != null) {
									for (int i = 0; i < aType.length; i++) {
										iTemp = Integer.parseInt(aType[i]);
										nmType |= iTemp;
									}
								}
								if (nmType != 0) {
									this.ebEnt.dbEnterprise
											.ExecuteUpdate("update X25User set nmPriviledge=(nmPriviledge & ~0x6FF) where RecId="
													+ stPk);
									this.ebEnt.dbEnterprise
											.ExecuteUpdate("update X25User set nmPriviledge=(nmPriviledge | "
													+ nmType
													+ ") where RecId="
													+ stPk);
								} else {
									this.stError += "<BR>ERROR: Must have at least on usertype flag";
								}
								String stWeeklyWorkHours = "";
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f104_mon");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f104_tue");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f104_wed");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f104_thu");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f104_fri");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f104_sat");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f104_sun");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								this.ebEnt.dbDyn
										.ExecuteUpdate("update Users set WeeklyWorkHours = \""
												+ stWeeklyWorkHours
												+ "\" where nmUserId=" + stPk);
								stWeeklyWorkHours = "";
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f91_mon");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f91_tue");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f91_wed");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f91_thu");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f91_fri");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f91_sat");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								stTemp = this.ebEnt.ebUd.request
										.getParameter("f91_sun");
								if (stTemp == null
										|| stTemp.trim().length() <= 0) {
									stTemp = "0";
								}
								stWeeklyWorkHours += "~" + stTemp;
								this.ebEnt.dbDyn
										.ExecuteUpdate("update Users set ActivityHours = \""
												+ stWeeklyWorkHours
												+ "\" where nmUserId=" + stPk);
								stTemp = this.ebEnt.ebUd.request
										.getParameter("stPassword");
								if (stTemp != null && stTemp.length() > 4) {
									this.ebEnt.dbEnterprise
											.ExecuteUpdate("update X25User set stPassword=password(\""
													+ stTemp
													+ "\") where RecId=" + stPk);
								}
								this.epsEf.processUsersInLaborCategory();
								this.epsEf.processUsersInDivision();
								if (stError.length() > 0) {
									this.ebEnt.ebUd
											.setRedirect("./?stAction=admin&t=9&do=edit&pk="
													+ stPk
													+ "&poperr="
													+ URLEncoder.encode(
															stError, "UTF-8"));
									return " ERROR ";
								}
								this.ebEnt.ebUd
										.setRedirect("./?stAction=admin&t=9&do=users");
							}
							if (rsTable.getInt("nmTableId") == 27) // Criteria
							{
								fixCriteriaFields();
							}
							if (this.ebEnt.ebUd.getRedirect().length() > 0) {
								return ""; // ----------------------------------------->
							}
						} else { // cancel
							if (rsTable.getInt("nmTableId") == 9
									&& "-1".equals(this.ebEnt.ebUd.request
											.getParameter("requestedPk"))) {// user
								this.ebEnt.dbEnterprise
										.ExecuteUpdate("delete from X25User where RecId="
												+ stPk);
								this.ebEnt.dbDyn
										.ExecuteUpdate("delete from Users where nmUserId="
												+ stPk);
							}
						}
						if (stDo.equals("xls") || this.stChild.equals("19")
								|| this.stChild.equals("21")
								|| this.stChild.equals("34")
								|| this.stChild.equals("46")
								|| this.stChild.equals("26")) {
							if (!stDo.equals("xls")) {
								this.ebEnt.ebUd
										.setRedirect("./?stAction=projects&t=12&do=xls&pk="
												+ stPk
												+ "&parent=12&child="
												+ stChild);
								return ""; // ----------------------------------------->
							} else {
								this.stChild = this.ebEnt.ebUd.request
										.getParameter("child");
								if (stChild == null) {
									stChild = "";
								}
								this.ebEnt.ebUd.setXlsProcess(stChild);
								return "";
							}
						}
						if (rsTable.getString("stDbTableName")
								.equals("Options")) {
							this.ebEnt.ebUd.setRedirect("./");
							return ""; // ----------------------------------------->
						}
						if (rsTable.getString("stDbTableName").equals("Users")) {
							this.ebEnt.ebUd
									.setRedirect("./?stAction=admin&t=9&do=users");
							return ""; // ----------------------------------------->
						}
						if (this.stChild.length() > 0) {
							nmTableFlags = this.ebEnt.dbDyn
									.ExecuteSql1n("select nmTableFlags from teb_table where nmTableId="
											+ stChild);
							if ((nmTableFlags & 0x100) != 0) {
								this.ebEnt.ebUd
										.setRedirect("./?stAction=projects&t="
												+ rsTable
														.getInt("nmRedirectToParent")
												+ "&do=xls&pk=" + stPk
												+ "&parent="
												+ rsTable.getInt("nmTableId")
												+ "&child=" + stChild);
								return ""; // ----------------------------------------->
							} else {
								return editTable(rsTable, stPk); // ------------------------------------------------------>
							}
						}
						if (rsTable.getInt("nmRedirectToParent") > 0) {
							this.ebEnt.ebUd.setRedirect("./?stAction=admin&t="
									+ rsTable.getInt("nmRedirectToParent")
									+ "&do=edit&pk=" + stPk);
							return ""; // ----------------------------------------->
						} else {
							stError += this.ebEnt.dbDyn.getError(); // just to
																	// see if
																	// there
																	// was an
																	// error
							if (this.stError.length() <= 0) {
								this.ebEnt.ebUd
										.setRedirect("./?stAction=admin&t="
												+ rsTable.getInt("nmTableId"));
								return ""; // ----------------------------------------->
							} else {
								stTemp = this.ebEnt.ebUd.getRedirect();
								this.ebEnt.ebUd.setRedirect("");
								return "<hr>ERROR OCCURED [" + stTemp + "]<br>"
										+ stReturn;
							}
						}
					} else {
						return editTable(rsTable, stPk); // ------------------------------------------------------>
					}
				}
			}
			// IF search: DO SEARCH
			ResultSet rsS = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_fields f, teb_epsfields ef where f.nmForeignId=ef.nmForeignId and f.nmTabId in ("
							+ rsTable.getString("stTabList")
							+ ") and f.nmOrder2 > 0 order by f.nmOrder2");
			rsS.last();
			int iMaxS = rsS.getRow();
			String stSearch = "";
			if (iMaxS > 0) {
				int iSearchByDivision = 0;
				stReturn += "<tr>";
				stReturn += "<th class=l1th colspan=3></form><form method=post id=search name=search><b>Search by:</b></th></tr>";
				for (int iS = 1; iS <= iMaxS; iS++) {
					rsS.absolute(iS);
					stTemp = this.ebEnt.ebUd.request.getParameter("search_"
							+ rsS.getString("nmForeignId"));
					String stWhere = this.ebEnt.ebUd.request
							.getParameter("where_"
									+ rsS.getString("nmForeignId"));
					if (stTemp != null && stTemp.length() > 0
							&& stWhere != null) {
						if (rsS.getInt("nmForeignId") == 893) {
							iSearchByDivision = 1;
						}
						if (stSearch.length() > 0) {
							stSearch += " and ";
						}
						stSearch += " " + rsS.getString("stDbFieldName")
								+ " like ";
						if (stWhere.equals("beg")) {
							stSearch += this.ebEnt.dbDyn.fmtDbString(stTemp
									+ "%");
						} else if (stWhere.equals("any")) {
							stSearch += this.ebEnt.dbDyn.fmtDbString("%"
									+ stTemp + "%");
						} else if (stWhere.equals("end")) {
							stSearch += this.ebEnt.dbDyn.fmtDbString("%"
									+ stTemp);
						} else {
							stSearch += this.ebEnt.dbDyn.fmtDbString(stTemp);
						}
					}
					stReturn += "<tr>";
					// 891, 893
					switch (rsS.getInt("nmForeignId")) {
					case 891:
						stReturn += "<td class=l1td>Select Year: </td>";
						stReturn += "<td class=l1td><select name=search_"
								+ rsS.getString("nmForeignId") + ">";
						for (int i = 2011; i < 2050; i++) {
							stReturn += this.ebEnt.ebUd.addOption2("" + i, ""
									+ i, "2011");
						}
						stReturn += "</select></td>";
						stReturn += "<td class=l1td><input type=hidden name='where_"
								+ rsS.getString("nmForeignId")
								+ "' value='beg'>&nbsp;</td>";
						break;
					case 893:
						stReturn += "<td class=l1td>Select Division: </td>";
						stReturn += "<td class=l1td>";
						stReturn += selectDivision("search_"
								+ rsS.getString("nmForeignId"));
						stReturn += "</td>";
						stReturn += "<td class=l1td><input type=hidden name='where_"
								+ rsS.getString("nmForeignId")
								+ "' value='beg'>&nbsp;</td>";
						if (iSearchByDivision == 0) {
							if (stSearch.length() > 0) {
								stSearch += " and ";
							}
							stSearch += rsS.getString("stDbFieldName")
									+ " in (" + this.stUsersDivision + ") ";
						}
						break;
					default:
						stReturn += "<td class=l1td>"
								+ rsS.getString("stLabel") + ": </td>";
						stReturn += "<td class=l1td><input type=text name=search_"
								+ rsS.getString("nmForeignId")
								+ " value=''></td>";
						stReturn += "<td class=l1td>"
								+ getMatchWhere(
										"where_" + rsS.getString("nmForeignId"),
										"") + "</td>";
						break;
					}
					stReturn += "</tr>";
				}
				stReturn += "<td class=l1td colspan=3 align=center><input type=submit name=Search value=Search></form></th></tr>";
				stReturn += "</table><hr><table class=l1tableb>";
			}
			String stLink = ".?stAction="
					+ this.ebEnt.ebUd.request.getParameter("stAction") + "&t="
					+ this.ebEnt.ebUd.request.getParameter("t");
			stReturn += "<tr>";
			if ((nmTableFlags & 0x18) != 0) {
				stReturn += "<th class='l1th col" + iFieldMax + "'>Action</th>";
				iFieldMax++;
			}
			if ((nmTableFlags & 0x40) != 0) {
				stReturn += "<th class='l1th col" + iFieldMax
						+ "'>Division</th>";
				iFieldMax++;
			}
			for (int iF = 1; iF <= iMaxF; iF++) {
				rsF.absolute(iF);
				if (rsF.getString("stLabelShort").length() > 0) {
					stReturn += "<th class='l1th col" + iFieldMax + "'>"
							+ rsF.getString("stLabelShort") + "</th>";
				} else {
					stReturn += "<th class='l1th col" + iFieldMax + "'>"
							+ rsF.getString("stLabel") + "</th>";
				}
				iFieldMax++;
			}
			stReturn += "</tr>";
			String stOrderBy = rsTable.getString("stOrderBy");
			if (stOrderBy != null && stOrderBy.trim().length() > 0) {
				stOrderBy = " order by " + stOrderBy;
			} else {
				stOrderBy = "";
			}
			stSql = rsTable.getString("stSqlFields");
			if (stSql == null || stSql.trim().length() <= 0) {
				stSql = "SELECT * FROM " + rsTable.getString("stDbTableName");
			} else {
				stSql = stSql.replace("~~LoginId~",
						"" + this.ebEnt.ebUd.getLoginId());
			}
			if (stSearch.length() > 0) {
				stSql += " where " + stSearch;
			}
			String stFrom = this.ebEnt.ebUd.request.getParameter("from");
			stSql += stOrderBy;
			if (stFrom != null && stFrom.length() > 0) {
				// stSql = this.ebEnt.ebUd.request.getParameter("stSql");
				try {
					iFrom = Integer.parseInt(stFrom);
				} catch (Exception e) {
				}
			}

			int iBlock = rsMyDiv.getInt("ReqSchRows");
			String stBlock = this.ebEnt.ebUd.request.getParameter("display");
			if (stBlock != null && !stBlock.isEmpty()) {
				try {
					iBlock = Integer.parseInt(stBlock);
				} catch (Exception e) {

				}
			}

			int iCount = this.ebEnt.dbDyn.ebSql(stSql).iRows;

			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql + " limit "
					+ iFrom + ", " + iBlock);
			rs.last();
			int iMax = rs.getRow();
			// Show each field / data element -- ROWS
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				stLink2 = stLink + "&pk="
						+ rs.getString(rsTable.getString("stPk"));
				stReturn += "<tr class=d0 id=tr" + iR + ">";
				nmTableFlags = rsTable.getInt("nmTableFlags");
				if ((nmTableFlags & 0x18) != 0) {
					stReturn += "<td class=l1td>";
					if ((nmTableFlags & 0x8) != 0) {
						stReturn += "<a title='Edit' href='"
								+ stLink2
								+ "&do=edit'><img src='./common/b_edit.png'></a>";
					}
					if ((nmTableFlags & 0x18) == 0x18) {
						stReturn += "&nbsp;";
					}
					if ((nmTableFlags & 0x80) != 0
							&& (rs.getInt("nmFlags") & 1) != 0) // Dont
																// DELTE
																// DEFAULT
																// CRITERIA
					{
						nmTableFlags &= ~0x10; // Delete flag
					}
					if ((nmTableFlags & 0x10) != 0) {
						if (rsTable.getString("stDbTableName").equals(
								"teb_division")) {
							if (this.ebEnt.dbDyn
									.ExecuteSql1n("select count(*) from teb_refdivision dv, users u where dv.nmRefId=u.nmUserId and dv.nmDivision='"
											+ rs.getString(rsTable
													.getString("stPk")) + "'") > 0) {
								stReturn += "<a title='Delete' href='"
										+ stLink2
										+ "&do=del'><img src='./common/b_drop.png'"
										+ " onClick=\"alert('Only divisions with no users can be deleted.');return false;\"></a>";
							} else {
								stReturn += "<a title='Delete' href='"
										+ stLink2
										+ "&do=del'><img src='./common/b_drop.png'"
										+ " onClick=\"return myConfirm('Are you sure you want to delete this item?', "
										+ iR + "," + iMax + " )\"></a>";
							}
						} else {
							stReturn += "<a title='Delete' href='"
									+ stLink2
									+ "&do=del'><img src='./common/b_drop.png'"
									+ " onClick=\"return myConfirm('Are you sure you want to delete this item?', "
									+ iR + "," + iMax + " )\"></a>";
						}
					}
					stReturn += "</td>";
				}
				if ((nmTableFlags & 0x40) != 0) {
					String stDivisionName = "??";
					try {
						stDivisionName = rs.getString("stDivisionName");
					} catch (Exception e) {
						stDivisionName = this.ebEnt.dbDyn
								.ExecuteSql1("select stDivisionName from teb_division where nmDivision="
										+ rs.getString("nmDivision"));
					}
					stReturn += "<td class=l1td>" + stDivisionName + "</td>";
				} // Rest of Header fields
				stValue = "";
				// DO ALL FIELDS FOR TABLE LIST
				for (int iF = 1; iF <= iMaxF; iF++) {
					rsF.absolute(iF);
					stAlign = "";
					if (rsF.getString("stHandler").contains("center")) {
						stAlign = " align=center ";
					} else if (rsF.getString("stHandler").contains("right")
							|| rsF.getInt("nmDataType") == 1
							|| rsF.getInt("nmDataType") == 5
							|| rsF.getInt("nmDataType") == 31) {
						stAlign = " align=right ";
					}
					stValue = rs.getString(rsF.getString("stDbFieldName"));
					if (stValue == null) {
						stValue = "";
					}
					if (rsF.getInt("nmDataType") == 9) {
						stValue = getChoice(rsF, stValue);
					} else if (rsF.getInt("nmDataType") == 5
							&& stValue.length() > 0) {
						stValue = this.rsMyDiv.getString("stMoneySymbol") + " "
								+ stValue + " <font class=small>"
								+ this.rsMyDiv.getString("stCurrency")
								+ "</font>";
					}
					if (rsF.getString("stHandler").contains("selectuser")) {
						if (stValue.length() > 0) {
							if (stValue.startsWith("~")
									|| stValue.contains(",")) {
								stTemp = stValue.replace("~", ",");
								stValue = "";
								if (stTemp.length() > 1) {
									if (stTemp.startsWith(",")) {
										stTemp = stTemp.substring(1);
									}
									ResultSet rs2 = this.ebEnt.dbDyn
											.ExecuteSql("select concat(FirstName,' ',LastName) as nm from Users where nmUserId in ( "
													+ stTemp
													+ " ) order by LastName,FirstName");
									rs2.last();
									int iMax2 = rs2.getRow();
									for (int i = 1; i <= iMax2; i++) {
										rs2.absolute(i);
										if (i > 1) {
											stValue += "<br>";
										}
										stValue += rs2.getString("nm");
									}
								}
							} else {
								stValue = this.ebEnt.dbDyn
										.ExecuteSql1("select concat(FirstName,' ',LastName) as nm from Users where nmUserId="
												+ stValue);
							}
						}
					}
					if (rsF.getInt("nmDataType") == 20 && stValue.length() > 8
							&& stValue.length() > 6) {
						stValue = this.ebEnt.ebUd.fmtDateFromDb(stValue);
					}
					stReturn += "<td class=l1td " + stAlign + ">" + stValue
							+ "</td>";
				}
				stReturn += "</tr>";
			}
			if ((nmTableFlags & 0x20) != 0) {
				stReturn += "<tr><td class=l1td colspan=" + iFieldMax
						+ " align=center style='background: skyblue'>";
				stReturn += "<input type=button onClick=\"parent.location='"
						+ stLink + "&do=insert'\" value='Insert New'>";

				stReturn += "</td></tr>";
			}
			stReturn += "</table>";
			if ((nmTableFlags & 0x20) != 0) {
				stReturn += makeToolbar(false, iCount, iFrom, iBlock,
						"./?stAction=admin&t=" + rsTable.getInt("nmTableId"));
			}
			if (rsTable.getInt("nmTableId") == 17) // Calendar
			{
				stReturn += "<br/>&nbsp;<br><center><a href='./?stAction=specialdays'>Recalculate Calendar</a></center>";
			}
		} catch (Exception e) {
			this.stError += "<br>ERROR: listTable " + e;
		}
		return stReturn;
	}

	public void setUser(int iUserId, int nmPrivUser) {
		this.ebEnt.ebUd.setLoginId(iUserId);
		this.ebEnt.ebUd.setLoginPersonFlags(nmPrivUser);
		try {
			if (iUserId > 0 && this.rsMyDiv == null) {
				rsMyDiv = this.ebEnt.dbDyn
						.ExecuteSql("SELECT d.*,o.* FROM teb_division d, teb_refdivision rd, Options o"
								+ " where rd.nmRefType=42 and rd.nmRefId="
								+ iUserId
								+ " and (rd.nmFlags & 1) = 1 and rd.nmDivision=d.nmDivision and o.RecId=1");
			} else {
				if (this.rsMyDiv == null) {
					rsMyDiv = this.ebEnt.dbDyn
							.ExecuteSql("SELECT d.*,o.* FROM teb_division d,Options o where d.nmDivision=1 and o.RecId=1");
				}
			}
			rsMyDiv.absolute(1);
		} catch (Exception e) {
			this.stError += "<BR>ERROR: setUser " + e;
		}
	}

	public void setHelp1(String stHelp) {
		this.stHelp1 = stHelp;
	}

	private String adminUsers(ResultSet rsTable, String stDo) {
		String stReturn = "";
		try {
			if (stDo.equals("users")) {
				stReturn += "<table border=0 width='100%'><tr><td align=center width='75%'><h2>User Search</h2></td><td align=centerwidth='25%'>";
				stReturn += "</td></tr></table>";
				stReturn += userSearch(rsTable, null);
			} else
				this.stError += "<br>ERROR: adminUsers stDo = " + stDo;
		} catch (Exception e) {
			this.stError += "<br>ERROR: adminUsers " + e;
		}
		stReturn += "</center>";
		return stReturn;
	}

	/* Start of change AS -- 4Oct2011 -- Issue#19 */

	public String divisionFilter(ResultSet rsTable, String stFilter) {
		String stReturn = "";
		try {
			String[] aDiv = this.ebEnt.ebUd.request.getParameterValues("nmDiv");
			stReturn += "<table bgcolor='#ddd'>";

			String stDivList = ",0,";
			if (aDiv != null && aDiv.length > 0) {
				stDivList = "";
				for (int i = 0; i < aDiv.length; i++) {
					stDivList += aDiv[i];
				}
			}

			stReturn += "<td class=l1td>Divison: </td><td class=l1td><select name=nmDiv size=8 multiple>";
			ResultSet rsDiv = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_division order by stDivisionName");
			rsDiv.last();
			int iMaxDiv = rsDiv.getRow();
			stReturn += this.ebEnt.ebUd.addOption3("All", ",0,", stDivList);
			for (int iL = 1; iL <= iMaxDiv; iL++) {
				rsDiv.absolute(iL);
				stReturn += this.ebEnt.ebUd.addOption3(
						rsDiv.getString("stDivisionName"),
						"," + rsDiv.getString("nmDivision") + ",", stDivList);
			}
			stReturn += "</select></td></tr>";

			if (stFilter != null && stFilter.equals("Save"))
				return stDivList; // ------>

		} catch (Exception e) {
			this.stError += "<BR>ERROR userSearch: " + e;
		}
		return stReturn;
	}

	/* End of change AS -- 4Oct2011 -- Issue#19 */

	public String userSearch(ResultSet rsTable, String stFilter) {
		String stReturn = "";
		String stSql = "";
		try {
			if (stFilter == null) // comes from Reports, form is already open
				stReturn = "</form><form method=post id='form"
						+ rsTable.getString("nmTableId")
						+ "' name='form"
						+ rsTable.getString("nmTableId")
						+ "' action='#next'  onsubmit='return myValidation(this)' >";

			stReturn += "<table bgcolor='#ddd' style='border-collapse: collapse'>";
			String stLookup = this.ebEnt.ebUd.request.getParameter("h");
			String[] aType = this.ebEnt.ebUd.request
					.getParameterValues("nmType");
			String stWhere = this.ebEnt.ebUd.request.getParameter("stWhere");
			String stSearchType = this.ebEnt.ebUd.request
					.getParameter("stSearchType");
			String stSearch = this.ebEnt.ebUd.request.getParameter("stSearch");
			String[] aLc = this.ebEnt.ebUd.request.getParameterValues("nmLc");
			String[] aDiv = this.ebEnt.ebUd.request.getParameterValues("nmDiv");
			String stLc = this.ebEnt.ebUd.request.getParameter("lc");

			String stFrom = this.ebEnt.ebUd.request.getParameter("from");
			if (stFrom == null || stFrom.isEmpty()
					|| Integer.parseInt(stFrom) < 0)
				stFrom = "0";
			int iFrom = Integer.parseInt(stFrom);

			String stDisplay = this.ebEnt.ebUd.request.getParameter("display");
			if (stDisplay == null || stDisplay.isEmpty()
					|| Integer.parseInt(stDisplay) <= 0)
				stDisplay = rsMyDiv.getString("MaxRecords");
			int iDisplay = Integer.parseInt(stDisplay);

			int nmType = 0;
			int iTemp = 0;
			if (aType != null) {
				for (int i = 0; i < aType.length; i++) {
					iTemp = Integer.parseInt(aType[i]);
					nmType |= iTemp;
				}
			}
			if (nmType <= 0) {
				nmType = 0xFFFF;
			}
			String stLcList = ",0,";
			if (aLc != null && aLc.length > 0) {
				stLcList = "";
				for (int i = 0; i < aLc.length; i++) {
					stLcList += aLc[i];
				}
			}
			String stDivList = ",0,";
			if (aDiv != null && aDiv.length > 0) {
				stDivList = "";
				for (int i = 0; i < aDiv.length; i++) {
					stDivList += aDiv[i];
				}
			}
			if (stFilter != null) {
				if (stFilter.length() > 5 && !stFilter.equals("Save")) {
					// makeUserSql( nmType,stLcList, stDivList, stSearchType,
					// stSearch,
					// stWhere );
					String[] aV = stFilter.split("\\^", -1);
					nmType = Integer.parseInt(aV[0]);
					stLcList = aV[1];
					stDivList = aV[2];
					stSearchType = aV[3];
					stSearch = aV[4];
					stWhere = aV[5];
				}
			}
			if (stWhere == null || stWhere.length() <= 0) {
				stWhere = "beg";
			}
			if (stSearchType == null || stSearchType.length() <= 0) {
				stSearchType = "full";
			}
			if (stSearch == null) {
				stSearch = "";
			}
			stReturn += "<tr><td class=l1td>User Type:</th><td class=l1td colspan=3>";
			stReturn += editUserType(nmType, 0);
			stReturn += "</td></tr>";
			stReturn += "<tr><td class=l1td>Labor Categories:</th><td class=l1td>";
			if (stLc != null && stLc.length() > 0) {
				stLcList = "," + stLc + ",";
				stReturn += "<input type=hidden name=nmLc id=nmLc value='"
						+ stLcList + "'>";
				stReturn += this.ebEnt.dbDyn
						.ExecuteSql1("SELECT LaborCategory FROM LaborCategory where nmLcId="
								+ stLc);
			} else {
				stReturn += "<select name=nmLc id=nmLc size=8 multiple>";
				ResultSet rsLc = this.ebEnt.dbDyn
						.ExecuteSql("SELECT * FROM LaborCategory order by LaborCategory");
				rsLc.last();
				int iMaxLc = rsLc.getRow();
				stReturn += this.ebEnt.ebUd.addOption3("All", ",0,", stLcList);
				for (int iL = 1; iL <= iMaxLc; iL++) {
					rsLc.absolute(iL);
					stReturn += this.ebEnt.ebUd.addOption3(
							rsLc.getString("LaborCategory"),
							"," + rsLc.getString("nmLcId") + ",", stLcList);
				}
				stReturn += "</select>";
			}
			stReturn += "</td>";
			stReturn += "<td class=l1td>Divison: </td><td class=l1td><select name=nmDiv size=8 multiple>";
			ResultSet rsDiv = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_division order by stDivisionName");
			rsDiv.last();
			int iMaxDiv = rsDiv.getRow();
			stReturn += this.ebEnt.ebUd.addOption3("All", ",0,", stDivList);
			for (int iL = 1; iL <= iMaxDiv; iL++) {
				rsDiv.absolute(iL);
				stReturn += this.ebEnt.ebUd.addOption3(
						rsDiv.getString("stDivisionName"),
						"," + rsDiv.getString("nmDivision") + ",", stDivList);
			}
			stReturn += "</select></td></tr>";
			stReturn += "<tr><td class=l1td><select name=stSearchType>";
			stReturn += this.ebEnt.ebUd.addOption2("Search By Full Name:",
					"full", stSearchType);
			stReturn += this.ebEnt.ebUd.addOption2("Search By First Name:",
					"FirstName", stSearchType);
			stReturn += this.ebEnt.ebUd.addOption2("Search By Last Name:",
					"LastName", stSearchType);
			stReturn += this.ebEnt.ebUd.addOption2("Search By Email:",
					"stEMail", stSearchType);
			stReturn += this.ebEnt.ebUd.addOption2("Search By Phone:",
					"Telephone", stSearchType);
			stReturn += "</select></td>";
			stReturn += "<td class=l1td colspan=3><input type=text name=stSearch size=60 value=\""
					+ stSearch + "\"> ";
			stReturn += "&nbsp;&nbsp;&nbsp; ";
			stReturn += getMatchWhere("stWhere", stWhere);
			stReturn += "</td></tr>";

			if (stFilter != null && !stFilter.equals("Save"))
				return stReturn + "</table>"; // -------------------> for
												// REPORTS --
												// Select User Types etc.

			stReturn += "<tr><td colspan=4 class=l1td align=center><input type=submit name=submit value='Search'>";
			if ((this.ebEnt.ebUd.getLoginPersonFlags() & rsTable
					.getInt("nmCreatePriv")) != 0 && stLookup == null) {
				stReturn += "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
						+ "<input type=button onClick=\"parent.location='./?stAction=admin&t=9&do=edit&pk=-1'\" value='Insert New User'>";
			} else {
				stReturn += "&nbsp;";
			}
			stReturn += "</td></tr>";
			String stSubmit = this.ebEnt.ebUd.request.getParameter("submit");
			String stReturn1 = "";
			String stReturn2 = "";
			String stNext = "";
			int iMax = 0;

			if (stFilter != null && stFilter.equals("Save"))
				return nmType + "^" + stLcList + "^" + stDivList + "^"
						+ stSearchType + "^" + stSearch + "^" + stWhere; // ------>

			if (stSubmit != null && stSubmit.length() > 0
					|| (stFrom != null && stFrom.length() > 0)) {
				if (this.ebEnt.ebUd.request.getParameter("stSql") != null) {
					stSql = this.ebEnt.ebUd.request.getParameter("stSql");
				} else {
					stSql = makeUserSql(nmType, stLcList, stDivList,
							stSearchType, stSearch, stWhere);
				}

				ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql
						+ " order by FirstName,LastName,stEMail");
				rs.last();
				int iCount = rs.getRow();
				rs = this.ebEnt.dbDyn.ExecuteSql(stSql
						+ " order by FirstName,LastName,stEMail limit " + iFrom
						+ "," + iDisplay);
				rs.last();
				iMax = rs.getRow();
				String stLink2 = "./?stAction=admin&t=9";
				String stClass = "";
				if (iMax > 0) {
					stReturn1 += "<tr><td colspan=4 class=l1td align=center><table class=l2table cellpadding=1 cellspacing=1>"
							+ "<tr><th class=l2th>Action</th><th class=l2th>First Name</th><th class=l2th>Last Name</th><th class=l2th>Email</th></tr>";
					for (int iR = 1; iR <= iMax; iR++) {
						rs.absolute(iR);
						if ((iR & 1) != 0) {
							stClass = " class=l2td ";
						} else {
							stClass = " class=l2td2 ";
						}
						stReturn1 += "<tr>";
						stReturn1 += "<td " + stClass + ">";
						if (stLookup != null && stLookup.length() > 0
								&& stLookup.equals("n")) {
							stReturn1 += "<input type=submit name=userslect"
									+ rs.getString("nmUserId")
									+ " value=Select class=small"
									+ " onClick='sendBack(\""
									+ rs.getString("FirstName") + " "
									+ rs.getString("LastName") + "\", "
									+ rs.getString("nmUserId") + " );'>";
							// +
							// " onClick='window.opener.dialogArguments.value += \""
							// +
							// rs.getString("FirstName") + " " +
							// rs.getString("LastName") +
							// "\n\"; window.close();'>";
							// + " onClick='window.close();'>";
						} else {
							if ((this.ebEnt.ebUd.getLoginPersonFlags() & rsTable
									.getInt("nmEditPriv")) != 0) {
								stReturn1 += "<a title='Edit' href='"
										+ stLink2
										+ "&do=edit&pk="
										+ rs.getString("nmUserId")
										+ "'><img src='./common/b_edit.png' width=10></a>";
							}
							stReturn1 += "&nbsp;";
							if ((this.ebEnt.ebUd.getLoginPersonFlags() & rsTable
									.getInt("nmDeletePriv")) != 0) {
								stReturn1 += "<a title='Delete' href='"
										+ stLink2
										+ "&do=del&pk="
										+ rs.getString("nmUserId")
										+ "' onClick=\"return confirm('Are you sure you want to delete this user? \\n"
										+ rs.getString("FirstName").replace(
												"'", "`")
										+ " "
										+ rs.getString("LastName").replace("'",
												"`")
										+ " "
										+ rs.getString("stEMail").replace("'",
												"`")
										+ "')\"><img src='./common/b_drop.png' width=10></a>";
							}
						}
						stReturn1 += "<td " + stClass + ">"
								+ rs.getString("FirstName") + "</td>";
						stReturn1 += "<td " + stClass + ">"
								+ rs.getString("LastName") + "</td>";
						stReturn1 += "<td " + stClass + ">"
								+ rs.getString("stEMail") + "</td>";
						stReturn2 += "\n<option value=\""
								+ rs.getString("nmUserId") + "\" >"
								+ rs.getString("FirstName") + " "
								+ rs.getString("LastName") + "</option>";
						// stReturn1 += "<td " + stClass + ">" +
						// rs.getString("nmPriviledge") + "</td>";
						stReturn1 += "<tr>";
					}

					boolean addAnchor = false;
					String stLookupLink = (stLookup != null
							&& stLookup.equals("n") ? "&h=n&list=0" : "");
					/*
					 * if (iFrom >= this.rsMyDiv.getInt("MaxRecords")) {
					 * addAnchor = true; stNext =
					 * "<a href='./?stAction=admin&t=9&do=users&stFrom=" + 0 +
					 * "&stSql=" + URLEncoder.encode(stSql, "UTF-8") +
					 * stLookupLink + "#next'>First</a>"; stNext += stNext =
					 * "&nbsp;<a href='./?stAction=admin&t=9&do=users&stFrom=" +
					 * iToPrevious + "&stSql=" + URLEncoder.encode(stSql,
					 * "UTF-8") + stLookupLink + "#next'>Previous " +
					 * this.rsMyDiv.getInt("MaxRecords") + "</a>&nbsp;"; } if
					 * (iMax >= this.rsMyDiv.getInt("MaxRecords")) { if
					 * (!addAnchor) stNext = ""; addAnchor = true; stNext +=
					 * "<a href='./?stAction=admin&t=9&do=users&stFrom=" + iTo +
					 * "&stSql=" + URLEncoder.encode(stSql, "UTF-8") +
					 * stLookupLink + "#next'>Next " +
					 * this.rsMyDiv.getInt("MaxRecords") + "</a>"; stNext +=
					 * "&nbsp;<a href='./?stAction=admin&t=9&do=users&stFrom=" +
					 * (iCount - iCount % this.rsMyDiv.getInt("MaxRecords")) +
					 * "&stSql=" + URLEncoder.encode(stSql, "UTF-8") +
					 * stLookupLink + "#next'>Last</a>"; } if (addAnchor) {
					 * stReturn1 += "<a name='next'></a>"; stReturn1 +=
					 * "</table><br>" + stNext + "</td></tr>"; }
					 */
					stReturn1 += "</table><br>"
							+ makeToolbar(true, iCount, iFrom, iDisplay,
									"./?stAction=admin&t=9&do=users&stSql="
											+ URLEncoder.encode(stSql, "UTF-8"))
							+ "</td></tr>";

				} else {
					stReturn1 += "<tr><td colspan=4 class=l1td align=left>No users found. Please refine search</td></tr>";
				}
			}
			String stList = this.ebEnt.ebUd.request.getParameter("list");
			if (stLookup != null && stLookup.equals("n") && stList != null
					&& !stList.equals("0")) {
				stReturn += this.epsEf.selectUsers(rsTable, stReturn2, iMax,
						iFrom, stSql);
			} else {
				stReturn += stReturn1;
			}
			stReturn += "</table></form><br /><p align=left class=small>"
					+ "NOTE: searching by full name: simply use a SPACE between the first and last name.<br>"
					+ "For Multi Choice selection (e.g: Labor Categories): please use the &lt;CTRL&gt; and click on one or more categories. "
					+ "You can also use the &lt;SHIFT&gt; to select a range.</p>";
		} catch (Exception e) {
			this.stError += "<BR>ERROR userSearch: " + e;
		}
		return stReturn;
	}

	public String editUserType(int nmType, int iBr) {
		String stChecked = "";
		String stReturn = "";
		if (iBr == 0) // Search
		{
			int iAll = (1 | 32 | 64 | 128 | 512 | 1024);
			if ((nmType & iAll) == iAll) {
				stChecked = " checked ";
				nmType = 0;
			} else {
				stChecked = "";
			}
			/* AS -- 2Oct2011 -- Issue #18 */
			// stReturn += "<input type='checkbox' name='nmType' value='" + iAll
			// +
			// "' " + stChecked + " /> ALL ";
			stReturn += "<input type='checkbox' name='nmType' value='"
					+ iAll
					+ "' "
					+ stChecked
					+ " onclick='checkAll(document.forms[1].nmType, this)'/> ALL ";
		}
		if ((nmType & 1024) != 0) {
			stChecked = " checked ";
		} else {
			stChecked = "";
		}
		/* AS -- 2Oct2011 -- Issue #18 */
		// stReturn += "<input type='checkbox' name='nmType' value='1024' " +
		// stChecked + " /> Administrator ";
		stReturn += "<input type='checkbox' name='nmType' value='1024' "
				+ stChecked
				+ (iBr == 0 ? " onclick='checkAll(document.forms[1].nmType, this)'"
						: "") + "/> Administrator ";
		if ((nmType & 128) != 0) {
			stChecked = " checked ";
		} else {
			stChecked = "";
		}
		/* AS -- 2Oct2011 -- Issue #18 */
		stReturn += "<input type='checkbox' name='nmType' value='128' "
				+ stChecked
				+ (iBr == 0 ? " onclick='checkAll(document.forms[1].nmType, this)'"
						: "") + "/> Business Analyst ";
		// stReturn += "<input type='checkbox' name='nmType' value='128' " +
		// stChecked + " /> Business Analyst ";

		if ((nmType & 512) != 0) {
			stChecked = " checked ";
		} else {
			stChecked = "";
		}
		/* AS -- 2Oct2011 -- Issue #18 */
		stReturn += "<input type='checkbox' name='nmType' value='512' "
				+ stChecked
				+ (iBr == 0 ? " onclick='checkAll(document.forms[1].nmType, this)'"
						: "") + "/> Executive ";
		// stReturn += "<input type='checkbox' name='nmType' value='512' " +
		// stChecked + " /> Executive ";
		if ((nmType & 64) != 0) {
			stChecked = " checked ";
		} else {
			stChecked = "";
		}
		if (iBr > 0) {
			stReturn += "<br>";
		}
		/* AS -- 2Oct2011 -- Issue #18 */
		stReturn += "<input type='checkbox' name='nmType' value='64' "
				+ stChecked
				+ (iBr == 0 ? " onclick='checkAll(document.forms[1].nmType, this)'"
						: "") + "/> Project Manager ";
		// stReturn += "<input type='checkbox' name='nmType' value='64' " +
		// stChecked + " /> Project Manager ";
		if ((nmType & 32) != 0) {
			stChecked = " checked ";
		} else {
			stChecked = "";
		}
		/* AS -- 2Oct2011 -- Issue #18 */
		stReturn += "<input type='checkbox' name='nmType' value='32' "
				+ stChecked
				+ (iBr == 0 ? " onclick='checkAll(document.forms[1].nmType, this)'"
						: "") + "/> Project Portfolio Manager ";
		// stReturn += "<input type='checkbox' name='nmType' value='32' " +
		// stChecked + " /> Project Portfolio Manager ";
		if ((nmType & 1) != 0) {
			stChecked = " checked ";
		} else {
			stChecked = "";
		}
		// if (iBr > 0)
		stReturn += "<br>";
		/* AS -- 2Oct2011 -- Issue #18 */
		stReturn += "<input type='checkbox' name='nmType' value='1' "
				+ stChecked
				+ (iBr == 0 ? " onclick='checkAll(document.forms[1].nmType, this)'"
						: "") + "/> Project Team Member ";
		// stReturn += "<input type='checkbox' name='nmType' value='1' " +
		// stChecked
		// + " /> Project Team Member ";
		return stReturn;
	}

	public String makeList(int iF, int iType, int iUserId) {
		String stEdit = "";
		ResultSet rs = null;
		ResultSet rsCurrent = null;
		String stClass = "";
		try {
			if (iType == 1) {
				rs = this.ebEnt.dbDyn
						.ExecuteSql("SELECT nmLcId as Id, LaborCategory as value FROM LaborCategory where nmLcId not in "
								+ " (SELECT nmLaborCategoryId FROM teb_reflaborcategory where nmRefType=42 and nmRefId="
								+ iUserId + ") " + " order by LaborCategory");
				rsCurrent = this.ebEnt.dbDyn
						.ExecuteSql("SELECT nmLcId as Id, LaborCategory as value FROM LaborCategory where nmLcId in "
								+ " (SELECT nmLaborCategoryId FROM teb_reflaborcategory where nmRefType=42 and nmRefId="
								+ iUserId + ") " + " order by LaborCategory");
			} else if (iType == 2) {
				rs = this.ebEnt.dbDyn
						.ExecuteSql("select d.nmDivision as Id, d.stDivisionName as value from teb_division d where d.nmDivision not in"
								+ " (select nmDivision from teb_refdivision rdiv where rdiv.nmRefType=42 and rdiv.nmRefId="
								+ iUserId + ")" + " order by d.stDivisionName");
				rsCurrent = this.ebEnt.dbDyn
						.ExecuteSql("select d.nmDivision as Id, d.stDivisionName as value, rdiv.nmFlags "
								+ " from teb_division d, teb_refdivision rdiv where d.nmDivision=rdiv.nmDivision"
								+ " and rdiv.nmRefType=42 and rdiv.nmRefId="
								+ iUserId + " order by d.stDivisionName");
			}
			rs.last();
			int iMax = rs.getRow();
			rsCurrent.last();
			int iMaxCurrent = rsCurrent.getRow();
			int ii = 0;
			if (iMax > iMaxCurrent) {
				ii = iMax;
			} else {
				ii = iMaxCurrent;
			}
			if (ii > 15) {
				ii = 15;
			}
			if (ii < 4) {
				ii = 4;
			}
			stEdit += "<table border=0><tr><td valign=top align=center>"
					+ "\n<select style='width:150px' MULTIPLE SIZE=" + ii
					+ " name='f" + iF + "_list' id='f" + iF
					+ "_list' onDblClick=\"moveOptions(document.form"
					+ this.nmTableId + ".f" + iF + "_list, document.form"
					+ this.nmTableId + ".f" + iF + "_selected);\">";
			String stChecked = " ";
			for (int i = 1; i <= iMax; i++) {
				rs.absolute(i);
				stEdit += "\n<option value=\"" + rs.getString("Id") + "\" "
						+ stChecked + ">" + rs.getString("value") + "</option>";
			}
			stEdit += "</select>";
			String stDblClick = "";
			if (iType == 2) {
				stDblClick = " ondblclick=\"makePrimary( document.form"
						+ this.nmTableId + ".f" + iF + "_selected," + iF
						+ ");\" ";
			}
			stEdit += "</td><td valign=middle align=center>";
			stEdit += "\n<input type=button onclick=\"moveOptions(document.form"
					+ this.nmTableId
					+ ".f"
					+ iF
					+ "_list, document.form"
					+ this.nmTableId
					+ ".f"
					+ iF
					+ "_selected);\"  name=f"
					+ iF
					+ "_add  id=n"
					+ iF
					+ "_add  value='ADD &gt;&gt;'><br>&nbsp;<br>"
					+ "<input type=button onclick=\"moveOptions(document.form"
					+ this.nmTableId
					+ ".f"
					+ iF
					+ "_selected, document.form"
					+ this.nmTableId
					+ ".f"
					+ iF
					+ "_list);\"  name=f"
					+ iF
					+ "_remove id=n" + iF + "_remove  value='&lt;&lt; REMOVE'>";
			stEdit += "</td><td valign=top align=center>"
					+ "<select style='width:150px' MULTIPLE SIZE=" + ii + " "
					+ stDblClick + " name='f" + iF + "_selected' id='f" + iF
					+ "_selected'>";
			stChecked = " ";
			String stPrimary = "";
			stChecked = " ";
			for (int i = 1; i <= iMaxCurrent; i++) {
				rsCurrent.absolute(i);
				if (iType == 2 && stPrimary.length() <= 0
						&& (rsCurrent.getInt("nmFlags") & 1) != 0) {
					stPrimary = rsCurrent.getString("Id");
					stClass = " class='option2' ";
				} else {
					stClass = "";
				}
				stEdit += "\n<option value=\"" + rsCurrent.getString("Id")
						+ "\" " + stChecked + stClass + ">"
						+ rsCurrent.getString("value") + "</option>";
			}
			stEdit += "</select>";
			if (iType == 2) {
				stEdit += "<br/><font class=small>(Double click for primary)</font>";
				stEdit += "\n<input type=hidden name='f" + iF
						+ "_dcvalue' id='f" + iF + "_dcvalue' value=\""
						+ stPrimary + "\">";
				// stEdit += "<input type=hidden name='f" + iF +
				// "_dcfield' id='f" + iF
				// + "_dcfield' value=\"abcd\"><br>";
			}
		} catch (Exception e) {
			this.stError += "<br>ERROR makeList: " + e;
		}
		stEdit += "</td></tr></table>";
		if (this.epsEf.stValidationMultiSel.length() > 0) {
			this.epsEf.stValidationMultiSel += "~";
		}
		this.epsEf.stValidationMultiSel += "f" + iF + "_selected";
		return stEdit;
	}

	public String makeSupportedLC(int nmUserId) {
		String stReturn = "";
		try {
			stReturn += "<table border=0 bgcolor=blue cellpadding=1 cellspacing=1>";
			stReturn += "<tr class=d1><td align=center>Labor Categories</td><td align=center>Estimated Hours</td><td align=center>Expended Hours</td><td align=center>Productivity Factor</td><td align=center>Cost Effectiveness</td></tr>";
			ResultSet rsC = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_reflaborcategory rl, LaborCategory lc"
							+ " where nmRefType=42 and nmRefId="
							+ nmUserId
							+ " and rl.nmLaborCategoryId=lc.nmLcId order by LaborCategory");
			rsC.last();
			int iMaxC = rsC.getRow();

			ResultSet rsD = this.ebEnt.dbDyn
					.ExecuteSql("select * from users where nmUserId="
							+ nmUserId);
			rsD.absolute(1);
			double hourlyRate = rsD.getDouble("HourlyRate");
			DecimalFormat df = new DecimalFormat("$ #,###,###,##0.00");

			for (int iC = 1; iC <= iMaxC; iC++) {
				stReturn += "<tr class=d0>";
				rsC.absolute(iC);
				stReturn += "<td>" + rsC.getString("LaborCategory") + "</td>";
				stReturn += "<td align=right>" + rsC.getString("nmActualHours")
						+ "</td>";
				stReturn += "<td align=right>"
						+ rsC.getString("nmEstimatedHours") + "</td>";
				stReturn += "<td align=right>"
						+ rsC.getString("nmProductiviyFactor") + "</td>";
				stReturn += "<td align=right>"
						+ df.format(hourlyRate
								* rsC.getDouble("nmProductiviyFactor"))
						+ "</td>";
				stReturn += "</tr>";
			}
			stReturn += "</table>";
		} catch (Exception e) {
			stReturn += "<BR>ERROR makeActualHours: " + e;
		}
		return stReturn;
	}

	public String getGroupCount(String stValue) {
		String stReturn = "";
		stReturn += "0";
		return stReturn;
	}

	private String epsReport() {
		String stReturn = "<center>";
		this.setPageTitle("");
		try {
			String stT = this.ebEnt.ebUd.request.getParameter("t");
			String stCh = this.ebEnt.ebUd.request.getParameter("stChild");
			if (stCh != null && stCh.length() > 0) {
				stT = stCh; // Child has higher priority.
			}
			String stSql = "SELECT * FROM teb_table where nmTableId=" + stT;
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			if (iMax > 0) {
				rs.absolute(1);
				stPageTitle += " " + rs.getString("stTableName") + " Report";
			} else {
				stPageTitle += "";
			}
			EpsReport epsReport = new EpsReport();
			stReturn += epsReport.doReport(rs, this);
			stError += epsReport.getError();
		} catch (Exception e) {
			this.stError += "<br>ERROR: epsReport " + e;
		}
		stReturn += "</center>";
		return stReturn;
	}

	public String getError() {
		if (this.epsEf != null) {
			stError += this.epsEf.getError();
		}
		return this.stError;
	}

	public String getValitation() {
		return this.ebEnt.ebDyn.getValitation(this.epsEf.giNrValidation,
				this.epsEf.stValidation, this.epsEf.stValidationMultiSel);
	}

	public String getLoginPage() {
		String stReturn = "<center>";
		try {
			// Rotate the User Questions ...
			String stTemp = this.ebEnt.ebUd.getCookieValue("lic");
			if (stTemp == null || stTemp.length() <= 0) {
				stTemp = "0";
			}
			this.ebEnt.ebUd.iLic = Integer.parseInt(stTemp);
			// What is your favorite pet?~What is your favorite sports
			// team?~What is
			// your favorite color?
			if (this.rsMyDiv.getInt("UserQuestionsOnOff") > 0) {
				String[] aV = this.rsMyDiv.getString("UserQuestions")
						.replace("~", "\n").split("\\\n", -1);
				int i = ((this.ebEnt.ebUd.iLic + 1) % aV.length);
				this.ebEnt.ebUd.setCookie("lic", "" + i);
			}
			ResultSet rsTable = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_table where nmTableId=3");
			rsTable.absolute(1);
			stReturn += editTable(rsTable, "");
		} catch (Exception e) {
			this.stError += "<BR>ERROR: getLoginPage " + e;
		}
		return stReturn + "</center>";
	}

	public String getPageTitle() {
		if (this.stPageTitle.length() > 0) {
			this.stPageTitle = this.stPageTitle
					.replace("Projects:", "Project:");
			this.stPageTitle = this.stPageTitle.replace(
					"Administration:  Projects", "Project: Projects");

			return this.stPageTitle;
		} else {
			return "";
		}
	}

	public void setPageTitle(String stTitle) {
		this.stPageTitle = stTitle;
	}

	public String getMatchWhere(String stLabel, String stWhere) {
		String stReturn = "<select name=" + stLabel + ">";
		stReturn += this.ebEnt.ebUd.addOption2("Match beginning of word",
				"beg", stWhere);
		stReturn += this.ebEnt.ebUd.addOption2("Match anywhere in word", "any",
				stWhere);
		stReturn += this.ebEnt.ebUd.addOption2("Match end of word", "end",
				stWhere);
		stReturn += this.ebEnt.ebUd.addOption2("Exact Match", "exact", stWhere);
		stReturn += "</select>";
		return stReturn;
	}

	public String getTypeDay(String stLabel, String stWhere) {
		String stReturn = "<select name=" + stLabel + " id=" + stLabel + ">";
		stReturn += this.ebEnt.ebUd.addOption2("Vacation", "Vacation", stWhere);
		stReturn += this.ebEnt.ebUd.addOption2("Illness", "Illness", stWhere);
		stReturn += this.ebEnt.ebUd.addOption2("Other", "Other", stWhere);
		stReturn += "</select>";
		return stReturn;
	}

	public String getYear(String stLabel, String stYear) {
		String stReturn = "<select name=" + stLabel + " id=" + stLabel
				+ " onChange=\"formsd.submit();\">";
		for (int iYear = 2009; iYear < 2099; iYear++) {
			stReturn += this.ebEnt.ebUd.addOption2("" + iYear, "" + iYear,
					stYear);
		}
		stReturn += "</select>";
		return stReturn;
	}

	private String specialDay(ResultSet rsTable) {
		String stReturn = "";
		int iYear = 0;
		// String stType = this.ebEnt.ebUd.request.getParameter("stType");
		String stType = this.ebEnt.ebUd.request.getParameter("f1");
		if (stType == null) {
			stType = "";
		}
		String stPk = this.ebEnt.ebUd.request.getParameter("pk");
		if (stPk == null) {
			stPk = "0";
		}
		// String stComment = this.ebEnt.ebUd.request.getParameter("stComment");
		String stComment = this.ebEnt.ebUd.request.getParameter("f2");
		if (stComment == null) {
			stComment = "";
		}
		String stYear = this.ebEnt.ebUd.request.getParameter("stYear");
		if (stYear == null) {
			// stYear = "2011";
			stYear = "" + Calendar.getInstance().get(Calendar.YEAR);
		}
		try {
			iYear = Integer.parseInt(stYear);
		} catch (Exception e) {
		}
		try {
			rsMyDiv = this.ebEnt.dbDyn
					.ExecuteSql("SELECT d.*,o.* FROM teb_division d, teb_refdivision rd, Options o"
							+ " where rd.nmRefType=42 and rd.nmRefId="
							+ stPk
							+ " and (rd.nmFlags & 1) = 1 and rd.nmDivision=d.nmDivision and o.RecId=1");
			rsMyDiv.absolute(1);
			ResultSet rsHoliday = this.ebEnt.dbDyn
					.ExecuteSql("SELECT * FROM Calendar where dtDay >= '"
							+ iYear + "-01-01' and dtDay <= '" + iYear
							+ "-12-31' and ( nmDivision="
							+ this.rsMyDiv.getString("nmDivision")
							+ " or nmUser = " + stPk + " ) order by dtDay");
			stReturn = "</div></div></form><form method=post name=formsd id=formsd><center><h2>Select Special Day</h2>";
			stReturn += "<table><tr>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 1, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 2, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 3, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 4, rsHoliday) + "</td>";
			stReturn += "</tr><tr>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 5, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 6, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 7, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 8, rsHoliday) + "</td>";
			stReturn += "</tr><tr>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 9, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 10, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 11, rsHoliday) + "</td>";
			stReturn += "<td valign=top>"
					+ getCalendarMonth(iYear, 12, rsHoliday) + "</td>";
			stReturn += "</tr>";
			stReturn += "</table>";
			stReturn += "<table border=0><tr><td align=center>Year: "
					+ getYear("stYear", stYear) + " Reason: "
					+ getTypeDay("stType", stType);
			stReturn += " Comment/Request: <input type=text name=stComment id=stComment value=\""
					+ stComment + "\"></td></tr>";
			stReturn += "<tr><td align=center><input type='radio' id='startdatetype' name='datetype' value='start' checked=checked />Start<input type='radio' id='enddatetype' name='datetype' value='finish' />Finish&nbsp;&nbsp;&nbsp;&nbsp;";
			stReturn += "Starting Date: "
					+ "<input type=text size=12 name=stStartDate id=stStartDate readonly=readonly value=\""
					+ this.ebEnt.ebUd.request.getParameter("f3")
					+ "\">&nbsp;&nbsp;Ending Date: "
					+ "<input type=text size=12 name=stFinishDate id=stFinishDate readonly=readonly value=\""
					+ this.ebEnt.ebUd.request.getParameter("f4")
					+ "\"></td></tr>";
			stReturn += "<tr><td align=center>For a date range, select the \"Start\" or \"Finish\" radio button then click on the desired date.</td></tr>";
			stReturn += "<tr><td align=center><input type=button value='Save' onclick='specialDay2();' /><input type=button value='Cancel' onclick='window.close();' /></td></tr>";
			stReturn += "</table>";
		} catch (Exception e) {
			this.stError += "<BR>ERROR: specialDay " + e;
		} // stReturn += this.ebEnt.ebUd.dumpRequest();
		return stReturn;
	}

	private String getCalendarMonth(int iYear, int iMonth, ResultSet rsHoliday) {
		String stEdit = "";
		String stDate = "";
		try {
			int[] aWorkDays = new int[7];
			String[] aV = this.rsMyDiv.getString("stWorkdays").split(",");
			for (int i = 0; i < aWorkDays.length; i++) {
				aWorkDays[i] = 0;
			}
			if (aV != null) {
				for (int i = 0; i < aV.length; i++) {
					aWorkDays[EpsStatic.getDay(aV[i])] = 1;
				}
			}
			rsHoliday.last();
			int iMaxHoliday = rsHoliday.getRow();
			int iMax = 31;
			int iF = 0;
			int iFirstDay = this.ebEnt.dbDyn.ExecuteSql1n("select dayofweek('"
					+ iYear + "-" + iMonth + "-01')");
			stEdit += "<table border=0 align=center bgcolor='#DDD'><tr class=cal1><td colspan=7 align=center>"
					+ EpsStatic.getMonth2(iMonth)
					+ " "
					+ iYear
					+ "</td></tr>"
					+ "<tr class=cal1><td>Su</th><td>Mo</th><td>Tu</th><td>We</th><td>Th</th><td>Fr</th><td>Sa</th></tr>";
			iMax = 31;
			switch (iMonth) {
			case 2:
				if ((iYear % 4) > 0) {
					iMax = 28;
				} else {
					iMax = 29;
				}
				break;
			case 1:
			case 3:
			case 5:
			case 7:
			case 8:
			case 10:
			case 12:
				iMax = 31;
				break;
			default:
				iMax = 30;
				break;
			}
			stEdit += "<tr class='cal0'>";
			int iPos = 1;
			for (; iPos < iFirstDay; iPos++) {
				stEdit += "<td>&nbsp;</td>";
			}
			iPos--;
			for (int iD = 1; iD <= iMax; iD++, iPos++) {
				if ((iPos % 7) == 0) {
					stEdit += "</tr><tr class='cal0'>";
				}
				if (aWorkDays[(iPos % 7)] == 0) {
					stEdit += "<td style='background-color:pink;'>";
					stEdit += "\n" + iD + "</td>";
				} else {
					stDate = "" + iYear + "-";
					if (iMonth < 10) {
						stDate += "0";
					}
					stDate += iMonth + "-";
					if (iD < 10) {
						stDate += "0";
					}
					stDate += iD;
					int iFound = 0;
					for (int iH = 1; iH < iMaxHoliday; iH++) {
						rsHoliday.absolute(iH);
						if (rsHoliday.getString("dtDay").equals(stDate)) {
							iFound = 1;
							break;
						}
					}
					if (iFound > 0) {
						stEdit += "<td style='background-color:yellow;'>";
						stEdit += "\n<a href='#' title=\""
								+ rsHoliday.getString("stEvent")
								+ "\" onClick='alert(\"Cannot select this day.  \\n"
								+ rsHoliday.getString("stEvent").replace("'",
										"`") + "\")'>" + iD + "</a></td>";
					} else {
						stEdit += "<td>";
						stEdit += "\n<a href='#' onClick=\"selectSpecialDay2( '"
								+ iMonth
								+ "/"
								+ iD
								+ "/"
								+ iYear
								+ "');\">"
								+ iD + "</a></td>";
					}
				}
			}
			for (; (iPos % 7) != 0; iPos++) {
				stEdit += "<td>&nbsp;</td>";
			}
		} catch (Exception e) {
		}
		stEdit += "</tr></table>";
		return stEdit;
	}

	private void saveSpecialDays(int nmFieldId, String stValue, String stPk) {
		try {
			// Handle DELETE FIRST
			String stDel = this.ebEnt.ebUd.request.getParameter("f" + nmFieldId
					+ "_del");
			if (stDel != null && stDel.trim().length() > 1) {
				String[] aV = stDel.trim().split("~", -1);
				for (int i = 0; i < aV.length; i++) {
					if (aV[i].length() > 0) {
						this.ebEnt.dbDyn
								.ExecuteUpdate("delete from Calendar where RecId="
										+ aV[i]);
					}
				}
			}
			String[] aLines = stValue.split("\\|", -1);
			String[] aSave = new String[aLines.length];
			String stSql = "";
			for (int iL = 0; iL < aSave.length; iL++) {
				aSave[iL] = "";
			}
			for (int iL = 1; iL < aLines.length; iL++) {
				try {
					String[] aV = aLines[iL].trim().split("~", -1);
					if (aV != null && aV.length > 1) {
						int iIdx = Integer.parseInt(aV[0]);
						aSave[iIdx] = aLines[iL].trim();
					}
				} catch (Exception e) {
				}
			}
			for (int iL = 0; iL < aSave.length; iL++) {
				if (aSave[iL].length() > 0) {
					String[] aV = aSave[iL].trim().split("~");
					// |1~Vacation~~12/20/2011~Pending
					int iRecId = this.ebEnt.dbDyn
							.ExecuteSql1n("select max(RecId) from Calendar where dtDay = \""
									+ this.ebEnt.ebUd.fmtDateToDb(aV[3])
									+ "\" and nmUser=" + stPk);
					if (iRecId <= 0) {
						iRecId = this.ebEnt.dbDyn
								.ExecuteSql1n("select max(RecId) from Calendar");
						iRecId++;
					}
					stSql = "replace into Calendar (RecId,stType,nmFlags,dtDay,stEvent,nmDivision,nmUser)"
							+ "values ("
							+ iRecId
							+ ",\""
							+ aV[1]
							+ "\",2,\""
							+ this.ebEnt.ebUd.fmtDateToDb(aV[3])
							+ "\",\""
							+ aV[2]
							+ "\","
							+ this.rsMyDiv.getString("nmDivision")
							+ ","
							+ stPk
							+ ")";
					this.ebEnt.dbDyn.ExecuteUpdate(stSql);
				}
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR : saveSpecialDays " + e;
		}
	}

	private String processApprove() {
		String stReturn = "<center></form><form method=post><h1>Processing Special Day Approvals</h1><table class=l1small>";
		try {
			String stSave = this.ebEnt.ebUd.request.getParameter("submit");
			String stSql = "select c.*,u.FirstName,u.LastName from Calendar c left join Users u on u.nmUserId=c.nmUser where c.dtDay >= now() and  c.nmUser > 0 and c.nmFlags = 2 order by c.dtDay";
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			if (stSave != null && stSave.length() > 0) {
				if (!stSave.contains("Cancel")) {
					for (int iR = 1; iR <= iMax; iR++) {
						rs.absolute(iR);
						String stFlags = this.ebEnt.ebUd.request
								.getParameter("approve" + rs.getString("RecId"));
						if (stFlags != null && stFlags.length() > 0) {
							if (stFlags.equals("8")) // Declined
							{
								this.ebEnt.dbDyn
										.ExecuteUpdate("delete from Calendar where RecId="
												+ rs.getString("RecId"));
							} else {
								this.ebEnt.dbDyn
										.ExecuteUpdate("update Calendar set nmFlags='"
												+ stFlags
												+ "' where RecId="
												+ rs.getString("RecId"));
							}
						}
					}
				}
				this.ebEnt.ebUd.setRedirect("./");
				return ""; // ----------------------------->
			}
			if (iMax > 0) {
				stReturn += "<tr class=d1><th>First Nm</th><th>Last Nm</th><th>Type</th><th>Comment</th><th>Date</th>"
						+ "<th>Approve</th><th>Decline</th><th>Pending</th></tr>";
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					stReturn += "<tr class=d0>";
					stReturn += "<td>" + rs.getString("FirstName") + "</td>";
					stReturn += "<td>" + rs.getString("LastName") + "</td>";
					stReturn += "<td>" + rs.getString("stType") + "</td>";
					stReturn += "<td>" + rs.getString("stEvent") + "</td>";
					stReturn += "<td>"
							+ this.ebEnt.ebUd.fmtDateFromDb(rs
									.getString("dtDay")) + "</td>";
					stReturn += "<td align=center><input type=radio name=approve"
							+ rs.getString("RecId") + " value=4></td>";
					stReturn += "<td align=center><input type=radio name=approve"
							+ rs.getString("RecId") + " value=8></td>";
					stReturn += "<td align=center><input type=radio name=approve"
							+ rs.getString("RecId") + " value=2 checked></td>";
					stReturn += "</tr>";
				}
				stReturn += "<tr class=d0><td colspan=8 align=center>"
						+ "<input type=submit name=submit value='Save'>"
						+ "<input type=submit name=submit value='Cancel'>"
						+ "</th></tr>";
			} else {
				stReturn += "<BR>No pending requests";
			}
		} catch (Exception e) {
		}
		stReturn += "</table>";
		return stReturn;
	}

	private String makeLevel(String stName, String stLevel) {
		String stReturn = "<select name='" + stName + "' name='" + stName
				+ "'>";
		try {
			int iLevel = Integer.parseInt(stLevel);
			for (int iL = 0; iL <= (iLevel + 1); iL++) {
				stReturn += this.ebEnt.ebUd.addOption2("" + iL, "" + iL,
						stLevel);
			}
		} catch (Exception e) {
			stError += "<BR>ERROR makeLevel: " + e;
		}
		stReturn += "</select>";
		return stReturn;
	}

	public String makeWordList(String stValue) {
		if (stValue == null)
			stValue = "";
		StringBuilder abWordList = new StringBuilder(stValue.length() + 1000);
		stValue = stValue.replace("-", " ");
		stValue = stValue.replace("?", " ");
		stValue = stValue.replace(",", " ");
		stValue = stValue.replace("\"", "");
		stValue = stValue.replace(".", " ");
		stValue = stValue.replace("  ", " ");
		stValue = stValue.replace("  ", " ");
		String[] aWords = stValue.split(" ", -1);
		for (int iW = 0; iW < aWords.length; iW++) {
			if (aWords[iW].length() > 0) {
				if (iW > 0)
					abWordList.append(",");
				abWordList.append("\"");
				abWordList.append(aWords[iW]);
				abWordList.append("\"");
			}
		}
		return abWordList.toString();
	}

	public String validateD50(ResultSet rsTable, ResultSet rsF, String stPk,
			String stValue, String stProject, int nmBaseline) {
		if (stValue == null)
			stValue = "";
		String stReturn = stValue;
		int iDup = 0;
		int iWordCount = 0;

		try {
			String stWords = makeWordList(stValue);
			String stId = this.ebEnt.dbDyn
					.ExecuteSql1("select ReqId from Requirements where nmProjectId="
							+ stProject
							+ " and nmBaseline="
							+ nmBaseline
							+ " and RecId=" + stPk);
			int stLvl = this.ebEnt.dbDyn
					.ExecuteSql1n("select ReqLevel from Requirements where nmProjectId="
							+ stProject
							+ " and nmBaseline="
							+ nmBaseline
							+ " and RecId=" + stPk);
			String[] aWords = null;
			String stSql = "";
			int iSch = 0;
			int err = 0;

			// check low level or parent description errors #31
			if (!this.ebEnt.ebUd.request.getParameter("stAction").equals(
					"runeob")) {
				stSql = "SELECT * FROM requirements where nmProjectId="
						+ stProject + " and nmBaseline=" + nmBaseline
						+ " order by RecId desc";
				ResultSet rs1 = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs1.last();
				iSch = rs1.getRow();
				int iLastLevel = -1;
				SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy");
				Calendar c = Calendar.getInstance();
				c.add(Calendar.DATE, 10); // number of days to add
				String dateEnd = fmt.format(c.getTime()); // dt is now the new
															// date
				ResultSet rs = this.ebEnt.dbDyn
						.ExecuteSql("select ProjectName, ProjectManagerAssignment, ProjectPortfolioManagerAssignment, BusinessAnalystAssignment, Sponsor FROM projects where RecId="
								+ stPk);
				rs.absolute(1);

				String parentId = "";
				for (int i = 1; i <= iSch; i++) {
					rs1.absolute(i);
					if (stId.equals(rs1.getString("RecId"))) {
						parentId = rs1.getString("ReqParentRecId");
					}
					if (iLastLevel == rs1.getInt("ReqLevel")
							&& stValue.length() <= 0
							&& stId.equals(rs1.getString("RecId"))) {
						// same level as before, therefore we are LAST
						this.ebEnt.ebUd
								.setPopupMessage("[ID:"
										+ rs1.getString("RecId")
										+ "] "
										+ rsF.getString("stLabel")
										+ ": Children tasks must have a description specified.");
						makeMessage(
								rs.getString("ProjectName"),
								rs.getString("ProjectPortfolioManagerAssignment")
										+ ","
										+ rs.getString("ProjectManagerAssignment")
										+ ","
										+ rs.getString("BusinessAnalystAssignment"),
								rs1.getString("ReqTitle"),
								"Children tasks must have a description specified",
								dateEnd);
						err = 1;
						break;
					}
					/*
					 * else if(iLastLevel != rs1.getInt("ReqLevel") &&
					 * stValue.length() > 0 &&
					 * stId.equals(rs1.getString("RecId"))){ //parent
					 * this.ebEnt.ebUd.setPopupMessage("[ID:" +
					 * rs1.getString("RecId") + "] " + rsF.getString("stLabel")
					 * + ": Parent tasks cannot have a description.");
					 * makeMessage("All",
					 * rs.getString("ProjectPortfolioManagerAssignment"
					 * )+","+rs.getString( "ProjectManagerAssignment"
					 * )+","+rs.getString("BusinessAnalystAssignment"),
					 * rs1.getString("ReqTitle"), rs.getString("ProjectName")+
					 * ": Parent tasks cannot have a descriptions" , dateEnd);
					 * err = 1; break; }
					 */
					else if (parentId.equals(rs1.getString("RecId"))) {
						if (iLastLevel != rs1.getInt("ReqLevel")
								&& rs1.getString("ReqDescription") != null
								&& rs1.getString("ReqDescription").length() > 0) {
							// parent
							this.ebEnt.ebUd
									.setPopupMessage("[ID:"
											+ stId
											+ "] "
											+ rsF.getString("stLabel")
											+ ": Parent tasks cannot have a description.");
							makeMessage(
									rs.getString("ProjectName"),
									rs.getString("ProjectPortfolioManagerAssignment")
											+ ","
											+ rs.getString("ProjectManagerAssignment")
											+ ","
											+ rs.getString("BusinessAnalystAssignment"),
									rs1.getString("ReqTitle"),
									"Parent tasks cannot have a descriptions",
									dateEnd);
							err = 1;
							break;
						}
						parentId = rs1.getString("ReqParentRecId");
					}
					iLastLevel = rs1.getInt("ReqLevel");
				}
			}

			if (err <= 0) {
				if (stValue.length() > 0) {
					aWords = stWords.split(",");
					iWordCount = aWords.length;
					int iCount = EpsStatic.countIndexOf(stValue, ".");
					if (iCount > 1) {
						iD50Flags |= 0x1;
						this.ebEnt.ebUd.setPopupMessage("[ID:" + stId + "] "
								+ rsF.getString("stLabel") + ": contains "
								+ iCount + " Sentences. ");
					}
					iCount = EpsStatic.countIndexOf(stValue, ",");
					if (iCount > 0) {
						iD50Flags |= 0x2;
						this.ebEnt.ebUd.setPopupMessage("[ID:" + stId + "] "
								+ rsF.getString("stLabel")
								+ ": contains lists or clauses.");
					} else {
						iCount = EpsStatic.countIndexOf(stValue, ":");
						if (iCount > 0) {
							iD50Flags |= 0x4;
							this.ebEnt.ebUd.setPopupMessage("[ID:" + stId
									+ "] " + rsF.getString("stLabel")
									+ ": contains clauses.");
						}
					}
				} else
					iWordCount = 0;
				if (iWordCount < this.rsMyDiv
						.getInt("ReqtsSpecificationMinimumWords") && stLvl > 1) {
					iD50Flags |= 0x8;
					this.ebEnt.ebUd.setPopupMessage(rsF.getString("stLabel")
							+ ": contains "
							+ iWordCount
							+ " words. "
							+ " Minumum words required: "
							+ this.rsMyDiv
									.getInt("ReqtsSpecificationMinimumWords"));
				}
				if (iWordCount > this.rsMyDiv
						.getInt("ReqtsSpecificationMaximumWords")) {
					iD50Flags |= 0x10;
					this.ebEnt.ebUd.setPopupMessage("[ID:"
							+ stId
							+ "] "
							+ rsF.getString("stLabel")
							+ ": contains too many ("
							+ iWordCount
							+ ") words. "
							+ " Maximum words: "
							+ this.rsMyDiv
									.getInt("ReqtsSpecificationMaximumWords"));
				}
				if (iWordCount > 0 && stWords.trim().length() > 0) {
					ResultSet rsType = this.ebEnt.dbDyn
							.ExecuteSql("SELECT count(*) cnt,stKeywordType, stKeyword FROM teb_dictionary where stKeyword in "
									+ "("
									+ stWords
									+ ") group by stKeywordType");
					rsType.last();
					int iMaxW = rsType.getRow();
					for (int iW = 1; iW <= iMaxW; iW++) {
						rsType.absolute(iW);
						if (rsType.getString("stKeywordType").equals("A")) {
							iD50Flags |= 0x20;
							this.ebEnt.ebUd.setPopupMessage("[ID:" + stId
									+ "] " + rsF.getString("stLabel")
									+ ": contains " + rsType.getString("cnt")
									+ " adjectives/adverbs ");
						}
						if (rsType.getString("stKeywordType").equals("C")) {
							iD50Flags |= 0x40;
							this.ebEnt.ebUd.setPopupMessage("[ID:" + stId
									+ "] " + rsF.getString("stLabel")
									+ ": contains conjunctions ");
							// this.ebEnt.ebUd.setPopupMessage("[ID:" + stId +
							// "] " +
							// rsF.getString("stLabel") + ": contains " +
							// rsType.getString("cnt") + " conjunctions ");
						}
					}
				}
			}
		} catch (Exception e) {
			stError += "<BR>ERROR validateD50: " + e;
		}
		return stReturn;
	}

	public String validateD53(ResultSet rsTable, ResultSet rsF, String stPk,
			String stValue, String stProject, int nmBaseline) {
		if (stValue == null)
			stValue = "";
		String stReturn = stValue;
		try {
			String stWords = makeWordList(stValue);
			String[] aWords = null;
			String stId = this.ebEnt.dbDyn
					.ExecuteSql1("select SchId from Schedule where nmProjectId="
							+ stProject
							+ " and nmBaseline="
							+ nmBaseline
							+ " and RecId=" + stPk);

			if (stValue.length() > 0) {
				aWords = stWords.split(",");
				int iVerb = this.ebEnt.dbDyn
						.ExecuteSql1n("select count(*) from teb_dictionary"
								+ " where stKeywordType='V' and stKeyword = "
								+ aWords[0]);
				if (iVerb <= 0) {
					iD53Flags |= 0x1;
					this.ebEnt.ebUd.setPopupMessage("[ID:" + stId + "] "
							+ rsF.getString("stLabel")
							+ ": does not begin with a verb.");
				}
			}
		} catch (Exception e) {
			stError += "<BR>ERROR validateD53: " + e;
		}
		return stReturn;
	}

	public String validateRID(ResultSet rsTable, ResultSet rsF, String stPk,
			String stValue, String stProject, int nmBaseline) {
		if (stValue == null)
			stValue = "";
		String stReturn = stValue;
		try {
			if (stValue.length() > 0) {
				// ?stAction=projects&t=12&do=xls&pk=5&parent=&child=34&from=0&a=editfull&r=4#next
				int i = this.ebEnt.dbDyn
						.ExecuteSql1n("SELECT count(*) FROM Requirements r,Projects p"
								+ " where r.nmProjectId=p.RecId and r.nmBaseline=p.CurrentBaseline and (r.ReqFlags & 0x10) != 0 "
								+ " and r.nmProjectId="
								+ this.ebEnt.ebUd.request.getParameter("pk")
								+ " and r.RecId=" + stValue);
				if (i <= 0)
					this.ebEnt.ebUd.setPopupMessage("[ID:" + stPk + "] "
							+ rsF.getString("stLabel")
							+ ": is not a valid low level ID.");
			} else
				this.ebEnt.ebUd.setPopupMessage("[ID:" + stPk + "] "
						+ rsF.getString("stLabel") + ": cannot be blank.");
		} catch (Exception e) {
			stError += "<BR>ERROR validateRID: " + e;
		}
		return stReturn;
	}

	public String validateD22(ResultSet rsTable, ResultSet rsF, String stPk,
			String stValue, String stProject, int nmBaseline) {
		if (stValue == null)
			stValue = "";
		String stReturn = stValue;
		int iDup = 0;
		try {
			String stSql = "select count(*) as cnt from "
					+ rsTable.getString("stDbTableName") + " where "
					+ rsF.getString("stDbFieldName") + " = \"" + stValue
					+ "\" and " + rsTable.getString("stPk") + " != " + stPk;
			switch (rsTable.getInt("nmTableId")) {
			case 19:
			case 21:
				stSql += " and nmProjectId=" + stProject + " and nmBaseline="
						+ nmBaseline;
				break;
			case 27: // Criteria
				stSql += " and nmDivision = 1"; // only count once.
			}
			/*
			 * iDup = this.ebEnt.dbDyn.ExecuteSql1n(stSql); if (iDup > 0) {
			 * stReturn += " - Copy" + stPk;
			 * this.ebEnt.ebUd.setPopupMessage("Duplicate entry for field: " +
			 * rsF.getString("stLabel") + " avoided by adding: - Copy" + stPk);
			 * }
			 */
		} catch (Exception e) {
			stError += "<BR>ERROR validateD22: " + e;
		}
		return stReturn;
	}

	public String validateD22(String stParam, ResultSet rs2, String stName,
			String stTable, String stPkField) {
		String stValue = "";
		String stValueOld = "";
		String stPopupError = "";
		char myChar;
		int iPos = 0;
		int iSpaceCount = 0;
		try {
			stValue = this.ebEnt.ebUd.request.getParameter(stParam);
			stValueOld = rs2.getString(stName);
			if (stValue == null) {
				stPopupError += "\nInvalid/missing field content";
			} else if (stValue.length() < 2) {
				stPopupError += "\nToo short. Must be at least 2 characters ";
			} else if (stValue.length() > 256) {
				stPopupError += "\nToo Long. Must be less then 256 characters ";
			} else {
				for (iPos = 0; stPopupError.length() <= 0
						&& iPos < stValue.length(); iPos++) {
					myChar = stValue.charAt(iPos);
					if ((myChar >= 'a' && myChar <= 'z')
							|| (myChar >= 'A' && myChar <= 'Z')) {
						iSpaceCount = 0;
						continue;
					} else {
						if (iPos == 0) {
							stPopupError += "\nFirst character must be ALPHA ";
							break;
						} else if (myChar >= '0' && myChar <= '9') {
							iSpaceCount = 0;
							continue;
						} else if (myChar == '\'' || myChar == '-') {
							iSpaceCount = 0;
							continue;
						} else if (myChar == ' ') {
							iSpaceCount++;
							if (iSpaceCount > 1) {
								stPopupError += "\nToo many spaces ";
							} else {
								continue;
							}
						} else {
							stPopupError += "\nInvalid special character. Only SPACE, HYPHEN and APOSTORPHES are permitted";
							break;
						}
					}
				}
			}
			if (stPopupError.length() <= 0) {
				// Check UNIQE in DB
				int iDup = 0;
				ResultSet rsDup = this.ebEnt.dbDyn
						.ExecuteSql("select count(*) as cnt," + stPkField
								+ " from " + stTable + " where " + stName
								+ " = \"" + stValue + "\" ");
				if (rsDup != null) {
					rsDup.last();
					int iMax = rsDup.getRow();
					for (int iD = 1; iD <= iMax; iD++) {
						rsDup.absolute(iD);
						String stTemp = rsDup.getString(stPkField);
						if (stTemp != null && stTemp.length() > 0) {
							if (!stTemp.equals(rs2.getString(stPkField))) {
								iDup++;
								break;
							}
						}
					}
					if (iDup > 0) {
						stPopupError += "\nThis field is not unique. Please change the value ";
					}
				}
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR : validateD22 " + e;
			stPopupError += "<BR>ERROR : validateD22 " + e;
		}
		return stPopupError.trim();
	}

	/*
	 * Flag low level efforts that exceed 40 hours
	 */
	public String validateEffort(ResultSet rsF, String stPk, String stValue,
			String stProject, int nmBaseline) {
		if (stValue == null)
			stValue = "";
		String stReturn = stValue;
		Double newEffort = Double.parseDouble(stValue);
		int lowlvl = 0;
		// String stId = "";
		try {
			// stId =
			// this.ebEnt.dbDyn.ExecuteSql1("select RecId from Schedule where nmProjectId="
			// + stProject + " and nmBaseline=" + nmBaseline + " and RecId=" +
			// stPk);
			lowlvl = this.ebEnt.dbDyn
					.ExecuteSql1n("SELECT count(*) FROM Schedule s, Projects p where s.nmProjectId=p.RecId and s.nmBaseline=p.CurrentBaseline and (SchFlags & 0x10 ) != 0 and s.nmProjectId="
							+ stProject + " and s.RecId=" + stPk);

			if (lowlvl > 0 && newEffort > rsMyDiv.getDouble("MaximumTaskHours")) {
				this.ebEnt.ebUd.setPopupMessage("[ID:" + stPk
						+ "] Effort may not exceed "
						+ rsMyDiv.getDouble("MaximumTaskHours") + " hours");
			}
		} catch (Exception e) {
			stError += "<BR>ERROR validateEffort: " + e;
		}
		return stReturn;
	}

	public int getPriviledge(String stPrivs) {
		int nmPriviledge = 0;
		String[] aV = stPrivs.trim().split(",");
		String stValue = "";
		for (int iV = 0; iV < aV.length; iV++) {
			stValue = aV[iV].trim().toLowerCase();
			if (stValue.startsWith("ad")) {
				nmPriviledge |= 0x400;
			} else if (stValue.startsWith("b")) {
				nmPriviledge |= 0x80;
			} else if (stValue.startsWith("ex")) {
				nmPriviledge |= 0x200;
			} else if (stValue.startsWith("su")) {
				nmPriviledge |= 0x800;
			} else if (stValue.startsWith("project manager")
					|| stValue.startsWith("pm")) {
				nmPriviledge |= 0x40;
			} else if (stValue.startsWith("project portfolio manager")
					|| stValue.startsWith("ppm")) {
				nmPriviledge |= 0x20;
			} else if (stValue.startsWith("project team member")
					|| stValue.startsWith("ptm")) {
				nmPriviledge |= 0x1;
			} else {
				stError += "<BR>getPriviledge INVALID PRIV: " + stValue;
			}
		}
		return nmPriviledge;
	}

	public String getPriviledgeTypes(int nmPriviledge) {
		StringBuilder abReturn = new StringBuilder(255);

		if ((nmPriviledge & 0x400) != 0)
			abReturn.append(",Ad");
		if ((nmPriviledge & 0x80) != 0)
			abReturn.append(",Ba");
		if ((nmPriviledge & 0x200) != 0)
			abReturn.append(",Ex");
		if ((nmPriviledge & 0x800) != 0)
			abReturn.append(",Su");
		if ((nmPriviledge & 0x40) != 0)
			abReturn.append(",Pm");
		if ((nmPriviledge & 0x20) != 0)
			abReturn.append(",Ppm");
		if ((nmPriviledge & 0x1) != 0)
			abReturn.append(",Ptm");
		String stReturn = abReturn.toString();
		if (stReturn != null && stReturn.length() > 0)
			stReturn = stReturn.substring(1);
		return stReturn;
	}

	public String runEOB() {
		long startTime = System.nanoTime();
		String stReturn = "<br><h1>End of Business Processor</h1>";
		String[] aItems = null;
		String[] aFields = null;
		String[] iWords = null;
		int iWordCount = 0;
		int iLc = 0;
		int iLcNumUsers = 0;
		Map<String, String> exchangeRatesMap = new HashMap<String, String>();

		// activity #16 see: EpsEditField line 2639 (send message when update
		// user)
		// activity #17 see: EpsUserData line 3060 (send message when delete
		// user)

		try {
			this.ebEnt.ebUd.setLoginId(5); // set to EOB user
			long endTime = 0;
			long elapsedTime = 0;
			double seconds = 0;
			SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy");
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DATE, 10); // number of days to add
			String dateEnd = fmt.format(c.getTime()); // dt is now the new date

			// backup databases -- activity #54
			String mysqlpath = "C:\\Program Files\\MySQL\\MySQL Server 5.5\\bin";
			String mysqldumppath = "C:\\EPS-DB-BACKUP";
			boolean backedup = true;
			if (!this.ebEnt.dbEnterprise.getConnect().exportTo(mysqlpath,
					mysqldumppath)) {
				this.stError += this.ebEnt.dbEnterprise.getConnect().getError();
				backedup = false;
			}
			if (!this.ebEnt.dbDyn.getConnect().exportTo(mysqlpath,
					mysqldumppath)) {
				this.stError += this.ebEnt.dbDyn.getConnect().getError();
				backedup = false;
			}
			if (!this.ebEnt.dbEb.getConnect()
					.exportTo(mysqlpath, mysqldumppath)) {
				this.stError += this.ebEnt.dbEb.getConnect().getError();
				backedup = false;
			}
			if (!this.ebEnt.dbCommon.getConnect().exportTo(mysqlpath,
					mysqldumppath)) {
				this.stError += this.ebEnt.dbCommon.getConnect().getError();
				backedup = false;
			}

			if (backedup) {
				makeMessage("All", getAllUsers(getPriviledge("ppm")),
						"Database Backup", "Databases are backed-up", dateEnd);
			}

			// Delete all users previous tasks -- activity #1
			this.ebEnt.dbEnterprise
					.ExecuteUpdate("update X25Task set nmTaskFlag=4 where nmTaskFlag=1");
			// Delete all users previous messages -- activity #2
			this.ebEnt.dbEnterprise
					.ExecuteUpdate("update X25Task set nmTaskFlag=4 where nmTaskFlag=2");

			// update ExchangeRate for divisions. -- activity #10
			String answer = "";
			String stSql = "select nmDivision,stCurrency from teb_division";
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			while (rs.next()) {
				String currencyCode = rs.getString(2);
				if (!exchangeRatesMap.containsKey(currencyCode)) {
					// URL convert = new
					// URL("http://www.exchangerate-api.com/"+currencyCode+"/usd/1?k=eBMtn-V4U6g-fiLRp");
					URL convert = new URL("http://www.exchangerate-api.com/"
							+ currencyCode + "/usd/1?k=puupy-nZ3tU-rHeQe");
					BufferedReader in = new BufferedReader(
							new InputStreamReader(convert.openStream()));
					answer = in.readLine();
					exchangeRatesMap.put(currencyCode, answer);
					in.close();
				} else {
					answer = exchangeRatesMap.get(currencyCode);
				}
				String insertsql = "update teb_division set nmExchangeRate="
						+ answer
						+ ",dtExchangeRate=CURRENT_TIMESTAMP where nmDivision="
						+ rs.getInt(1);
				this.ebEnt.dbDyn.ExecuteUpdate(insertsql);
			}
			rs.close();

			// TANPD: TODO: DOn't know what it check
			// Now, let's fill in User's AVAILABLE hours in calendar to END OF
			// LAST
			// schedule
			// String stMaxDate =
			// this.ebEnt.dbDyn.ExecuteSql1("select max(SchFinishDate) from Projects p, Schedule s where p.RecId=s.nmProjectId and p.CurrentBaseline=s.nmBaseline and (s.SchFlags & 0x10) != 0");
			// String stMaxDate = this.ebEnt.dbDyn
			// .ExecuteSql1("select DATE_ADD(curdate(),INTERVAL "
			// + (this.rsMyDiv.getInt("AllocationPeriod") + 180) + " DAY)");
			// Calendar dtEnd = EpsStatic.getCalendar(stMaxDate);
			// ResultSet rsU = this.ebEnt.dbDyn
			// .ExecuteSql("select distinct u.nmUserId,WeeklyWorkHours from Users u"
			// + " left join teb_allocate a1 on a1.nmUserId=u.nmUserId"
			// + " where a1.dtAllocate is null or a1.dtAllocate < '" + stMaxDate
			// + "'");
			// rsU.last();
			// int iUMax = rsU.getRow();
			// int iCount = 0;
			// SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			// // stReturn = formatter.format(dtEnd.getTime());
			// this.ebEnt.dbDyn.ExecuteUpdate("lock tables teb_allocate write");
			// for (int iU = 1; iU <= iUMax; iU++) {
			// rsU.absolute(iU);
			// String stTemp = rsU.getString("WeeklyWorkHours");
			// String[] aW = null;
			// if (stTemp == null || stTemp.length() < 7)
			// stTemp = "~8~8~8~8~8~0~0";
			//
			// aW = stTemp.split("~", -1);
			// aW[0] = aW[7]; // Sun=1 Mon=2 Tue=3 Wed=4 Thu=5 Fri=6 Sat=7
			// // Calendar.SATURDAY
			//
			// String stStart = this.ebEnt.dbDyn
			// .ExecuteSql1("select max(dtAllocate) from teb_allocate where nmUserId ="
			// + rsU.getString("nmUserId") + " and dtAllocate >= curdate()");
			//
			// Calendar dtStart = null;
			//
			// if (stStart != null && stStart.length() > 8)
			// dtStart = EpsStatic.getCalendar(stStart);
			// else
			// dtStart = Calendar.getInstance();
			// iCount = 0;
			// StringBuilder sbSql = new StringBuilder(1000000);
			// sbSql
			// .append("replace into teb_allocate (dtAllocate,nmUserId,nmAvailable) values ");
			// while (dtStart.before(dtEnd) && iCount < 4000) {
			// // Check Holiday or DAY OFF !!!
			// if (iCount > 0)
			// sbSql.append(",");
			// iCount++;
			// sbSql.append("('");
			// sbSql.append(formatter.format(dtStart.getTime()));
			// sbSql.append("',");
			// sbSql.append(rsU.getString("nmUserId"));
			// sbSql.append(",");
			// sbSql.append(aW[(dtStart.get(Calendar.DAY_OF_WEEK) - 1)]);
			// sbSql.append(")");
			// dtStart.add(Calendar.DAY_OF_YEAR, +1); // Add 1 Day
			// }
			// this.ebEnt.dbDyn.ExecuteUpdate(sbSql.toString());
			// }
			//
			// System.out.println("UNKNOWN TIME: " + (System.currentTimeMillis()
			// - st)
			// / 1000);
			// System.out.println(ebEnt.dbEnterprise
			// .ExecuteSql1n("select count(*) from x25task"));
			//
			// endTime = System.nanoTime();
			// elapsedTime = endTime - startTime;
			// seconds = elapsedTime / 1.0E09;
			// stReturn += "<br>Users (" + iUMax + ") " + seconds;
			// this.ebEnt.dbDyn.ExecuteUpdate("unlock tables");
			// endTime = System.nanoTime();
			// elapsedTime = endTime - startTime;
			// seconds = elapsedTime / 1.0E09;
			// stReturn += "<br>Unlock " + seconds;

			// do allocate -- activity #51,#52,#53
			stReturn += this.epsEf.processAllocate(this, 1);

			runD50D53();

			// Check Criteria Assignment
			// check criteria has weight must have users as responsiblities --
			// activity #18
			stSql = "SELECT count(*) as cnt ,c.nmDivision, d.stDivisionName FROM Criteria c, teb_division d "
					+ "where c.nmDivision=d.nmDivision and (Responsibilities is null or Responsibilities = '') "
					+ "and WeightImportance != 0 group by c.nmDivision,stDivisionName";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				// makeTask(getUsers(rs.getInt("nmDivision"),
				// getPriviledge("ppm")),
				// "Please assign: " + rs.getInt("cnt") +
				// " CRITERIA for Division: " +
				// rs.getString("stDivisionName"), "7/1/2011");
				makeMessage(
						"All",
						getUsers(rs.getInt("nmDivision"), getPriviledge("ppm")),
						"Criteria",
						"Please assign: " + rs.getInt("cnt")
								+ " Criteria for Division: "
								+ rs.getString("stDivisionName"), dateEnd);
			}
			rs.close();

			// Verify atleast 1 Criteria has non zero weight -- activity #19
			stSql = "SELECT count(*) as cnt ,c.nmDivision, d.stDivisionName FROM Criteria c, teb_division d where"
					+ " c.nmDivision=d.nmDivision and WeightImportance != 0 group by c.nmDivision,stDivisionName";
			if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) == 0) {
				makeMessage(
						"All",
						getUsers(rs.getInt("nmDivision"), getPriviledge("ppm")),
						"Criteria Requirement",
						"Missing non-zero for Division: "
								+ rs.getString("stDivisionName"), dateEnd);
			}

			// compute project ranking -- activity #3,#4, #20, #21
			EpsReport epsReport = new EpsReport();
			epsReport.doProjectRanking(this);
			/*
			 * makeMessage("All", getAllUsers(getPriviledge("ppm")) + "," +
			 * getAllUsers(getPriviledge("ex")), "Projects",
			 * "The ranking of the active projects has been updated", dateEnd);
			 */

			// Missing Divisions
			// verify atleast one division exists -- activity #8
			stSql = "select count(nmDivision) as divs from teb_division";
			if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) < 1) {
				// send message to admin
				makeMessage("All", getAllUsers(getPriviledge("ad")),
						"Missing Division", "Missing Division", dateEnd);
			}
			// verify all divisions have atleast 1 user -- activity #9
			stSql = "select stDivisionName from teb_division where nmUsersInDivision <= 0";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			while (rs.next()) {
				// send message to admin
				makeMessage(
						"All",
						getAllUsers(getPriviledge("ad")),
						"No Users In Division",
						"Please assign User to Division: "
								+ rs.getString("stDivisionName"), dateEnd);
			}
			rs.close();

			// verify atleast one labor category exists -- activity #5
			stSql = "select count(nmLcId) as cats from LaborCategory";
			if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) < 1) {
				// send message to ppm
				makeMessage("All", getAllUsers(getPriviledge("ppm")),
						"Missing Labor Category", "Missing Labor Category",
						dateEnd);
			}

			// Missing Labor category
			List<List<String>> stByPrjSch = this.epsEf.getMissingLc(0);
			if (stByPrjSch.size() > 0) {
				// stByPrjSch =
				// "<h2>Missing Resources in Labor Categories per Project Task</h2><table border=1>"
				// +
				// "<tr><th>Project Name</th><th>Task ID</th><th>Labor Category</th><th># Missing</th></tr>"
				// + stByPrjSch;
				// stByPrjSch += "</table>";
				// stReturn += makeTask(11, stByPrjSch);
				for (List<String> list : stByPrjSch) {
					makeMessage(
							list.get(0),
							getAllUsers(getPriviledge("ppm")),
							"Missing Resources in Labor Categories per Project Task",
							"Task ID: " + list.get(1) + " - Labor Category: "
									+ list.get(2) + " - #Missing: "
									+ list.get(3), dateEnd);
				}
			}

			// check lc 'Business Analyst', 'Project Manager', 'Project
			// Portfolio
			// Manager' exist -- activity #7
			stSql = "select count(lc.nmLcId) as cats from LaborCategory lc where lc.LaborCategory='Project Manager'";
			if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) < 1) {
				makeMessage("All", getAllUsers(getPriviledge("ppm")),
						"Labor Category",
						"Missing Labor Category: 'Project Manager'", dateEnd);
			}
			stSql = "select count(lc.nmLcId) as cats from LaborCategory lc where lc.LaborCategory='Project Portfolio Manager'";
			if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) < 1) {
				makeMessage("All", getAllUsers(getPriviledge("ppm")),
						"Labor Category",
						"Missing Labor Category: 'Project Portfolio Manager'",
						dateEnd);
			}
			stSql = "select count(lc.nmLcId) as cats from LaborCategory lc where lc.LaborCategory='Business Analyst'";
			if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) < 1) {
				makeMessage("All", getAllUsers(getPriviledge("ppm")),
						"Labor Category",
						"Missing Labor Category: 'Business Analyst'", dateEnd);
			}

			// update high, low, avg salary for each labor category -- activity
			// #6
			this.epsEf.processUsersInLaborCategory();

			endTime = System.nanoTime();
			elapsedTime = endTime - startTime;
			seconds = elapsedTime / 1.0E09;
			stReturn += "<br>Missing resources " + seconds;

			// Verify User Supervisor -- activity #11
			int ceoID = this.ebEnt.dbDyn
					.ExecuteSql1n("SELECT nmLcId FROM laborcategory WHERE LaborCategory = 'CEO'");
			int presID = this.ebEnt.dbDyn
					.ExecuteSql1n("SELECT nmLcId FROM laborcategory WHERE LaborCategory = 'President'");
			int mdID = this.ebEnt.dbDyn
					.ExecuteSql1n("SELECT nmLcId FROM laborcategory WHERE LaborCategory = 'Marketing Director'");
			stSql = "select FirstName, LastName from Users where (Supervisor <= 0 or Supervisor = '') and nmUserId NOT IN "
					+ "( SELECT nmRefId FROM teb_reflaborcategory where nmLaborCategoryId = "
					+ ceoID
					+ " or nmLaborCategoryId = "
					+ presID
					+ " or nmLaborCategoryId = " + mdID + ")";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			if (iMax > 0) {
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					if (!rs.getString("FirstName").isEmpty()
							&& !rs.getString("LastName").isEmpty())
						makeMessage(
								"All",
								getAllUsers(getPriviledge("ppm")),
								"Missing User Supervisor",
								rs.getString("FirstName") + " "
										+ rs.getString("LastName"), dateEnd);
				}
			}
			rs.close();

			// Verify Each User has atleast 1 Labor Category -- activity #12
			stSql = "SELECT u.FirstName, u.LastName FROM users as u LEFT JOIN teb_reflaborcategory as l ON u.nmUserId=l.nmRefId WHERE l.nmRefId IS NULL";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			if (iMax > 0) {
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					if (!rs.getString("FirstName").isEmpty()
							&& !rs.getString("LastName").isEmpty())
						makeMessage(
								"All",
								getAllUsers(getPriviledge("ppm")),
								"Missing User Labor Category",
								rs.getString("FirstName") + " "
										+ rs.getString("LastName"), dateEnd);
				}
			}
			rs.close();

			// Verify Each User has atleast 1 Division -- activity #15
			stSql = "SELECT u.FirstName, u.LastName FROM users as u LEFT JOIN teb_refdivision as d ON u.nmUserId=d.nmRefId WHERE d.nmRefId IS NULL";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			if (iMax > 0) {
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					if (!rs.getString("FirstName").isEmpty()
							&& !rs.getString("LastName").isEmpty())
						makeMessage(
								"All",
								getAllUsers(getPriviledge("ad")),
								"Missing User Division",
								rs.getString("FirstName") + " "
										+ rs.getString("LastName"), dateEnd);
				}
			}
			rs.close();

			// Verify User has Telephone -- activity #13
			stSql = "SELECT Telephone, FirstName, LastName FROM users WHERE (Telephone IS NULL or Telephone = '')";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			if (iMax > 0) {
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					if (!rs.getString("FirstName").isEmpty()
							&& !rs.getString("LastName").isEmpty())
						makeMessage(
								"All",
								getAllUsers(getPriviledge("ppm")),
								"Missing User Work Telephone Number",
								rs.getString("FirstName") + " "
										+ rs.getString("LastName"), dateEnd);
				}
			}
			rs.close();

			// Verify User has Email -- activity #14
			stSql = "SELECT x.stEMail, u.FirstName, u.LastName FROM dbeps01.users as u INNER JOIN ebeps01.x25user as x ON u.nmUserId = x.RecId WHERE (stEmail IS NULL or stEMail = '')";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			if (iMax > 0) {
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					if (!rs.getString("FirstName").isEmpty()
							&& !rs.getString("LastName").isEmpty())
						makeMessage(
								"All",
								getAllUsers(getPriviledge("ppm")),
								"Missing User Email Address",
								rs.getString("FirstName") + " "
										+ rs.getString("LastName"), dateEnd);
				}
			}
			rs.close();

			endTime = System.nanoTime();
			elapsedTime = endTime - startTime;
			seconds = elapsedTime / 1.0E09;
			stReturn += "<br>Missing users " + seconds;

			// Verify Missing Trigger Contact List -- activity #22
			stSql = "SELECT RecId FROM triggers WHERE ContactList IS NULL OR ContactList = ''";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			if (iMax > 0) {
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					makeMessage("All", getAllUsers(getPriviledge("ppm")) + ","
							+ getAllUsers(getPriviledge("ex")), "Triggers",
							rs.getString("TriggerName")
									+ " missing contact list", dateEnd);
				}
			}
			rs.close();

			// Missing Project Manager EOB EPPORA Message Rob Eder -- activity
			// #23
			String stMissing = "";
			stSql = "select * from Projects where ProjectManagerAssignment=''";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				stMissing += "<tr><td>" + rs.getString("ProjectName")
						+ "</td></tr>";
				// Send message to PPM and SP in this project if Project Manager
				// is
				// missing
				String receivers = rs
						.getString("ProjectPortfolioManagerAssignment")
						+ ","
						+ rs.getString("Sponsor");
				makeMessage(rs.getString("ProjectName"), receivers,
						"Missing Project Manager",
						"Please assign Project Manager", dateEnd);
			}
			if (stMissing.length() > 0) {
				stMissing = "<table border=1>"
						+ "<tr><th>Project Name</th></tr>" + stMissing;
				stMissing += "</table>";
				stReturn += makeTask(12, stMissing);
			}
			rs.close();

			// Missing Missing Project Portfolio Manager -- activity #25
			stMissing = "";
			stSql = "select * from Projects where ProjectPortfolioManagerAssignment=''";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				stMissing += "<tr><td>" + rs.getString("ProjectName")
						+ "</td></tr>";

				String receivers = rs.getString("ProjectManagerAssignment")
						+ "," + rs.getString("BusinessAnalystAssignment") + ","
						+ rs.getString("Sponsor") + ","
						+ getAllUsers(getPriviledge("ppm")) + ","
						+ getAllUsers(getPriviledge("ex"));
				makeMessage(rs.getString("ProjectName"), receivers,
						"Missing Project Portfolio Manager",
						"Please assign Project Portfolio Manager", dateEnd);
			}
			if (stMissing.length() > 0) {
				stMissing = "<table border=1>"
						+ "<tr><th>Project Name</th></tr>" + stMissing;
				stMissing += "</table>";
				stReturn += makeTask(13, stMissing);
			}
			rs.close();

			// Missing Missing Business Analyst -- activity #24
			stMissing = "";
			stSql = "select * from Projects where BusinessAnalystAssignment=''";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				stMissing += "<tr><td>" + rs.getString("ProjectName")
						+ "</td></tr>";

				String receivers = rs
						.getString("ProjectPortfolioManagerAssignment")
						+ ","
						+ rs.getString("Sponsor");
				makeMessage(rs.getString("ProjectName"), receivers,
						"Missing Business Analyst",
						"Please assign Business Analyst", dateEnd);
			}
			if (stMissing.length() > 0) {
				stMissing = "<table border=1>"
						+ "<tr><th>Project Name</th></tr>" + stMissing;
				stMissing += "</table>";
				stReturn += makeTask(23, stMissing);
			}
			rs.close();

			// Missing Sponsor -- activity #26
			stMissing = "";
			stSql = "select * from Projects where Sponsor=''";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				stMissing += "<tr><td>" + rs.getString("ProjectName")
						+ "</td></tr>";

				if (rs.getString("ProjectPortfolioManagerAssignment") != null) {
					makeMessage(rs.getString("ProjectName"),
							rs.getString("ProjectPortfolioManagerAssignment"),
							"Missing Business Analyst",
							"Please assign Business Analyst", dateEnd);
				}
			}
			if (stMissing.length() > 0) {
				stMissing = "<table border=1>"
						+ "<tr><th>Project Name</th></tr>" + stMissing;
				stMissing += "</table>";
				stReturn += makeTask(14, stMissing);
			}
			rs.close();

			endTime = System.nanoTime();
			elapsedTime = endTime - startTime;
			seconds = elapsedTime / 1.0E09;
			stReturn += "<br>Missing pm,ppm,sponsor,ba " + seconds;

			// Lookup missing project information and send message -- activity
			// #27
			stSql = "select * from Projects where ProjectStatus=1 AND (nmReq IS NULL OR nmReq=0 OR nmReq='')";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				String receivers = rs
						.getString("ProjectPortfolioManagerAssignment")
						+ ","
						+ rs.getString("Sponsor")
						+ ","
						+ rs.getString("BusinessAnalystAssignment")
						+ ","
						+ rs.getString("ProjectManagerAssignment");
				makeMessage(rs.getString("ProjectName"), receivers,
						"Requirements are not specified", "No Requirements",
						dateEnd);
			}
			rs.close();

			// mising schedules -- activity #28
			stSql = "select * from Projects where nmSch IS NULL OR nmSch=0 OR nmSch=''";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				String receivers = rs
						.getString("ProjectPortfolioManagerAssignment")
						+ ","
						+ rs.getString("Sponsor")
						+ ","
						+ rs.getString("ProjectManagerAssignment");
				makeMessage(rs.getString("ProjectName"), receivers,
						"Schedule is not specified", "No Schedules", dateEnd);
			}
			rs.close();

			// Missing Requirements Info
			stSql = "SELECT r.*, p.ProjectName, p.ProjectPortfolioManagerAssignment, p.ProjectManagerAssignment, p.BusinessAnalystAssignment, "
					+ "(select count(*) from requirements r1 where r1.nmProjectId=r.nmProjectId and r1.nmBaseLine=r.nmBaseLine and r1.ReqParentRecId=r.RecId) as cntChildren "
					+ "FROM requirements as r INNER JOIN projects as p ON r.nmProjectId=p.RecId";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				// Verify Requirement Description Containts Shall -- activity
				// #29
				if (rs.getString("ReqDescription") == null
						|| rs.getString("ReqDescription").trim().equals(""))
					continue;
				if (rs.getInt("cntChildren") > 0)
					continue;
				if (!rs.getString("ReqDescription").contains("shall")) {
					makeMessage(
							rs.getString("ProjectName"),
							rs.getString("ProjectPortfolioManagerAssignment")
									+ ","
									+ rs.getString("ProjectManagerAssignment")
									+ ","
									+ rs.getString("BusinessAnalystAssignment"),
							"Requirement Warning",
							"Requirements must contain the word <em>shall</em>",
							dateEnd);
				}
				// Verify Description does not exceed max word count -- activity
				// #33
				if (!rs.getString("ReqDescription").isEmpty()) {
					iWords = rs.getString("ReqDescription").split(" ");
					iWordCount = iWords.length;
					if (iWordCount > this.rsMyDiv
							.getInt("ReqtsSpecificationMaximumWords")) {
						makeMessage(
								rs.getString("ProjectName"),
								rs.getString("ProjectPortfolioManagerAssignment")
										+ ","
										+ rs.getString("ProjectManagerAssignment")
										+ ","
										+ rs.getString("BusinessAnalystAssignment"),
								"Requirement Warning",
								"Requirements cannot exceed "
										+ this.rsMyDiv
												.getInt("ReqtsSpecificationMaximumWords")
										+ " words", dateEnd);
					}

					if (iWordCount > 0) {
						String wordList = "";
						for (String w : iWords) {
							if (!w.isEmpty())
								wordList += ebEnt.dbDyn.fmtDbString(w.replace(
										".", "")) + ",";
						}
						if (wordList.length() > 0)
							wordList = wordList.substring(0,
									wordList.length() - 1);
						// req contains adverb or adjective -- activity #30
						if (this.ebEnt.dbDyn
								.ExecuteSql1n("select count(*) from teb_dictionary where stKeywordType='A' and stKeyword IN ("
										+ wordList + ")") > 0) {
							makeMessage(
									rs.getString("ProjectName"),
									rs.getString("ProjectPortfolioManagerAssignment")
											+ ","
											+ rs.getString("ProjectManagerAssignment")
											+ ","
											+ rs.getString("BusinessAnalystAssignment"),
									"Requirement Warning",
									"Project "
											+ rs.getString("ProjectName")
											+ " requirement ID "
											+ rs.getString("RecId")
											+ " has an adjective or adverb as part of its description",
									dateEnd);
						}

						// req description contains compound and complex --
						// activity #31 &
						// #32
						if (this.ebEnt.dbDyn
								.ExecuteSql1n("select count(*) from teb_dictionary where stKeywordType='C' and stKeyword IN ("
										+ wordList + ")") > 0) {
							makeMessage(
									rs.getString("ProjectName"),
									rs.getString("ProjectPortfolioManagerAssignment")
											+ ","
											+ rs.getString("ProjectManagerAssignment")
											+ ","
											+ rs.getString("BusinessAnalystAssignment"),
									"Requirement Warning",
									"Project " + rs.getString("ProjectName")
											+ " requirement ID "
											+ rs.getString("RecId")
											+ " is a compound statement",
									dateEnd);
						}
					}
					iWordCount = 0;
					iWords = null;
				}

				// VerifyMapping -- activity #47
				stSql = "SELECT count(*) FROM teb_link tl, schedule s where s.nmProjectId=tl.nmToProject and s.nmBaseLine=tl.nmBaseline and tl.nmToId=s.RecId and s.lowlvl=1 and tl.nmFromId="
						+ rs.getString("RecId")
						+ " AND tl.nmProjectId="
						+ rs.getString("nmProjectId")
						+ " AND tl.nmBaseLine="
						+ rs.getString("nmBaseLine");
				if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) == 0) {
					makeMessage(
							rs.getString("ProjectName"),
							rs.getString("ProjectPortfolioManagerAssignment")
									+ ","
									+ rs.getString("ProjectManagerAssignment")
									+ ","
									+ rs.getString("BusinessAnalystAssignment"),
							"Requirement Warning",
							"Project " + rs.getString("ProjectName")
									+ " requirement ID "
									+ rs.getString("RecId")
									+ " is not mapped to any low-level tasks.",
							dateEnd);
				}

				// -- activity #34
				stSql = "SELECT count(*) FROM test where ReqMap="
						+ rs.getString("RecId") + " AND nmProjectId="
						+ rs.getString("nmProjectId");
				if (this.ebEnt.dbDyn.ExecuteSql1n(stSql) == 0) {
					makeMessage(
							rs.getString("ProjectName"),
							rs.getString("ProjectPortfolioManagerAssignment")
									+ ","
									+ rs.getString("ProjectManagerAssignment")
									+ ","
									+ rs.getString("BusinessAnalystAssignment"),
							"Requirement Warning",
							"Project "
									+ rs.getString("ProjectName")
									+ " requirement ID "
									+ rs.getString("RecId")
									+ " is a low-level requirement and needs to be linked to one or more test cases.",
							dateEnd);
				}
			}

			stSql = "SELECT nmProjectId, MAX(dtSchLastUpdate) as maxdate FROM schedule GROUP BY nmProjectId";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			c = Calendar.getInstance();
			String today = fmt.format(c.getTime());
			c.add(Calendar.DATE, -1); // set calendar to date range we are
										// checking
			String yesterday = fmt.format(c.getTime());
			String lastUpdated = "";
			ResultSet rs2 = null;
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				lastUpdated = this.ebEnt.ebUd.fmtDateFromDb(rs
						.getString("maxdate"));

				// update daily -- activity #40
				if (lastUpdated == null
						|| !(lastUpdated.equals(today) || lastUpdated
								.equals(yesterday))) {
					stSql = "SELECT s.dtSchLastUpdate, p.ProjectName, p.ProjectPortfolioManagerAssignment, p.ProjectManagerAssignment,p.Sponsor "
							+ "FROM schedule as s INNER JOIN projects as p ON s.nmProjectId=p.RecId WHERE nmProjectId="
							+ rs.getString("nmProjectId")
							+ " ORDER BY dtSchLastUpdate DESC LIMIT 1";
					rs2 = this.ebEnt.dbDyn.ExecuteSql(stSql);
					rs2.absolute(1);
					if (rs2.getRow() > 0) {
						makeMessage(
								rs2.getString("ProjectName"),
								rs2.getString("ProjectPortfolioManagerAssignment")
										+ ","
										+ rs2.getString("ProjectManagerAssignment")
										+ rs2.getString("Sponsor"),
								"Schedule Warning",
								"Schedules should be updated daily", dateEnd);
					}

					stSql = "SELECT s.*, p.ProjectName, p.ProjectPortfolioManagerAssignment, p.ProjectManagerAssignment, p.BusinessAnalystAssignment,p.Sponsor, lowlvl "
							+ "FROM schedule as s INNER JOIN projects as p ON s.nmProjectId=p.RecId WHERE nmProjectId="
							+ rs.getString("nmProjectId") + " ORDER BY s.RecId";
					rs2 = this.ebEnt.dbDyn.ExecuteSql(stSql);
					int iMax2 = rs2.getRow();
					for (int iR2 = 1; iR2 <= iMax2; iR2++) {
						rs2.absolute(iR2);
						// check start with verb; -- activity #35
						iWords = rs2.getString("SchDescription").split(" ");
						iWordCount = iWords.length;
						if (rs2.getBoolean("lowlvl")) {
							if (iWordCount > 0) {
								if (this.ebEnt.dbDyn
										.ExecuteSql1n("SELECT count(*) FROM teb_dictionary where stKeyWord='"
												+ iWords[0]
												+ "' AND stKeywordType!='V'") > 0) {
									makeMessage(
											rs2.getString("ProjectName"),
											rs2.getString("ProjectPortfolioManagerAssignment")
													+ ","
													+ rs2.getString("ProjectManagerAssignment"),
											"Schedule Warning",
											"Schedule low-level task ID "
													+ rs2.getString("RecId")
													+ "�s description needs to start with a verb.",
											dateEnd);
								}
							}
							// Verify low-level has labor categories -- activity
							// #36
							if (rs2.getString("SchLaborCategories") == null
									|| rs2.getString("SchLaborCategories")
											.isEmpty()) {
								makeMessage(
										rs2.getString("ProjectName"),
										rs2.getString("ProjectPortfolioManagerAssignment")
												+ ","
												+ rs2.getString("ProjectManagerAssignment"),
										"Schedule Warning",
										"Schedule low-level task ID "
												+ rs2.getString("RecId")
												+ " does not have a labor category assigned.",
										dateEnd);
							}
							// Verify low-level has no resources over 40 effort
							// -- activity
							// #38
							if (rs2.getInt("lowlvl") > 0
									&& rs2.getInt("nmEffort40Flag") > 0) {
								makeMessage(
										rs2.getString("ProjectName"),
										rs2.getString("ProjectPortfolioManagerAssignment")
												+ ","
												+ rs2.getString("ProjectManagerAssignment"),
										"Schedule Warning",
										"Schedule low-level task ID "
												+ rs2.getString("RecId")
												+ " has a resource assigned over the maximum recommended hours (default is 40)",
										dateEnd);
							}

							// VerifyMapping -- activity #49
							stSql = "SELECT sum(nmPercent) FROM teb_link WHERE nmToId="
									+ rs.getString("RecId")
									+ " AND nmProjectId="
									+ rs.getString("nmProjectId")
									+ " AND nmBaseLine="
									+ rs.getString("nmBaseLine");
							String percent = this.ebEnt.dbDyn
									.ExecuteSql1(stSql);
							if (percent == null || "NULL".equals(percent)
									|| percent.isEmpty()) {
								makeMessage(
										rs2.getString("ProjectName"),
										rs.getString("ProjectPortfolioManagerAssignment")
												+ ","
												+ rs.getString("ProjectManagerAssignment")
												+ ","
												+ rs.getString("BusinessAnalystAssignment"),
										"Schedule Warning",
										"Schedule low-level task ID "
												+ rs.getString("RecId")
												+ " is not mapped to any requirements.",
										dateEnd);
							}

							// mapping percent -- activity #48
							if (Integer.parseInt(percent) < 100) {
								makeMessage(
										rs2.getString("ProjectName"),
										rs.getString("ProjectPortfolioManagerAssignment")
												+ ","
												+ rs.getString("ProjectManagerAssignment")
												+ ","
												+ rs.getString("BusinessAnalystAssignment"),
										"Schedule Warning",
										"Schedule low-level task ID "
												+ rs.getString("RecId")
												+ " is not 100% mapped.",
										dateEnd);
							}
						} else {
							// Verify high-level has no resources -- activity
							// #39
							if (!(rs2.getString("SchOtherResources") == null || rs2
									.getString("SchOtherResources").isEmpty())) {
								makeMessage(
										rs2.getString("ProjectName"),
										rs2.getString("ProjectPortfolioManagerAssignment")
												+ ","
												+ rs2.getString("ProjectManagerAssignment"),
										"Schedule Warning",
										"Schedule low-level task ID "
												+ rs2.getString("RecId")
												+ " is a parent task and should not have any resources assigned.",
										dateEnd);
							}
						}

						iWordCount = 0;
						iWords = null;
					}
				}
			}

			stSql = "SELECT * FROM projects ORDER BY RecId";
			rs = ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				String ppm = rs.getString("ProjectPortfolioManagerAssignment");
				String pm = rs.getString("ProjectManagerAssignment");
				String ba = rs.getString("BusinessAnalystAssignment");
				String sp = rs.getString("Sponsor");

				// fixed date threshold, 0x20 is fixed date -- activity #37
				stSql = "SELECT count(*) FROM schedule as s "
						+ " WHERE s.nmProjectId=" + rs.getString("RecId");
				double nmSchs = ebEnt.dbDyn.ExecuteSql1n(stSql);
				stSql = "SELECT count(*) FROM schedule as s"
						+ " WHERE (s.SchFlags & 0x20) !=0 AND s.nmProjectId="
						+ rs.getString("RecId");
				double nmFixedSchs = ebEnt.dbDyn.ExecuteSql1n(stSql);
				if (nmFixedSchs > nmSchs
						* rsMyDiv.getDouble("FixedDateThreshold") / 100) {
					makeMessage(
							rs.getString("ProjectName"),
							ppm + "," + pm,
							"Out Of Threshold",
							"Fixed dates schedules is out of project's threshold",
							dateEnd);
				}

				// deliverable, 0x2000 is devliverable
				stSql = "SELECT s.* FROM schedule as s"
						+ " WHERE (s.SchFlags & 0x2000) != 0 AND s.nmProjectId="
						+ rs.getString("RecId") + " ORDER BY s.RecId";
				rs2 = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs2.last();
				iMax = rs2.getRow();
				for (int i = 1; i <= iMax; iR++) {
					rs2.absolute(i);
					String receivers = getAllUsers(getPriviledge("ex")) + ","
							+ ppm + "," + pm + "," + sp;
					Date lastUpdateTime = rs2.getDate("dtSchLastUpdate");
					Date finishTime = rs2.getDate("SchFinishDate");
					if ((rs2.getInt("SchFlags") & 0x40) != 0) {// deliverable
																// late
						if (finishTime == null)
							rs2.getDate("SchFixedFinishDate");
						if (lastUpdateTime != null && finishTime != null) {
							Calendar cLastUpdate = Calendar.getInstance();
							cLastUpdate.setTime(lastUpdateTime);
							Calendar cFinish = Calendar.getInstance();
							cFinish.setTime(finishTime);
							cLastUpdate.add(Calendar.DAY_OF_YEAR, -7);
							// late more than 1 week -- activity #41
							if (cFinish.before(cLastUpdate)) {
								makeMessage(
										rs.getString("ProjectName"),
										receivers,
										"A Week Late Deliverable",
										"Schedule task ID "
												+ rs2.getString("SchId")
												+ " is a deliverable and late more than a week.",
										dateEnd);
							} else {
								makeMessage(
										rs.getString("ProjectName"),
										receivers,
										"Late Deliverable",
										"Schedule task ID "
												+ rs2.getString("SchId")
												+ " is a deliverable and is late.",
										dateEnd);
							}
						} else { // late deliverable -- activity #42
							makeMessage(rs.getString("ProjectName"), receivers,
									"Late Deliverable", "Schedule task ID "
											+ rs2.getString("SchId")
											+ " is a deliverable and is late.",
									dateEnd);
						}
					}

					// complete today -- activity #45
					if (lastUpdateTime != null
							&& fmt.format(lastUpdateTime)
									.equals(fmt.format(Calendar.getInstance()
											.getTime()))) {
						makeMessage(
								rs.getString("ProjectName"),
								receivers,
								"Today Completed Deliverable",
								"Schedule task ID "
										+ rs2.getString("SchId")
										+ " is a deliverable and was completed today.",
								dateEnd);
					}
				}
				rs2.close();

				// milestone
				stSql = "SELECT s.* FROM schedule as s"
						+ " WHERE (s.SchFlags & 0x4000) != 0 AND nmProjectId="
						+ rs.getString("RecId") + " ORDER BY s.RecId";
				rs2 = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rs2.last();
				iMax = rs2.getRow();
				for (int i = 1; i <= iMax; i++) {
					rs2.absolute(i);
					String receivers = getAllUsers(getPriviledge("ex")) + ","
							+ ppm + "," + pm + "," + sp;
					Date lastUpdateTime = rs2.getDate("dtSchLastUpdate");
					Date finishTime = rs2.getDate("SchFinishDate");
					if ((rs2.getInt("SchFlags") & 0x40) != 0) {
						if (finishTime == null)
							rs2.getDate("SchFixedFinishDate");
						if (lastUpdateTime != null && finishTime != null) {
							Calendar cLastUpdate = Calendar.getInstance();
							cLastUpdate.setTime(lastUpdateTime);
							Calendar cFinish = Calendar.getInstance();
							cFinish.setTime(finishTime);
							cLastUpdate.add(Calendar.DAY_OF_YEAR, -7);
							// late more than 1 week -- activity #43
							if (cFinish.before(cLastUpdate)) {
								makeMessage(
										rs.getString("ProjectName"),
										receivers,
										"A Week Late Milestone",
										"Schedule task ID "
												+ rs2.getString("SchId")
												+ " is a milestone and is late more than a week.",
										dateEnd);
							} else {
								makeMessage(
										rs.getString("ProjectName"),
										receivers,
										"Late Milestone",
										"Schedule task ID "
												+ rs2.getString("SchId")
												+ " is a milestone and is late.",
										dateEnd);
							}
						} else { // late milestone -- activity #44
							makeMessage(
									rs.getString("ProjectName"),
									receivers,
									"Late Milestone",
									"Schedule task ID "
											+ rs2.getString("SchId")
											+ " is a milestone and is late.",
									dateEnd);
						}
					}

					// complete today -- activity #46
					if (lastUpdateTime != null
							&& fmt.format(lastUpdateTime)
									.equals(fmt.format(Calendar.getInstance()
											.getTime()))) {
						makeMessage(
								rs2.getString("ProjectName"),
								receivers,
								"Today Completed Milestone",
								"Schedule task ID "
										+ rs2.getString("SchId")
										+ " is a milestone and was completed today.",
								dateEnd);
					}
				}
				rs2.close();
			}
			rs.close();

			// Missing Inventory -- activity #50
			stSql = "SELECT * FROM inventory";
			rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				// Verify Each Inventory Has Atleast 1 Of Each Item
				if (rs.getInt("Quantity") <= 0)
					makeMessage("All", getAllUsers(getPriviledge("ppm")),
							"Missing Inventory", rs.getString("InventoryName")
									+ ": No Inventory Remaining", dateEnd);
			}

			this.ebEnt.dbDyn
					.ExecuteUpdate("update Inventory set TotalInventoryValue = ( CostPerUnit * Quantity )");

		} catch (Exception e) {
			this.stError += "<BR>ERROR runEOB " + e;
		}
		System.out.println("Finish runEOB: " + (System.nanoTime() - startTime)
				/ 10E9 + " seconds");
		return stReturn;
	}

	public String loadingPage() {
		String stReturn = "";
		String stURL = this.ebEnt.ebUd.request.getParameter("r").trim();
		String stMessage = this.ebEnt.ebUd.request.getParameter("m");
		stReturn = this.ebEnt.ebUd.UrlReader("./common/loading.html");
		stReturn = stReturn.replaceAll("~~Message~~", stMessage);

		if (stURL != null && stURL.length() > 0) {
			stReturn = stReturn.replaceAll("~~Redirect~~",
					"window.location = '" + stURL + "';");
		} else {
			stReturn = stReturn.replaceAll("~~Redirect~~", "");
		}
		return stReturn;
	}

	public String makeTask(int iTriggerId, String stTaskDetail) {
		String stTitle = "";
		String stReturn = "";
		String stSql = "";
		int iU = 0;
		try {
			ResultSet rsTrigger = this.ebEnt.dbDyn
					.ExecuteSql("select * from Triggers" + " where RecId="
							+ iTriggerId);
			rsTrigger.absolute(1);
			stTitle = rsTrigger.getString("TriggerName");
			stReturn = "<h1>" + stTitle + "</h1>";
			if (rsTrigger.getString("TriggerEvent").equals("Enabled")
					&& rsTrigger.getString("ContactList").length() > 0) {
				int iTaskId = this.ebEnt.dbEnterprise
						.ExecuteSql1n("select max(RecId) from X25Task"
								+ " where stTitle="
								+ this.ebEnt.dbEnterprise.fmtDbString(stTitle)
								+ " and stDescription = "
								+ this.ebEnt.dbEnterprise
										.fmtDbString(stTaskDetail)
								+ " and nmTaskFlag="
								+ rsTrigger.getString("nmTasktype"));
				if (iTaskId <= 0) {
					iTaskId = this.ebEnt.dbEnterprise
							.ExecuteSql1n("select max(RecId) from X25Task");
					iTaskId++;
					stSql = "INSERT INTO X25Task "
							+ "(RecId,nmMasterTaskId,nmTaskType,nmTaskFlag,dtStart,dtTargetEnd,stTitle,stDescription,nmUserCreated)VALUES("
							+ iTaskId + "," + iTriggerId + ",1,"
							+ rsTrigger.getString("nmTasktype")
							+ ",now(),DATE_ADD(now(),INTERVAL 10 DAY),"
							+ this.ebEnt.dbEnterprise.fmtDbString(stTitle)
							+ ","
							+ this.ebEnt.dbEnterprise.fmtDbString(stTaskDetail)
							+ "," + this.ebEnt.ebUd.getLoginId() + ")";
					this.ebEnt.dbEnterprise.ExecuteUpdate(stSql);
				} else {
					this.ebEnt.dbEnterprise
							.ExecuteUpdate("update X25Task set dtStart=now(),"
									+ "stDescription="
									+ this.ebEnt.dbEnterprise
											.fmtDbString(stTaskDetail)
									+ " where RecId=" + iTaskId);
				}
				if (rsTrigger.getString("Communication").equals("Yes")) {
					// Handle EMAIL here
					EbMail ebM = new EbMail(this.ebEnt);
					ebM.setEbEmail("smtp.myinfo.com", "false",
							"EPPORA Do Not Reply", "donotreply@eppora.com",
							"donotreply@eppora.com", "eppora123");
					ebM.setProduction(1);
					stSql = "select * from X25User where RecId in ("
							+ rsTrigger.getString("ContactList") + ")";
					ResultSet rsU = this.ebEnt.dbEnterprise.ExecuteSql(stSql);
					rsU.last();
					int iMaxUser = rsU.getRow();
					for (int iUser = 1; iUser <= iMaxUser; iUser++) {
						rsU.absolute(iUser);
						int nmCommId = ebM.sendMail(rsU, "-200", stTitle,
								stTaskDetail);
					}
					rsU.close();
					stError += ebM.getError();
				}
				String[] aV = rsTrigger.getString("ContactList").trim()
						.split(",");
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("delete from X25RefTask where nmTaskId="
								+ iTaskId + " and nmRefType=42 ");
				for (iU = 0; iU < aV.length; iU++) {
					if (aV[iU].trim().length() > 0) {
						stSql = "INSERT IGNORE INTO X25RefTask (nmTaskId,nmRefType,nmRefId,nmTaskFlag,dtAssignStart) VALUES("
								+ iTaskId + ",42," + aV[iU] + ",1,now())";
						this.ebEnt.dbEnterprise.ExecuteUpdate(stSql);
					}
				}
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR makeTask " + e;
		}
		stReturn += stTaskDetail;
		return stReturn;
	}

	public String makeTask(String stUsers, String stTask, String dtEnd) {
		String stReturn = "<br>Workflow Task: [" + stTask + "] for: ";
		String stSql = "";
		int iU = 0;
		try {
			int iSchId = this.ebEnt.dbEnterprise
					.ExecuteSql1n("select max(RecId) from X25Task where stTitle="
							+ this.ebEnt.dbEnterprise.fmtDbString(stTask) + " ");
			if (iSchId <= 0) {
				iSchId = this.ebEnt.dbEnterprise
						.ExecuteSql1n("select max(RecId) from X25Task");
				iSchId++;
				stSql = "INSERT INTO X25Task "
						+ "(RecId,nmTaskType,nmTaskFlag,dtStart,dtTargetEnd,stTitle)VALUES("
						+ iSchId
						+ ",1,1,now(),"
						+ this.ebEnt.dbEnterprise.fmtDbString(this.ebEnt.ebUd
								.fmtDateToDb(dtEnd)) + ","
						+ this.ebEnt.dbEnterprise.fmtDbString(stTask) + ")";
				this.ebEnt.dbEnterprise.ExecuteUpdate(stSql);
			} else {
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("update X25Task set nmTaskFlag=1 where RecId="
								+ iSchId);
			}
			String[] aV = stUsers.trim().split(",");
			this.ebEnt.dbEnterprise
					.ExecuteUpdate("delete from X25RefTask where nmTaskId="
							+ iSchId + " and nmRefType=42 ");
			for (iU = 0; iU < aV.length; iU++) {
				if (aV[iU].trim().length() > 0) {
					stSql = "INSERT IGNORE INTO X25RefTask (nmTaskId,nmRefType,nmRefId,nmTaskFlag,dtAssignStart) VALUES("
							+ iSchId + ",42," + aV[iU] + ",1,now())";
					this.ebEnt.dbEnterprise.ExecuteUpdate(stSql);
				}
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR makeTask " + e;
		}
		stReturn += iU + " users";
		return stReturn;
	}

	/*
	 * Create user message with permissions and description
	 * 
	 * @param projectName the name of approved project for message or 'All' to
	 * always show
	 */
	public String makeMessage(String projectName, String stUsers,
			String stTask, String stTaskDetail, String dtEnd) {
		String stReturn = "<br>Workflow Task: [" + stTask + "] for: ";
		String stSql = "";
		String[] users = stUsers.trim().split(",");
		Set<String> us = new HashSet<String>();
		for (String u : users) {
			if (u == null || u.trim().equals("") || u.trim().equals("null"))
				continue;
			us.add(u);
		}
		if (us.size() == 0)
			return null;

		String[] aV = us.toArray(new String[us.size()]);

		int iU = 0;
		try {
			int iSchId = this.ebEnt.dbEnterprise
					.ExecuteSql1n("select max(RecId) from X25Task where stTitle="
							+ this.ebEnt.dbEnterprise.fmtDbString(stTask)
							+ " and stDescription="
							+ this.ebEnt.dbEnterprise.fmtDbString(projectName
									+ " - " + stTaskDetail));
			if (iSchId <= 0) {
				iSchId = this.ebEnt.dbEnterprise
						.ExecuteSql1n("select max(RecId) from X25Task");
				iSchId++;
				stSql = "INSERT INTO X25Task "
						+ "(RecId,nmTaskType,nmTaskFlag,dtStart,dtTargetEnd,stTitle,stDescription)VALUES("
						+ iSchId
						+ ",1,2,now(),"
						+ this.ebEnt.dbEnterprise.fmtDbString(this.ebEnt.ebUd
								.fmtDateToDb(dtEnd))
						+ ","
						+ this.ebEnt.dbEnterprise.fmtDbString(stTask)
						+ ","
						+ this.ebEnt.dbEnterprise.fmtDbString(projectName
								+ " - " + stTaskDetail) + ")";
				this.ebEnt.dbEnterprise.ExecuteUpdate(stSql);
			} else {
				this.ebEnt.dbEnterprise
						.ExecuteUpdate("update X25Task set nmTaskFlag=2, dtStart=now(),dtTargetEnd="
								+ ebEnt.dbEnterprise
										.fmtDbString(this.ebEnt.ebUd
												.fmtDateToDb(dtEnd))
								+ " where RecId=" + iSchId);
			}

			this.ebEnt.dbEnterprise
					.ExecuteUpdate("delete from X25RefTask where nmTaskId="
							+ iSchId + " and nmRefType=42 ");
			for (iU = 0; iU < aV.length; iU++) {
				if (aV[iU].trim().length() > 0) {
					stSql = "INSERT IGNORE INTO X25RefTask (nmTaskId,nmRefType,nmRefId,nmTaskFlag,dtAssignStart) VALUES("
							+ iSchId + ",42," + aV[iU] + ",2,now())";
					this.ebEnt.dbEnterprise.ExecuteUpdate(stSql);
				}
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR makeMessage " + e;
		}
		stReturn += iU + " users";
		return stReturn;
	}

	public String getUsers(int iDivision, int nmPriv) {
		String stReturn = "";
		try {
			// Check Criteria Assignment
			String stSql = "SELECT distinct u.RecId FROM "
					+ this.ebEnt.dbEnterprise.getDbName()
					+ ".X25User u, "
					+ "teb_refdivision rd where u.RecId=rd.nmRefId and rd.nmRefType=42 and "
					+ "rd.nmDivision=" + iDivision + " and ( u.nmPriviledge & "
					+ nmPriv + " ) != 0";
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				if (stReturn.length() > 0) {
					stReturn += ",";
				}
				stReturn += rs.getString("RecId");
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR getUsers " + e;
		}
		return stReturn;
	}

	public String getAllUsers(int nmPriv) {
		String stReturn = "";
		try {
			// Check Criteria Assignment
			String stSql = "SELECT distinct u.RecId FROM "
					+ this.ebEnt.dbEnterprise.getDbName()
					+ ".X25User u, "
					+ "teb_refdivision rd where u.RecId=rd.nmRefId and rd.nmRefType=42 and ( u.nmPriviledge & "
					+ nmPriv + " ) != 0";
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				if (stReturn.length() > 0) {
					stReturn += ",";
				}
				stReturn += rs.getString("RecId");
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR getUsers " + e;
		}
		return stReturn;
	}

	public String processCritScor() {
		String stReturn = "";
		try {
			int iValue = 0;
			double dValue = 0;
			String nmScore = "";
			String nmResponder = "";
			String dtResponder = "";
			stPrj = this.ebEnt.ebUd.getCookieValue("f8");
			if (stPrj == null || stPrj.length() <= 0) {
				stPrj = "1";
			}
			// Criteria Score
			this.fixCriteriaFields();
			String stSql = "select * from Projects p, teb_fields f left join teb_project tp"
					+ " on tp.nmFieldId=f.nmForeignId and tp.nmProjectId = "
					+ stPrj
					+ " where tp.nmBaseline = p.CurrentBaseline and p.RecId="
					+ stPrj
					+ " and (f.nmFlags & 0x10000000 ) != 0 order by f.stLabel;";
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			if (iMax == 0) {
				stReturn += "<center><h2>No Selected Project</h2></center>";
				return stReturn;
			}
			stReturn += "<center><h2>" + rs.getString("ProjectName")
					+ "</h2></center>";
			stReturn += "</form><form method=post><table border=0><tr><th class=l1th>Criteria</th><th class=l1th>Division</th><th class=l1th>Weight (importance)</th>"
					+ "<th class=l1th>Score</th><th class=l1th>Last Responder</th><th class=l1th>Response Date</th></tr>";
			String stTemp = this.ebEnt.ebUd.request.getParameter("savescore");
			if (stTemp != null && stTemp.length() > 0) {
				if (!stTemp.contains("Cancel")) {
					for (int iR = 1; iR <= iMax; iR++) {
						rs.absolute(iR);
						stTemp = this.ebEnt.ebUd.request.getParameter("score"
								+ rs.getString("nmForeignId"));
						if (stTemp != null) {
							nmScore = rs.getString("stValue");
							if (nmScore == null || nmScore.length() <= 0) {
								nmScore = "-1";
							}
							if (!stTemp.equals(nmScore)) {
								try {
									iValue = Integer.parseInt(stTemp);
									dValue = Double.parseDouble(stTemp);
								} catch (Exception e) {
									iValue = 0;
									dValue = 0;
								}
								this.ebEnt.dbDyn
										.ExecuteUpdate("update teb_project set "
												+ "stValue="
												+ this.ebEnt.dbDyn
														.fmtDbString(stTemp)
												+ ",iValue="
												+ iValue
												+ ",nmValue="
												+ dValue
												+ " ,dtLastChanged=now(),nmUserId="
												+ this.ebEnt.ebUd.getLoginId()
												+ " where nmProjectId="
												+ stPrj
												+ " and nmBaseline="
												+ rs.getString("CurrentBaseline")
												+ " and nmFieldId="
												+ rs.getString("nmForeignId"));
							}
						}
					}
					EpsReport epsReport = new EpsReport();
					epsReport.doProjectRanking(this);
					stError += epsReport.getError();
				}
				this.ebEnt.ebUd.setRedirect("./");
				return ""; // ----------------------------->
			} else {
				for (int iR = 1; iR <= iMax; iR++) {
					rs.absolute(iR);
					// WeightImportance
					ResultSet rsCriteria = this.ebEnt.dbDyn
							.ExecuteSql("select c.*, d.stDivisionName from Criteria c, teb_division d where c.CriteriaName "
									+ "= '"
									+ rs.getString("stLabel")
									+ "' and c.nmDivision=d.nmDivision and c.nmDivision="
									+ rs.getString("nmDivision"));
					rsCriteria.last();
					if (rsCriteria != null && rsCriteria.getRow() > 0) {
						rsCriteria.absolute(1);
						stTemp = rsCriteria.getString("Responsibilities");
						if (stTemp != null) {
							stTemp = stTemp.replace("~", ",");
						} else {
							stTemp = "";
						}
						if (stTemp.length() > 1 && stTemp.startsWith("0")) {
							stTemp = stTemp.substring(1);
						}
						String stResponsibilities = "," + stTemp + ",";
						if (stResponsibilities.contains(","
								+ this.ebEnt.ebUd.getLoginId() + ",")) {
							int iWeight = rsCriteria.getInt("WeightImportance");
							if (iWeight != 0) {
								stReturn += "<tr>";
								stReturn += "<td class=l1td>"
										+ rs.getString("stLabel") + "</td>";
								stReturn += "<td class=l1td>"
										+ rsCriteria
												.getString("stDivisionName")
										+ "</td>";
								stReturn += "<td class=l1td style='text-align:center;'>"
										+ iWeight + "</td>";
								if (rs.getString("nmProjectId") == null) {
									stSql = "INSERT INTO teb_project "
											+ "(nmProjectId,nmBaseline,nmFieldId,iValue,nmValue,stValue,nmUserId,dtLastChanged) VALUES("
											+ stPrj + ",1,"
											+ rs.getString("nmForeignId") + ","
											+ "0,0,'',"
											+ this.ebEnt.ebUd.getLoginId()
											+ ",now())";
									this.ebEnt.dbDyn.ExecuteUpdate(stSql);
									nmScore = "";
									nmResponder = "";
									dtResponder = "";
								} else {
									nmScore = rs.getString("stValue");
									dtResponder = rs.getString("dtLastChanged");
									nmResponder = rs.getString("nmUserId");
									if (nmScore == null) {
										nmScore = "";
									}
									if (dtResponder == null) {
										dtResponder = "";
									}
									if (nmResponder == null) {
										nmResponder = "";
									}
								}
								stReturn += "<td class=l1td'><select name=score"
										+ rs.getString("nmForeignId") + ">";
								for (int ii = -1; ii <= 10; ii++) {
									if (ii == -1) {
										stTemp = "--";
									} else {
										stTemp = "" + ii;
									}
									stReturn += this.ebEnt.ebUd.addOption2(
											stTemp, "" + ii, nmScore);
								}
								stReturn += "</select></td>";
								if (nmResponder.length() > 0) {
									stTemp = this.ebEnt.dbDyn
											.ExecuteSql1("select concat(FirstName,' ',LastName) as nm from Users where nmUserId="
													+ nmResponder);
								} else {
									stTemp = "";
								}
								stReturn += "<td>" + stTemp + "</td>";
								stReturn += "<td>" + dtResponder + "</td>";
								stReturn += "</tr>";
							} // else
								// stError += "<br> WEIGHT = 0 " +
								// rs.getString("stLabel");
						} // else
							// stError += "<BR> NOT RESPONSIBLE " +
							// rs.getString("stLabel");
					}
				}
				stReturn += "<tr><td colspan=5 align=center><input type=submit name=savescore value='Save'>&nbsp;&nbsp;&nbsp;"
						+ "<input type=submit name=savescore value='Cancel'></td></tr>";
			}
		} catch (Exception e) {
			this.stError += "<BR>ERROR processCritScor " + e;
		}
		stReturn += "</table>";
		return stReturn;
	}

	public void fixCriteriaFields() {
		String stSql = "select distinct CriteriaName from Criteria c left join teb_fields f on c.CriteriaName = f.stLabel where f.stLabel is null";
		try {
			ResultSet rs = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs.last();
			int iMax = rs.getRow();
			for (int iR = 1; iR <= iMax; iR++) {
				rs.absolute(iR);
				String stValue = rs.getString("CriteriaName");
				int nmFieldId = this.ebEnt.dbDyn
						.ExecuteSql1n("select max(nmForeignId) from teb_fields ");
				nmFieldId++;
				String stDbFieldName = stValue.trim().replace(" ", "_");
				stDbFieldName = stDbFieldName.trim().replace("'", "");
				this.ebEnt.dbDyn
						.ExecuteUpdate("insert into teb_fields "
								+ "(nmForeignId,nmFlags,stDbFieldName,stLabel,nmDataType,nmMinBytes,nmMaxBytes,nmCols,nmTabId) "
								+ "values(" + nmFieldId + ",0x10000001,\""
								+ stDbFieldName + "\",\"" + stValue.trim()
								+ "\",3,0,255,64,300) ");
				this.ebEnt.dbDyn
						.ExecuteUpdate("insert into teb_epsfields (nmForeignId) values("
								+ nmFieldId + ")");
			}
			rs.close();
			rs = null;

			stSql = "select distinct CriteriaName,f.nmForeignId from Criteria c left join teb_fields f"
					+ " on c.CriteriaName = f.stLabel where ( f.nmFlags & 0x10000000 )  != 0;";
			ResultSet rs1 = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rs1.last();
			int iMax1 = rs1.getRow();
			for (int iR1 = 1; iR1 <= iMax1; iR1++) {
				rs1.absolute(iR1);
				stSql = "select * from Projects ";
				ResultSet rsP = this.ebEnt.dbDyn.ExecuteSql(stSql);
				rsP.last();
				int iMaxP = rsP.getRow();
				for (int iP = 1; iP <= iMaxP; iP++) {
					rsP.absolute(iP);
					int iCount = this.ebEnt.dbDyn
							.ExecuteSql1n("select count(*) from teb_project"
									+ " where nmProjectId= "
									+ rsP.getString("RecId")
									+ " and nmBaseline="
									+ rsP.getString("CurrentBaseline")
									+ " and nmFieldId="
									+ rs1.getString("nmForeignId"));
					if (iCount <= 0) {
						stSql = "INSERT INTO teb_project "
								+ "(nmProjectId,nmBaseline,nmFieldId) VALUES("
								+ rsP.getString("RecId") + ","
								+ rsP.getString("CurrentBaseline") + ","
								+ rs1.getString("nmForeignId") + ")";
						this.ebEnt.dbDyn.ExecuteUpdate(stSql);
					}
				}
			}
		} catch (Exception e) {
			stError += "<br>ERROR fixCriteriaFields " + e;
		}
	}

	public double makeDecimal(String stIn) {
		double dReturn = 0.0;
		if (stIn != null) {
			stIn = stIn.trim().replace("$", "");
			stIn = stIn.replace(",", "");
			try {
				dReturn = Double.parseDouble(stIn);
			} catch (Exception e) {
			}
		}
		return dReturn;
	}

	public String makeNumeric(String stIn) {
		String stReturn = "";
		char myChar;
		if (stIn != null) {
			stIn = stIn.trim();
			for (int i = 0; i < stIn.length(); i++) {
				myChar = stIn.charAt(i);
				if ((myChar >= '0' && myChar <= '9') || (myChar == '.')) {
					stReturn += myChar;
				}
			}
		}
		if (stReturn.length() <= 0) {
			stReturn = "0";
		}
		return stReturn;
	}

	public String makeHours(String stIn) {
		String stReturn = "";
		char myChar;
		if (stIn != null) {
			stIn = stIn.trim();
			for (int i = 0; i < stIn.length(); i++) {
				myChar = stIn.charAt(i);
				if ((myChar >= '0' && myChar <= '9') || (myChar == '.')) {
					stReturn += myChar;
				}
			}
			if (stIn.toLowerCase().contains("day")) {
				try {
					double dDays = Double.parseDouble(stReturn);
					double dHours = dDays
							* this.rsMyDiv.getDouble("nmHoursPerDay");
					stReturn = "" + dHours;
				} catch (Exception e) {
				}
			}
		}
		if (stReturn.length() <= 0) {
			stReturn = "0";
		}
		return stReturn;
	}

	public String makeYesNo(String stIn) {
		String stReturn = "";
		if (stIn != null && stIn.length() > 0) {
			if (stIn.trim().toLowerCase().substring(0, 1).equals("y")) {
				stReturn = "1";
			}
		}
		if (stReturn.length() <= 0) {
			stReturn = "0";
		}
		return stReturn;
	}

	private String getChoice(ResultSet rsF, String stValue) {
		String stReturn = "";
		try {
			stReturn = this.ebEnt.dbDyn
					.ExecuteSql1("select max(stChoiceValue) from teb_choices where nmFieldId="
							+ rsF.getString("nmForeignId")
							+ " and UniqIdChoice=\"" + stValue + "\"");
		} catch (Exception e) {
			this.stError += "<BR>ERROR: getChoice " + e;
		}
		return stReturn;
	}

	public int saveTable(ResultSet rsTable, ResultSet rsF, int iMaxF,
			String stPk, int iPk, int iPkFrom) {
		int iCount = 0;
		String stProject = "";
		String stWhere = "";
		int nmBaseline = 0;
		try {
			String stSqlOld = "select * from "
					+ rsTable.getString("stDbTableName") + "  ";
			String stSql = "update " + rsTable.getString("stDbTableName")
					+ " set ";
			String stValue = "";
			String stTemp = "";

			iCount = 0;
			if ((rsTable.getInt("nmTableFlags") & 0x400) != 0) // Edit Divsion
			{
				stSql += "nmDivision="
						+ this.ebEnt.ebUd.request.getParameter("nmDivision");
				iCount++;
			}
			switch (rsTable.getInt("nmTableId")) {
			case 19:
			case 21:
				stProject = this.ebEnt.ebUd.request.getParameter("pk");
				nmBaseline = this.ebEnt.dbDyn
						.ExecuteSql1n("select CurrentBaseline from Projects where RecId="
								+ stProject);
				break;
			}

			for (int iF = 1; iF <= iMaxF; iF++) {
				rsF.absolute(iF);
				int iFlags = rsF.getInt("nmFlags");
				if (rsF.getInt("nmForeignId") == 352
						&& (this.ebEnt.dbDyn
								.ExecuteSql1n("select nmFlags from "
										+ rsTable.getString("stDbTableName")
										+ " where " + rsTable.getString("stPk")
										+ " = \"" + stPk + "\"") & 1) != 0) // Criteria
																			// Name
				{
					iFlags = 0; // Cannot change DEFAULT CRITERIA
				}
				if ((iFlags & 1) != 0) // must be editable field to SAVE IT
				{
					if (rsF.getInt("nmDataType") == 8
							|| rsF.getInt("nmDataType") == 20) {
						stTemp = this.ebEnt.ebUd.request.getParameter("f"
								+ rsF.getString("nmForeignId"));
						if (stTemp == null || stTemp.length() <= 0) {
							if (iCount > 0) {
								stSql += ",";
							}

							iCount++;
							stSql += rsF.getString("stDbFieldName") + "= null "; // Empty
																					// date/time
							continue; // ----------------------------------------------->
						}
					}

					if (iCount > 0) {
						stSql += ",";
					}
					iCount++;
					if (rsF.getInt("nmDataType") == 21) {
						stValue = this.ebEnt.ebUd.getFormValue(-1, rsF);
					} else if (rsF.getInt("nmDataType") == 39) {
						stValue = "";
						String[] paramValues = this.ebEnt.ebUd.request
								.getParameterValues("f"
										+ rsF.getString("nmForeignId"));
						for (int i = 0; paramValues != null
								&& i < paramValues.length; i++) {
							if (i > 0) {
								stValue += "\n";
							}
							stValue += paramValues[i];
						}
					} else {
						stValue = this.ebEnt.ebUd.request.getParameter("f"
								+ rsF.getString("nmForeignId"));
					}
					if (stValue == null) {
						stValue = "";
					}

					stTemp = rsF.getString("stValidation");
					if (stTemp != null && stTemp.length() > 0
							&& stTemp.toLowerCase().equals("d22")) {
						stValue = validateD22(rsTable, rsF, stPk, stValue,
								stProject, nmBaseline);
					} else if (stTemp != null && stTemp.length() > 0
							&& stTemp.toLowerCase().equals("d50")) {
						stValue = validateD50(rsTable, rsF, stPk, stValue,
								stProject, nmBaseline);
					} else if (stTemp != null && stTemp.length() > 0
							&& stTemp.toLowerCase().equals("d53")) {
						stValue = validateD53(rsTable, rsF, stPk, stValue,
								stProject, nmBaseline);
					} else if (stTemp != null && stTemp.length() > 0
							&& stTemp.toLowerCase().equals("rid")) {
						stValue = validateRID(rsTable, rsF, stPk, stValue,
								stProject, nmBaseline);
					}
					if (stValue.trim().length() <= 0) {
						if (rsF.getInt("nmDataType") == 1
								|| rsF.getInt("nmDataType") == 5
								|| rsF.getInt("nmDataType") == 31) {
							stValue = "0";
						} else if (rsF.getInt("nmDataType") == 21) {
							stValue = "00:00";
						} else if (rsF.getInt("nmDataType") == 20
								|| rsF.getInt("nmDataType") == 8) {
							// DateFormat dateFormat = new
							// SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
							DateFormat dateFormat = new SimpleDateFormat(
									"MM/dd/yyyy");
							Date date = new Date();
							stValue = dateFormat.format(date); // stValue =
																// "2011-04-01";
						}
					}
					if (rsF.getInt("nmDataType") == 20
							|| rsF.getInt("nmDataType") == 8) {
						stValue = this.ebEnt.ebUd.fmtDateToDb(stValue);
					}

					if (rsF.getString("stDbFieldName").equals(
							"SchFixedFinishDate")
							|| rsF.getString("stDbFieldName").equals(
									"SchFixedStartDate")) {
						// delete all dependencies if we set fixed date
						String bline = this.ebEnt.dbDyn
								.ExecuteSql1("select CurrentBaseline from Projects where RecId="
										+ this.ebEnt.ebUd.request
												.getParameter("pk"));
						String sql = "delete from teb_link where nmProjectId="
								+ this.ebEnt.ebUd.request.getParameter("pk")
								+ " and nmBaseline="
								+ bline
								+ " and nmLinkFlags=2 "
								+ "and nmToId="
								+ stPk
								+ " and nmToProject=nmProjectId order by nmFromId";
						this.ebEnt.dbDyn.ExecuteUpdate(sql);
						sql = "update schedule set SchDependencies='' where nmProjectId="
								+ this.ebEnt.ebUd.request.getParameter("pk")
								+ " and nmBaseline="
								+ bline
								+ " and RecId="
								+ stPk;
						this.ebEnt.dbDyn.ExecuteUpdate(sql);
						stSql += rsF.getString("stDbFieldName") + "="
								+ this.ebEnt.dbDyn.fmtDbString(stValue);

					} else if (rsF.getString("stDbFieldName").equals(
							"SchLaborCategories")) {
						String bline = this.ebEnt.dbDyn
								.ExecuteSql1("select CurrentBaseline from Projects where RecId="
										+ this.ebEnt.ebUd.request
												.getParameter("pk"));
						stSql += rsF.getString("stDbFieldName")
								+ "="
								+ this.ebEnt.dbDyn
										.fmtDbString(this.ebEnt.dbDyn.ExecuteSql1("SELECT SchLaborCategories From schedule WHERE nmProjectId="
												+ this.ebEnt.ebUd.request
														.getParameter("pk")
												+ " and nmBaseline="
												+ bline
												+ " and RecId=" + stPk));
					} else {
						stSql += rsF.getString("stDbFieldName") + "="
								+ this.ebEnt.dbDyn.fmtDbString(stValue);
					}
				}

				switch (rsF.getInt("nmDataType")) {
				case 40:
					saveSpecialDays(rsF.getInt("nmForeignId"), stValue, stPk);
					break;
				case 47: // Indicators
					int iMask = 0;
					stTemp = this.ebEnt.ebUd.request
							.getParameter("indicator_4096");
					if (stTemp != null && stTemp.length() > 0) {
						iMask |= 0x1000;
					}
					stTemp = this.ebEnt.ebUd.request
							.getParameter("indicator_8192");
					if (stTemp != null && stTemp.length() > 0) {
						iMask |= 0x2000;
					}
					stTemp = this.ebEnt.ebUd.request
							.getParameter("indicator_16384");
					if (stTemp != null && stTemp.length() > 0) {
						iMask |= 0x4000;
					}
					if (iCount > 0) {
						stSql += ",";
					}
					iCount++;
					stSql += rsF.getString("stDbFieldName") + "= (( "
							+ rsF.getString("stDbFieldName")
							+ " & ~(0xF000 )) | " + iMask + ") ";
					break;
				}
			}

			if (iCount > 0) {
				if (iPk > 0) {
					for (; iPkFrom <= iPk; iPkFrom++) {
						this.ebEnt.dbDyn.ExecuteUpdate(stSql + " where "
								+ rsTable.getString("stPk") + " = \"" + iPkFrom
								+ "\"");
					}
				} else {
					stWhere = " where " + rsTable.getString("stPk") + " = \""
							+ stPk + "\"";
					switch (rsTable.getInt("nmTableId")) {
					case 19:
					case 21:
						stWhere += " and nmProjectId=" + stProject
								+ " and nmBaseline=" + nmBaseline;
						break;
					}
					ResultSet rsOld = this.ebEnt.dbDyn.ExecuteSql(stSqlOld
							+ stWhere);

					this.ebEnt.dbDyn.ExecuteUpdate(stSql + stWhere);

					switch (rsTable.getInt("nmTableId")) {
					case 21:
						double dCost = calculateScheduleCost(stWhere);
						// set remaining cost = cost if task not started
						ResultSet rsDate = this.ebEnt.dbDyn
								.ExecuteSql("select SchStartDate from  Schedule"
										+ stWhere);
						rsDate.first();

						if (rsDate.getTimestamp("SchStartDate") != null) {
							Timestamp startDate = rsDate
									.getTimestamp("SchStartDate");
							Date date = new Date();
							Timestamp today = new Timestamp(date.getTime());

							if (today.compareTo(startDate) < 0) {
								// if task not started yet, cost remaining =
								// estimated cost
								this.ebEnt.dbDyn
										.ExecuteUpdate("update Schedule set SchRemainingCost = "
												+ dCost
												+ ", SchCost = "
												+ dCost + " " + stWhere);
							} else {
								this.ebEnt.dbDyn
										.ExecuteUpdate("update Schedule set SchCost = "
												+ dCost + " " + stWhere);
							}
						} else {
							this.ebEnt.dbDyn
									.ExecuteUpdate("update Schedule set SchCost = "
											+ dCost + " " + stWhere);
						}
						rsOld.absolute(1);
						if (rsOld.getDouble("lowlvl") == 1
								&& rsOld.getDouble("SchEstimatedEffort") > rsMyDiv
										.getDouble("MaximumTaskHours")) {
							ebEnt.ebUd.setPopupMessage("Estimate ("
									+ rsOld.getDouble("SchEstimatedEffort")
									+ ") exceeds maximum task hours ("
									+ rsMyDiv.getDouble("MaximumTaskHours")
									+ ")");
						}
						break;
					}
					ResultSet rsNew = this.ebEnt.dbDyn.ExecuteSql(stSqlOld);
					this.epsEf.addAuditTrail(rsTable, rsOld, rsNew, stPk,
							stProject, nmBaseline);
					switch (rsTable.getInt("nmTableId")) {
					case 9: // Users
						int iSpecialDaysApproval = this.ebEnt.dbDyn
								.ExecuteSql1n("select count(*) from Calendar c left join Users u "
										+ "on u.nmUserId=c.nmUser where c.dtDay >= now() and  c.nmUser > 0 and c.nmFlags = 2");
						if (iSpecialDaysApproval > 0)
							makeTask(19, "Days to approve: "
									+ iSpecialDaysApproval); // Special
																// Days
																// Approval
						break;
					}
				}

				if (this.stError.length() > 0) {
					stError += "<br>" + stSql + "<br>";
				}
			}
		} catch (Exception e) {
			stError += "<br>ERROR saveTable " + e;
		}
		return iCount;
	}

	public String getUserName(int iUserId) {
		String stName = "";
		try {
			stName = this.ebEnt.dbDyn
					.ExecuteSql1("select concat( FirstName, ' ', LastName) as nm from Users where nmUserId="
							+ iUserId);
		} catch (Exception e) {
			stError += "<br>ERROR getUserName " + e;
		}
		return stName;
	}

	private String analyzePrjSchTotals() {
		String stName = "";
		String stSql = "";
		try {
			stSql = "select count(*) as cnt,s.nmProjectId from Schedule s, Projects p where s.nmProjectId=p.RecId "
					+ "and s.nmBaseline = p.CurrentBaseline group by s.nmProjectId;";
			ResultSet rsS = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rsS.last();
			int iMax = rsS.getRow();
			for (int iS = 1; iS <= iMax; iS++) {
				rsS.absolute(iS);
				this.ebEnt.dbDyn.ExecuteUpdate("update Projects set nmSch="
						+ rsS.getString("cnt") + " where RecId="
						+ rsS.getString("nmProjectId"));
			}
			rsS.close();

			stSql = "select count(*) as cnt,r.nmProjectId from Requirements r, Projects p where r.nmProjectId=p.RecId "
					+ "and r.nmBaseline = p.CurrentBaseline group by r.nmProjectId;";
			ResultSet rsR = this.ebEnt.dbDyn.ExecuteSql(stSql);
			rsR.last();
			int iMaxR = rsR.getRow();
			for (int iR = 1; iR <= iMaxR; iR++) {
				rsR.absolute(iR);
				this.ebEnt.dbDyn.ExecuteUpdate("update Projects set nmReq="
						+ rsR.getString("cnt") + " where RecId="
						+ rsR.getString("nmProjectId"));
			}
			rsR.close();
		} catch (Exception e) {
			stError += "<br>ERROR analyzePrjSchTotals " + e;
		}
		return stName;
	}

	public double calculateScheduleCost(String stWhere) {
		double dCost = 0;
		try {
			ResultSet rs = this.ebEnt.dbDyn
					.ExecuteSql("select * from Schedule " + stWhere);
			rs.absolute(1);
			dCost = calculateScheduleCost(rs);
		} catch (Exception e) {
			stError += "<br>ERROR calculateScheduleCost " + e;
		}
		return dCost;
	}

	public double calculateScheduleCost(ResultSet rs) {
		double dCost = 0;
		String stValue = "";
		String[] aRecords = null;
		String[] aFields = null;
		try {
			// 39~1~160.0~~~~~|10~1~10~~~~~
			stValue = rs.getString("SchLaborCategories");
			if (stValue != null && stValue.length() > 0) {
				aRecords = stValue.split("\\|", -1);
				for (int iR = 0; iR < aRecords.length; iR++) {
					aFields = aRecords[iR].split("~", -1);
					double dAvg = this.ebEnt.dbDyn
							.ExecuteSql1n("select sum(HourlyRate)/count(*) from LaborCategory l,"
									+ " Users u, teb_reflaborcategory rlc where  l.nmLcId="
									+ aFields[0]
									+ " and u.nmUserId=rlc.nmRefId and rlc.nmRefType=42 and rlc.nmLaborCategoryId=l.nmLcId");
					double dHours = Double.parseDouble(aFields[2]);
					dCost += dAvg * dHours;
				}
			}
			// 6~12~|1~2~
			stValue = rs.getString("SchInventory");
			if (stValue != null && stValue.length() > 0) {
				aRecords = stValue.split("\\|", -1);
				for (int iR = 0; iR < aRecords.length; iR++) {
					aFields = aRecords[iR].split("~", -1);
					double dUnit = this.ebEnt.dbDyn
							.ExecuteSql1n("select CostPerUnit from Inventory where RecId="
									+ aFields[0]);
					int iQty = Integer.parseInt(aFields[0]);
					dCost += dUnit * iQty;
				}
			}
			// Travel~1234.56~|more travel~33.44~
			stValue = rs.getString("SchOtherResources");
			if (stValue != null && stValue.length() > 0) {
				aRecords = stValue.split("\\|", -1);
				for (int iR = 0; iR < aRecords.length; iR++) {
					aFields = aRecords[iR].split("~", -1);
					dCost += Double.parseDouble(aFields[1]);
				}
			}
		} catch (Exception e) {
			stError += "<br>ERROR calculateScheduleCost " + e;
		}
		return dCost;
	}

	public String makeUserSql(int nmType, String stLcList, String stDivList,
			String stSearchType, String stSearch, String stWhere) {
		String stSql = "";
		stSql = "select DISTINCT u.*,xu.stEMail,xu.nmPriviledge,xu.RecId"
				+ " from " + this.ebEnt.dbEnterprise.getDbName()
				+ ".X25User xu, Users u";
		if (!stLcList.contains(",0,")) {
			stLcList = stLcList.replace(",,", ",");
			stLcList = stLcList.substring(1, stLcList.length() - 1);
			stSql += " join teb_reflaborcategory lc on lc.nmRefId=u.nmUserId and lc.nmLaborCategoryId in ("
					+ stLcList + ") ";
		}
		if (!stDivList.contains(",0,")) {
			stDivList = stDivList.replace(",,", ",");
			stDivList = stDivList.substring(1, stDivList.length() - 1);
			stSql += " join teb_refdivision dv on dv.nmRefId=u.nmUserId and dv.nmDivision in ("
					+ stDivList + ") ";
		}
		stSql += " where xu.RecId=u.nmUserId and ";
		if (stSearchType.equals("full")) {
			String[] aV = stSearch.split(" ");
			stSearch = aV[0].trim();
			stSql += " FirstName like ";
			if (stWhere.equals("beg")) {
				stSql += this.ebEnt.dbDyn.fmtDbString(stSearch + "%");
			} else if (stWhere.equals("any")) {
				stSql += this.ebEnt.dbDyn.fmtDbString("%" + stSearch + "%");
			} else if (stWhere.equals("end")) {
				stSql += this.ebEnt.dbDyn.fmtDbString("%" + stSearch);
			} else {
				stSql += this.ebEnt.dbDyn.fmtDbString(stSearch);
			}
			if (aV.length > 1 && aV[1].trim().length() > 0) {
				stSearch = aV[1].trim();
				stSql += " and LastName like ";
				if (stWhere.equals("beg")) {
					stSql += this.ebEnt.dbDyn.fmtDbString(stSearch + "%");
				} else if (stWhere.equals("any")) {
					stSql += this.ebEnt.dbDyn.fmtDbString("%" + stSearch + "%");
				} else if (stWhere.equals("end")) {
					stSql += this.ebEnt.dbDyn.fmtDbString("%" + stSearch);
				} else {
					stSql += this.ebEnt.dbDyn.fmtDbString(stSearch);
				}
			}
		} else {
			stSql += stSearchType + " like ";
			if (stWhere.equals("beg")) {
				stSql += this.ebEnt.dbDyn.fmtDbString(stSearch + "%");
			} else if (stWhere.equals("any")) {
				stSql += this.ebEnt.dbDyn.fmtDbString("%" + stSearch + "%");
			} else if (stWhere.equals("end")) {
				stSql += this.ebEnt.dbDyn.fmtDbString("%" + stSearch);
			} else {
				stSql += this.ebEnt.dbDyn.fmtDbString(stSearch);
			}
		}
		stSql += " and (xu.nmPriviledge & " + nmType + ") != 0 ";
		return stSql;
	}

	public String runD50D53() {
		String stReturn = "";
		try {
			ResultSet rsTable = this.ebEnt.dbDyn
					.ExecuteSql("SELECT * FROM teb_table where nmTableId=19");
			rsTable.absolute(1);
			ResultSet rsF = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_fields where stValidation = 'd50'");
			rsF.absolute(1);
			ResultSet rsReq = this.ebEnt.dbDyn
					.ExecuteSql("select * from Requirements");
			rsReq.last();
			int iReqMax = rsReq.getRow();
			for (int iR = 1; iR <= iReqMax; iR++) {
				rsReq.absolute(iR);
				this.ebEnt.ebUd.clearPopupMessage();
				this.iD50Flags = 0;
				validateD50(rsTable, rsF, rsReq.getString("RecId"),
						rsReq.getString("ReqDescription"),
						rsReq.getString("nmProjectId"),
						rsReq.getInt("nmBaseline"));
				this.ebEnt.dbDyn
						.ExecuteUpdate("update Requirements set nmD50Flags = "
								+ iD50Flags + " where RecId="
								+ rsReq.getString("RecId")
								+ " and nmProjectId="
								+ rsReq.getString("nmProjectId")
								+ " and nmBaseline= "
								+ rsReq.getString("nmBaseline"));
			}
			rsReq.close();
			rsF.close();
			rsF = this.ebEnt.dbDyn
					.ExecuteSql("select * from teb_fields where stValidation = 'd53'");
			rsF.absolute(1);
			ResultSet rsSch = this.ebEnt.dbDyn
					.ExecuteSql("select * from Schedule");
			rsSch.last();
			int iSchMax = rsSch.getRow();
			for (int iS = 1; iS <= iSchMax; iS++) {
				rsSch.absolute(iS);
				this.ebEnt.ebUd.clearPopupMessage();
				this.iD53Flags = 0;
				validateD53(rsTable, rsF, rsSch.getString("RecId"),
						rsSch.getString("SchDescription"),
						rsSch.getString("nmProjectId"),
						rsSch.getInt("nmBaseline"));
				this.ebEnt.dbDyn
						.ExecuteUpdate("update Schedule set nmD53Flags = "
								+ iD53Flags + " where RecId="
								+ rsSch.getString("RecId")
								+ " and nmProjectId="
								+ rsSch.getString("nmProjectId")
								+ " and nmBaseline= "
								+ rsSch.getString("nmBaseline"));
			}
			rsReq.close();
		} catch (Exception e) {
			stError += "<BR>ERROR: runD50D53 " + e;
		}
		this.ebEnt.ebUd.clearPopupMessage();
		return stReturn;
	}

	public String makeToolbar(boolean twoLines, int totalRecords, int fromPos,
			int displayPerList, String stLink) {

		displayPerList = (displayPerList > 5 ? displayPerList : 5);
		fromPos = (fromPos > 0 ? fromPos : 0);
		String firstAction = "javascript:void(0);";
		String lastAction = "javascript:void(0);";
		String nextAction = "javascript:void(0);";
		String previousAction = "javascript:void(0);";
		String upAction = "javascript:void(0);";
		String downAction = "javascript:void(0);";
		if (stLink != null && !stLink.isEmpty()) {
			if (fromPos > 0)
				firstAction = stLink + "&from=0" + "&display=" + displayPerList;
			if (fromPos + displayPerList < totalRecords)
				lastAction = stLink
						+ "&from="
						+ (totalRecords - (totalRecords % displayPerList == 0 ? displayPerList
								: totalRecords % displayPerList)) + "&display="
						+ displayPerList;
			if (fromPos > displayPerList) {
				previousAction = stLink + "&from=" + (fromPos - displayPerList)
						+ "&display=" + displayPerList;
			} else if (fromPos > 0) {
				previousAction = stLink + "&from=0" + "&display="
						+ displayPerList;
			}
			if (fromPos + displayPerList < totalRecords)
				nextAction = stLink + "&from=" + (fromPos + displayPerList)
						+ "&display=" + displayPerList;
			if (fromPos > 0)
				upAction = stLink + "&from=" + (fromPos - 1) + "&display="
						+ displayPerList;
			if (fromPos < totalRecords)
				downAction = stLink + "&from=" + (fromPos + 1) + "&display="
						+ displayPerList;
		}

		StringBuilder stReturn = new StringBuilder(
				"<div class='toolbar-wrapper'>");

		stReturn.append("<div class='button-wrapper'>");
		stReturn.append(" <a class='first-button' href='" + firstAction
				+ "'>First</a>");
		stReturn.append(" <div class='vsplitter'><span></span></div>");
		stReturn.append(" <a class='previous-button' href='" + previousAction
				+ "'>Previous</a>");
		stReturn.append(" <div class='vsplitter'><span></span></div>");
		stReturn.append(" <a class='up-button' href='" + upAction
				+ "'>Up One</a>");
		stReturn.append(" <div class='vsplitter'><span></span></div>");
		stReturn.append(" <a class='down-button' href='" + downAction
				+ "'>Down One</a>");
		stReturn.append(" <div class='vsplitter'><span></span></div>");
		stReturn.append(" <a class='next-button' href='" + nextAction
				+ "'>Next</a>");
		stReturn.append(" <div class='vsplitter'><span></span></div>");
		stReturn.append(" <a class='last-button' href='" + lastAction
				+ "'>Last</a>");
		stReturn.append(" <div class='vsplitter'><span></span></div>");
		stReturn.append(" <div class='clr'><span></span></div>");
		stReturn.append("</div>");

		stReturn.append("<div class='options-wrapper'>");
		stReturn.append(" <div class='options-left'>");
		stReturn.append("  <div><label>Display per list:</label><select class='display-per-list' onchange='location.href=\""
				+ (stLink + "&from=" + fromPos + "&display=")
				+ "\"+this.value'>");
		int[] displays = { 5, 10, 20, 50, 100 };
		for (int i = 0; i < displays.length; i++) {
			stReturn.append("   <option")
					.append(displays[i] == displayPerList ? " selected" : "")
					.append(" value='").append(displays[i]).append("'>")
					.append(displays[i]).append("</option>");
		}
		stReturn.append("  </select></div>");
		stReturn.append("  <div><label>Starting position:</label><select class='starting-position' onchange='location.href=\""
				+ (stLink + "&display=" + displayPerList + "&from=")
				+ "\"+this.value'>");
		Set<Integer> posSet = new HashSet<Integer>();
		int iLastPos = Math.round(totalRecords / 10) * 10;
		posSet.add(0);
		posSet.add(Math.round(iLastPos / 16));
		posSet.add(Math.round(iLastPos / 8));
		posSet.add(Math.round(iLastPos / 4));
		posSet.add(Math.round(iLastPos / 2));
		posSet.add(iLastPos);
		posSet.add(fromPos);
		List<Integer> posList = new ArrayList<Integer>(posSet);
		Collections.sort(posList);
		for (Integer pos : posList) {
			stReturn.append("   <option ")
					.append(pos == fromPos ? " selected" : "")
					.append(" value='").append(pos).append("'>")
					.append(pos.intValue() + 1).append("</option>");
		}
		stReturn.append("  </select></div>");
		stReturn.append(" </div>");
		stReturn.append(" <div class='options-right'>");
		stReturn.append("  <div><label>Last Record:</label></div>");
		stReturn.append("  <div><input value='").append(totalRecords)
				.append("' disabled /></div>");
		stReturn.append(" </div>");
		stReturn.append(" <div class='clr'><span></span></div>");
		stReturn.append("</div>");

		stReturn.append("<div class='clr'><span></span></div>");
		stReturn.append("</div>");
		return stReturn.toString();
	}

}
