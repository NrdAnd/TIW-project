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
 * This method checks if an email is valid.
 * @param email is the specific email.
 * @returns {*} true if the email is valid, false otherwise.
 */
function checkEmail(email) {
  return email.match(
    /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}])|(([a-zA-Z\-\d]+\.)+[a-zA-Z]{2,}))$/,
  );
}
