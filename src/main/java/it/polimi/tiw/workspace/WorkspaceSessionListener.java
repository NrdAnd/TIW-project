package it.polimi.tiw.workspace;

import it.polimi.tiw.beans.User;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.*;

@WebListener
public class WorkspaceSessionListener implements HttpSessionListener {
  @Override
  public void sessionDestroyed(HttpSessionEvent event) {
    HttpSession session = event.getSession();
    User user = (User) session.getAttribute("utente");
    String key = (String) session.getAttribute("workspaceSession");
    if (user == null || key == null) return;
    try (WorkspaceService service =
        new WorkspaceService(session.getServletContext(), user.getUserID(), key)) {
      service.lock();
      service.closeSession();
      service.commit();
    } catch (Exception exception) {
      session
          .getServletContext()
          .log(
              "Session undo cleanup deferred; it will be retried on the next workspace operation.");
    }
  }
}
