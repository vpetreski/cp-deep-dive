// Validates each file in schemas/*.schema.json against the JSON Schema 2020-12 spec
// and ensures Ajv can compile it (catching malformed refs, unknown keywords, etc).
//
// Usage: `npm run validate:schemas` from apps/shared/.

import { readdirSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCHEMAS_DIR = resolve(__dirname, "..", "schemas");

function loadSchemas() {
  const files = readdirSync(SCHEMAS_DIR)
    .filter((f) => f.endsWith(".schema.json"))
    .sort();
  return files.map((file) => {
    const fullPath = join(SCHEMAS_DIR, file);
    const contents = readFileSync(fullPath, "utf8");
    let parsed;
    try {
      parsed = JSON.parse(contents);
    } catch (err) {
      throw new Error(`${file}: invalid JSON — ${err.message}`);
    }
    return { file, fullPath, schema: parsed };
  });
}

function main() {
  const schemas = loadSchemas();
  if (schemas.length === 0) {
    throw new Error(`No *.schema.json files found under ${SCHEMAS_DIR}`);
  }

  const ajv = new Ajv2020({
    strict: true,
    allErrors: true,
    allowUnionTypes: true,
  });
  addFormats.default(ajv);

  // Register every schema so that cross-file $refs resolve (e.g. solve-response
  // references schedule.schema.json).
  for (const { file, schema } of schemas) {
    if (!schema.$id) {
      throw new Error(`${file}: missing top-level "$id"`);
    }
    // Allow referencing schemas by filename (sibling $refs we already use).
    ajv.addSchema(schema, file);
  }

  let failed = 0;
  for (const { file, schema } of schemas) {
    try {
      // compile() verifies the schema is itself a valid JSON Schema and
      // that all refs resolve.
      ajv.compile(schema);
      console.log(`ok  ${file}`);
    } catch (err) {
      failed += 1;
      console.error(`FAIL ${file}: ${err.message}`);
    }
  }

  if (failed > 0) {
    console.error(`\n${failed} schema(s) failed validation.`);
    process.exit(1);
  }
  console.log(`\n${schemas.length} schema(s) validated against JSON Schema 2020-12.`);
}

main();

// Quiet unused-imports warning for the URL helper we keep around for future use.
void pathToFileURL;
