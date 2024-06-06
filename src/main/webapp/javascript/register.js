function passwordMatch() {
	let pswd1 = document.getElementById("pswd1").value;
	let pswd2 = document.getElementById("pswd2").value;

	if (pswd1 != null && pswd2 != null && pswd1 !== "" && pswd2 !== "")
		return pswd1.match(pswd2);

	return false;
}

(function() { // avoid variables ending up in the global scope
	document.getElementById("registerForm").addEventListener('submit', (e) => {
		e.preventDefault()

		let form = e.target;

		if (form.checkValidity()) {
			if (!passwordMatch()) {
				document.getElementById("errorMessage").textContent = "Le password non corrispondono (o sono mancanti)!";
				document.getElementById("errorMessage").hidden = false;
				return false;
			}

			/*if (checkEmail(document.getElementById("email").value)) { 
				document.getElementById("errorMsg").textContent.style.visibility = "visible";
				document.getElementById("errorMsg").textContent = "Email is not valid";
				return false;
			}*/

			let formData = new FormData();
			formData.append("username", document.getElementById("username").value);
			formData.append("email", document.getElementById("email").value);

			formData.append("password", document.getElementById("pswd1").value);
			formData.append("passwordCheck", document.getElementById("pswd2").value);

			makeCall("POST", 'CheckSignupCredentials', formData,
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
							case 409: // conflict
								document.getElementById("errorMessage").textContent = message;
								document.getElementById("errorMessage").hidden = false;
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
			document.getElementById("errorMessage").hidden = true;
			form.reportValidity();
		}
	});
})();