import Ajv from "ajv/dist/2020";
import type { ErrorObject } from "ajv";
import addFormats from "ajv-formats";
import type { NspInstance } from "./types";

/**
 * Client-side NspInstance validator. Uses the wire-format schema
 * (apps/shared/schemas/nsp-instance.schema.json) imported inline so the
 * validator works in SSR + browser without network fetches.
 *
 * We keep the schema a local copy to avoid a build-time dependency on the
 * sibling apps/shared directory (the web build must be deployable standalone).
 */

const nspInstanceSchema = {
  $schema: "https://json-schema.org/draft/2020-12/schema",
  $id: "https://cp-deep-dive.dev/schemas/nsp-instance.schema.json",
  title: "NspInstance",
  type: "object",
  required: ["id", "horizonDays", "shifts", "nurses", "coverage"],
  additionalProperties: false,
  properties: {
    id: { type: "string", minLength: 1 },
    name: { type: "string" },
    source: {
      type: "string",
      enum: ["toy", "nsplib", "inrc1", "inrc2", "custom"],
    },
    horizonDays: { type: "integer", minimum: 1 },
    shifts: {
      type: "array",
      minItems: 1,
      items: { $ref: "#/$defs/Shift" },
    },
    nurses: {
      type: "array",
      minItems: 1,
      items: { $ref: "#/$defs/Nurse" },
    },
    coverage: {
      type: "array",
      items: { $ref: "#/$defs/CoverageRequirement" },
    },
    forbiddenTransitions: {
      type: "array",
      items: {
        type: "array",
        minItems: 2,
        maxItems: 2,
        items: { type: "string" },
      },
    },
    metadata: { type: "object", additionalProperties: true },
  },
  $defs: {
    Shift: {
      type: "object",
      required: ["id", "label"],
      additionalProperties: false,
      properties: {
        id: { type: "string", minLength: 1 },
        label: { type: "string" },
        startMinutes: { type: "integer", minimum: 0, maximum: 1439 },
        durationMinutes: { type: "integer", minimum: 1 },
        skill: { type: "string" },
      },
    },
    Nurse: {
      type: "object",
      required: ["id"],
      additionalProperties: false,
      properties: {
        id: { type: "string", minLength: 1 },
        name: { type: "string" },
        skills: {
          type: "array",
          items: { type: "string" },
          uniqueItems: true,
        },
        maxShiftsPerWeek: { type: "integer", minimum: 0 },
        minShiftsPerWeek: { type: "integer", minimum: 0 },
        maxConsecutiveWorkingDays: { type: "integer", minimum: 1 },
        preferences: {
          type: "array",
          items: { $ref: "#/$defs/Preference" },
        },
        unavailable: {
          type: "array",
          items: { type: "integer", minimum: 0 },
          uniqueItems: true,
        },
      },
    },
    CoverageRequirement: {
      type: "object",
      required: ["day", "shiftId", "required"],
      additionalProperties: false,
      properties: {
        day: { type: "integer", minimum: 0 },
        shiftId: { type: "string", minLength: 1 },
        required: { type: "integer", minimum: 0 },
        skill: { type: "string" },
      },
    },
    Preference: {
      type: "object",
      required: ["day", "shiftId", "weight"],
      additionalProperties: false,
      properties: {
        day: { type: "integer", minimum: 0 },
        shiftId: { type: "string", minLength: 1 },
        weight: { type: "integer" },
      },
    },
  },
} as const;

type AjvLike = {
  validate: (schema: unknown, data: unknown) => boolean;
  errors?: ErrorObject[] | null;
};

let cached: AjvLike | null = null;

function getAjv(): AjvLike {
  if (cached) return cached;
  // Ajv has dual CJS/ESM — handle both.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const AjvCtor = (Ajv as any).default ?? Ajv;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const addFormatsFn = (addFormats as any).default ?? addFormats;
  const ajv = new AjvCtor({ allErrors: true, strict: false });
  addFormatsFn(ajv);
  cached = ajv as AjvLike;
  return cached;
}

export interface ValidationError {
  path: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  data?: NspInstance;
}

function formatPath(err: ErrorObject): string {
  return err.instancePath || err.schemaPath || "/";
}

function formatMessage(err: ErrorObject): string {
  const base = err.message || "invalid";
  if (err.keyword === "required" && err.params && "missingProperty" in err.params) {
    return `missing required property '${(err.params as { missingProperty: string }).missingProperty}'`;
  }
  if (err.keyword === "additionalProperties" && err.params && "additionalProperty" in err.params) {
    return `unexpected property '${(err.params as { additionalProperty: string }).additionalProperty}'`;
  }
  if (err.keyword === "enum" && err.params && "allowedValues" in err.params) {
    return `${base}: ${JSON.stringify((err.params as { allowedValues: unknown[] }).allowedValues)}`;
  }
  return base;
}

export function validateInstance(raw: unknown): ValidationResult {
  if (typeof raw !== "object" || raw === null) {
    return {
      valid: false,
      errors: [{ path: "/", message: "root must be a JSON object" }],
    };
  }
  const ajv = getAjv();
  const ok = ajv.validate(nspInstanceSchema, raw);
  if (ok) {
    return { valid: true, errors: [], data: raw as NspInstance };
  }
  const errors = (ajv.errors ?? []).map((e) => ({
    path: formatPath(e),
    message: formatMessage(e),
  }));
  return { valid: false, errors };
}

export function parseInstanceJson(text: string): ValidationResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (err) {
    return {
      valid: false,
      errors: [
        {
          path: "/",
          message: `invalid JSON: ${err instanceof Error ? err.message : String(err)}`,
        },
      ],
    };
  }
  return validateInstance(parsed);
}
