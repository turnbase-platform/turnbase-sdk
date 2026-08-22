import { defineConfig } from "tsup";

// conventions.md §2: ESM + CJS, bundled .d.ts, from one config (FR-FEGDK-009). Zero runtime
// dependencies (FR-FEGDK-008) means there is nothing for tsup to bundle in — this only
// compiles and re-emits the package's own source.
export default defineConfig({
  entry: ["src/main/ts/index.ts"],
  format: ["esm", "cjs"],
  dts: true,
  sourcemap: true,
  clean: true,
  splitting: false,
  target: "es2022",
});
