(function() { // avoid variables ending up in the global scope

  document.getElementById("login-button").addEventListener('click', (e) => {
    e.preventDefault()
    
    let form = e.target.closest("form");
    
    let formData = new FormData();
    
    formData.append("username",document.getElementById("username").value);
    formData.append("password",document.getElementById("password").value);

    if (form.checkValidity()) {
      makeCall("POST", 'CheckLoginCredentials', formData,
        function(req) {
          if (req.readyState === XMLHttpRequest.DONE) {
            let message = req.responseText;

            switch (req.status) {
              case 200:
                sessionStorage.setItem('utente', message);
                window.location.href = "homepage.html";
                break;
              case 400: // bad request
                document.getElementById("errorMessage").textContent = message;
                document.getElementById("errorMessage").hidden = false;
                break;
              case 401: // unauthorized
                document.getElementById("errorMessage").textContent = message;
                document.getElementById("errorMessage").hidden = false;
                break;
              case 403: //an other account is already logged in
				window.location.href = req.getResponseHeader("Location");
                window.sessionStorage.removeItem('utente');
				alert("An other account is already logged in. Automatically log out...");
				break;
              case 500: // server error
            	document.getElementById("errorMessage").textContent = message;
                document.getElementById("errorMessage").hidden = false;
                break;
            }
          }
        }
      );
    } else {
    	 form.reportValidity();
    }
  });
})();