(function () {
  // avoid variables ending up in the global scope

  document.getElementById("loginForm").addEventListener("submit", (e) => {
    e.preventDefault();
    if (document.getElementById("login-button").disabled) return;

    let form = e.target;

    let formData = new FormData();

    formData.append("username", document.getElementById("username").value);
    formData.append("password", document.getElementById("password").value);

    if (form.checkValidity()) {
      document.getElementById("login-button").disabled = true;
      makeCall("POST", "CheckLoginCredentials", formData, function (req) {
        if (req.readyState === XMLHttpRequest.DONE) {
          document.getElementById("login-button").disabled = false;
          if (req.status === 200) {
            try {
              const result = parseJsonResponse(req.responseText);
              if (!result.user) throw new Error("Missing user information.");
              sessionStorage.setItem("utente", result.user);
              window.location.href = "homepage.html";
            } catch (_) {
              showRequestError(
                document.getElementById("errorMessage"),
                req,
                "The server returned an invalid sign-in response. Please try again.",
              );
            }
            return;
          }

          if (req.status === 403 && req.getResponseHeader("Location")) {
            window.sessionStorage.removeItem("utente");
            window.location.href = req.getResponseHeader("Location");
            return;
          }

          showRequestError(document.getElementById("errorMessage"), req);
        }
      });
    } else {
      form.reportValidity();
    }
  });
})();
