{

	let folderTree, documentInfo, createFolder, createDocument, dragAndDropHandler, versionHistoryHandler,
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


	/**
	 * This function calls the pageManager refresh and sets the logout button
	 */
	function start() {

		document.getElementById("Logout").addEventListener("click", function() {
			document.getElementById("Logout").disable = true;
			logout();
		});

		let globalPage = document.getElementById("globalPage");

		globalPage.addEventListener('dragover', function(event) {
			event.preventDefault();
		});

		/*globalPage.addEventListener('drop', function(event) {
			event.preventDefault();
		});*/

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
					case 403:
						alert("An other account is already logged in. Automatically log out...");
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
	 * This class handles data acquisition and dynamic printing of the folderTree
	 * @param container is a specific container
	 */

	function FolderTree(container) {

		this.container = container;
		this.editConfig = false;
		this.rootConfig = false;
		

		/**
		 * This method handles the datas acqusition of the folderTree
		 */
		this.show = function() {
			this.container.innerHTML = "";
			document.getElementById("wasteBin").style.visibility = "visible";
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


		/**
		 * This method changes the value of the EditButton and then show the edit buttons to adding new content.
		 */
		this.edit = function() {
			const self = this;
			let editButton = document.getElementById("EditButton");
			editButton.textContent = "UNDO";
			editButton.onclick = function() {
				self.undo();
				pageManager.hideContent();
			};

			let showDetails = document.getElementsByClassName("ShowDocumentInfo");
			for (const btnDetail of showDetails) {
				btnDetail.style.visibility = "hidden";
			}

			self.editConfig = true;

		}


		/**
		 * This method hides the edit buttons and sets the value of the EditButton to "EDIT".
		 */
		this.undo = function() {

			pageManager.hideContent();
			const self = this;
			let editButton = document.getElementById("EditButton");
			editButton.textContent = "EDIT";
			editButton.onclick = function() {
				self.edit();
			};

			let editButtons = document.getElementsByClassName("mngBtn");
			for (const editBtn of editButtons) {
				editBtn.style.visibility = "hidden";
			}

			self.editConfig = false;
		}



		/**
		 * This method handles the dynamic printing of the folderTree
		 */
		this.update = function(folderTree) {

			this.container.innerHTML = "";
			const self = this;


			//Get edit button and set up onclick event.
			let editButton = document.getElementById("EditButton");
			editButton.textContent = "EDIT";
			editButton.onclick = function() {
				self.edit();
			};

			let treeContainer = document.getElementById('treeContainer');
			
			//Ricursive Function to print the Folder Tree
			self.traverseTree(folderTree, treeContainer);

			//Set up the drag and drop
			dragAndDropHandler.setUp();
			//Set up Button Dynamic Visual
			self.undo();

			//Button to create Root Folders
			let rootButton = document.getElementById("RootButton");
			rootButton.textContent = "Create a Root Folder";
			rootButton.addEventListener("click", function() {

				self.rootConfig = true;
				let editButtons = document.getElementsByClassName("mngBtn");
				for (const editBtn of editButtons) {
					editBtn.style.visibility = "hidden";
				}

				let showDetails = document.getElementsByClassName("ShowDocumentInfo");
				for (const btnDetail of showDetails) {
					btnDetail.style.visibility = "visible";
					btnDetail.style.visibility = "hidden";
				}

				createFolder.enableForm(folderTree.folder.folderID, "HomePage");

				let editButton = document.getElementById("EditButton");
				editButton.onclick = null;
				editButton.style.visibility = "hidden";

			});

			self.rootConfig = false;
			editButton.style.visibility = "visible";

		}


		/**
		 * This recursive method is used by this.update for the dynamic printing of the folderTree
		 */
		this.traverseTree = function traverseTree(node, parentElement) {

			const self = this;

			if (node.folder.depth > 0) {

				let folderUL = document.createElement("ul");
				let folderLI = document.createElement("li");
				let folderDiv = document.createElement("div");
				

				folderUL.classList.add("folder-container");

				folderDiv.textContent = node.folder.folderName;
				folderDiv.classList.add("folder");
				folderDiv.setAttribute("folderID", node.folder.folderID);

				//creates new folder button.
				let folderButton = document.createElement("button");
				folderButton.className = "mngBtn";
				folderButton.textContent = "Create Folder";
				folderButton.addEventListener("click", function() {
					createFolder.enableForm(node.folder.folderID, node.folder.folderName);
				});

				//create new a document button.
				let docButton = document.createElement("button");
				docButton.className = "mngBtn";
				docButton.textContent = "Create Document";
				docButton.addEventListener("click", function() {
					createDocument.enableForm(node.folder.folderID, node.folder.folderName);
				});

				let internalContainer = document.createElement("div");
				internalContainer.append(folderDiv);
				internalContainer.append(folderButton);
				internalContainer.append(docButton);


				//It permits the dynamic visual of the mngButton when the editConfig is active
				internalContainer.addEventListener("mouseenter", function() {
					if (self.editConfig) {
						folderButton.style.visibility = "visible";
						docButton.style.visibility = "visible";
					}
				});
				
				//It permits the dynamic visual of the mngButton when the editConfig is active
				internalContainer.addEventListener("mouseleave", function() {
					if (self.editConfig) {
						folderButton.style.visibility = "hidden";
						docButton.style.visibility = "hidden";
					}
				});

				folderLI.append(internalContainer);

				folderUL.append(folderLI);
				parentElement.appendChild(folderUL);



				// Document print
				if (node.documentList && node.documentList.length > 0) {

					let documents = document.createElement('ul');

					node.documentList.forEach(function(doc) {

						//creates the li element that contains the document.
						let documentLi = document.createElement("li");
						let documentDiv = document.createElement("div");
						let documentIcon = document.createElement("img");

						//setup the document-icon parameters
						documentIcon.src = 'resources/images/doc.png';
						documentIcon.height = 25;
						documentIcon.style.float = 'left';
						documentIcon.style.width = 'auto';
						
						let docNameText = document.createTextNode(doc.documentName + "." + doc.documentType);

						//docElement.style.display = "inline";
						documentDiv.classList.add("document");
						let documentNameSpan = document.createElement("span");
						documentNameSpan.classList.add("doc-btn");
						
						documentNameSpan.appendChild(documentIcon);
						documentNameSpan.appendChild(docNameText);
						
						documentDiv.setAttribute("documentID", doc.documentID);
						documentDiv.setAttribute("folderID", doc.folderID);
						
						//It creates a new ShowDocumentInfo button
						let docInfo = document.createElement("button");
						docInfo.className = "ShowDocumentInfo";
						docInfo.textContent = "Show Document Info";
						docInfo.style.visibility = "hidden";


						let docAndButton = document.createElement("div");
						docAndButton.append(documentDiv);
						docAndButton.append(docInfo);

						documentDiv.appendChild(documentNameSpan);
						documentDiv.appendChild(docInfo);

						//Show details on click
						docInfo.addEventListener("click", function() {
							documentInfo.openDocument(doc.documentID);
						});

						//It permits the DocInfoButton Dynamic Visual when the editConfig is inactive
						docAndButton.addEventListener("mouseenter", function() {
							if (!self.editConfig && !self.rootConfig) {
								docInfo.style.visibility = "visible";
							}
						});

						//It permits the DocInfoButton Dynamic Visual when the editConfig is inactive
						docAndButton.addEventListener("mouseleave", function() {
							if (!self.editConfig && !self.rootConfig) {
								docInfo.style.visibility = "hidden";
							}
						});


						documentLi.append(docAndButton);
						documents.append(documentLi);

					});

					folderUL.appendChild(documents);
				}

				//Recursive call
				if (node.children && node.children.length > 0) {
					node.children.forEach(function(child) {
						self.traverseTree(child, folderUL);
					});
				}


			} else {

				//Recursive call for the printing of the root folders
				if (node.children && node.children.length > 0) {
					node.children.forEach(function(child) {
						self.traverseTree(child, parentElement);
					});
				}
			}
		}
	}


	/**
	 * This class handles Drag and Drop
	 */
	function DragAndDropHandler() {

		const self = this;

		/**
		 * This method sets up all the elements to be draggable or droppable
		 */
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
				
				const infoDocButton = self.startElement.querySelector('.ShowDocumentInfo');
				infoDocButton.style.visibility = "hidden";
				
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
		 * This method sets up the dragstart for a deletable element (usually a folder).
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
				
				//Request to delete the element
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
			//pageManager.refresh();
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

				element.classList.add("folder-btn");
				element.addEventListener("dragleave", function() {
					if (element.classList.contains("droppable")) {
						element.classList.remove("dragover");
					}
				});

				element.addEventListener("drop", function(e) {

					document.getElementById("wasteBin").style.visibility = "hidden";

					let folderID = e.target.getAttribute("folderID");

					if (self.startElement !== null && self.startElement !== undefined) {

						if (self.startElement.classList.contains('document')) {
							if (folderID !== self.startElement.getAttribute("folderID")) {
								let formData = new FormData();
								formData.append("folderID", folderID);
								formData.append("documentID", self.startElement.getAttribute("documentID"));
								
								//Send the move request to the server. If it's successful the folder list is refreshed.
								makeCall("POST", 'MoveDocument', formData, function(response) {
									checkResponse(response);
								});
								self.resetDroppable();

							} else {

								alert("You cannot move a document to the same folder it came from!");
								self.resetDroppable();
								//versionHistoryHandler.clear();
								//pageManager.refresh();

							}
							
						} else {

							alert("You can only move folders to the trash!");
							self.resetDroppable();
							//versionHistoryHandler.clear();
							//pageManager.refresh();

						}
					}
					
					document.getElementById("wasteBin").style.visibility = "visible";
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
		const title = document.getElementById("createFolderFormTitle");
		let destinationID;

		//It creates and sets the back button in the form
		let backButton = document.createElement("button");
		backButton.className = "BackButton";
		backButton.textContent = "Cancel";
		backButton.style.visibility = "visible";
		form.append(backButton);

		backButton.addEventListener("click", function() {
			pageManager.refresh();
		});

		form.addEventListener("submit", function(e) {
			e.preventDefault();
			if (form.checkValidity()) {
				const formData = new FormData(form);
				formData.append("destinationID", destinationID);
				
				//Make a request to the server to create the folder.
				makeCall("POST", 'CreateFolder', formData, function(response) {
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
		this.enableForm = function(folderID, folderName) {

			destinationID = folderID;
			pageManager.hideContent();
			versionHistoryHandler.clear();
			container.style.visibility = "visible";
			title.textContent = "Create subfolder inside " + folderName;
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
		let destinationID;

		//It creates and sets the back button in the form
		let backButton = document.createElement("button");
		backButton.className = "BackButton";
		backButton.textContent = "Cancel";
		backButton.style.visibility = "visible";
		form.append(backButton);

		backButton.addEventListener("click", function() {
			pageManager.refresh();
		});

		form.addEventListener("submit", function(e) {
			e.preventDefault();
			if (form.checkValidity()) {
				const formData = new FormData(form);
				formData.append("destinationID", destinationID);
				
				//Make a request to the server to create the document.
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

			destinationID = folderID;
			pageManager.hideContent();
			versionHistoryHandler.clear();
			container.style.visibility = "visible";		
			title.textContent = "Create document inside folder: " + folderName;
			container.append(form);
			
		}
	}


	/**
	 * This class is used to show the document details.
	 * @param options a list of container elements.
	 */
	function ShowDocument(options) {
		
		const documentDetails = document.getElementById("documentDetails");
		documentDetails.parentNode.removeChild(documentDetails);

		/**
		 * Hides the document details.
		 */
		this.hide = function() {
			document.getElementById("rightContainer").style.visibility = "hidden";
			if (document.getElementById("rightContainer").contains(documentDetails))
				document.getElementById("rightContainer").removeChild(documentDetails);
		};

		/**
		 * Shows the document details
		 * @param documentID the id of the document to show.
		 */
		this.openDocument = function(documentID) {
			let self = this;
			
			//Make a request to the server to get the document details.
			makeCall("GET", "GetDocument?documentID=" + documentID, null, function(response) {
				if (response.readyState === XMLHttpRequest.DONE) {
					let text = response.responseText;
					switch (response.status) {
						case 200:
							self.setDocumentDetails(JSON.parse(text));
							document.getElementById("rightContainer").append(documentDetails);
							break;
						case 401:
							alert("You are not logged in.")
							logout();
							break;
						case 403:
							alert("An other account is already logged in. Automatically log out...");
							sessionStorage.clear();
							window.location.href = "index.html";
							break;
						case 400:
						case 500:
							alert(text);
							break;
						default:
							alert("Unknown error");
							break;
					}
				}
			});

		}


		/**
		 * Sets up the container with the document details.
		 * @param doc the document to show.
		 */
		this.setDocumentDetails = function(doc) {

			versionHistoryHandler.clear();
			pageManager.hideContent();
			document.getElementById("rightContainer").style.visibility = "visible";
			options['documentName'].textContent = doc.documentName;
			options['documentFormat'].textContent = doc.documentType;
			options['documentSummary'].textContent = doc.summary;
			options['documentDate'].textContent = doc.creationDate;

			options['button'].onclick = function() {
				versionHistoryHandler.clear();
				versionHistoryHandler.getVersionHistory();
				document.getElementById("rightContainer").style.visibility = "hidden";
			};

		}
	}


	/**
	 * This class handles the Version Log 
	 */
	function ShowVersionHistory(container) {

		const self = this;

		/**
		 * Hides the Version History.
		 */
		this.clear = function() {

			document.getElementById("rightContainer").innerHTML = "";
			document.getElementById("rightContainer").style.visibility = "hidden";
		};

		/**
		 * Take the Version History details.
		 */
		this.getVersionHistory = function() {
			let self = this;
			
			//Make a request to the server to get the version history datas.
			makeCall("GET", "GetVersionHistory", null, function(response) {
				if (response.readyState === XMLHttpRequest.DONE) {
					let text = response.responseText;
					switch (response.status) {
						case 200:
							self.versionHistoryData = JSON.parse(text);
							self.setVersionHistoryData();
							break;
						case 401:
							alert("You are not logged in.")
							logout();
							break;
						case 403:
							alert("An other account is already logged in. Automatically log out...");
							sessionStorage.clear();
							window.location.href = "index.html";
							break;
						case 400:
						case 500:
							alert(text);
							break;
						default:
							alert("Unknown error");
							break;
					}
				}
			});
		}


		/**
		 * This method handles the printing of the VersionHistoryData
		 */
		this.setVersionHistoryData = function() {

			pageManager.hideContent();
			const rightContainer = document.getElementById("rightContainer");
			rightContainer.style.visibility = "visible";

			let titleSpaceDiv = document.createElement("div");
				titleSpaceDiv.style.marginTop = "20px";
				rightContainer.append(titleSpaceDiv);
				
			let title = document.createElement("h2");
			title.textContent = "History Log";
			title.style.visibility = "visible";

			rightContainer.append(title);
		
			let spaceDiv = document.createElement("div");
				spaceDiv.style.marginTop = "20px";
				rightContainer.append(spaceDiv);
			
			let datasUl = document.createElement('ul');
			
			self.versionHistoryData.forEach(function(element) {

				let elementDiv = document.createElement("div");
				let elementLI = document.createElement("li");
				let divContainer = document.createElement("div");

				elementDiv.classList.add("historyDatas");
				elementDiv.textContent = element;
				elementDiv.style.visibility = "visible";
				divContainer.append(elementDiv);

				elementLI.append(divContainer);
				datasUl.append(elementLI);

			});

			rightContainer.append(datasUl);

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
			documentInfo = new ShowDocument({
				documentName: document.getElementById("documentName"),
				documentDate: document.getElementById("documentDate"),
				documentFormat: document.getElementById("documentFormat"),
				documentSummary: document.getElementById("documentSummary"),
				button: document.getElementById("hideDetails")
			});
			createFolder = new CreateFolder(rightContainer);
			createDocument = new CreateDocument(rightContainer);
			dragAndDropHandler = new DragAndDropHandler();
			versionHistoryHandler = new ShowVersionHistory(rightContainer);
			this.hideContent();
		}

		/**
		 * This method refreshes the page.
		 */
		this.refresh = function() {
			folderTree.show();
			versionHistoryHandler.clear();
			versionHistoryHandler.getVersionHistory();
		}

		/**
		 * This method hides all the content except the folder list.
		 */
		this.hideContent = function() {
			documentInfo.hide();
			createFolder.hide();
			createDocument.hide();
		}
	}

	
	/**
	 * This function checks the different responses that could come from the server side. If is okey it calls pageManager.refresh()
	 */
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
					alert("You are not logged in.");
					logout();
					break;
				case 403:
					alert("An other account is already logged in. Automatically log out...");
					sessionStorage.clear();
					window.location.href = "index.html";
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