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
          let message = req.responseText;

          switch (req.status) {
            case 200:
              sessionStorage.setItem("utente", message);
              window.location.href = "homepage.html";
              break;
            case 400: // bad request
              document.getElementById("errorMessage").textContent =
                message || "Unable to reach the server. Please try again.";
              document.getElementById("errorMessage").hidden = false;
              break;
            case 401: // unauthorized
              document.getElementById("errorMessage").textContent =
                message || "Unable to reach the server. Please try again.";
              document.getElementById("errorMessage").hidden = false;
              break;
            case 403: //an other account is already logged in
              window.location.href = req.getResponseHeader("Location");
              window.sessionStorage.removeItem("utente");
              alert(
                "An other account is already logged in. Automatically log out...",
              );
              break;
            default: // Includes network errors/timeouts and unexpected server responses
            case 500: // server error
              document.getElementById("errorMessage").textContent =
                message || "Unable to reach the server. Please try again.";
              document.getElementById("errorMessage").hidden = false;
              break;
          }
        }
      });
    } else {
      form.reportValidity();
    }
  });
})();
