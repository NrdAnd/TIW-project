{

    let folderTree, documentInfo, createFolder, createDocument, dragAndDropManager,
        pageManager = new PageManager();
    /**
     * This starts the page if the user is logged in.
     */
    window.addEventListener('load', function () {
        pageManager.start();
        if (sessionStorage.getItem("utente") === null) {
            logout()
        } else {
            start();
        }
    }, false);

    function start() {
		
        //document.getElementById("userName").textContent = JSON.parse(sessionStorage.getItem("utente"));
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
        makeCall("GET", 'Logout', null, function (response) {
			
			console.log(response);
            if (response.readyState === XMLHttpRequest.DONE) {
                switch (response.status) {
                    case 200:
                        loggedOut = true;
                        sessionStorage.clear();
                        window.location.href = "index.html";
                        break;
                    default:
                        alert("Unknown Error");
                        break;
                }
            }
        });
        
        if (!loggedOut) {
            sessionStorage.clear();
            window.location.href = "index.html";
        }
    }


    /**
     * This method permits the dynamic print of the Folder Tree
     * @param {*} container is the container
     */

    function FolderTree(container) {

        this.container = container;

        this.show = function () {
            this.container.innerHTML = "";
            const self = this;
            makeCall("GET", "GetTree", null,
                function (req) {
                    if (req.readyState === 4) {
						
                        let message = req.responseText;
                        let error = document.getElementById("treeError");
                        console.log(sessionStorage.getItem("utente"));
                        console.log(req.status);

                        if (req.status === 200) {
                            let folderTree = JSON.parse(req.responseText);
                            console.log(folderTree);

                            if (!folderTree) {
                                error.textContent = "Nessuna Folder presente!";
                                error.classList.add("alert", "alert-danger");
                                return;
                            }

                            self.update(folderTree); // self visible by closure
                        } else if (req.status === 403) {
                            window.location.href = req.getResponseHeader("Location");
                            window.sessionStorage.removeItem('utente');
                        } else {
                            error.textContent = message;
                        }
                    }
                }
            )
        }

        this.update = function (folderTree) {

            this.container.innerHTML = "";
            const self = this;

            let treeContainer = document.getElementById('treeContainer');
            //Ricursive Function to print the Folder Tree
            self.traverseTree(folderTree, treeContainer);

            //Set up the drag and drop
            dragAndDropManager.setupDragAndDrop();
        }
        
        
        this.traverseTree = function traverseTree(node, parentElement) {
			
			const self = this;

            let nodeElement = document.createElement('div');
            if (node.folder.depth > 0) {
                nodeElement.textContent = node.folder.folderName;
            }
            parentElement.appendChild(nodeElement);

            // Document print
            if (node.documentList && node.documentList.length > 0) {

                let documents = document.createElement('ul');

                node.documentList.forEach(function (doc) {

                    //create the li element that contains the document.
                    let documentLi = document.createElement("li");
                    let documentDiv = document.createElement("div");

                    //docElement.style.display = "inline";
                    documentDiv.classList.add("document");
                    documentDiv.textContent = doc.documentName + "." + doc.documentType;
                    documentDiv.setAttribute("documentId", doc.documentID);
                    documentDiv.setAttribute("subfolderId", doc.folderID);
                    documentLi.append(documentDiv);

                    /*
                    //let docInfo = document.createElement("button");
                    docInfo.className = "ShowDocumentInfo";
                    docInfo.textContent = "Show Document Info";

                    //show details on click
                    docInfo.addEventListener("click", function () {
                        documentInfo.openDocument(doc.documentID);
                    });
					
					*/
					
                    documentLi.append(docInfo);
                    documents.append(documentLi);

                });

                nodeElement.appendChild(documents);
            }

            if (node.children && node.children.length > 0) {
                let folderUl = document.createElement('ul');
                folderUl.className = 'treeNode';
                nodeElement.appendChild(folderUl);
                node.children.forEach(function (child) {
                    self.traverseTree(child, folderUl);
                });
            }
        }
    }




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
            folderTree = new FolderTree(document.getElementById("treeContainer"));
            const rightContainer = document.getElementById("rightContainer");
            /*documentInfo = new ShowDocument({
                documentName: document.getElementById("documentName"),
                documentDate: document.getElementById("documentDate"),
                documentFormat: document.getElementById("documentFormat"),
                documentSummary: document.getElementById("documentSummary"),
                button: document.getElementById("hideDetails")
            });*/
            createFolder = new CreateFolder(rightContainer);
            createDocument = new CreateDocument(rightContainer);
            //dragAndDropManager = new DragAndDropManager();
            this.hideContent();
        }

        /**
         * This method refreshes the page.
         */
        this.refresh = function () {
            folderTree.show();
        }

        /**
         * This method hides all the content except the folder list.
         */
        this.hideContent = function () {
            //documentInfo.hide();
            createFolder.hide();
            createDocument.hide();
        }
    }


    function checkResponse(response) {
        if (response.readyState === XMLHttpRequest.DONE) {
            let text = response.responseText;
            switch (response.status) {
                case 200:
                    pageManager.refresh();
                    break;
                case 400:
                    alert(text);
                    break;
                case 401:
                    alert("You are not logged in.")
                    logout();
                    break;
                case 403:
                    alert("No response from the Server.")
                    break;
                case 500:
                    alert(text);
                    break;
                default:
                    alert("Unknown error");
            }
        }
    }
}


