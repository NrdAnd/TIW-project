package it.polimi.tiw.filters;

import it.polimi.tiw.utils.ApiResponse;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;


public class Checker implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse res = (HttpServletResponse) servletResponse;
        String loginpath = req.getServletContext().getContextPath() + "/index.html";

        HttpSession s = req.getSession(false);
        if (s == null || s.isNew() || s.getAttribute("utente") == null) {
            res.setHeader("Location", loginpath);
            ApiResponse.error(
                    res,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "Your session has expired. Sign in again.");
            return;
        }

        filterChain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
