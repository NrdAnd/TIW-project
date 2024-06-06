package it.polimi.tiw.filters;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import it.polimi.tiw.beans.User;

import java.io.File;
import java.io.IOException;


public class Checker implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // If the user is not logged in (not present in session) redirect to the login
        //System.out.print("Login checker filter executing ...\n");

        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse res = (HttpServletResponse) servletResponse;
        HttpSession session = req.getSession();
        User utente = (User) session.getAttribute("utente");
        String loginpath = req.getServletContext().getContextPath() + "/index.html";

        HttpSession s = req.getSession();
        if (s.isNew() || s.getAttribute("utente") == null) {
        	
            res.setStatus(403);
            res.setHeader("Location", loginpath);
            //System.out.print("Login checker FAILED...\n");
            
            try {
    			String filePath = "REDACTED_HOME/git/TIW_Project_2024_RIA/src/main/java/it/polimi/tiw/utils/SaveDatas_ID_" + utente.getUserID() + ".json";
    			File saveDatasFile = new File(filePath);
    			saveDatasFile.delete();
    		} catch (Exception e) {
    			//e.printStackTrace();
    		}
            
            return;
        }

        filterChain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
