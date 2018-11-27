package paging;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;

import com.twilio.sdk.resource.api.v2010.account.Call;
import com.twilio.sdk.resource.api.v2010.account.Message;
import com.twilio.sdk.type.PhoneNumber;

public class PagingThread extends Thread {
	
	private Properties properties;
	private JSONObject contacts;
	private long timestamp;
	private List<String> recipients;
	private boolean call;
	private String body;
	
	public PagingThread(Properties properties, JSONObject contacts, long timestamp, List<String> recipients, boolean call, String body) {
		super();
		this.properties = properties;
		this.contacts = contacts;
		this.timestamp = timestamp;
		this.recipients = recipients;
		this.call = call;
		this.body = body;
	}
	
	private void sms(JSONObject contacts, String name, String body) {
		try {
			JSONObject individual = contacts.getJSONObject("individuals").getJSONObject(name);

			Message.create(
					properties.getProperty("twilio.accountSid"),
	                new PhoneNumber(individual.getString("sms")),
	                new PhoneNumber(properties.getProperty("twilio.number")),
	                body).execute();
			
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	private void call(JSONObject contacts, String name, long timestamp) {
		try {
			JSONObject individual = contacts.getJSONObject("individuals").getJSONObject(name);
			if(!individual.isNull("phone")) {
				Call.create(properties.getProperty("twilio.accountSid"),
						new PhoneNumber(individual.getString("phone")),
						new PhoneNumber(properties.getProperty("twilio.number")),
						new URI(properties.getProperty("server") + "/read/" + timestamp)).execute();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		for(String name : recipients) {
			sms(contacts, name, body);
			if(call) call(contacts, name, timestamp);
		}
	}

}
