package it.polimi.tiw.controllers;

import it.polimi.tiw.utils.ApiResponse;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Replaces container-generated HTML error pages with a small, stable JSON response. */
@WebServlet("/Error")
public final class ErrorController extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    Object statusValue = request.getAttribute("javax.servlet.error.status_code");
    int status = statusValue instanceof Integer ? (Integer) statusValue : 500;
    if (status < 400 || status > 599) status = 500;

    String code;
    String message;
    switch (status) {
      case 400:
        code = "BAD_REQUEST";
        message = "The request is invalid. Check the submitted data and try again.";
        break;
      case 401:
        code = "AUTHENTICATION_REQUIRED";
        message = "Your session has expired. Sign in again.";
        break;
      case 403:
        code = "ACCESS_DENIED";
        message = "You do not have permission to perform this action.";
        break;
      case 404:
        code = "NOT_FOUND";
        message = "The requested page or service could not be found.";
        break;
      case 405:
        code = "METHOD_NOT_ALLOWED";
        message = "This action does not support the requested HTTP method.";
        break;
      case 408:
        code = "REQUEST_TIMEOUT";
        message = "The request took too long. Please try again.";
        break;
      case 409:
        code = "CONFLICT";
        message = "The request conflicts with the current data. Refresh and try again.";
        break;
      case 413:
        code = "PAYLOAD_TOO_LARGE";
        message = "The submitted files are too large.";
        break;
      case 415:
        code = "UNSUPPORTED_MEDIA_TYPE";
        message = "The submitted file or request format is not supported.";
        break;
      case 429:
        code = "TOO_MANY_REQUESTS";
        message = "Too many requests were submitted. Wait a moment and try again.";
        break;
      case 502:
      case 503:
      case 504:
        code = "SERVICE_UNAVAILABLE";
        message = "The service is temporarily unavailable. Please try again shortly.";
        break;
      default:
        code = "INTERNAL_ERROR";
        message = "The server could not complete the request. Please try again.";
        break;
    }
    ApiResponse.error(response, status, code, message);
  }
}
