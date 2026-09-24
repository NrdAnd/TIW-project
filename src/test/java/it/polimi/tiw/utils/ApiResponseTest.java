package it.polimi.tiw.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class ApiResponseTest {
  static class Exchange {
    int status;
    String contentType;
    String encoding;
    final Map<String, String> headers = new HashMap<>();
    final StringWriter body = new StringWriter();
    final HttpServletResponse response =
        (HttpServletResponse)
            Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                  switch (method.getName()) {
                    case "setStatus":
                      status = (int) args[0];
                      return null;
                    case "setContentType":
                      contentType = (String) args[0];
                      return null;
                    case "setCharacterEncoding":
                      encoding = (String) args[0];
                      return null;
                    case "setHeader":
                      headers.put((String) args[0], (String) args[1]);
                      return null;
                    case "getWriter":
                      return new PrintWriter(body);
                    case "isCommitted":
                      return false;
                    default:
                      return null;
                  }
                });
  }

  @Test
  void errorsAreShortStructuredJsonInsteadOfContainerHtml() throws Exception {
    Exchange exchange = new Exchange();

    ApiResponse.error(
        exchange.response, 503, "DATABASE_UNAVAILABLE", "The database is unavailable.");

    assertEquals(503, exchange.status);
    assertEquals("application/json", exchange.contentType);
    assertEquals("UTF-8", exchange.encoding);
    assertEquals("no-store", exchange.headers.get("Cache-Control"));
    assertTrue(exchange.body.toString().contains("\"code\":\"DATABASE_UNAVAILABLE\""));
    assertTrue(exchange.body.toString().contains("The database is unavailable."));
    assertFalse(exchange.body.toString().contains("<html"));
    assertFalse(exchange.body.toString().contains("Exception"));
  }
}
