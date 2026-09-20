package it.polimi.tiw.workspace;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;

public final class FileRules {
  public static final long MAX_FILE = 25L * 1024 * 1024;
  public static final long MAX_REQUEST = 100L * 1024 * 1024;
  public static final long QUOTA = 250L * 1024 * 1024;

  private FileRules() {}

  public static String name(String value, int max) {
    if (value == null) throw new WorkspaceException(400, "A name is required.");
    String result = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    if (result.isEmpty()
        || result.length() > max
        || result.equals(".")
        || result.equals("..")
        || result.matches(".*[\\\\/:*?\"<>|].*")
        || result
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)
        || result.endsWith(".")) {
      throw new WorkspaceException(
          400,
          "Use a valid name without path separators or control characters (maximum "
              + max
              + " characters).");
    }
    return result;
  }

  public static String[] splitName(String value) {
    String clean = name(value, 200);
    int dot = clean.lastIndexOf('.');
    String stem = clean, extension = "";
    if (dot > 0) {
      stem = clean.substring(0, dot);
      extension = clean.substring(dot + 1);
    }
    if (stem.length() > 180 || extension.length() > 20)
      throw new WorkspaceException(400, "The file name or extension is too long.");
    return new String[] {stem, extension};
  }

  public static String fileName(String stem, String extension) {
    return stem + (extension == null || extension.isEmpty() ? "" : "." + extension);
  }

  // Only explicitly recognised raster signatures may be displayed inline.
  // All other types, including SVG, HTML and PDF, remain attachment downloads.
  public static String previewType(byte[] prefix) {
    if (prefix.length >= 8
        && Arrays.equals(
            Arrays.copyOf(prefix, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
      return "image/png";
    if (prefix.length >= 3
        && (prefix[0] & 255) == 255
        && (prefix[1] & 255) == 216
        && (prefix[2] & 255) == 255) return "image/jpeg";
    if (prefix.length >= 6) {
      String six = new String(prefix, 0, 6, StandardCharsets.US_ASCII);
      if (six.equals("GIF87a") || six.equals("GIF89a")) return "image/gif";
    }
    if (prefix.length >= 12
        && new String(prefix, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
        && new String(prefix, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
    return "application/octet-stream";
  }
}
