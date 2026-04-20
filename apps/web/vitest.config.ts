import { defineConfig } from "vitest/config";
import path from "node:path";

// Minimal, standalone config (no React Router / Tailwind plugins) so that
// unit tests don't need the full framework pipeline.
export default defineConfig({
  resolve: {
    alias: {
      "~": path.resolve(__dirname, "app"),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    include: ["tests/**/*.{test,spec}.{ts,tsx}"],
    setupFiles: ["./tests/setup.ts"],
    css: false,
  },
});
