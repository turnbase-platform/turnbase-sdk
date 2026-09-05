import { defineConfig } from "vitest/config";

// conventions.md §3: coverage measured the same way as the Java repos' JaCoCo gate — a
// threshold, checked, at 70%. `environment: "jsdom"` is required from IMP-M3-12 onward:
// TurnBaseClient reads `window`/`window.parent` and the postMessage event stream directly
// (frontend-gdk-api.md §4, §8), which don't exist under vitest's default "node" environment.
export default defineConfig({
  test: {
    environment: "jsdom",
    restoreMocks: true,
    include: ["src/test/ts/**/*.test.ts"],
    coverage: {
      provider: "v8",
      include: ["src/main/ts/**/*.ts"],
      thresholds: {
        lines: 70,
        statements: 70,
        branches: 70,
        functions: 70,
      },
    },
  },
});
