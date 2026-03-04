package servlet;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import data.User;

@WebServlet("/")
public class IndexServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
    public IndexServlet() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Redirect root to the new SPA login page
		response.sendRedirect("/login.html");
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
