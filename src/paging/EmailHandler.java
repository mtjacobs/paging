package paging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;

public class EmailHandler implements MessageHandlerFactory {
	
	private PagingServlet servlet;

    private class Handler implements MessageHandler {
        private String to;
        private String from;
        private String message;

        private Handler(MessageContext ctx) {
        }
        
        @Override
		public void from(String from) throws RejectException {
        	this.from = from;
        }

        @Override
		public void recipient(String recipient) throws RejectException {
        	this.to = recipient;
        }

        @Override
		public void data(InputStream data) throws IOException {
        	message = this.convertStreamToString(data);
        	if("coordinators".equals(to.substring(0, to.indexOf("@")))) {
	        	servlet.sendMessage(null, new String[] { "Matt Jacobs" }, "low", "From " + from + ":\n" + message.substring(message.indexOf("Subject")));
        	}
        }

        @Override
		public void done() {
        }

        private String convertStreamToString(InputStream is) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                
                String line = null;
                try {
                        while ((line = reader.readLine()) != null) {
                                sb.append(line + "\n");
                        }
                } catch (IOException e) {
                        e.printStackTrace();
                }
                return sb.toString();
        }

    }

    @Override
	public MessageHandler create(MessageContext ctx) {
        return new Handler(ctx);
    }
    
    public EmailHandler(PagingServlet servlet) {
    	this.servlet = servlet;
    }

}
