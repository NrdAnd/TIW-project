/* UI tests against the loopback-only, in-memory preview; live API tests are separate. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const { chromium } = require("playwright");
const base = process.env.TIW_PREVIEW_URL || "http://127.0.0.1:8765";
const screenshots = process.env.TIW_SCREENSHOT_DIR;
let browser,
  passed = 0;
const check = async (name, fn) => {
  await fn();
  console.log("PASS " + name);
  passed++;
};
(async () => {
  browser = await chromium.launch({
    headless: true,
    ...(process.env.TIW_BROWSER_CHANNEL
      ? { channel: process.env.TIW_BROWSER_CHANNEL }
      : {}),
  });
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1040 },
  });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const idle = async () => {
    await page.waitForFunction(
      () =>
        document.querySelector("#treeContainer").getAttribute("aria-busy") ===
          "false" && !document.querySelector("#EditButton").disabled,
    );
  };
  const mutate = async (endpoint, action) => {
    const response = page.waitForResponse(
      (r) =>
        r.url().endsWith("/" + endpoint) && r.request().method() === "POST",
    );
    await action();
    assert.equal((await response).status(), 200);
    await idle();
  };
  const header = (name) =>
    page.locator(".folder-header").filter({
      has: page.locator(".folder-name", {
        hasText: new RegExp("^" + name + "$"),
      }),
    });
  const card = (name) => header(name).locator("..");
  const details = async (name) => {
    await page
      .getByRole("button", { name: "Details for " + name, exact: true })
      .click();
    await page.waitForSelector(".document-details");
  };
  const back = async () => {
    await page.locator("#activityButton").click();
  };
  const undo = async (index = 0) => {
    page.once("dialog", (d) => d.accept());
    await mutate("UndoActions", () =>
      page.locator(".undo-button").nth(index).click(),
    );
  };
  const capture = async (name) => {
    await page.evaluate(() => window.scrollTo(0, 0));
    if (screenshots)
      await page.screenshot({
        path: screenshots + "/" + name + ".png",
        fullPage: true,
      });
  };
  await page.goto(base + "/homepage.html");
  await page.waitForSelector(".folder-card");
  await idle();
  const folders = Number(await page.locator("#folderCount").innerText()),
    docs = Number(await page.locator("#documentCount").innerText());
  await check(
    "tree, real-file totals, cumulative activity and top Polimi logo",
    async () => {
      assert.ok(folders >= 4);
      assert.ok(docs >= 6);
      assert.ok((await page.locator(".undo-button").count()) >= 3);
      assert.equal(
        await page
          .locator(".polimi-top-logo")
          .evaluate((i) => i.complete && i.naturalWidth > 0),
        true,
      );
      assert.match(
        await page.locator(".undo-button").nth(2).innerText(),
        /2 newer/,
      );
    },
  );
  await capture("workspace-desktop");
  await check("search keeps matching ancestors", async () => {
    await page.locator("#searchInput").fill("Project brief");
    assert.equal(await page.locator(".document-row").count(), 1);
    assert.equal(await page.locator(".folder-card").count(), 2);
    await page.locator("#searchInput").fill("no result 987654");
    assert.equal(await page.locator("#searchEmpty").isVisible(), true);
    await page.locator("#searchInput").fill("");
  });
  await check(
    "protected raster preview and original file download",
    async () => {
      await details("Polimi identity.png");
      await page.waitForFunction(
        () => document.querySelector(".file-preview")?.naturalWidth > 0,
      );
      await capture("document-details");
      const download = page.waitForEvent("download");
      await page.getByRole("link", { name: "Download" }).click();
      const d = await download;
      assert.equal(d.suggestedFilename(), "Polimi identity.png");
      assert.deepEqual(
        fs.readFileSync(await d.path()),
        fs.readFileSync("src/main/webapp/resources/images/polimi_logo.png"),
      );
      await back();
    },
  );
  await check(
    "cancelled undo preserves state; third action undoes all three",
    async () => {
      let calls = 0;
      const listener = (r) => {
        if (r.url().endsWith("/UndoActions")) calls++;
      };
      page.on("request", listener);
      page.once("dialog", (d) => {
        assert.match(d.message(), /all 2 newer/);
        return d.dismiss();
      });
      await page.locator(".undo-button").nth(2).click();
      assert.equal(calls, 0);
      await undo(2);
      assert.equal(
        Number(await page.locator("#documentCount").innerText()),
        docs - 1,
      );
      assert.equal(await page.locator(".activity-item").count(), 0);
      page.off("request", listener);
    },
  );
  const name = "Review " + Date.now().toString().slice(-8);
  await check(
    "create root folder and nested folder using buttons",
    async () => {
      await page.locator("#RootButton").click();
      await page.locator("#folderName").fill(name);
      await mutate("CreateFolder", () =>
        page
          .getByRole("button", { name: "Create folder", exact: true })
          .click(),
      );
      await page.locator("#EditButton").click();
      await card(name)
        .locator(":scope > .folder-actions")
        .getByRole("button", { name: "+ Folder", exact: true })
        .click();
      await page.locator("#folderName").fill("Nested");
      await mutate("CreateFolder", () =>
        page
          .getByRole("button", { name: "Create folder", exact: true })
          .click(),
      );
      assert.equal(
        Number(await page.locator("#folderCount").innerText()),
        folders + 2,
      );
    },
  );
  const text = Buffer.from(
    "Real bytes: café, 日本語\n<script>window.injected=true</script>\n",
  );
  const png = fs.readFileSync(
    "src/main/webapp/resources/images/polimi_logo.png",
  );
  await check(
    "multi-file button upload stores real bytes and safe names",
    async () => {
      await card(name)
        .locator(":scope > .folder-actions")
        .getByRole("button", { name: "↑ Upload", exact: true })
        .click();
      await page.locator("#fileInput").setInputFiles([
        { name: "Notes & café.txt", mimeType: "text/plain", buffer: text },
        { name: "Campus.png", mimeType: "image/png", buffer: png },
      ]);
      await mutate("UploadFiles", () =>
        page.getByRole("button", { name: "Upload selected files" }).click(),
      );
      assert.match(await card(name).innerText(), /Notes & café.txt/);
      await details("Notes & café.txt");
      const download = page.waitForEvent("download");
      await page.getByRole("link", { name: "Download" }).click();
      assert.deepEqual(fs.readFileSync(await (await download).path()), text);
      assert.equal(await page.evaluate(() => window.injected), undefined);
      await back();
    },
  );
  await check(
    "rename, button move and delete are cumulatively reversible",
    async () => {
      await details("Notes & café.txt");
      await page
        .locator("#rightContainer")
        .getByRole("button", { name: "Rename", exact: true })
        .click();
      await page.locator("#renameInput").fill("Renamed.txt");
      await mutate("RenameItem", () =>
        page.getByRole("button", { name: "Save name" }).click(),
      );
      await details("Renamed.txt");
      await page
        .getByRole("button", { name: "Move file", exact: true })
        .click();
      await page
        .locator("#moveDestination")
        .selectOption({ label: "Reading room" });
      await mutate("MoveDocument", () =>
        page.getByRole("button", { name: "Move here" }).click(),
      );
      await details("Renamed.txt");
      page.once("dialog", (d) => d.dismiss());
      await page
        .locator("#rightContainer")
        .getByRole("button", { name: "Delete", exact: true })
        .click();
      assert.equal(
        await page
          .getByRole("button", { name: "Details for Renamed.txt", exact: true })
          .count(),
        1,
      );
      page.once("dialog", (d) => d.accept());
      await mutate("DeleteDocument", () =>
        page
          .locator("#rightContainer")
          .getByRole("button", { name: "Delete", exact: true })
          .click(),
      );
      await undo(2);
      assert.match(await card(name).innerText(), /Notes & café.txt/);
      assert.equal(
        await page
          .getByRole("button", { name: "Details for Renamed.txt", exact: true })
          .count(),
        0,
      );
    },
  );
  await check(
    "external file drag and drop uploads directly into a folder",
    async () => {
      const dt = await page.evaluateHandle(() => {
        const d = new DataTransfer();
        d.items.add(
          new File(["external drop bytes"], "Dropped.txt", {
            type: "text/plain",
          }),
        );
        return d;
      });
      await mutate("UploadFiles", () =>
        header("Reading room").dispatchEvent("drop", { dataTransfer: dt }),
      );
      assert.match(await card("Reading room").innerText(), /Dropped.txt/);
      await dt.dispose();
    },
  );
  await check(
    "library drop zone accepts files and destination selection",
    async () => {
      const dt = await page.evaluateHandle(() => {
        const d = new DataTransfer();
        d.items.add(
          new File(["zone bytes"], "Zone.txt", { type: "text/plain" }),
        );
        return d;
      });
      await page
        .locator("#uploadDropzone")
        .dispatchEvent("drop", { dataTransfer: dt });
      await page
        .locator("#uploadDestination")
        .selectOption({ label: "Personal projects" });
      await mutate("UploadFiles", () =>
        page.getByRole("button", { name: "Upload selected files" }).click(),
      );
      assert.match(await card("Personal projects").innerText(), /Zone.txt/);
      await dt.dispose();
    },
  );
  await check(
    "internal file drag and drop moves through the real UI route",
    async () => {
      await mutate("MoveDocument", () =>
        page
          .locator(".document-row")
          .filter({ hasText: "Dropped.txt" })
          .dragTo(header("Personal projects")),
      );
      assert.match(await card("Personal projects").innerText(), /Dropped.txt/);
    },
  );
  await check(
    "folder drag and drop and button move preserve descendants",
    async () => {
      await mutate("MoveFolder", () =>
        header("Nested").dragTo(header("Reading room")),
      );
      assert.equal(
        await card("Reading room")
          .locator(".folder-name", { hasText: "Nested" })
          .count(),
        1,
      );
      await card("Nested")
        .locator(":scope > .folder-actions")
        .getByRole("button", { name: "Move", exact: true })
        .click();
      await page.locator("#moveDestination").selectOption({ label: name });
      await mutate("MoveFolder", () =>
        page.getByRole("button", { name: "Move here" }).click(),
      );
      assert.equal(
        await card(name).locator(".folder-name", { hasText: "Nested" }).count(),
        1,
      );
    },
  );
  await check(
    "folder rename, recursive delete and undo restore tree and bytes",
    async () => {
      await card("Nested")
        .locator(":scope > .folder-actions")
        .getByRole("button", { name: "Rename", exact: true })
        .click();
      await page.locator("#renameInput").fill("Appunti università");
      await mutate("RenameItem", () =>
        page.getByRole("button", { name: "Save name" }).click(),
      );
      page.once("dialog", (d) => d.accept());
      await mutate("DeleteFolder", () =>
        card(name)
          .locator(":scope > .folder-actions")
          .getByRole("button", { name: "Delete", exact: true })
          .click(),
      );
      assert.equal(await header(name).count(), 0);
      await undo();
      assert.match(await card(name).innerText(), /Appunti università/);
      await details("Campus.png");
      await page.waitForFunction(
        () => document.querySelector(".file-preview")?.naturalWidth > 0,
      );
      await back();
    },
  );
  await check(
    "trash drop requires confirmation and supports undo",
    async () => {
      page.once("dialog", (d) => d.accept());
      await mutate("DeleteDocument", () =>
        page
          .locator(".document-row")
          .filter({ hasText: "Dropped.txt" })
          .dragTo(page.locator("#wasteBin")),
      );
      assert.equal(
        await page
          .getByRole("button", { name: "Details for Dropped.txt", exact: true })
          .count(),
        0,
      );
      await undo();
      assert.equal(
        await page
          .getByRole("button", { name: "Details for Dropped.txt", exact: true })
          .count(),
        1,
      );
    },
  );
  await check(
    "client rejects oversized upload without a server mutation",
    async () => {
      await page.locator("#UploadButton").click();
      await page.locator("#fileInput").setInputFiles({
        name: "Oversized.bin",
        mimeType: "application/octet-stream",
        buffer: Buffer.alloc(25 * 1024 * 1024 + 1),
      });
      let calls = 0;
      const listener = (r) => {
        if (r.url().endsWith("/UploadFiles")) calls++;
      };
      page.on("request", listener);
      await page.getByRole("button", { name: "Upload selected files" }).click();
      assert.match(await page.locator("#statusMessage").innerText(), /25 MB/);
      assert.equal(calls, 0);
      page.off("request", listener);
      await page.getByRole("button", { name: "Cancel", exact: true }).click();
    },
  );
  await check(
    "failed mutation retains input and safe error feedback",
    async () => {
      await page.route("**/CreateFolder", (r) =>
        r.fulfill({ status: 500, body: "Cannot create folder" }),
      );
      await page.locator("#RootButton").click();
      await page.locator("#folderName").fill("Keep my input");
      await page
        .getByRole("button", { name: "Create folder", exact: true })
        .click();
      await page.waitForFunction(
        () =>
          document.querySelector("#statusMessage").textContent ===
          "Cannot create folder",
      );
      assert.equal(
        await page.locator("#folderName").inputValue(),
        "Keep my input",
      );
      await page.unroute("**/CreateFolder");
      await page.getByRole("button", { name: "Cancel", exact: true }).click();
    },
  );
  await check(
    "stale undo refreshes activity without claiming success",
    async () => {
      await page.route("**/UndoActions", (r) =>
        r.fulfill({ status: 409, body: "Workspace changed. Review activity." }),
      );
      page.once("dialog", (d) => d.accept());
      await page.locator(".undo-button").first().click();
      await page.waitForFunction(
        () =>
          document.querySelector("#statusMessage").textContent ===
          "Workspace changed. Review activity.",
      );
      assert.equal(
        await page.locator(".undo-button").first().isEnabled(),
        true,
      );
      await page.unroute("**/UndoActions");
    },
  );
  await check(
    "guide supports Escape and mobile layouts do not overflow",
    async () => {
      await page.locator("#guideButton").click();
      await page.keyboard.press("Escape");
      assert.equal(await page.locator("#guideDialog").isVisible(), false);
      await page.locator("#EditButton").click();
      for (const width of [320, 390, 768, 1024]) {
        await page.setViewportSize({ width, height: 844 });
        assert.equal(
          await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth,
          ),
          false,
          "width " + width,
        );
      }
      await page.setViewportSize({ width: 390, height: 844 });
      await capture("workspace-mobile");
    },
  );
  await check("expired authentication returns to login", async () => {
    await page.route("**/GetTree", (r) =>
      r.fulfill({ status: 401, body: "Please sign in again." }),
    );
    await page.reload();
    await page.waitForURL("**/index.html");
    assert.equal(
      await page.evaluate(() => sessionStorage.getItem("utente")),
      null,
    );
    await page.unroute("**/GetTree");
  });
  await check(
    "login keyboard submission and network failure feedback",
    async () => {
      await page.route("**/CheckLoginCredentials", (r) => r.abort("failed"));
      await page.locator("#username").fill("test");
      await page.locator("#password").fill("test");
      await page.locator("#password").press("Enter");
      await page.waitForFunction(() =>
        document
          .querySelector("#errorMessage")
          .textContent.includes("server could not be reached"),
      );
      assert.equal(await page.locator("#login-button").isEnabled(), true);
      await page.unroute("**/CheckLoginCredentials");
      await page.reload();

      await page.route("**/CheckLoginCredentials", (r) =>
        r.fulfill({
          status: 404,
          contentType: "text/html",
          body: "<!doctype html><html><body><h1>HTTP Status 404</h1><pre>internal container details</pre></body></html>",
        }),
      );
      await page.locator("#username").fill("test");
      await page.locator("#password").fill("test");
      await page.locator("#login-button").click();
      await page.waitForFunction(
        () => document.querySelector("#errorMessage").textContent.length > 0,
      );
      assert.equal(
        await page.locator("#errorMessage").innerText(),
        "The requested service is unavailable. Rebuild and redeploy the application.",
      );
      assert.doesNotMatch(
        await page.locator("#errorMessage").innerText(),
        /doctype|HTTP Status|container details/i,
      );
      await page.unroute("**/CheckLoginCredentials");
      await page.reload();
      await page.setViewportSize({ width: 1440, height: 1040 });
      await capture("login-desktop");
    },
  );
  await check("signup retains validation and responsive layout", async () => {
    await page.goto(base + "/signup.html");
    await page.locator("#username").fill("test");
    await page.locator("#email").fill("test@example.com");
    await page.locator("#pswd1").fill("one");
    await page.locator("#pswd2").fill("two");
    await page.locator("#register-button").click();
    assert.match(
      await page.locator("#errorMessage").innerText(),
      /passwords do not match/,
    );
    await page.locator("#email").fill("not-email");
    assert.equal(
      await page.locator("#email").evaluate((e) => e.checkValidity()),
      false,
    );
    await page.reload();
    await capture("signup-desktop");
    for (const width of [320, 390]) {
      await page.setViewportSize({ width, height: 844 });
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
        false,
      );
    }
    await capture("signup-mobile");
  });
  await check(
    "preview excludes private paths and no uncaught JS errors",
    async () => {
      for (const path of [
        "/WEB-INF/web.xml",
        "/WEB-INF/lib/gson-2.8.6.jar",
        "/../config/document-manager.example.xml",
      ])
        assert.equal((await page.request.get(base + path)).status(), 404);
      assert.deepEqual(errors, []);
    },
  );
  console.log(`\n${passed} browser checks passed.`);
})()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (browser) await browser.close();
  });
