import { defineConfig } from "vitest/config";

// conventions.md §3: coverage measured the same way as the Java repos' JaCoCo gate — a
// threshold, checked, at 70%. This module is types-only until IMP-M3-12 lands TurnBaseClient,
// so — like the JaCoCo gate in this same repository and in turnbase-common — the check is
// inert today: there is no branching logic to miss. It becomes load-bearing the moment
// IMP-M3-12 lands. Do not lower the threshold to make an empty module's number look real.
export default defineConfig({
  test: {
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
