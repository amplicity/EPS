package org.mypackage.eps;

import java.sql.Statement;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import com.ederbase.model.EbConnect;

public class EpsListener implements HttpSessionListener {

	@Override
	public void sessionCreated(HttpSessionEvent event) {
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		Object userId = event.getSession().getAttribute("userId");
		if (userId != null && !"".equals(userId.toString().trim())) { 
			EbConnect eb = new EbConnect(0, "localhost", "eps", "eps", "ebeps01", "");
			String stSql = "update X25User set nmLastLoginTime=0 where RecId = " + userId.toString();
			try {
	      Statement stmt = eb.getEbConn().createStatement();
	      stmt.executeUpdate(stSql);
      } catch (Exception e) {
	      e.printStackTrace();
      }
		}
	}

}
