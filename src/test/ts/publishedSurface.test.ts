import { describe, expect, it } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, "../../..");

/**
 * Turns IMP-M0-02's Done-when clause into assertions rather than an impression, mirroring
 * PublishedSurfaceTest on the Java side.
 */
describe("published npm surface", () => {
  it("declares zero runtime dependencies (FR-FEGDK-008)", () => {
    const pkg = JSON.parse(readFileSync(resolve(repoRoot, "package.json"), "utf-8")) as {
      dependencies?: Record<string, string>;
    };
    expect(Object.keys(pkg.dependencies ?? {})).toHaveLength(0);
  });

  it("builds ESM, CJS and .d.ts output (FR-FEGDK-009)", () => {
    for (const file of ["dist/index.js", "dist/index.cjs", "dist/index.d.ts"]) {
      expect(existsSync(resolve(repoRoot, file)), `${file} must exist after npm run build`).toBe(
        true,
      );
    }
  });
});
