// Pure helpers for the free-tier exporter (export_firestore.js). No Firebase here, so they are
// unit-tested in test/export.test.js.
const zlib = require("zlib");

/** A batch document's gzipped NDJSON bytes -> sample rows (each already carries deviceId). */
function decodeBatch(gz) {
  const text = zlib.gunzipSync(gz).toString("utf8");
  return text.split("\n").filter((l) => l.trim()).map((l) => JSON.parse(l));
}

const sampleKey = (r) => `${r.deviceId}|${r.timestampMs}`;

/**
 * Keeps only rows not seen before: dedup on (deviceId, timestampMs), the same key as the
 * proposed BigQuery view. This removes the duplicates an upload retry after a lost
 * acknowledgement can create.
 */
function newRows(rows, seenKeys, keyFn = sampleKey) {
  const out = [];
  for (const r of rows) {
    const k = keyFn(r);
    if (seenKeys.has(k)) continue;
    seenKeys.add(k);
    out.push(r);
  }
  return out;
}

/** CSV with a fixed column order. Arrays (capabilitySet) are joined with "|"; null is empty. */
function toCsv(rows, columns, withHeader) {
  const cell = (v) => {
    if (v === null || v === undefined) return "";
    const s = Array.isArray(v) ? v.join("|") : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = rows.map((r) => columns.map((c) => cell(r[c])).join(","));
  return (withHeader ? [columns.join(","), ...lines] : lines).join("\n") + (lines.length ? "\n" : "");
}

module.exports = { decodeBatch, newRows, sampleKey, toCsv };
