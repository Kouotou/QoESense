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

/**
 * Whether an existing CSV still has the current header. After an additive column (such as
 * cellChangesSinceLastSample), appending rows under the old header would misalign every
 * later row, so the CSV is rebuilt from the NDJSON instead.
 */
const csvHeaderMatches = (firstLine, columns) => (firstLine || "").replace(/\r$/, "") === columns.join(",");

/**
 * The --delete-exported pass (Option A, 2026-11-14): deletes every telemetry batch document
 * at or before its device's checkpoint, i.e. already written to the export files. It runs
 * only after those files and the checkpoint are saved, and it's idempotent: an interrupted
 * pass is finished by the next run. Devices without a checkpoint, or excluded, are untouched.
 * Rating documents (mosResponses) are never deleted.
 *
 * Reads matter on Spark (50,000/day): the batches exported in this run are passed in
 * `exported` (uid -> DocumentReferences) and deleted without reading them again. The sweep
 * query afterwards then normally finds nothing, which costs 1 read per device; it only
 * returns documents left over by an interrupted earlier pass.
 *
 * `devices` are Firestore DocumentReferences (or test fakes with the same shape);
 * `newBatch` returns a WriteBatch. Returns the number of deleted documents.
 */
async function deleteExported({
  devices, checkpoint, exported = new Map(), exclude = new Set(), newBatch, chunk = 400, log = () => {},
}) {
  const deleteAll = async (refs) => {
    for (let i = 0; i < refs.length; i += chunk) {
      const batch = newBatch();
      for (const ref of refs.slice(i, i + chunk)) batch.delete(ref);
      await batch.commit();
    }
    return refs.length;
  };
  let deleted = 0;
  for (const device of devices) {
    const cp = checkpoint[device.id];
    if (!cp || exclude.has(device.id)) continue;
    let n = await deleteAll(exported.get(device.id) || []);
    const leftovers = await device.collection("telemetryBatches").where("createdAtMs", "<=", cp).get();
    n += await deleteAll(leftovers.docs.map((d) => d.ref));
    if (n) log(device.id, n);
    deleted += n;
  }
  return deleted;
}

module.exports = { csvHeaderMatches, decodeBatch, deleteExported, newRows, sampleKey, toCsv };
