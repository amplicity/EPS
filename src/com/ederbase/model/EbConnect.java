package com.ederbase.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

public class EbConnect {
	private Connection dbConn;
	private String stError = "";
	private int iDbType = 0;
	private String stHost = "";
	private String stUser = "";
	private String stPassword = "";
	private String stDbName = "";
	private String stConnectString = "";

	public void ebClose() {
		try {
			if (this.dbConn != null) {
				this.dbConn.close();
			}
		} catch (Exception e) {
			this.stError = (this.stError + "<br>ERROR  ebClose: " + e
					.toString());
		}
	}

	public EbConnect(int iDbType, String stHost, String stUser,
			String stPassword, String stDbName, String stConnectString) {
		EbConnect2(iDbType, stHost, stUser, stPassword, stDbName,
				stConnectString);
	}

	public void EbConnect2(int iDbType, String stHost, String stUser,
			String stPassword, String stDbName, String stConnectString) {
		this.dbConn = null;
		this.iDbType = iDbType;
		this.stHost = stHost;
		this.stUser = stUser;
		this.stPassword = stPassword;
		this.stDbName = stDbName;
		this.stConnectString = stConnectString;
		try {
			switch (iDbType) {
			case 0:
				try {
					Class.forName("com.mysql.jdbc.Driver").newInstance();

					InputStream is = this.getClass().getClassLoader()
							.getResourceAsStream("config.properties");
					String suffix = "";
					if (is != null) {
						Properties props = new Properties();
						props.load(is);
						suffix = props.getProperty("databaseSuffix", "");
					}
					String stUrl = "jdbc:mysql://localhost:3306/" + stDbName
							+ (suffix.length() > 0 ? "_" + suffix : "")
							+ "?zeroDateTimeBehavior=convertToNull";
					this.dbConn = DriverManager.getConnection(stUrl, "eps",
							"eps");
				} catch (Exception e) {
					e.printStackTrace();
					this.stError = (this.stError
							+ "<br>ERROR  mysql Exception: " + e.toString());
				}
				break;

			case 1:
				try {
					Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
					String connectionUrl = "jdbc:sqlserver://" + stHost
							+ ":1433;" + "databaseName=" + stDbName + ";user="
							+ stUser + ";password=" + stPassword + ";";

					this.dbConn = DriverManager.getConnection(connectionUrl);
				} catch (SQLException e) {
					this.stError = (this.stError
							+ "<br>ERROR  sqlserver Exception: " + e.toString());
				} catch (ClassNotFoundException cE) {
					this.stError = (this.stError
							+ "<br>ERROR  Class Not Found Exception: " + cE
							.toString());
				}
				break;

			case 2:
				try {
					Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
					String connectionUrl = "jdbc:odbc:" + stConnectString;
					connectionUrl = "jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ=C:\\tmp\\ederbase.mdb;DriverID=22;READONLY=false;";

					this.dbConn = DriverManager.getConnection(connectionUrl);
				} catch (Exception e) {
					this.stError = (this.stError
							+ "<br>ERROR  odbc Exception: " + e.toString());
				}
				break;

			default:
				this.stError = (this.stError
						+ "<br>ERROR  FATAL ERROR: invalid DB TYPE " + iDbType);
				break;
			}
		} catch (Exception e) {
			this.stError = (this.stError + "<br>ERROR  ERROR EbConnect: "
					+ stHost + " " + stUser + " type: " + iDbType
					+ " Exception: " + e);
		}
	}

	public Connection getEbConn() {
		try {
			if (this.dbConn != null) {
				if (this.dbConn.isClosed() == true) {
					this.dbConn = null;
				}
			}
			if (this.dbConn == null) {
				EbConnect2(this.iDbType, this.stHost, this.stUser,
						this.stPassword, this.stDbName, this.stConnectString);
				this.stError += "<BR> getEbConn is CLOSED ";
			}
		} catch (Exception e) {
			this.stError = (this.stError + "<br> getEbConn ERROR " + e);
		}
		return this.dbConn;
	}

	public String getError() {
		return this.stError;
	}

	public boolean exportTo(String mysqlpath, String dumppath) {
		try {
			if (!mysqlpath.endsWith("\\") && !mysqlpath.endsWith("/"))
				mysqlpath += "/";
			if (!dumppath.endsWith("\\") && !dumppath.endsWith("/"))
				dumppath += "/";
			Date aDate = Calendar.getInstance().getTime();
			SimpleDateFormat formatter = new SimpleDateFormat("ddMMyy");
			Runtime rt = Runtime.getRuntime();
			switch (iDbType) {
			case 0:
				Process p = rt.exec(mysqlpath + "mysqldump.exe " + stDbName
						+ " -hlocalhost -ueps -peps");

				InputStream is = p.getInputStream();
				File f = new File(dumppath, "EPPORA-BACKUP-" + stDbName + "-"
						+ formatter.format(aDate) + ".sql");
				if (!f.exists())
					f.createNewFile();
				BufferedReader br = new BufferedReader(
						new InputStreamReader(is));
				BufferedWriter bw = new BufferedWriter(new FileWriter(f));
				int i;
				do {
					i = br.read();
					if (i != -1) {
						bw.write((char) i);
					}
				} while (i != -1);
				bw.close();
				br.close();
				return true;
			default:
				this.stError = "ERROR: ERROR EbConnect: Dumpdata not support with database type";
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
			this.stError = "ERROR: ERROR EbConnect: Dumpdata " + stDbName;
		}
		return false;
	}
}