{

	let folderTree, documentInfo, createFolder, createDocument, dragAndDropHandler,
		pageManager = new PageManager();
	/**
	 * This starts the page if the user is logged in.
	 */
	window.addEventListener('load', function() {
		pageManager.start();
		if (sessionStorage.getItem("utente") === null) {
			logout()
		} else {
			start();
		}
	}, false);

	function start() {

		//document.getElementById("userName").textContent = JSON.parse(sessionStorage.getItem("utente"));
		document.getElementById("Logout").addEventListener("click", function() {
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
		makeCall("GET", 'Logout', null, function(response) {

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

		this.show = function() {
			this.container.innerHTML = "";
			const self = this;
			makeCall("GET", "GetTree", null,
				function(req) {
					if (req.readyState === 4) {

						let message = req.responseText;
						let error = document.getElementById("treeError");

						if (req.status === 200) {
							let folderTree = JSON.parse(req.responseText);

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

		this.update = function(folderTree) {

			this.container.innerHTML = "";
			const self = this;

			let treeContainer = document.getElementById('treeContainer');
			//Ricursive Function to print the Folder Tree
			self.traverseTree(folderTree, treeContainer);

			//Set up the drag and drop
			dragAndDropHandler.setUp();
		}


		this.traverseTree = function traverseTree(node, parentElement) {

			const self = this;

			let nodeElement = document.createElement('div');
			if (node.folder.depth > 0) {
				nodeElement.textContent = node.folder.folderName;
				nodeElement.classList.add("folder");
			}
			parentElement.appendChild(nodeElement);

			// Document print
			if (node.documentList && node.documentList.length > 0) {

				let documents = document.createElement('ul');

				node.documentList.forEach(function(doc) {

					//create the li element that contains the document.
					let documentLi = document.createElement("li");
					let documentDiv = document.createElement("div");

					//docElement.style.display = "inline";
					documentDiv.classList.add("document");
					documentDiv.textContent = doc.documentName + "." + doc.documentType;
					documentDiv.setAttribute("documentID", doc.documentID);
					documentDiv.setAttribute("folderID", doc.folderID);
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

					//documentLi.append(docInfo);
					documents.append(documentLi);

				});

				nodeElement.appendChild(documents);
			}

			if (node.children && node.children.length > 0) {
				let folderUl = document.createElement('ul');
				folderUl.className = 'folder';
				nodeElement.appendChild(folderUl);
				node.children.forEach(function(child) {
					self.traverseTree(child, folderUl);
				});
			}
		}
	}


	/**
	 * 
	 */
	function DragAndDropHandler() {

		const self = this;

		this.setUp = function() {
			let objList = document.getElementsByClassName("document");

			for (let doc of objList) {
				self.setMove(doc);
				doc.setAttribute('draggable', "true");
			}

			objList = document.getElementsByClassName("folder");
			for (let folder of objList) {
				self.setDelete(folder);
				self.setMove(folder);
				folder.setAttribute('draggable', "true");
				folder.classList.add("droppable");
			}

			let wasteBin = document.getElementById("wasteBin");

			wasteBin.classList.add("droppable");

			self.setDrop();
			self.setWasteBin();
		}

		/**
		 * This method sets up the dragstart for a movable element (usually a document).
		 * @param element the element we want to assign the dragstart event to.
		 */
		this.setMove = function(element) {
			element.addEventListener("dragstart", function(e) {
				e.target.classList.add("dragging");
				self.startElement = e.target;
				if (self.findNotDroppable(e.target)) {
					self.notDroppable.classList.add("not-droppable");
				}
			});
			element.addEventListener("dragend", function(e) {
				e.target.classList.remove("dragging");
				self.resetDroppable();
			});
		}

		/**
		 * This method sets up the dragstart for a deletable element (usually a folder or subfolder).
		 * @param element the element we want to assign the dragstart event to.
		 */
		this.setDelete = function(element) {
			element.addEventListener("dragstart", function(e) {
				e.target.classList.add("dragging");
				self.startElement = e.target;
				let wasteBin = document.getElementById("wasteBin");
				wasteBin.classList.add("droppable");
			});
			element.addEventListener("dragend", function(e) {
				e.target.classList.remove("dragging");
				self.resetDroppable();
			});
		}


		/**
		* Reset the droppable elements and the notDroppable element.
		*/
		this.resetDroppable = function() {

			let elements = Array.from(document.getElementsByClassName("not-droppable"));
			for (const elem of elements) {
				elem.classList.remove("not-droppable");
			}

			elements = Array.from(document.getElementsByClassName("droppable"));
			for (const element of elements) {
				element.classList.remove("droppable");
			}
			
			const wasteBin = document.getElementById("wasteBin");
			wasteBin.removeEventListener("drop", self.deletionFunction);
			//self.setWasteBin();

			self.notDroppable = null;
			self.startElement = null;
		}


		/**
		* Finds the element that can't be a drop target cause is the subfolder of the startElement.
		* @param startElement the document element who has been dragged.
		*/
		this.findNotDroppable = function(startElement) {
			let elements = document.getElementsByClassName("folder");

			for (const element of elements) {
				if (element.getAttribute("folderID") === startElement.getAttribute("folderID")) {
					self.notDroppable = element;
					return true;
				}
			}
			return false;
		}


		/**
		 * This method sets up the dragover, dragleave and drop events for the trash can element.
		 * When an element is dragged over the trash can it can be deleted.
		 */
		this.setWasteBin = function() {

			const wasteBin = document.getElementById("wasteBin");
			wasteBin.addEventListener("dragover", function(e) {
				e.preventDefault();
				wasteBin.classList.add("dragover");
			});

			wasteBin.addEventListener("dragleave", function() {
				wasteBin.classList.remove("dragover");
			});

			wasteBin.addEventListener("drop", self.deletionFunction);
		}
		
		
		/**
		 * This function permits to delete the document and the folder that are dropped in the Waste Bin
		 */
		this.deletionFunction = function() {

				let decision = confirm("Are you sure you want to delete this item?");
				if (decision) {
					//request to delete the element
					//For the request we have to find the proper servlet
					//If the request is successful the folder list has to be refreshed

					if (self.startElement.classList.contains("document")) {
						let formData = new FormData();

						formData.append('documentID', self.startElement.getAttribute("documentID"));
						makeCall("POST", 'DeleteDocument', formData, function(response) {
							checkResponse(response);
						});
					} else if (self.startElement.classList.contains("folder")) {
						let formData = new FormData();
						formData.append("folderID", self.startElement.getAttribute("folderID"));
						makeCall("POST", 'DeleteFolder', formData, function(response) {
							checkResponse(response);
						});
					}
				}
				self.resetDroppable();
			};


		/**
		 * This method sets the dragover, dragleave and drop for each droppable element.
		 * The droppable elements are the subfolders.
		 */
		this.setDrop = function() {
			let elements = document.getElementsByClassName("folder");

			for (const element of elements) {
				element.addEventListener("dragover", function(e) {
					if (element.classList.contains("droppable")) {
						e.preventDefault();
						element.classList.add("dragover");
					}
				});

				element.addEventListener("dragleave", function() {
					if (element.classList.contains("droppable")) {
						element.classList.remove("dragover");
					}
				});

				element.addEventListener("drop", function(e) {

					let folderID = e.target.getAttribute("folderID");
					if (folderID !== self.startElement.getAttribute("folderID")) {
						let formData = new FormData();
						formData.append("folderID", folderID);
						formData.append("documentID", self.startElement.getAttribute("documentID"));
						//send the move request to the server. If it's successful the folder list is refreshed.
						makeCall("POST", 'MoveDocument', formData, function(response) {
							checkResponse(response);
						});
					}
					self.resetDroppable();
					
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
		form.addEventListener("submit", function(e) {
			e.preventDefault();
			if (form.checkValidity()) {
				//make a request to the server to create the folder.
				makeCall("POST", 'CreateFolder', form, function(response) {
					checkResponse(response);
				});
				form.reset();
			} else form.reportValidity();
		}, false);
		form.parentNode.removeChild(form);

		/**
		 * Hides the container.
		 */
		this.hide = function() {
			container.style.visibility = "hidden";
			if (container.contains(form))
				container.removeChild(form);
		}

		/**
		 * This method sets the create folder form visible and the event on the submit button.
		 */
		this.enableForm = function() {
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
		form.addEventListener("submit", function(e) {
			e.preventDefault();
			if (form.checkValidity()) {
				const formData = new FormData(form);
				//make a request to the server to create the document.
				makeCall("POST", 'CreateDocument', formData, function(response) {
					checkResponse(response);
				});
				form.reset();
			} else form.reportValidity();
		}, false);
		form.parentNode.removeChild(form);

		/**
		 * Hides the container.
		 */
		this.hide = function() {
			container.style.visibility = "hidden";
			if (container.contains(form))
				container.removeChild(form);
		}

		/**
		 * This method sets the create document form visible and the event on the submit button.
		 */
		this.enableForm = function(folderID, folderName) {
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
		this.start = function() {
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
			dragAndDropHandler = new DragAndDropHandler();
			this.hideContent();
		}

		/**
		 * This method refreshes the page.
		 */
		this.refresh = function() {
			folderTree.show();
		}

		/**
		 * This method hides all the content except the folder list.
		 */
		this.hideContent = function() {
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