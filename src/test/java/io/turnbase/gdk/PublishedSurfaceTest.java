package io.turnbase.gdk;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Turns IMP-M0-02's Done-when clause into assertions rather than an impression.
 *
 * <p>Two things fail silently if left unchecked: a Java 22+ API compiles here but dies at run time
 * inside Espresso (turnbase-sdk/CLAUDE.md), and a package missing {@code @NullMarked} silently
 * stops being part of the published nullness contract. Neither shows up as a compile error, so both
 * need a runtime assertion instead.
 */
class PublishedSurfaceTest {

  /** Java 21 class files carry major version 65 (one of {@code java.lang.Runtime.Version}). */
  private static final int JAVA_21_CLASS_FILE_MAJOR_VERSION = 65;

  /**
   * The five packages fixed by {@code backend-gdk-api.md} §2. All five exist from IMP-M0-02 even
   * though four are empty until IMP-M3-01 — the layout is part of the published surface, and
   * renaming one later is a breaking change.
   */
  private static final String[] FIXED_PACKAGES = {
    "io.turnbase.gdk",
    "io.turnbase.gdk.context",
    "io.turnbase.gdk.effect",
    "io.turnbase.gdk.result",
    "io.turnbase.gdk.model",
  };

  @Test
  void gameStateCompilesToJava21Bytecode() throws IOException {
    assertThat(classFileMajorVersion(GameState.class)).isEqualTo(JAVA_21_CLASS_FILE_MAJOR_VERSION);
  }

  @Test
  void everyFixedPackageCarriesNullMarked() {
    for (String packageName : FIXED_PACKAGES) {
      assertThat(packageInfoDeclaresNullMarked(packageName))
          .as("package %s must carry @NullMarked (backend-gdk-api.md §2, ADR-0015)", packageName)
          .isTrue();
    }
  }

  /**
   * Reads {@code package-info.class}'s raw bytes and looks for the {@code NullMarked} annotation's
   * constant-pool entry. Cruder than parsing the constant pool structurally, but sufficient: the
   * descriptor string {@code NullMarked} cannot appear in a compiled package-info by accident.
   */
  private static boolean packageInfoDeclaresNullMarked(String packageName) {
    String resource = packageName.replace('.', '/') + "/package-info.class";
    InputStream raw = PublishedSurfaceTest.class.getClassLoader().getResourceAsStream(resource);
    if (raw == null) {
      throw new AssertionError("package-info.class must exist for " + packageName);
    }
    try (InputStream in = raw) {
      byte[] bytes = in.readAllBytes();
      String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
      return constantPool.contains("NullMarked");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static int classFileMajorVersion(Class<?> type) throws IOException {
    String resource = type.getName().replace('.', '/') + ".class";
    InputStream raw = type.getClassLoader().getResourceAsStream(resource);
    if (raw == null) {
      throw new AssertionError("class file for " + type + " must be on the classpath");
    }
    try (InputStream in = raw) {
      DataInputStream data = new DataInputStream(in);
      int magic = data.readInt();
      assertThat(magic).as("class file magic number").isEqualTo(0xCAFEBABE);
      data.readUnsignedShort(); // minor version, unused
      return data.readUnsignedShort();
    }
  }
}
