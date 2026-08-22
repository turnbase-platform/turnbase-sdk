// @ts-check
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import eslintConfigPrettier from "eslint-config-prettier";

// conventions.md §2: flat config, typescript-eslint strict + stylistic (type-aware),
// eslint-config-prettier last so ESLint stops having opinions about formatting. Pinned here
// rather than left to whoever runs a scaffold tool first.
export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  ...tseslint.configs.stylisticTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  {
    ignores: ["dist/**", "node_modules/**"],
  },
  eslintConfigPrettier,
);
