package it.polimi.tiw.utils;

import javax.servlet.http.HttpServletRequest;

public final class RequestFields {
  private RequestFields() {}

  /** Reject ambiguous scalar input across the merged query/body parameter set. */
  public static boolean unambiguous(HttpServletRequest request) {
    if (request.getQueryString() != null && !request.getQueryString().isEmpty()) return false;
    try {
      return request.getParameterMap().values().stream().allMatch(values -> values.length == 1);
    } catch (IllegalStateException | IllegalArgumentException e) {
      return false;
    }
  }
}
