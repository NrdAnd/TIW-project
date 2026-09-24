/* Server-authorised workspace. Uploaded bytes and cumulative undo are transactional. */
(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const folders = new Map(),
    documents = new Map();
  let tree = null,
    history = [],
    workspaceRevision = "0",
    csrfToken = "",
    editing = false,
    dragged = null,
    busy = false,
    panelRevision = 0,
    ready = false,
    loading = false,
    historyError = "";
  let limits = {
    maxFileSize: 25 * 1024 * 1024,
    maxRequestSize: 100 * 1024 * 1024,
    storageLimit: 250 * 1024 * 1024,
  };
  const fileName = (doc) =>
    doc.fileName ||
    doc.documentName + (doc.documentType ? "." + doc.documentType : "");
  const bytes = (value) =>
    value < 1024
      ? value + " B"
      : value < 1024 * 1024
        ? (value / 1024).toFixed(1) + " KB"
        : (value / (1024 * 1024)).toFixed(1) + " MB";
  function node(tag, className, text) {
    const e = document.createElement(tag);
    if (className) e.className = className;
    if (text !== undefined) e.textContent = text;
    return e;
  }
  function button(text, className, action) {
    const e = node("button", className, text);
    e.type = "button";
    e.addEventListener("click", action);
    return e;
  }
  function formData(values) {
    const data = new FormData();
    Object.entries(values).forEach(([key, value]) => data.append(key, value));
    return data;
  }
  function announce(text, error = false) {
    const e = $("statusMessage");
    e.textContent = text;
    e.className = error ? "alert" : "status-message";
    e.hidden = false;
  }
  function request(method, endpoint, data = null) {
    return new Promise((resolve, reject) => {
      const uploading = endpoint === "UploadFiles";
      makeCall(
        method,
        endpoint,
        data,
        (response) => {
          if (response.readyState !== XMLHttpRequest.DONE) return;
          if (
            response.status === 401 ||
            (response.status === 403 && response.getResponseHeader("Location"))
          ) {
            sessionStorage.removeItem("utente");
            window.location.href = "index.html";
          }
          if (response.status === 200) resolve(response.responseText);
          else {
            const error = new Error(requestErrorMessage(response));
            error.status = response.status;
            reject(error);
          }
        },
        true,
        {
          headers: method === "POST" ? { "X-CSRF-Token": csrfToken } : {},
          timeout: uploading ? 300000 : 20000,
          onProgress: uploading
            ? (event) => {
                if (event.lengthComputable) {
                  const percentage = Math.round(
                    (event.loaded / event.total) * 100,
                  );
                  $("uploadProgressBar").value = percentage;
                  $("uploadProgressLabel").textContent =
                    percentage === 100
                      ? "Saving your files…"
                      : "Uploading… " + percentage + "%";
                }
              }
            : null,
        },
      );
    });
  }
  function updateControls() {
    const unavailable = !ready || loading || busy;
    ["RootButton", "UploadButton", "EditButton", "searchInput"].forEach(
      (id) => ($(id).disabled = unavailable),
    );
    $("uploadDropzone").setAttribute("aria-disabled", String(unavailable));
    $("RetryButton").disabled = loading || busy;
    document.querySelectorAll(".undo-button").forEach((control) => {
      const entry = history.find((entry) =>
        entry.id === control.closest(".activity-item").dataset.actionId);
      control.disabled = unavailable || !entry?.undoable;
    });
  }
  function validateTree(value) {
    const ids = new Set();
    function visit(branch, depth = 0) {
      if (!branch || !branch.folder || depth > 40 ||
          !Number.isInteger(branch.folder.folderID) || branch.folder.folderID <= 0 ||
          ids.has(branch.folder.folderID) || typeof branch.folder.folderName !== "string" ||
          !Number.isInteger(branch.folder.depth) ||
          !Array.isArray(branch.children) || !Array.isArray(branch.documentList) ||
          branch.documentList.some((doc) => !doc || !Number.isInteger(doc.documentID) ||
            typeof doc.documentName !== "string" || typeof doc.documentType !== "string"))
        throw new Error("The server returned an invalid folder tree. Please retry.");
      ids.add(branch.folder.folderID);
      branch.children.forEach((child) => visit(child, depth + 1));
    }
    visit(value);
    return value;
  }
  function validateHistory(value) {
    if (!value || !Array.isArray(value.actions) ||
        typeof value.revision !== "string" || !/^\d+$/.test(value.revision) ||
        !Number.isFinite(value.storageUsed) || !Number.isFinite(value.storageLimit) ||
        value.actions.some((entry) => !entry || typeof entry.id !== "string" ||
          typeof entry.description !== "string" || typeof entry.undoable !== "boolean" ||
          !Number.isInteger(entry.undoCount)))
      throw new Error("The server returned invalid activity data. Please retry.");
    return value;
  }
  async function loadSession() {
    const config = parseJsonResponse(await request("GET", "SessionToken"));
    if (!config || typeof config.csrfToken !== "string" || !config.csrfToken ||
        typeof config.user !== "string" || !config.user.trim() ||
        !Number.isFinite(config.maxFileSize) || !Number.isFinite(config.maxRequestSize) ||
        !Number.isFinite(config.storageLimit))
      throw new Error("The server returned invalid session data. Please retry.");
    csrfToken = config.csrfToken;
    limits = config;
    // The cookie-backed server session works in every tab. Storage is only a display cache.
    sessionStorage.setItem("utente", config.user);
    $("userLabel").textContent = config.user;
    $("userInitial").textContent = config.user.trim().charAt(0).toUpperCase() || "U";
  }
  async function bootstrap() {
    if (loading || busy) return;
    loading = true;
    ready = false;
    $("treeContainer").setAttribute("aria-busy", "true");
    $("RetryButton").hidden = true;
    updateControls();
    try {
      await loadSession();
      await refresh();
    } catch (error) {
      announce(error.message, true);
      $("rightContainer").replaceChildren(node("p", "alert", error.message));
      $("RetryButton").hidden = false;
    } finally {
      loading = false;
      $("treeContainer").setAttribute("aria-busy", "false");
      updateControls();
    }
  }
  async function refresh() {
    $("treeContainer").setAttribute("aria-busy", "true");
    $("treeError").hidden = true;
    const revision = panelRevision;
    try {
      const results = await Promise.allSettled([
        request("GET", "GetTree").then(parseJsonResponse).then(validateTree),
        request("GET", "GetVersionHistory").then(parseJsonResponse).then(validateHistory),
      ]);
      ready = results[0].status === "fulfilled";
      folders.clear();
      documents.clear();
      if (ready) {
        tree = results[0].value;
        (function collect(branch) {
          folders.set(branch.folder.folderID, branch.folder);
          branch.documentList.forEach((doc) => documents.set(doc.documentID, doc));
          branch.children.forEach(collect);
        })(tree);
        $("folderCount").textContent = [...folders.values()].filter((f) => f.depth > 0).length;
        $("documentCount").textContent = documents.size;
        renderTree();
      } else {
        tree = null;
        $("treeContainer").replaceChildren();
        $("emptyState").hidden = true;
        $("folderCount").textContent = "—";
        $("documentCount").textContent = "—";
        $("treeError").textContent = results[0].reason.message;
        $("treeError").hidden = false;
      }
      historyError = results[1].status === "rejected" ? results[1].reason.message : "";
      if (!historyError) {
        const data = results[1].value;
        history = data.actions;
        workspaceRevision = data.revision;
        $("storageUsed").textContent = bytes(data.storageUsed) + " / " + bytes(data.storageLimit) + " · includes undo";
      } else {
        history = [];
        $("storageUsed").textContent = "Storage unavailable";
      }
      if (panelRevision === revision) showActivity();
      $("RetryButton").hidden = ready && !historyError;
      if (ready && !historyError) $("statusMessage").hidden = true;
      return ready && !historyError;
    } finally {
      $("treeContainer").setAttribute("aria-busy", "false");
      updateControls();
    }
  }
  function setEdit(value) {
    editing = value;
    $("EditButton").setAttribute("aria-pressed", String(value));
    $("EditButton").textContent = value ? "Done editing" : "Edit workspace";
    $("modeHint").textContent = value
      ? "Create, rename or move folders. Deleted items can be restored from Session activity."
      : "Drag files or folders to move them. Drop files from your computer onto a folder to upload.";
    renderTree();
  }
  function pathFor(folder) {
    const parts = [folder.folderName];
    let parent = folders.get(folder.parentFolderID);
    while (parent && parent.depth > 0) {
      parts.unshift(parent.folderName);
      parent = folders.get(parent.parentFolderID);
    }
    return folder.depth === 0 ? "Workspace root" : parts.join(" / ");
  }
  function externalDrag(event) {
    return [...(event.dataTransfer?.types || [])].includes("Files");
  }
  function renderTree() {
    if (!tree) return;
    const query = $("searchInput").value.trim().toLowerCase(),
      list = node("ul", "folder-tree");
    function renderFolder(branch, inherited = false) {
      const folder = branch.folder,
        matching = inherited || folder.folderName.toLowerCase().includes(query);
      const children = (branch.children || [])
        .map((child) => renderFolder(child, matching))
        .filter(Boolean);
      const docs = (branch.documentList || []).filter(
        (doc) => matching || fileName(doc).toLowerCase().includes(query),
      );
      if (query && !matching && !docs.length && !children.length) return null;
      const item = node("li", "folder-card"),
        header = node("div", "folder-header");
      header.dataset.folderId = folder.folderID;
      header.append(
        node("span", "folder-icon folder-symbol"),
        node("span", "folder-name", folder.folderName),
      );
      header.firstChild.setAttribute("aria-hidden", "true");
      const count = (branch.documentList || []).length;
      header.append(
        node("span", "folder-meta", count + (count === 1 ? " file" : " files")),
      );
      makeDraggable(header, { type: "folder", id: folder.folderID });
      header.addEventListener("dragover", (event) => {
        if (dragged || externalDrag(event)) {
          event.preventDefault();
          event.stopPropagation();
          event.dataTransfer.dropEffect = externalDrag(event) ? "copy" : "move";
          header.classList.add("dragover");
        }
      });
      header.addEventListener("dragleave", () =>
        header.classList.remove("dragover"),
      );
      header.addEventListener("drop", (event) => {
        event.preventDefault();
        event.stopPropagation();
        header.classList.remove("dragover");
        if (event.dataTransfer.files.length)
          uploadFiles([...event.dataTransfer.files], folder.folderID);
        else if (dragged) moveItem(dragged, folder.folderID);
        dragged = null;
      });
      item.append(header);
      if (editing) {
        const actions = node("div", "folder-actions");
        actions.append(
          button("+ Folder", "button secondary", () => showFolderForm(folder)),
          button("↑ Upload", "button secondary", () =>
            showUpload(folder.folderID),
          ),
          button("Rename", "button secondary", () =>
            showRename({ type: "folder", id: folder.folderID }),
          ),
          button("Move", "button secondary", () =>
            showMove({ type: "folder", id: folder.folderID }),
          ),
          button("Delete", "button danger", () =>
            deleteItem({ type: "folder", id: folder.folderID }),
          ),
        );
        item.append(actions);
      }
      const docList = node("ul", "document-list");
      docs.forEach((doc) => {
        const row = node("li", "document-row");
        row.dataset.documentId = doc.documentID;
        const badge = node(
          "span",
          "file-badge",
          (doc.documentType || "FILE").slice(0, 5),
        );
        badge.setAttribute("aria-hidden", "true");
        if (doc.canPreview) badge.classList.add("image-badge");
        const open = button(fileName(doc), "document-open", () =>
          showDocument(doc.documentID),
        );
        open.append(
          node(
            "span",
            "document-format",
            doc.hasFile ? bytes(doc.size) : "Legacy metadata",
          ),
        );
        const details = button("Details ↗", "text-button", () =>
          showDocument(doc.documentID),
        );
        details.setAttribute("aria-label", "Details for " + fileName(doc));
        row.append(badge, open, details);
        makeDraggable(row, { type: "document", id: doc.documentID });
        docList.append(row);
      });
      item.append(docList);
      if (!docs.length && !children.length && !query) {
        const empty = node("div", "folder-empty");
        empty.append(
          node("span", "", "Drop your first files here, or "),
          button("choose files", "text-button", () =>
            showUpload(folder.folderID),
          ),
        );
        item.append(empty);
      }
      if (children.length) {
        const nested = node("ul", "folder-tree");
        nested.append(...children);
        item.append(nested);
      }
      return item;
    }
    (tree.children || []).forEach((branch) => {
      const item = renderFolder(branch);
      if (item) list.append(item);
    });
    $("treeContainer").replaceChildren(list);
    $("emptyState").hidden = !!tree.children?.length;
    $("searchEmpty").hidden =
      !query || !!list.children.length || !tree.children?.length;
  }
  function makeDraggable(element, item) {
    element.draggable = true;
    element.addEventListener("dragstart", (event) => {
      if (busy) {
        event.preventDefault();
        return;
      }
      event.stopPropagation();
      dragged = item;
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", String(item.id));
      element.classList.add("dragging");
    });
    element.addEventListener("dragend", () => {
      dragged = null;
      element.classList.remove("dragging");
      document
        .querySelectorAll(".dragover")
        .forEach((e) => e.classList.remove("dragover"));
    });
  }
  function showActivity() {
    panelRevision++;
    const panel = $("rightContainer");
    panel.replaceChildren(
      node("span", "eyebrow", "EVERY STEP, REVERSIBLE"),
      node("h2", "", "Session activity"),
      node(
        "p",
        "inspector-subtitle",
        "Undo an action and everything after it. Available until your session ends.",
      ),
    );
    if (historyError) {
      panel.append(node("p", "alert", historyError));
      return;
    }
    if (!history.length) {
      const empty = node("p", "activity-empty");
      empty.append(
        node("strong", "", "A fresh session."),
        document.createTextNode(
          "Upload, move or organise your files. You can retrace your steps here.",
        ),
      );
      panel.append(empty);
      return;
    }
    const list = node("ol", "activity-list");
    history.forEach((entry, index) => {
      const item = node("li", "activity-item");
      item.dataset.actionId = entry.id;
      item.append(
        node(
          "span",
          "activity-kind",
          index === 0 ? "MOST RECENT" : "EARLIER THIS SESSION",
        ),
        node("p", "activity-description", entry.description),
      );
      const undo = button(
        entry.undoCount > 1
          ? "↶ Undo this + " + (entry.undoCount - 1) + " newer"
          : "↶ Undo this action",
        "undo-button",
        () => undoAction(entry),
      );
      undo.disabled = !entry.undoable || busy || !ready || loading;
      undo.setAttribute(
        "aria-label",
        "Undo " +
          entry.description +
          (entry.undoCount > 1
            ? " and " + (entry.undoCount - 1) + " newer actions"
            : ""),
      );
      if (!entry.undoable)
        undo.title =
          "Another session changed the workspace, or undo data expired.";
      item.append(undo);
      if (!entry.undoable)
        item.append(
          node(
            "span",
            "field-help",
            "Undo unavailable after another session or expiry.",
          ),
        );
      list.append(item);
    });
    panel.append(list);
  }
  function undoAction(entry) {
    if (busy || !ready || loading || !entry.undoable) return;
    const text =
      entry.undoCount === 1
        ? "Undo “" + entry.description + "”?"
        : "Undo “" +
          entry.description +
          "” and all " +
          (entry.undoCount - 1) +
          " newer actions?";
    if (
      !confirm(
        text +
          " Files, names and folder locations will return to their earlier state. There is no redo.",
      )
    )
      return;
    return mutate(
      "UndoActions",
      formData({ actionID: entry.id, revision: workspaceRevision }),
      entry.undoCount +
        " action" +
        (entry.undoCount === 1 ? "" : "s") +
        " undone.",
    );
  }
  function focusPanel() {
    const heading = $("rightContainer").querySelector("h2");
    if (heading) {
      heading.tabIndex = -1;
      heading.focus({ preventScroll: true });
    }
    if (matchMedia("(max-width: 960px)").matches)
      $("rightContainer").scrollIntoView({ behavior: "auto", block: "start" });
  }
  function showFolderForm(destination) {
    panelRevision++;
    const panel = $("rightContainer");
    panel.replaceChildren($("folderFormTemplate").content.cloneNode(true));
    panel.querySelector(".form-destination").textContent =
      "Inside " + pathFor(destination);
    panel
      .querySelector(".cancel-button")
      .addEventListener("click", showActivity);
    const form = panel.querySelector("form");
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (form.reportValidity())
        mutate(
          "CreateFolder",
          formData({
            newFolderName: $("folderName").value,
            destinationID: destination.folderID,
          }),
          "Folder created.",
        );
    });
    focusPanel();
    $("folderName").focus({ preventScroll: true });
  }
  function showUpload(destination = null, selected = []) {
    if (busy || loading || !ready) return;
    panelRevision++;
    const panel = $("rightContainer");
    panel.replaceChildren($("uploadFormTemplate").content.cloneNode(true));
    const select = $("uploadDestination");
    [...folders.values()]
      .filter((f) => f.depth > 0)
      .forEach((folder) => {
        const option = node("option", "", pathFor(folder));
        option.value = folder.folderID;
        select.append(option);
      });
    if (destination !== null) select.value = String(destination);
    if (!select.options.length) {
      panel.replaceChildren(
        node("h2", "", "Create your first folder"),
        node(
          "p",
          "muted form-destination",
          "Files live inside folders. Create a root folder, then upload your files.",
        ),
        button("Create root folder", "button primary", () =>
          showFolderForm(tree.folder),
        ),
      );
      focusPanel();
      return;
    }
    let files = selected;
    const showSelection = () => {
      const list = $("uploadSelection");
      list.replaceChildren();
      files.forEach((file) =>
        list.append(node("li", "", file.name + " · " + bytes(file.size))),
      );
    };
    showSelection();
    $("fileInput").addEventListener("change", (event) => {
      files = [...event.target.files];
      showSelection();
    });
    panel
      .querySelector(".cancel-button")
      .addEventListener("click", showActivity);
    $("uploadForm").addEventListener("submit", (event) => {
      event.preventDefault();
      if (event.target.reportValidity())
        uploadFiles(files, Number(select.value));
    });
    focusPanel();
  }
  async function uploadFiles(files, destination) {
    if (busy || loading || !ready) return;
    if (!files.length || files.length > 20) {
      announce("Choose between 1 and 20 files.", true);
      return;
    }
    if (
      files.some((file) => file.size > limits.maxFileSize) ||
      files.reduce((total, file) => total + file.size, 0) >
        limits.maxRequestSize
    ) {
      announce(
        "Use files up to 25 MB, with at most 100 MB per selection.",
        true,
      );
      return;
    }
    const data = formData({ destinationID: destination });
    files.forEach((file) => data.append("files", file, file.name));
    return mutate(
      "UploadFiles",
      data,
      files.length === 1 ? "File uploaded." : files.length + " files uploaded.",
    );
  }
  async function showDocument(id) {
    const revision = ++panelRevision;
    try {
      const doc = parseJsonResponse(
        await request(
          "GET",
          "GetDocument?documentID=" + encodeURIComponent(id),
        ),
      );
      if (revision !== panelRevision) return;
      if (!doc) throw new Error("This file is no longer available.");
      const panel = $("rightContainer");
      panel.replaceChildren(
        node("span", "eyebrow", "FILE DETAILS"),
        node("h2", "detail-heading", fileName(doc)),
        node("span", "format-chip", doc.documentType || "File"),
      );
      if (doc.canPreview) {
        const image = node("img", "file-preview");
        image.alt = "Preview of " + fileName(doc);
        image.src = "PreviewFile?documentID=" + encodeURIComponent(id);
        image.addEventListener("error", () =>
          image.replaceWith(
            node(
              "p",
              "field-help",
              "Preview unavailable. Download the original file to open it.",
            ),
          ),
        );
        panel.append(image);
      }
      const details = node("dl", "document-details");
      const fields = [
        ["Size", doc.hasFile ? bytes(doc.size) : "Metadata only"],
        ["Created", doc.creationDate],
        ["Folder", folders.get(doc.folderID)?.folderName || "—"],
      ];
      if (doc.summary) fields.unshift(["Summary", doc.summary]);
      fields.forEach(([label, value]) =>
        details.append(node("dt", "", label), node("dd", "", value)),
      );
      const actions = node("div", "form-actions");
      if (doc.hasFile) {
        const download = node("a", "button primary", "↓ Download");
        download.href = "DownloadFile?documentID=" + encodeURIComponent(id);
        download.setAttribute("download", fileName(doc));
        actions.append(download);
      }
      actions.append(
        button("Rename", "button secondary", () =>
          showRename({ type: "document", id }),
        ),
        button("Move file", "button secondary", () =>
          showMove({ type: "document", id }),
        ),
        button("Delete", "button danger", () =>
          deleteItem({ type: "document", id }),
        ),
      );
      panel.append(
        details,
        actions,
        button("← Back to activity", "text-button back-link", showActivity),
      );
      focusPanel();
    } catch (error) {
      announce(error.message, true);
    }
  }
  function showRename(item) {
    if (busy || loading || !ready) return;
    panelRevision++;
    const entry =
      item.type === "folder" ? folders.get(item.id) : documents.get(item.id);
    if (!entry) return;
    const panel = $("rightContainer"),
      form = node("form");
    panel.replaceChildren(
      node("span", "eyebrow", "A FRESH NAME"),
      node("h2", "", "Rename " + (item.type === "folder" ? "folder" : "file")),
    );
    const label = node("label", "", "Name");
    label.htmlFor = "renameInput";
    const input = node("input");
    input.id = "renameInput";
    input.required = true;
    input.maxLength = item.type === "folder" ? 100 : 200;
    input.value = entry.folderName || fileName(entry);
    const save = node("button", "button primary", "Save name");
    save.type = "submit";
    const actions = node("div", "form-actions");
    actions.append(save, button("Cancel", "button secondary", showActivity));
    form.append(label, input, actions);
    panel.append(form);
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (form.reportValidity())
        mutate(
          "RenameItem",
          formData({ itemType: item.type, itemID: item.id, name: input.value }),
          "Name updated.",
        );
    });
    focusPanel();
    input.focus({ preventScroll: true });
    input.select();
  }
  function isDescendant(folder, id) {
    let current = folder;
    while (current) {
      if (current.folderID === id) return true;
      current = folders.get(current.parentFolderID);
    }
    return false;
  }
  function showMove(item) {
    if (busy || loading || !ready) return;
    panelRevision++;
    const entry =
      item.type === "folder" ? folders.get(item.id) : documents.get(item.id);
    if (!entry) return;
    const panel = $("rightContainer");
    panel.replaceChildren(
      node("span", "eyebrow", "A NEW PLACE"),
      node("h2", "", "Move " + (item.type === "folder" ? "folder" : "file")),
      node("p", "muted form-destination", entry.folderName || fileName(entry)),
    );
    const form = node("form"),
      label = node("label", "", "Destination folder"),
      select = node("select");
    label.htmlFor = "moveDestination";
    select.id = "moveDestination";
    select.required = true;
    [...folders.values()]
      .filter((folder) =>
        item.type === "folder"
          ? !isDescendant(folder, item.id) &&
            folder.folderID !== entry.parentFolderID
          : folder.depth > 0 && folder.folderID !== entry.folderID,
      )
      .forEach((folder) => {
        const option = node("option", "", pathFor(folder));
        option.value = folder.folderID;
        select.append(option);
      });
    const submit = node("button", "button primary", "Move here");
    submit.type = "submit";
    submit.disabled = !select.options.length;
    if (!select.options.length)
      form.append(
        node("p", "field-help", "There are no available destination folders."),
      );
    const actions = node("div", "form-actions");
    actions.append(submit, button("Cancel", "button secondary", showActivity));
    form.append(label, select, actions);
    panel.append(form);
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (form.reportValidity()) moveItem(item, Number(select.value));
    });
    focusPanel();
  }
  function moveItem(item, destination) {
    if (item.type === "folder")
      return mutate(
        "MoveFolder",
        formData({ folderID: item.id, destinationID: destination }),
        "Folder moved.",
      );
    if (documents.get(item.id)?.folderID === destination) {
      announce("Choose a different destination folder.", true);
      return;
    }
    return mutate(
      "MoveDocument",
      formData({ folderID: destination, documentID: item.id }),
      "File moved.",
    );
  }
  function deleteItem(item) {
    if (busy || !item) return;
    const entry =
      item.type === "folder" ? folders.get(item.id) : documents.get(item.id);
    if (!entry || entry.depth === 0) return;
    const name = entry.folderName || fileName(entry),
      extra =
        item.type === "folder"
          ? " This includes all files and subfolders."
          : "";
    if (
      !confirm(
        "Delete “" +
          name +
          "”?" +
          extra +
          " You can restore it from Session activity before this session ends.",
      )
    )
      return;
    return mutate(
      item.type === "folder" ? "DeleteFolder" : "DeleteDocument",
      formData(
        item.type === "folder"
          ? { folderID: item.id }
          : { documentID: item.id },
      ),
      "Item deleted. Undo is available in Session activity.",
    );
  }
  async function mutate(endpoint, data, message) {
    if (busy || loading || !ready) return false;
    busy = true;
    const controls = [
      ...document.querySelectorAll(
        "button:not(:disabled),input:not(:disabled),select:not(:disabled)",
      ),
    ];
    controls.forEach((control) => (control.disabled = true));
    if (endpoint === "UploadFiles") {
      $("uploadProgress").hidden = false;
      $("uploadProgressBar").value = 0;
      $("uploadProgressLabel").textContent = "Uploading…";
    }
    try {
      await request("POST", endpoint, data);
      panelRevision++;
      const refreshed = await refresh();
      announce(refreshed ? message : message + " The workspace could not refresh. Use Retry workspace.", !refreshed);
      return true;
    } catch (error) {
      if (endpoint === "UndoActions" && error.status === 409) {
        panelRevision++;
        await refresh();
      }
      announce(error.message, true);
      return false;
    } finally {
      busy = false;
      controls.forEach((control) => (control.disabled = false));
      $("uploadProgress").hidden = true;
      updateControls();
    }
  }
  async function logout() {
    if (busy) return;
    $("Logout").disabled = true;
    try {
      if (!csrfToken) await loadSession();
      await request("POST", "Logout");
      sessionStorage.removeItem("utente");
      location.href = "index.html";
    } catch (error) {
      announce(error.message, true);
      $("Logout").disabled = false;
    }
  }
  $("Logout").addEventListener("click", logout);
  $("EditButton").addEventListener("click", () => setEdit(!editing));
  $("RootButton").addEventListener("click", () => {
    if (tree && ready && !loading && !busy) showFolderForm(tree.folder);
  });
  $("UploadButton").addEventListener("click", () => {
    if (tree && ready && !loading && !busy) showUpload();
  });
  $("searchInput").addEventListener("input", renderTree);
  $("overviewButton").addEventListener("click", () => {
    $("searchInput").value = "";
    renderTree();
    $("workspace").focus();
  });
  $("activityButton").addEventListener("click", () => {
    showActivity();
    focusPanel();
  });
  $("guideButton").addEventListener("click", () =>
    $("guideDialog").showModal(),
  );
  $("closeGuide").addEventListener("click", () => $("guideDialog").close());
  const dropzone = $("uploadDropzone");
  dropzone.addEventListener("click", () => {
    if (tree && ready && !loading && !busy) showUpload();
  });
  dropzone.addEventListener("keydown", (event) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      if (tree && ready && !loading && !busy) showUpload();
    }
  });
  dropzone.addEventListener("dragover", (event) => {
    if (externalDrag(event)) {
      event.preventDefault();
      dropzone.classList.add("dragover");
    }
  });
  dropzone.addEventListener("dragleave", () =>
    dropzone.classList.remove("dragover"),
  );
  dropzone.addEventListener("drop", (event) => {
    event.preventDefault();
    event.stopPropagation();
    dropzone.classList.remove("dragover");
    if (tree && ready && !loading && !busy && event.dataTransfer.files.length)
      showUpload(null, [...event.dataTransfer.files]);
  });
  window.addEventListener("dragover", (event) => {
    if (externalDrag(event)) event.preventDefault();
  });
  window.addEventListener("drop", (event) => {
    if (externalDrag(event)) event.preventDefault();
  });
  $("wasteBin").addEventListener("dragover", (event) => {
    if (dragged) {
      event.preventDefault();
      $("wasteBin").classList.add("dragover");
    }
  });
  $("wasteBin").addEventListener("dragleave", () =>
    $("wasteBin").classList.remove("dragover"),
  );
  $("wasteBin").addEventListener("drop", (event) => {
    event.preventDefault();
    event.stopPropagation();
    $("wasteBin").classList.remove("dragover");
    deleteItem(dragged);
    dragged = null;
  });
  $("rightContainer").append(
    node("p", "loading-state", "Loading your workspace…"),
  );
  $("RetryButton").addEventListener("click", bootstrap);
  bootstrap();
})();
