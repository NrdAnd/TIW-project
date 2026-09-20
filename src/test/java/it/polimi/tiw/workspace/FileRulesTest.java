package it.polimi.tiw.workspace;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileRulesTest {
  @Test
  void rejectsTraversalAndHeaderInjection() {
    for (String value :
        new String[] {
          "../private.txt",
          "..",
          "/etc/passwd",
          "a\\b.txt",
          "a\r\nInjected: x",
          "a\u0000b",
          "fake\u202Etxt.exe"
        }) assertThrows(WorkspaceException.class, () -> FileRules.splitName(value), value);
  }

  @Test
  void supportsRealUnicodeFilenamesAndNormalizesThem() {
    assertArrayEquals(
        new String[] {"Tesi finale 2026", "pdf"}, FileRules.splitName("Tesi finale 2026.pdf"));
    assertArrayEquals(new String[] {"café", "txt"}, FileRules.splitName("cafe\u0301.txt"));
    assertArrayEquals(new String[] {"README", ""}, FileRules.splitName("README"));
    assertArrayEquals(new String[] {".gitignore", ""}, FileRules.splitName(".gitignore"));
  }

  @Test
  void rejectsOversizedNamesAndExtensions() {
    assertThrows(WorkspaceException.class, () -> FileRules.splitName("a".repeat(181) + ".pdf"));
    assertThrows(WorkspaceException.class, () -> FileRules.splitName("file." + "x".repeat(21)));
  }

  @Test
  void recognisesRasterSignaturesOnly() {
    assertEquals(
        "image/png", FileRules.previewType(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}));
    assertEquals(
        "image/jpeg", FileRules.previewType(new byte[] {(byte) 255, (byte) 216, (byte) 255}));
    assertEquals("image/gif", FileRules.previewType("GIF89a".getBytes(StandardCharsets.US_ASCII)));
    assertEquals(
        "image/webp", FileRules.previewType("RIFFxxxxWEBP".getBytes(StandardCharsets.US_ASCII)));
  }

  @Test
  void activeContentNeverGetsAnInlineType() {
    for (String value :
        new String[] {
          "<svg onload=alert(1)>", "<html><script>", "%PDF-1.7", "", "not really a PNG"
        })
      assertEquals(
          "application/octet-stream",
          FileRules.previewType(value.getBytes(StandardCharsets.UTF_8)));
  }
}
