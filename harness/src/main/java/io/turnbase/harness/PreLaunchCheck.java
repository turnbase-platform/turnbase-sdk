package io.turnbase.harness;

import io.turnbase.bundle.BundleValidator;
import io.turnbase.bundle.Finding;
import io.turnbase.bundle.Severity;
import io.turnbase.bundle.VersionOrdering;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs the shared bundle validator against a game project before launch ({@code test-harness.md}
 * §6, FR-HARNESS-002). A project directory is an unpacked bundle: {@code manifest.json} at its root
 * plus every path the manifest declares. The directory is zipped in memory and handed to the same
 * {@link BundleValidator} the upload pipeline uses, so the two cannot disagree about what a bundle
 * is.
 */
public final class PreLaunchCheck {

  /**
   * The validator's ceilings — {@code bundle-format.md} §2.3 defaults. The harness cannot know the
   * platform's supported {@code gdkApiVersion} range offline (§7), so E5 accepts every version and
   * {@link Outcome#findings()} carries a warning saying so.
   */
  private static final BundleValidator.Options OPTIONS =
      new BundleValidator.Options(
          1L << 30,
          256L << 20,
          100,
          20_000,
          1L << 20,
          true,
          new VersionOrdering.Version(0, 0, 0),
          new VersionOrdering.Version(Integer.MAX_VALUE, 0, 0),
          null);

  /** The finding code for the check the harness structurally cannot perform. */
  public static final String GDK_RANGE_UNCHECKED = "manifest.gdkApiVersion.unchecked";

  private PreLaunchCheck() {}

  /**
   * The result of the pre-launch check.
   *
   * @param findings every finding, plus the harness's own {@link #GDK_RANGE_UNCHECKED} warning
   * @param validation the validator's result, for the launcher to read the manifest from
   * @param launch whether the launch may proceed
   */
  public record Outcome(
      List<Finding> findings, BundleValidator.Result validation, boolean launch) {}

  /**
   * @param projectDir the game project directory
   * @param config the harness configuration
   * @return the outcome; {@code launch} is false for any fatal finding, and for any warning when
   *     {@link HarnessConfig#failOnWarnings()}
   */
  public static Outcome run(Path projectDir, HarnessConfig config) {
    BundleValidator.Result result = new BundleValidator().validate(zip(projectDir), OPTIONS);
    List<Finding> findings = new ArrayList<>(result.findings());
    findings.add(
        Finding.warning(
            GDK_RANGE_UNCHECKED,
            "gdkApiVersion cannot be checked offline; the platform may still reject this bundle"
                + " at upload (test-harness.md §7)."));
    boolean fatal = findings.stream().anyMatch(f -> f.severity() == Severity.ERROR);
    // The range warning is unconditional, so it must not trip failOnWarnings by itself.
    boolean warned = result.findings().stream().anyMatch(f -> f.severity() == Severity.WARNING);
    return new Outcome(
        List.copyOf(findings), result, !fatal && !(config.failOnWarnings() && warned));
  }

  /** Zips a project directory in memory, leaving out {@code harness.json}. */
  static byte[] zip(Path projectDir) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out);
        Stream<Path> files = Files.walk(projectDir)) {
      for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
        String name = projectDir.relativize(file).toString().replace('\\', '/');
        if (name.equals(HarnessConfig.FILE_NAME)) {
          continue;
        }
        zip.putNextEntry(new ZipEntry(name));
        zip.write(Files.readAllBytes(file));
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }
}
