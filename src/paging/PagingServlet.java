package paging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PagingServlet extends HttpServlet {

	private static final long serialVersionUID = 0L;

	private Properties properties;
	private SQLiteJDBCSource db;

	PagingServlet(Properties properties, SQLiteJDBCSource source) {
		this.properties = properties;
		this.db = source;
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
				JSONObject contacts = new JSONObject(FileUtils.readFileToString(new File("contacts.json")));
				JSONObject sendTo = new JSONObject(request.getParameter("contacts"));
				List<String> aliases = new ArrayList<String>();
				List<String> recipients = new ArrayList<String>();
				for(int i = 0; i < sendTo.getJSONArray("groups").length(); i++) {
					String group = sendTo.getJSONArray("groups").getString(i);
					if(!aliases.contains(group)) aliases.add(group);
					JSONArray members = contacts.getJSONObject("groups").getJSONArray(group);
					for(int j = 0; j < members.length(); j++) {
						if(!recipients.contains(members.getString(j))) recipients.add(members.getString(j));
					}
				}
				for(int i = 0; i < sendTo.getJSONArray("individuals").length(); i++) {
					String name = sendTo.getJSONArray("individuals").getString(i);
					if(!aliases.contains(name)) aliases.add(name);
					if(!recipients.contains(name)) recipients.add(name);
				}
				boolean call = "high".equals(request.getParameter("priority"));
				boolean urgent = !("low".equals(request.getParameter("priority")));
				long timestamp = System.currentTimeMillis();
				db.execute("insert into messages (direction, sender, recipients, message, timestamp) values (?, ?, ?, ?, ?)", new String[] { "1", null, StringUtils.join(aliases, ","), body, Long.toString(timestamp) });
				new PagingThread(properties, contacts, timestamp, recipients, urgent, call, body).start();
			} catch (JSONException e){
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
			parameters.put("contacts", FileUtils.readFileToString(new File("contacts.json")));
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
			parameters.put("contacts", FileUtils.readFileToString(new File("contacts.json")));
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
			content=content.replaceAll("\\$\\{" + key + "\\}", parameters.get(key));
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
