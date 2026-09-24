/**
 * AJAX call management
 */

function makeCall(method, url, formElement, cback, reset = true, options = {}) {
  var req = new XMLHttpRequest(); // visible by closure
  req.onreadystatechange = function () {
    cback(req);
  }; // closure
  req.open(method, url);
  req.timeout = options.timeout || 20000;
  req.setRequestHeader("Accept", "application/json");
  Object.entries(options.headers || {}).forEach(([name, value]) =>
    req.setRequestHeader(name, value),
  );
  if (options.onProgress)
    req.upload.addEventListener("progress", options.onProgress); // Fail visibly instead of leaving the UI waiting indefinitely.
  if (formElement == null) {
    req.send();
  } else {
    req.send(formElement);
  }
  if (formElement !== null && reset === true && method === "GET") {
    formElement.reset();
  }
}

/**
 * Returns a short application-owned message for every failed HTTP request.
 * Container HTML pages, stack traces and oversized responses are never shown.
 */
function requestErrorMessage(request, fallback) {
  const messages = {
    0: "The server could not be reached. Check that Tomcat is running and try again.",
    400: "The request is invalid. Check the entered data and try again.",
    401: "Your session has expired. Sign in again.",
    403: "You do not have permission to perform this action.",
    404: "The requested service is unavailable. Rebuild and redeploy the application.",
    405: "This action is not supported.",
    408: "The request took too long. Please try again.",
    409: "The data changed or already exists. Refresh and try again.",
    413: "The selected files are too large.",
    415: "This file or request format is not supported.",
    429: "Too many requests were submitted. Wait a moment and try again.",
    500: "The server could not complete the request. Please try again.",
    502: "The application service is temporarily unavailable.",
    503: "The application service is unavailable. Check MySQL and Tomcat, then try again.",
    504: "The server took too long to respond. Please try again.",
  };
  const contentType = (request.getResponseHeader("Content-Type") || "")
    .toLowerCase();
  const body = (request.responseText || "").trim();

  if (contentType.includes("application/json") && body.length <= 4000) {
    try {
      const value = JSON.parse(body);
      const message = value?.error?.message || value?.message;
      if (safeErrorText(message)) return message.trim();
    } catch (_) {
      // Fall through to the status-specific message.
    }
  }

  if (
    (!contentType || contentType.startsWith("text/plain")) &&
    safeErrorText(body)
  )
    return body;

  return fallback || messages[request.status] || messages[500];
}

function safeErrorText(value) {
  return (
    typeof value === "string" &&
    value.trim().length > 0 &&
    value.trim().length <= 400 &&
    !/<\/?(?:html|head|body|style|script)|<!doctype/i.test(value) &&
    !/(?:java\.|javax\.|org\.apache\.|at [\w.$]+\([^)]*:\d+\))/i.test(value)
  );
}

function parseJsonResponse(text) {
  try {
    return JSON.parse(text);
  } catch (_) {
    throw new Error(
      "The server returned an invalid response. Please try again.",
    );
  }
}

function showRequestError(element, request, fallback) {
  element.textContent = requestErrorMessage(request, fallback);
  element.hidden = false;
}

/**
 * This method checks if an email is valid.
 * @param email is the specific email.
 * @returns {*} true if the email is valid, false otherwise.
 */
function checkEmail(email) {
  return /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$/.test(email);
}
