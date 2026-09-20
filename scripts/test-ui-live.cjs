/* Opt-in real browser → servlet → MySQL smoke test. Disposable loopback installation only. */
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const { chromium } = require("playwright");
const base = process.env.TIW_LIVE_URL;
if (
  !base ||
  !["127.0.0.1", "localhost"].includes(new URL(base).hostname) ||
  process.env.TIW_ALLOW_TEMPORARY_DATA !== "1"
)
  throw Error(
    "Set TIW_LIVE_URL to a disposable loopback installation and TIW_ALLOW_TEMPORARY_DATA=1.",
  );
let browser;
(async () => {
  browser = await chromium.launch({
    headless: true,
    ...(process.env.TIW_BROWSER_CHANNEL
      ? { channel: process.env.TIW_BROWSER_CHANNEL }
      : {}),
  });
  const page = await browser.newPage({
      viewport: { width: 1440, height: 1040 },
    }),
    errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  const username = "ui" + Date.now(),
    password = crypto.randomBytes(10).toString("hex");
  const idle = () =>
    page.waitForFunction(
      () =>
        document.querySelector("#treeContainer").getAttribute("aria-busy") ===
          "false" && !document.querySelector("#EditButton").disabled,
    );
  const mutate = async (endpoint, action) => {
    const response = page.waitForResponse(
      (r) =>
        r.url().endsWith("/" + endpoint) && r.request().method() === "POST",
    );
    await action();
    assert.equal((await response).status(), 200, endpoint);
    await idle();
  };
  await page.goto(base + "/signup.html");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(username + "@test.invalid");
  await page.locator("#pswd1").fill(password);
  await page.locator("#pswd2").fill(password);
  await page.locator("#register-button").click();
  await page.waitForURL("**/homepage.html");
  await idle();
  for (const name of ["Uploads", "Destination"]) {
    await page.locator("#RootButton").click();
    await page.locator("#folderName").fill(name);
    await mutate("CreateFolder", () =>
      page.getByRole("button", { name: "Create folder", exact: true }).click(),
    );
  }
  const text = Buffer.from("Browser to MySQL: café 日本語\n"),
    png = fs.readFileSync("src/main/webapp/resources/images/polimi_logo.png");
  await page.locator("#UploadButton").click();
  await page.locator("#uploadDestination").selectOption({ label: "Uploads" });
  await page.locator("#fileInput").setInputFiles([
    { name: "Original.txt", mimeType: "text/plain", buffer: text },
    { name: "Photo.png", mimeType: "image/png", buffer: png },
  ]);
  await mutate("UploadFiles", () =>
    page.getByRole("button", { name: "Upload selected files" }).click(),
  );
  await page
    .getByRole("button", { name: "Details for Photo.png", exact: true })
    .click();
  await page.waitForFunction(
    () => document.querySelector(".file-preview")?.naturalWidth > 0,
  );
  await page
    .getByRole("button", { name: "Details for Original.txt", exact: true })
    .click();
  await page.getByRole("link", { name: "Download" }).waitFor();
  const download = page.waitForEvent("download");
  await page.getByRole("link", { name: "Download" }).click();
  assert.deepEqual(fs.readFileSync(await (await download).path()), text);
  await page
    .locator("#rightContainer")
    .getByRole("button", { name: "Rename", exact: true })
    .click();
  await page.locator("#renameInput").fill("Renamed.txt");
  await mutate("RenameItem", () =>
    page.getByRole("button", { name: "Save name" }).click(),
  );
  await mutate("MoveDocument", () =>
    page
      .locator(".document-row")
      .filter({ hasText: "Renamed.txt" })
      .dragTo(
        page.locator(".folder-header").filter({ hasText: "Destination" }),
      ),
  );
  await page
    .getByRole("button", { name: "Details for Photo.png", exact: true })
    .click();
  await page.locator(".document-details").waitFor();
  page.once("dialog", (d) => d.accept());
  await mutate("DeleteDocument", () =>
    page
      .locator("#rightContainer")
      .getByRole("button", { name: "Delete", exact: true })
      .click(),
  );
  page.once("dialog", (d) => {
    assert.match(d.message(), /all 2 newer/);
    return d.accept();
  });
  await mutate("UndoActions", () =>
    page.locator(".undo-button").nth(2).click(),
  );
  const uploads = page
    .locator(".folder-header")
    .filter({ hasText: "Uploads" })
    .locator("..");
  assert.match(await uploads.innerText(), /Original.txt/);
  assert.match(await uploads.innerText(), /Photo.png/);
  assert.equal(
    await page
      .getByRole("button", { name: "Details for Renamed.txt", exact: true })
      .count(),
    0,
  );
  assert.deepEqual(errors, []);
  await page.locator("#Logout").click();
  await page.waitForURL("**/index.html");
  console.log(
    "PASS real browser: registration, folders, multi-upload, raster preview, exact-byte download, rename, drag move, deletion, cumulative undo and logout",
  );
})()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (browser) await browser.close();
  });
