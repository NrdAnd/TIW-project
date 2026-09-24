function passwordMatch() {
  let pswd1 = document.getElementById("pswd1").value;
  let pswd2 = document.getElementById("pswd2").value;

  if (pswd1 != null && pswd2 != null && pswd1 !== "" && pswd2 !== "")
    return pswd1 === pswd2;

  return false;
}

(function () {
  // avoid variables ending up in the global scope
  document.getElementById("registerForm").addEventListener("submit", (e) => {
    e.preventDefault();
    if (document.getElementById("register-button").disabled) return;

    let form = e.target;

    if (form.checkValidity()) {
      if (!passwordMatch()) {
        document.getElementById("errorMessage").textContent =
          "The passwords do not match (or are missing)!";
        document.getElementById("errorMessage").hidden = false;
        return false;
      }

      if (!checkEmail(document.getElementById("email").value)) {
        document.getElementById("errorMessage").textContent =
          "Email is not valid!";
        document.getElementById("errorMessage").hidden = false;
        return false;
      }

      let formData = new FormData();
      formData.append("username", document.getElementById("username").value);
      formData.append("email", document.getElementById("email").value);

      formData.append("password", document.getElementById("pswd1").value);
      formData.append("passwordCheck", document.getElementById("pswd2").value);

      document.getElementById("register-button").disabled = true;
      makeCall("POST", "CheckSignupCredentials", formData, function (req) {
        if (req.readyState === XMLHttpRequest.DONE) {
          document.getElementById("register-button").disabled = false;
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
                "The server returned an invalid registration response. Please try again.",
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
      document.getElementById("errorMessage").hidden = true;
      form.reportValidity();
    }
  });
})();
