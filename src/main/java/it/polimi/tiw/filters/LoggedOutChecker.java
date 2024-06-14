package it.polimi.tiw.filters;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class LoggedOutChecker implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // If the user is not logged in (not present in session) redirect to the login
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;
        HttpSession session = req.getSession();

        if (!session.isNew() && session.getAttribute("utente") != null && req.getMethod().equals("POST")) {
        	
            //((HttpServletResponse) resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            
            if(session != null) {
                session.invalidate();
            }
              
            String loginpath = req.getServletContext().getContextPath() + "/index.html";
            resp.getWriter().println("You are already logged in! Automatically log out of your previous account ...");
            
            resp.setStatus(403);
            resp.setHeader("Location", loginpath);
            return;
            
        } else {
        	filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
