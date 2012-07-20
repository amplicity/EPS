package org.mypackage.eps;

import java.util.Date;
import java.util.Set;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public class EpsListener implements HttpSessionListener {

	@Override
	public void sessionCreated(HttpSessionEvent event) {
		System.out.println("--------> CREATE SESSION " + event.getSession().getId()
		    + " " + new Date());
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		System.out.println("--------> DESTROY SESSION "
		    + event.getSession().getId() + " " + new Date());
		Set<Integer> ids = (Set<Integer>) event.getSession().getServletContext()
		    .getAttribute("LOG_IN_USERS");
		Object userId = event.getSession().getAttribute("userId");
		if (ids != null && userId != null && ids.contains(userId)) {
			ids.remove(userId);
			event.getSession().getServletContext().setAttribute("LOG_IN_USERS", ids);

			// EbConnect eb = new EbConnect(0, "localhost", "eps", "eps", "ebeps01",
			// "");
			// String stSql = "update X25User set nmLastLoginTime=0 where RecId=" +
			// userId.toString();
			// try {
			// Statement stmt = eb.getEbConn().createStatement();
			// stmt.executeUpdate(stSql);
			// } catch (Exception e) {
			// e.printStackTrace();
			// }
			System.out.println("Reset timeout: " + userId);
		}
	}

}
