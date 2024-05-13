{
    /**
     * This method checks if the user is logged in.
     */
    if (localStorage.getItem("user") === null) {
        logout();
    }

    let folderList, documentDetails, createFolder, createDocument, dragAndDropManager,
        pageManager = new PageManager();
    /**
     * This starts the page if the user is logged in.
     */
    window.addEventListener('load', function () {
        pageManager.start();
        if (localStorage.getItem("user") === null) {
            logout()
        } else {
            start();
        }
    }, false);

    function start() {
        document.getElementById("userName").textContent = JSON.parse(localStorage.getItem("user"));
        document.getElementById("Logout").addEventListener("click", function () {
            document.getElementById("Logout").disable = true;
            logout();
        });
        pageManager.refresh();
    }

    /**
     * This method logs out the user and goes to the login page.
     */
    function logout() {
        let loggedOut = false;
        makeCall("GET", 'logout', function (response) {
            if (response.readyState === XMLHttpRequest.DONE) {
                switch (response.status) {
                    case 200:
                        loggedOut = true;
                        localStorage.clear();
                        window.location.href = "login.html";
                        break;
                    default :
                        alert("Unknown Error");
                        break;
                }
            }
        });
        if (!loggedOut) {
            localStorage.clear();
            window.location.href = "login.html";
        }
    }
    
    
    //Da aggiungere codice di stampa e di gestione delle robe
    
    
    
    
    /**
     * This class is used for creating a new Folder
     * @param container the container element.
     */
    function CreateFolder(container) {
        const form = document.getElementById("createFolder");
        form.addEventListener("submit", function (e) {
            e.preventDefault();
            if (form.checkValidity()) {
                //make a request to the server to create the folder.
                makeCall("POST", 'CreateFolder', form, function (response) {
                    checkResponse(response);
                });
                form.reset();
            } else form.reportValidity();
        }, false);
        form.parentNode.removeChild(form);

        /**
         * Hides the container.
         */
        this.hide = function () {
            container.style.visibility = "hidden";
            if (container.contains(form))
                container.removeChild(form);
        }

        /**
         * This method sets the create folder form visible and the event on the submit button.
         */
        this.enableForm = function () {
            pageManager.hideContent();
            container.style.visibility = "visible";
            container.append(form);
        }
    }
    
    
    /**
     * This class is used for creating a new document.
     * @param container the container element.
     */
    function CreateDocument(container) {
        
        const title = document.getElementById("createDocumentTitle");
        const form = document.getElementById("createDocument");
        form.addEventListener("submit", function (e) {
            e.preventDefault();
            if (form.checkValidity()) {
                const formData = new FormData(form);
                //make a request to the server to create the document.
                makeCall("POST", 'CreateDocument', formData, function (response) {
                    checkResponse(response);
                });
                form.reset();
            } else form.reportValidity();
        }, false);
        form.parentNode.removeChild(form);

        /**
         * Hides the container.
         */
        this.hide = function () {
            container.style.visibility = "hidden";
            if (container.contains(form))
                container.removeChild(form);
        }

        /**
         * This method sets the create document form visible and the event on the submit button.
         */
        this.enableForm = function (folderID, folderName) {
            pageManager.hideContent();
            container.style.visibility = "visible";
            form.getElementsByClassName("hiddenInput")[0].value = folderID;
            title.textContent = "Create document inside folder: " + folderName;
            container.append(form);
        }
    }

    /**
     * This class is used for setting up the page and passing the right elements to the classes.
     */
    function PageManager() {
        /**
         * This method is called on refresh. Crates the classes by passing them the right elements.
         */
        this.start = function () {
            folderList = new FolderList(document.getElementById("folderList"));
            const rightContainer = document.getElementById("rightContainer");
            documentDetails = new ShowDocument({
                documentName: document.getElementById("documentName"),
                documentDate: document.getElementById("documentDate"),
                documentFormat: document.getElementById("documentFormat"),
                documentSummary: document.getElementById("documentSummary"),
                button: document.getElementById("hideDetails")
            });
            createFolder = new CreateFolder(rightContainer);
            createDocument = new CreateDocument(rightContainer);
            dragAndDropManager = new DragAndDropManager();
            this.hideContent();
        }

        /**
         * This method refreshes the page.
         */
        this.refresh = function () {
            folderList.show();
        }

        /**
         * This method hides all the content except the folder list.
         */
        this.hideContent = function () {
            documentDetails.hide();
            createFolder.hide();
            createDocument.hide();
        }
    }
}
    
    
