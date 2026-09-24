package it.polimi.tiw.filters;

import it.polimi.tiw.utils.ApiResponse;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;

/** Redundant authentication never signs out another tab or destroys undo history. */
public class LoggedOutChecker implements Filter {
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpSession session = req.getSession(false);
    if (session != null && session.getAttribute("utente") != null && req.getMethod().equals("POST")) {
      ApiResponse.error((HttpServletResponse) response, 409, "ALREADY_AUTHENTICATED",
          "You are already signed in. Open your workspace, or sign out before changing accounts.");
      return;
    }
    chain.doFilter(request, response);
  }
}
