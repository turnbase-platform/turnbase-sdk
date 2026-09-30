package io.turnbase.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

/**
 * Phases C-F of {@code bundle-format.md} §3 over an in-memory archive — the one entry point the
 * upload pipeline and the local test harness share ({@code bundle-format.md} §2.4). §3.3's
 * barriers: C and D are each a barrier for what follows; E1-E6 accumulate with no barrier among
 * them; a failing E1/E2 skips F entirely; F1/F2 are a barrier for F3-F7.
 *
 * <p>Deliberately free of persistence, storage and framework types: what the caller knows and this
 * class does not (the slot version, whether the game may declare {@code runtime: "jvm"}, the
 * supported GDK range and the resource ceilings) arrives through {@link Options}. Phase G
 * (publication) is the caller's, and consumes the {@link Bundle} of a valid {@link Result}.
 */
public final class BundleValidator {

  private static final int MAX_MANIFEST_BYTES =
      256 * 1024; // bundle-format.md §2.3 — fixed, not configurable

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ArchiveReader archiveReader = new ArchiveReader();
  private final ManifestValidator manifestValidator = new ManifestValidator();
  private final BackendJarAnalyzer backendJarAnalyzer = new BackendJarAnalyzer(archiveReader);
  private final StateSerializabilityAnalyzer stateSerializabilityAnalyzer =
      new StateSerializabilityAnalyzer();

  /**
   * What the caller supplies that the bundle cannot say about itself.
   *
   * @param maxUncompressedBytes {@code bundle-format.md} §2.3 total inflated size ceiling
   * @param maxEntryUncompressedBytes §2.3 per-entry inflated size ceiling
   * @param maxCompressionRatio §2.3 per-entry compression ratio ceiling
   * @param maxEntryCount §2.3 entry count ceiling
   * @param maxInfoDocBytes ceiling on the {@code infoDoc} file (E3)
   * @param jvmRuntimeAllowed whether {@code runtime: "jvm"} is legal for this bundle (E4) — the
   *     platform's trust rule; the harness passes {@code true}
   * @param gdkLow lowest supported {@code gdkApiVersion} (E5), inclusive
   * @param gdkHigh highest supported {@code gdkApiVersion} (E5), inclusive
   * @param expectedVersion the version the manifest must declare (D3), or {@code null} to skip
   */
  public record Options(
      long maxUncompressedBytes,
      long maxEntryUncompressedBytes,
      int maxCompressionRatio,
      int maxEntryCount,
      long maxInfoDocBytes,
      boolean jvmRuntimeAllowed,
      VersionOrdering.Version gdkLow,
      VersionOrdering.Version gdkHigh,
      @Nullable String expectedVersion) {}

  /**
   * The parts of a valid bundle Phase G needs, so the caller does not re-read the archive.
   *
   * @param archive the inflated archive
   * @param manifest the parsed manifest
   * @param manifestText the manifest exactly as decoded
   * @param backendPath {@code backend.path}
   * @param gdkApiVersion the declared {@code gdkApiVersion}
   * @param infoDoc {@code infoDoc}, or {@code null} when the manifest declares none
   */
  public record Bundle(
      ArchiveReadResult archive,
      JsonNode manifest,
      String manifestText,
      String backendPath,
      String gdkApiVersion,
      @Nullable String infoDoc) {}

  /**
   * @param findings every finding, errors and warnings, in the order the stages produced them
   * @param bundle the validated bundle, present exactly when no finding is an {@link
   *     Severity#ERROR}
   */
  public record Result(List<Finding> findings, Optional<Bundle> bundle) {

    /**
     * @return the fatal findings
     */
    public List<Finding> errors() {
      return findings.stream().filter(f -> f.severity() == Severity.ERROR).toList();
    }

    /**
     * @return the advisory findings
     */
    public List<Finding> warnings() {
      return findings.stream().filter(f -> f.severity() == Severity.WARNING).toList();
    }

    /**
     * @return {@code true} when no finding is fatal
     */
    public boolean valid() {
      return bundle.isPresent();
    }
  }

  /**
   * Validates one bundle archive. Never throws for a malformed bundle — every defect is a {@link
   * Finding}.
   *
   * @param zipBytes the bundle archive
   * @param options what the caller knows that the bundle does not
   * @return the findings and, if none is fatal, the {@link Bundle}
   */
  public Result validate(byte[] zipBytes, Options options) {
    ArchiveReader.Limits limits =
        new ArchiveReader.Limits(
            options.maxUncompressedBytes(),
            options.maxEntryUncompressedBytes(),
            options.maxCompressionRatio(),
            options.maxEntryCount());

    // Phase C — barrier.
    ArchiveReadResult archive = archiveReader.read(zipBytes, limits);
    if (!archive.ok()) {
      return failed(archive.errors(), List.of());
    }

    // Phase D — barrier.
    List<Finding> warnings = new ArrayList<>();
    Optional<ArchiveEntry> manifestEntry = archive.find("manifest.json");
    if (manifestEntry.isEmpty()) {
      return failed(
          List.of(
              Finding.error("manifest.present", "manifest.json is missing from the archive root.")),
          warnings);
    }
    byte[] manifestBytes = manifestEntry.get().data();
    if (manifestBytes.length > MAX_MANIFEST_BYTES) {
      return failed(
          List.of(
              Finding.error(
                  "manifest.present", "manifest.json exceeds " + MAX_MANIFEST_BYTES + " bytes.")),
          warnings);
    }
    String manifestText;
    try {
      manifestText = decodeStrictUtf8(manifestBytes);
    } catch (CharacterCodingException e) {
      return failed(
          List.of(Finding.error("manifest.present", "manifest.json is not valid UTF-8.")),
          warnings);
    }
    JsonNode manifestNode;
    try {
      manifestNode = objectMapper.readTree(manifestText);
    } catch (IOException e) {
      return failed(
          List.of(
              Finding.error(
                  "manifest.present", "manifest.json does not parse as JSON: " + e.getMessage())),
          warnings);
    }

    List<Finding> dErrors = new ArrayList<>();
    ManifestValidationResult d2 = manifestValidator.validate(manifestNode);
    dErrors.addAll(d2.errors());
    warnings.addAll(d2.warnings());

    String manifestVersion = manifestNode.path("version").asText(null);
    String expectedVersion = options.expectedVersion();
    if (manifestVersion != null
        && expectedVersion != null
        && !manifestVersion.equals(expectedVersion)) {
      dErrors.add(
          Finding.error(
              "manifest.version",
              "Manifest version '%s' does not match the declared slot version '%s'."
                  .formatted(manifestVersion, expectedVersion)));
    }
    if (!dErrors.isEmpty()) {
      return failed(dErrors, warnings);
    }

    // Phase E — accumulate, no internal barriers.
    List<Finding> errors = new ArrayList<>();
    String backendPath = manifestNode.path("backend").path("path").asText(null);
    String backendEntryPointClassName =
        manifestNode.path("backend").path("entryPoint").asText(null);
    String frontendEntryPoint = manifestNode.path("frontend").path("entryPoint").asText(null);
    String infoDoc =
        manifestNode.hasNonNull("infoDoc") ? manifestNode.path("infoDoc").asText(null) : null;
    String runtime = manifestNode.path("runtime").asText(null);
    String gdkApiVersion = manifestNode.path("gdkApiVersion").asText(null);

    boolean e1ok = backendPath != null && archive.find(backendPath).isPresent();
    if (!e1ok) {
      errors.add(
          Finding.error(
              "bundle.paths.backend", "backend.path '" + backendPath + "' is not in the archive."));
    }
    boolean e2ok = frontendEntryPoint != null && archive.find(frontendEntryPoint).isPresent();
    if (!e2ok) {
      errors.add(
          Finding.error(
              "bundle.paths.frontend",
              "frontend.entryPoint '" + frontendEntryPoint + "' is not in the archive."));
    }
    if (infoDoc != null) {
      Optional<ArchiveEntry> infoDocEntry = archive.find(infoDoc);
      if (infoDocEntry.isEmpty()) {
        errors.add(
            Finding.error(
                "bundle.paths.infoDoc", "infoDoc '" + infoDoc + "' is not in the archive."));
      } else if (infoDocEntry.get().data().length > options.maxInfoDocBytes()) {
        errors.add(
            Finding.error(
                "bundle.paths.infoDoc",
                "infoDoc exceeds " + options.maxInfoDocBytes() + " bytes."));
      }
    }
    if ("jvm".equals(runtime) && !options.jvmRuntimeAllowed()) {
      errors.add(
          Finding.error(
              "bundle.runtime.trust",
              "runtime 'jvm' is accepted only for an Official, ownerless game uploaded through"
                  + " the admin API (ADR-0006)."));
    }
    if (gdkApiVersion == null
        || !VersionOrdering.isValidFormat(gdkApiVersion)
        || VersionOrdering.compare(VersionOrdering.Version.parse(gdkApiVersion), options.gdkLow())
            < 0
        || VersionOrdering.compare(VersionOrdering.Version.parse(gdkApiVersion), options.gdkHigh())
            > 0) {
      errors.add(
          Finding.error(
              "manifest.gdkApiVersion",
              "gdkApiVersion '%s' is outside the platform's supported range [%s, %s]."
                  .formatted(gdkApiVersion, options.gdkLow(), options.gdkHigh())));
    }
    if (frontendEntryPoint != null
        && !(frontendEntryPoint.endsWith(".js") || frontendEntryPoint.endsWith(".mjs"))) {
      warnings.add(
          Finding.warning("frontend.entryType", "frontend.entryPoint should end in .js or .mjs."));
    }
    if (frontendEntryPoint != null && hasHtmlInFrontendTree(archive, frontendEntryPoint)) {
      warnings.add(
          Finding.warning(
              "frontend.entryType",
              "The frontend tree contains an .html file, which is never served."));
    }

    // Phase F — jvm only, requires E1 and E2.
    if ("jvm".equals(runtime)
        && e1ok
        && e2ok
        && backendPath != null
        && backendEntryPointClassName != null) {
      byte[] backendJarBytes = archive.find(backendPath).orElseThrow().data();
      BackendJarAnalyzer.Result f =
          backendJarAnalyzer.analyze(backendJarBytes, backendEntryPointClassName, limits);
      errors.addAll(f.errors());
      warnings.addAll(f.warnings());
      if (f.errors().isEmpty() && f.entryClassInternalName() != null && f.innerArchive() != null) {
        ArchiveReadResult innerArchive = f.innerArchive();
        String entryClassInternalName = f.entryClassInternalName();
        ClassNode entryClassNode =
            ClassFileUtil.readClassNode(
                innerArchive.find(entryClassInternalName + ".class").orElseThrow().data());
        String stateType =
            stateSerializabilityAnalyzer.resolveStateType(entryClassNode, innerArchive);
        if (stateType == null) {
          warnings.add(
              Finding.warning(
                  "state.type",
                  "The state type argument S of TurnBaseGame<S> is not statically resolvable (raw"
                      + " usage or a type variable)."));
        } else {
          StateSerializabilityAnalyzer.Result stateResult =
              stateSerializabilityAnalyzer.analyze(stateType, innerArchive);
          errors.addAll(stateResult.errors());
          warnings.addAll(stateResult.warnings());
        }
      }
    }

    if (!errors.isEmpty()) {
      return failed(errors, warnings);
    }
    if (backendPath == null || gdkApiVersion == null) {
      // Unreachable: E1 and E5 above would already have populated `errors`. Defensive only, and
      // it is what lets the compiler see backendPath/gdkApiVersion as non-null below.
      return failed(
          List.of(
              Finding.error("pipeline.interrupted", "Internal validation state was inconsistent.")),
          warnings);
    }

    List<Finding> findings = List.copyOf(warnings);
    return new Result(
        findings,
        Optional.of(
            new Bundle(archive, manifestNode, manifestText, backendPath, gdkApiVersion, infoDoc)));
  }

  private static Result failed(List<Finding> errors, List<Finding> warnings) {
    List<Finding> findings = new ArrayList<>(errors);
    findings.addAll(warnings);
    return new Result(List.copyOf(findings), Optional.empty());
  }

  private static boolean hasHtmlInFrontendTree(
      ArchiveReadResult archive, String frontendEntryPoint) {
    int slash = frontendEntryPoint.lastIndexOf('/');
    String prefix = slash >= 0 ? frontendEntryPoint.substring(0, slash + 1) : "";
    return archive.entries().stream()
        .anyMatch(
            e ->
                !e.directory()
                    && e.name().endsWith(".html")
                    && (prefix.isEmpty() ? !e.name().contains("/") : e.name().startsWith(prefix)));
  }

  private static String decodeStrictUtf8(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }
}
