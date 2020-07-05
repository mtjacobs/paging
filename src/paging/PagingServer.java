package paging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.twilio.sdk.Twilio;

public class PagingServer {

	public static void main(String[] args) {
		
		Properties properties = new Properties();
		try {
			FileInputStream fis = new FileInputStream(new File("settings.properties"));
			properties.load(fis);
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
        Twilio.init(properties.getProperty("twilio.accountSid"), properties.getProperty("twilio.password"));
		
		Server server = new Server(Integer.parseInt(properties.getProperty("port")));
		HashSessionIdManager idmanager = new HashSessionIdManager();
		server.setSessionIdManager(idmanager);

		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		
		SQLiteJDBCSource source = new SQLiteJDBCSource();

		context.addServlet(new ServletHolder(new StaticServlet()), "/static/*");
		context.addServlet(new ServletHolder(new PagingServlet(properties, source)), "/*");

		server.setHandler(context);
		
		SessionHandler sessions = new SessionHandler();
		context.setHandler(sessions);

		ErrorHandler handler = new ErrorHandler();
		handler.setShowStacks(true);
		context.setErrorHandler(handler);

        try {
			server.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
        
	}	
	
}
