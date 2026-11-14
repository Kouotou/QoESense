// Free-tier exporter helpers, tested against the same synthetic Android + iOS batches as the
// function tests.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const { decodeBatch, newRows, sampleKey, toCsv } = require("../tools/export_lib");
const { validateRow } = require("./harness");

const FIX = path.join(__dirname, "fixtures");
const gz = (f) => zlib.gzipSync(fs.readFileSync(path.join(FIX, f), "utf8").replace(/\r\n/g, "\n").trim());
const V2_FIELDS = require("./fixtures/bigquery_samples_schema_v2.json").fields;

test("a Firestore batch decodes into rows that fit the BigQuery schema (Android + iOS, v1 + v2)", () => {
  for (const f of ["batch_v1.ndjson", "samples_v2.ndjson"]) {
    const rows = decodeBatch(gz(f));
    assert.ok(rows.length >= 2);
    for (const r of rows) assert.equal(validateRow(r, V2_FIELDS), null, `${f}: ${validateRow(r, V2_FIELDS)}`);
  }
});

test("a retried batch (the same rows uploaded twice) is exported once", () => {
  const seen = new Set();
  const rows = decodeBatch(gz("samples_v2.ndjson"));
  const distinct = new Set(rows.map(sampleKey)).size; // the fixture reuses one deviceId for all four
  assert.equal(newRows(rows, seen).length, distinct);
  assert.equal(newRows(rows, seen).length, 0, "second delivery adds nothing");
});

test("CSV: fixed columns, header once, arrays joined, nulls empty, quoting", () => {
  const rows = [{ a: 1, b: ["RADIO", "RTT"], c: null }, { a: 2, b: [], c: 'x,"y"' }];
  assert.equal(toCsv(rows, ["a", "b", "c"], true), 'a,b,c\n1,RADIO|RTT,\n2,,"x,""y"""\n');
  assert.equal(toCsv([], ["a"], false), "");
});
