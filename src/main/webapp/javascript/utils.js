/**
 * AJAX call management
 */

function makeCall(method, url, formElement, cback, reset = true) {
	var req = new XMLHttpRequest(); // visible by closure
	req.onreadystatechange = function() {
	  cback(req)
	}; // closure
	req.open(method, url);
	if (formElement == null) {
	  req.send();
	} else {
	  req.send(new FormData(formElement));
	}
	if (formElement !== null && reset === true) {
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
        /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}])|(([a-zA-Z\-\d]+\.)+[a-zA-Z]{2,}))$/
    );
}