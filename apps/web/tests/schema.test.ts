import { describe, it, expect } from "vitest";
import { parseInstanceJson, validateInstance } from "~/lib/schema";
import { exampleToy01, exampleToy02 } from "~/lib/examples";

describe("instance schema", () => {
  it("accepts bundled toy-01 example", () => {
    expect(validateInstance(exampleToy01).valid).toBe(true);
  });
  it("accepts bundled toy-02 example", () => {
    expect(validateInstance(exampleToy02).valid).toBe(true);
  });
  it("rejects a non-JSON string", () => {
    const result = parseInstanceJson("not really json");
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toMatch(/invalid JSON/i);
  });
  it("reports missing required fields with a path", () => {
    const result = parseInstanceJson(JSON.stringify({ id: "x" }));
    expect(result.valid).toBe(false);
    const messages = result.errors.map((e) => e.message).join(" ");
    expect(messages).toMatch(/horizonDays/);
  });
  it("rejects unknown top-level properties", () => {
    const instance = { ...exampleToy01, bogus: 42 };
    const result = validateInstance(instance);
    expect(result.valid).toBe(false);
  });
});
