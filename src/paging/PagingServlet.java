package paging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.subethamail.smtp.server.SMTPServer;

public class PagingServlet extends HttpServlet {

	private static final long serialVersionUID = 0L;

	private Properties properties;
	private SQLiteJDBCSource db;
	private JSONObject contacts;
	
	private Set<String> following = new ConcurrentHashSet<String>();

	PagingServlet(Properties properties, SQLiteJDBCSource source) {
		this.properties = properties;
		this.db = source;
		try {
			this.contacts = new JSONObject(FileUtils.readFileToString(new File("contacts.json")));
		} catch (IOException | JSONException e) {
			e.printStackTrace();
		}
        SMTPServer smtpServer = new SMTPServer(new EmailHandler(this));
        smtpServer.setPort(25);
        smtpServer.start();
	}
	
	@SuppressWarnings("unchecked")
	private String getNameForNumber(String number) {
		try {
			JSONObject individuals = contacts.getJSONObject("individuals");
			Iterator<String> it = individuals.keys();
			while(it.hasNext()) {
				String name = it.next();
				JSONObject person = individuals.getJSONObject(name);
				if(person != null && person.has("sms") && number.equals(person.getString("sms"))) return name;
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public void sendMessage(String[] groups, String[] individuals, String priority, String body) {
		sendMessage(groups, individuals, priority, body, true);
	}
	
	private void sendMessage(String[] groups, String[] individuals, String priority, String body, boolean saveToDB) {
		try {
			List<String> aliases = new ArrayList<String>();
			List<String> recipients = new ArrayList<String>();
			if(groups != null) {
				for(int i = 0; i < groups.length; i++) {
					String group = groups[i];
					if(!aliases.contains(group)) aliases.add(group);
					JSONArray members = contacts.getJSONObject("groups").getJSONArray(group);
					// TODO single- or multi-level iteration
					for(int j = 0; j < members.length(); j++) {
						if(!recipients.contains(members.getString(j))) recipients.add(members.getString(j));
					}
				}
			}
			if(individuals != null) {
				for(int i = 0; i < individuals.length; i++) {
					String name = individuals[i];
					if(!aliases.contains(name)) aliases.add(name);
					if(!recipients.contains(name)) recipients.add(name);
				}
			}
			long timestamp = System.currentTimeMillis();
			if(saveToDB) {
				db.execute("insert into messages (direction, sender, recipients, message, timestamp) values (?, ?, ?, ?, ?)", new String[] { "1", null, StringUtils.join(aliases, ","), body, Long.toString(timestamp) });
			}
			new PagingThread(properties, contacts, timestamp, recipients, priority, body).start();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void doPost(HttpServletRequest request,HttpServletResponse response) throws ServletException, IOException {
		String uri = request.getRequestURI();
		if("/loginpw".equals(uri)) {
			String username = request.getParameter("username");
			String password = request.getParameter("password");
			if(properties.getProperty("username").equals(username) && properties.getProperty("password").equals(password)) {
				request.getSession(true).setAttribute("authenticated", Boolean.TRUE);
				response.sendRedirect("/");
			}
			respond(response, "login.jsp", new HashMap<String, String>());
		}
		if("/smswebhook".equals(uri)) {
			String body = request.getParameter("Body");
			String sender = request.getParameter("From");
			
			db.execute("insert into messages (direction, sender, recipients, message, timestamp) values (?, ?, ?, ?, ?)", new String[] { "0", sender, null, body, Long.toString(System.currentTimeMillis()) });
			
			if(following.size() > 0) {
				String senderName = getNameForNumber(sender);
				sendMessage(null, following.toArray(new String[following.size()]), "control", "From " + sender + " " + (senderName != null ? senderName + " " : "") + ": " + body, false);
			}
			
			response.setContentType("text/html");
			response.getOutputStream().write("<Response></Response>".getBytes());
		}
		if("/inboundsmswebhook".equals(uri)) {
			String body = request.getParameter("Body");
			String sender = request.getParameter("From");
			db.execute("insert into messages (direction, sender, recipients, message, timestamp) values (?, ?, ?, ?, ?)", new String[] { "0", sender, "coordinators", body, Long.toString(System.currentTimeMillis()) });
			if(body.toLowerCase().startsWith("follow")) {
				following.add(sender);
				sendMessage(null, new String[] { sender }, "control", "You are now following replies, reply \"quiet\" to stop following", false);
			} else if(body.toLowerCase().startsWith("quiet")) {
				following.remove(sender);
				sendMessage(null, new String[] { sender }, "control", "You have stopped following replies", false);
			} else if(body.toLowerCase().startsWith("coordinator")) {
				sendMessage(null, new String[] { "Matt Jacobs" }, "low", "From " + sender + ": " + body);
				sendMessage(null, new String[] { sender }, "control", "Message sent to coordinators", false);
			} else {
				sendMessage(null, new String[] { sender }, "control", "Message not recognized, to send a message to team coordinators, begin your message with the word coordinator", false);
			}
			
			response.setContentType("text/html");
			response.getOutputStream().write("<Response></Response>".getBytes());
		}
		if(uri.startsWith("/read")) {
			Long timestamp = Long.parseLong(uri.substring(6));
			String[][] results = db.query("select message from messages where direction=1 and timestamp=?", new String [] { timestamp.toString() });
			// TODO XML encode
			String readback = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Response><Say loop=\"5\">" + results[0][0] + "</Say></Response>";
			response.setContentType("text/xml");
			response.getOutputStream().write(readback.getBytes());
		}
		if(uri.startsWith("/send")) {
			if(! (Boolean) request.getSession(true).getAttribute("authenticated")) return;
			try {
				String body = request.getParameter("message");
				JSONObject sendTo = new JSONObject(request.getParameter("contacts"));
				String priority = request.getParameter("priority");

				JSONArray jsgroups = sendTo.getJSONArray("groups");
				String[] groups = new String[jsgroups.length()];
				for(int i = 0; i < jsgroups.length(); i++) {
					groups[i] = jsgroups.getString(i);
				}
				JSONArray jsindividuals = sendTo.getJSONArray("individuals");
				String[] individuals = new String[jsindividuals.length()];
				for(int i = 0; i < jsindividuals.length(); i++) {
					individuals[i] = jsindividuals.getString(i);
				}
				sendMessage(groups, individuals, priority, body);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void doGet(HttpServletRequest request,HttpServletResponse response) throws ServletException, IOException {
		String id = request.getRequestURI().replaceFirst("/static/", "/");
		if("/".equals(id) || "/index.html".equals(id)) {
			if(!Boolean.TRUE.equals(request.getSession(true).getAttribute("authenticated"))) {
				respond(response, "login.jsp", new HashMap<String, String>());
				return;
			}
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("contacts", contacts.toString());
			long timestamp = System.currentTimeMillis();
			parameters.put("timestamp", Long.toString(timestamp));
			parameters.put("messages", getMessages(timestamp - 24*60*60*1000).toString());
			respond(response, "index.jsp", parameters);
		}
		if(id.startsWith("/messages")) {
			try {
				JSONObject jsobj = new JSONObject();
				jsobj.put("timestamp", System.currentTimeMillis());
				jsobj.put("messages", getMessages(Long.parseLong(request.getParameter("since"))));
				
				response.setContentType("application/json");
				response.getOutputStream().write(jsobj.toString().getBytes());
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		if("/report".equals(id)) {
			if(!Boolean.TRUE.equals(request.getSession(true).getAttribute("authenticated"))) {
				respond(response, "login.jsp", new HashMap<String, String>());
				return;
			}
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("contacts", contacts.toString());
			respond(response, "report.jsp", parameters);
		}
	}
	
	private JSONArray getMessages(long since) {
		String[][] results = db.query("select direction, sender, recipients, message, timestamp from messages where timestamp > ?", new String[] { Long.toString(since)});
		JSONArray jsarray = new JSONArray();
		for(int i = 0; i < results.length; i++) {
			try {
				JSONObject result = new JSONObject();
				result.put("outbound", "1".equals(results[i][0]));
				result.put("sender", results[i][1]);
				JSONArray recipients = new JSONArray();
				if(results[i][2] != null) {
					String[] parts = results[i][2].split(",");
					for(String name : parts) recipients.put(name);
				}
				result.put("recipients", recipients);
				result.put("message", results[i][3]);
				result.put("timestamp", Long.parseLong(results[i][4]));
				jsarray.put(result);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}	
		return jsarray;
	}

	protected void respond(HttpServletResponse response, String name, Map<String, String> parameters) throws IOException {
		String content = IOUtils.toString(getClass().getResourceAsStream((name.startsWith("/") ? "" : "/") + name));
		content=content.replaceAll("<%.*?%>", "");
		for(String key : parameters.keySet()) {
			content=content.replaceAll("\\$\\{" + key + "\\}", Matcher.quoteReplacement(parameters.get(key)));
		}
		content=content.replaceAll("\\$\\{.*?\\}", "");
		response.setContentType("text/html");
		try {
			response.getOutputStream().write(content.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
}
