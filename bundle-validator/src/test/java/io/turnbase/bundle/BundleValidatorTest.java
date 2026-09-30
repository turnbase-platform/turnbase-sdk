package io.turnbase.bundle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The C-F pipeline of {@code bundle-format.md} §3 as one entry point (IMP-M3-13). */
class BundleValidatorTest {

  private static final VersionOrdering.Version LOW = VersionOrdering.Version.parse("0.3.0");
  private static final VersionOrdering.Version HIGH = VersionOrdering.Version.parse("0.3.0");

  private final BundleValidator validator = new BundleValidator();

  private static BundleValidator.Options options(
      boolean jvmAllowed, @Nullable String expectedVersion) {
    return new BundleValidator.Options(
        1_000_000_000L,
        256_000_000L,
        100,
        20_000,
        512 * 1024,
        jvmAllowed,
        LOW,
        HIGH,
        expectedVersion);
  }

  @Test
  void validBundleYieldsTheBundleAndNoErrors() {
    BundleValidator.Result result =
        validator.validate(TestBundles.validChessBundle("1.0.0"), options(true, "1.0.0"));

    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
    BundleValidator.Bundle bundle = result.bundle().orElseThrow();
    assertThat(bundle.backendPath()).isEqualTo("backend/game.jar");
    assertThat(bundle.gdkApiVersion()).isEqualTo("0.3.0");
    assertThat(bundle.infoDoc()).isNull();
    assertThat(bundle.manifest().path("title").asText()).isEqualTo("Chess");
    assertThat(bundle.archive().find("frontend/main.js")).isPresent();
  }

  @Test
  void versionMismatchIsAPhaseDError() {
    BundleValidator.Result result =
        validator.validate(TestBundles.validChessBundle("1.0.0"), options(true, "2.0.0"));

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).extracting(Finding::code).containsExactly("manifest.version");
  }

  @Test
  void nullExpectedVersionSkipsTheVersionCheck() {
    assertThat(
            validator.validate(TestBundles.validChessBundle("1.0.0"), options(true, null)).valid())
        .isTrue();
  }

  @Test
  void missingFrontendEntryIsE2AndSkipsPhaseF() {
    BundleValidator.Result result =
        validator.validate(
            TestBundles.chessBundleMissingFrontendEntry("1.0.0"), options(true, "1.0.0"));

    assertThat(result.errors()).extracting(Finding::code).containsExactly("bundle.paths.frontend");
  }

  @Test
  void jvmRuntimeWhenNotAllowedIsATrustError() {
    BundleValidator.Result result =
        validator.validate(TestBundles.validChessBundle("1.0.0"), options(false, "1.0.0"));

    assertThat(result.errors()).extracting(Finding::code).containsExactly("bundle.runtime.trust");
  }

  @Test
  void gdkApiVersionOutsideTheRangeIsE5() {
    BundleValidator.Options narrow =
        new BundleValidator.Options(
            1_000_000_000L,
            256_000_000L,
            100,
            20_000,
            512 * 1024,
            true,
            VersionOrdering.Version.parse("0.4.0"),
            VersionOrdering.Version.parse("0.5.0"),
            null);

    BundleValidator.Result result =
        validator.validate(TestBundles.validChessBundle("1.0.0"), narrow);

    assertThat(result.errors()).extracting(Finding::code).containsExactly("manifest.gdkApiVersion");
  }

  @Test
  void anArchiveThatIsNotAZipFailsPhaseC() {
    BundleValidator.Result result =
        validator.validate("not a zip".getBytes(UTF_8), options(true, null));

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).extracting(Finding::code).containsExactly("bundle.archive.format");
  }

  @Test
  void missingManifestFailsPhaseD() {
    BundleValidator.Result result =
        validator.validate(zip(Map.of("other.txt", "x")), options(true, null));

    assertThat(result.errors()).extracting(Finding::code).containsExactly("manifest.present");
  }

  @Test
  void manifestThatIsNotJsonFailsPhaseD() {
    BundleValidator.Result result =
        validator.validate(zip(Map.of("manifest.json", "{nope")), options(true, null));

    assertThat(result.errors()).extracting(Finding::code).containsExactly("manifest.present");
    assertThat(result.errors().get(0).message()).contains("does not parse as JSON");
  }

  @Test
  void manifestThatIsNotUtf8FailsPhaseD() throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
      zos.putNextEntry(new ZipEntry("manifest.json"));
      zos.write(new byte[] {(byte) 0xC3, (byte) 0x28});
      zos.closeEntry();
    }

    BundleValidator.Result result = validator.validate(buffer.toByteArray(), options(true, null));

    assertThat(result.errors().get(0).message()).contains("not valid UTF-8");
  }

  @Test
  void manifestSchemaViolationsAreReportedTogether() {
    BundleValidator.Result result =
        validator.validate(zip(Map.of("manifest.json", "{}")), options(true, null));

    assertThat(result.errors()).hasSizeGreaterThan(1);
    assertThat(result.errors()).allMatch(f -> f.severity() == Severity.ERROR);
  }

  @Test
  void aFindingFactoryFixesTheSeverity() {
    assertThat(Finding.error("c", "m").severity()).isEqualTo(Severity.ERROR);
    assertThat(Finding.warning("c", "m").severity()).isEqualTo(Severity.WARNING);
  }

  private static byte[] zip(Map<String, String> entries) {
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (ZipOutputStream zos = new ZipOutputStream(buffer, UTF_8)) {
        for (Map.Entry<String, String> e : entries.entrySet()) {
          zos.putNextEntry(new ZipEntry(e.getKey()));
          zos.write(e.getValue().getBytes(UTF_8));
          zos.closeEntry();
        }
      }
      return buffer.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
