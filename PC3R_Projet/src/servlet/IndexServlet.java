package servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import data.User;

@WebServlet("/")
public class IndexServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
    public IndexServlet() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		if(User.isConnected(request.getSession())) {
			response.sendRedirect("/game");
			return;
		}
		
		request.getRequestDispatcher("/WEB-INF/index.jsp").forward(request, response);
		
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		try {
			
			var action = request.getParameter("action");
			
			if(action.equals("login")) {
				User.login(request);
			} else if(action.equals("signup")) {
				User.signup(request);
			} else if(action.equals("logout")) {
				User.logout(request);
			} else {
				throw new Exception("Unrecognized action");
			}
			
			doGet(request, response);
			
		} catch (Exception e) {
			e.printStackTrace();
			
			request.setAttribute("error", e.getMessage());
			request.getRequestDispatcher("/WEB-INF/index.jsp").forward(request, response);
			
		}
	}
}
