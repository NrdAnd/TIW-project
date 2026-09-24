package it.polimi.tiw.utils;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

/** Writes every API response in a predictable, browser-safe format. */
public final class ApiResponse {
  private static final Gson JSON = new Gson();

  private ApiResponse() {}

  public static void ok(HttpServletResponse response, Object value) throws IOException {
    json(response, HttpServletResponse.SC_OK, value);
  }

  public static void json(HttpServletResponse response, int status, Object value)
      throws IOException {
    if (response.isCommitted()) return;
    response.resetBuffer();
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.getWriter().write(JSON.toJson(value));
  }

  public static void error(
      HttpServletResponse response, int status, String code, String message) throws IOException {
    json(
        response,
        status,
        Map.of("ok", false, "error", Map.of("code", code, "message", message)));
  }

  public static void methodNotAllowed(HttpServletResponse response, String allowed)
      throws IOException {
    response.setHeader("Allow", allowed);
    error(
        response,
        HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "This action does not support the requested HTTP method.");
  }
}
