package paging;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

class StaticServlet extends HttpServlet {
	
	private static final long serialVersionUID = 0L;

	StaticServlet() {
	}

	@Override
	public void doGet(HttpServletRequest request,HttpServletResponse response) throws ServletException, IOException {
		String id = request.getRequestURI().replaceFirst("/static/", "/");
		if(id.endsWith(".html") || id.endsWith(".css") || id.endsWith(".js")) {
			String content = IOUtils.toString(getClass().getResourceAsStream((id.startsWith("/") ? "" : "/") + id));
			if(content == null) {
				System.out.println("static resource " + id + " not found");
			} else {
				response.getOutputStream().write(content.getBytes());
			}
		}
	}

}
