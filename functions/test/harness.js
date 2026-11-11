// Controlled test environment for the Cloud Functions: in-memory BigQuery and Cloud Storage
// fakes that apply the same rules as the real services where it matters, so the real
// function code can run against synthetic Android/iOS payloads without touching production.
//
// BigQuery rules emulated (from the BigQuery docs for NDJSON load jobs and streaming inserts):
// - load with ignoreUnknownValues:false: any key not in the schema fails the WHOLE job, and
//   nothing is appended (load jobs are atomic);
// - REQUIRED columns must be present and non-null; types must match (INTEGER, FLOAT, BOOLEAN,
//   STRING, and REPEATED arrays);
// - a load job's schema must not contain columns the table doesn't have (no
//   ALLOW_FIELD_ADDITION is set), which is why ensureTable() reconciles first;
// - a streaming insert of an unknown column fails.
const path = require("path");
const zlib = require("zlib");

function typeOk(type, v) {
  switch (type) {
    case "INTEGER": return Number.isInteger(v);
    case "FLOAT": return typeof v === "number";
    case "BOOLEAN": return typeof v === "boolean";
    case "STRING": return typeof v === "string";
    default: return false;
  }
}

function validateRow(row, fields) {
  const byName = new Map(fields.map((f) => [f.name, f]));
  for (const k of Object.keys(row)) if (!byName.has(k)) return `no such field: ${k}`;
  for (const f of fields) {
    const v = row[f.name];
    const mode = f.mode || "NULLABLE";
    if (v === undefined || v === null) {
      if (mode === "REQUIRED") return `missing required field: ${f.name}`;
      continue;
    }
    if (mode === "REPEATED") {
      if (!Array.isArray(v) || !v.every((e) => typeOk(f.type, e))) return `bad repeated value for ${f.name}`;
    } else if (!typeOk(f.type, v)) {
      return `bad ${f.type} value for ${f.name}: ${JSON.stringify(v)}`;
    }
  }
  return null;
}

class FakeTable {
  constructor(id) {
    this.id = id;
    this.fields = null;
    this.rows = [];
  }
  async exists() { return [this.fields !== null]; }
  async getMetadata() { return [{ schema: { fields: this.fields } }]; }
  async setMetadata({ schema }) {
    const have = new Set(this.fields.map((f) => f.name));
    for (const f of this.fields) {
      if (!schema.fields.some((n) => n.name === f.name)) throw new Error(`cannot drop column ${f.name}`);
    }
    for (const f of schema.fields) {
      if (!have.has(f.name) && (f.mode || "NULLABLE") === "REQUIRED") throw new Error(`cannot add REQUIRED ${f.name}`);
    }
    this.fields = schema.fields.map((f) => ({ ...f }));
    return [{}];
  }
  async load(file, opts) {
    const errors = [];
    const tableNames = new Set(this.fields.map((f) => f.name));
    const extra = (opts.schema?.fields || []).filter((f) => !tableNames.has(f.name));
    if (extra.length) errors.push({ message: `Provided Schema does not match Table: ${extra.map((f) => f.name)}` });
    const text = zlib.gunzipSync(file.contents).toString("utf8");
    const parsed = [];
    text.split("\n").forEach((line, i) => {
      if (!line.trim()) return;
      let row;
      try { row = JSON.parse(line); } catch (e) { errors.push({ message: `line ${i + 1}: invalid JSON` }); return; }
      const err = validateRow(row, opts.schema.fields);
      if (err) errors.push({ message: `line ${i + 1}: ${err}` }); else parsed.push(row);
    });
    if (opts.ignoreUnknownValues !== false) throw new Error("tests expect ignoreUnknownValues:false");
    if (errors.length === 0) this.rows.push(...parsed); // atomic: all or nothing
    this.loads = (this.loads || 0) + 1;
    return [{ status: errors.length ? { errors } : {} }];
  }
  async insert(rows) {
    for (const r of rows) {
      const err = validateRow(r, this.fields);
      if (err) {
        const e = new Error(`PartialFailureError: ${err}`);
        e.name = "PartialFailureError";
        throw e;
      }
    }
    this.rows.push(...rows);
    return [{}];
  }
}

class FakeDataset {
  constructor() { this.tables = new Map(); this.exist = false; }
  async exists() { return [this.exist]; }
  table(id) {
    if (!this.tables.has(id)) this.tables.set(id, new FakeTable(id));
    return this.tables.get(id);
  }
  async createTable(id, { schema }) {
    this.table(id).fields = schema.fields.map((f) => ({ ...f }));
    return [this.table(id)];
  }
}

class FakeBigQuery {
  constructor() { this.datasets = new Map(); }
  dataset(id) {
    if (!this.datasets.has(id)) this.datasets.set(id, new FakeDataset());
    return this.datasets.get(id);
  }
  async createDataset(id) { this.dataset(id).exist = true; return [this.dataset(id)]; }
}

class FakeStorage {
  constructor() { this.objects = new Map(); }
  put(bucket, name, buffer) { this.objects.set(`${bucket}/${name}`, buffer); }
  bucket(b) {
    return { file: (name) => ({ name, bucket: b, contents: this.objects.get(`${b}/${name}`) }) };
  }
}

const FN_DIR = path.join(__dirname, "..");
const BQ_PATH = require.resolve("@google-cloud/bigquery", { paths: [FN_DIR] });
const GCS_PATH = require.resolve("@google-cloud/storage", { paths: [FN_DIR] });

/**
 * Loads one version of index.js with the fakes injected in place of the Google clients.
 * Returns the exported triggers, the fakes and the captured log lines.
 */
function loadFunctions(indexPath) {
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: "demo-qoesense", storageBucket: "demo-bucket" });
  process.env.GCLOUD_PROJECT = "demo-qoesense";
  const bq = new FakeBigQuery();
  const gcs = new FakeStorage();
  require.cache[BQ_PATH] = { id: BQ_PATH, filename: BQ_PATH, loaded: true, exports: { BigQuery: function () { return bq; } } };
  require.cache[GCS_PATH] = { id: GCS_PATH, filename: GCS_PATH, loaded: true, exports: { Storage: function () { return gcs; } } };
  const resolved = require.resolve(indexPath);
  for (const k of Object.keys(require.cache)) {
    if (k.startsWith(path.dirname(resolved)) && !k.includes("node_modules")) delete require.cache[k];
  }
  const logs = [];
  const { logger } = require(require.resolve("firebase-functions", { paths: [FN_DIR] }));
  for (const level of ["info", "warn", "error"]) logger[level] = (...args) => logs.push([level, ...args].map(String).join(" "));
  const fns = require(resolved);
  return { fns, bq, gcs, logs };
}

/** A table shaped like production today: the deployed schema, with existing rows. */
function seedLiveTable(bq, tableId, fields, rows) {
  const ds = bq.dataset("qoesense_telemetry");
  ds.exist = true;
  const t = ds.table(tableId);
  t.fields = fields.map((f) => ({ ...f }));
  t.rows.push(...rows);
  return t;
}

module.exports = { loadFunctions, seedLiveTable, validateRow, zlib };
