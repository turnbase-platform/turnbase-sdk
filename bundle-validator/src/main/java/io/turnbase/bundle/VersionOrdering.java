package io.turnbase.bundle;

import java.util.regex.Pattern;

/**
 * The three-integer version comparison every ordering check in {@code bundle-format.md} uses —
 * stage A1/A2, the §5.4 activation re-check, and §6's {@code gdkApiVersion} range. {@code
 * data-model-catalog.md} §2 stores the parsed triple as generated columns for exactly this reason:
 * {@code '1.10.0' < '1.9.0'} as text, which gets FR-GAME-009's own worked example backwards.
 */
public final class VersionOrdering {

  public static final Pattern FORMAT = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

  private VersionOrdering() {}

  public static boolean isValidFormat(String version) {
    return FORMAT.matcher(version).matches();
  }

  public static int compare(Version a, Version b) {
    int major = Integer.compare(a.major(), b.major());
    if (major != 0) {
      return major;
    }
    int minor = Integer.compare(a.minor(), b.minor());
    if (minor != 0) {
      return minor;
    }
    return Integer.compare(a.patch(), b.patch());
  }

  /**
   * A parsed {@code x.y.z} version.
   *
   * @param major the major component
   * @param minor the minor component
   * @param patch the patch component
   */
  public record Version(int major, int minor, int patch) {

    public static Version parse(String value) {
      if (!isValidFormat(value)) {
        throw new IllegalArgumentException("Not a valid x.y.z version: " + value);
      }
      String[] parts = value.split("\\.");
      return new Version(
          Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Override
    public String toString() {
      return major + "." + minor + "." + patch;
    }
  }
}
