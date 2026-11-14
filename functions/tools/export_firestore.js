// Free-tier exporter: replaces loadTelemetryBatch/exportMosResponse while the project has
// no billing. Run it on the researcher's PC whenever fresh data is needed. It only READS
// Firestore and writes local files. Incremental: a checkpoint per device means each batch
// document is read once (this matters for Spark's 50,000 reads/day).
//
//   node tools/export_firestore.js [--out DIR] [--exclude uid1,uid2]
//
// Output (DIR, default C:/Users/<you>/QoESense-exports):
//   samples.ndjson / samples.csv               every telemetry row, dedup on (deviceId, timestampMs)
//   mos_responses.ndjson / mos_responses.csv   every rating, dedup on responseId, the same mapping
//                                              as exportMosResponse (mosRow)
//   checkpoint.json                            last createdAtMs exported per device
// The columns follow the BigQuery schemas exactly, so the files load straight into BigQuery
// (sandbox or paid) or pandas.
const fs = require("fs");
const os = require("os");
const path = require("path");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { mosRow, redactUid } = require("../schema");
const { decodeBatch, newRows, sampleKey, toCsv } = require("./export_lib");
const SAMPLE_COLUMNS = require("../test/fixtures/bigquery_samples_schema_v2.json").fields.map((f) => f.name);
const MOS_COLUMNS = require("../test/fixtures/bigquery_mos_responses_schema_v2.json").fields.map((f) => f.name);

const arg = (name, def) => {
  const i = process.argv.indexOf(name);
  return i > 0 ? process.argv[i + 1] : def;
};
const OUT = arg("--out", path.join(os.homedir(), "QoESense-exports"));
const EXCLUDE = new Set((arg("--exclude", "") || "").split(",").filter(Boolean));

function readNdjsonKeys(file, keyFn) {
  const keys = new Set();
  if (!fs.existsSync(file)) return keys;
  for (const l of fs.readFileSync(file, "utf8").split("\n")) if (l.trim()) keys.add(keyFn(JSON.parse(l)));
  return keys;
}

function append(base, rows, columns) {
  if (!rows.length) return;
  const nd = path.join(OUT, `${base}.ndjson`);
  const csv = path.join(OUT, `${base}.csv`);
  fs.appendFileSync(nd, rows.map((r) => JSON.stringify(r)).join("\n") + "\n");
  fs.appendFileSync(csv, toCsv(rows, columns, !fs.existsSync(csv)));
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const cpFile = path.join(OUT, "checkpoint.json");
  const checkpoint = fs.existsSync(cpFile) ? JSON.parse(fs.readFileSync(cpFile, "utf8")) : {};
  const seenSamples = readNdjsonKeys(path.join(OUT, "samples.ndjson"), sampleKey);
  const seenMos = readNdjsonKeys(path.join(OUT, "mos_responses.ndjson"), (r) => r.responseId);

  initializeApp({ projectId: "qoesense" });
  const db = getFirestore();
  let reads = 0, batches = 0, sampleCount = 0, mosCount = 0, dupes = 0;
  // listDocuments() also returns devices that have subcollections but no document of their own.
  for (const device of await db.collection("devices").listDocuments()) {
    const uid = device.id;
    if (EXCLUDE.has(uid)) continue;
    const since = checkpoint[uid] || 0;
    const snap = await device.collection("telemetryBatches").where("createdAtMs", ">", since).orderBy("createdAtMs").get();
    reads += snap.size;
    for (const doc of snap.docs) {
      const rows = decodeBatch(doc.get("ndjsonGz"));
      const fresh = newRows(rows, seenSamples);
      dupes += rows.length - fresh.length;
      append("samples", fresh, SAMPLE_COLUMNS);
      sampleCount += fresh.length;
      batches++;
      checkpoint[uid] = Math.max(checkpoint[uid] || 0, doc.get("createdAtMs"));
    }
    const mosSnap = await device.collection("mosResponses").get();
    reads += mosSnap.size;
    const mos = newRows(mosSnap.docs.map((d) => mosRow(uid, d.id, d.data())), seenMos, (r) => r.responseId);
    append("mos_responses", mos, MOS_COLUMNS);
    mosCount += mos.length;
    if (snap.size || mos.length) console.log(`  ${redactUid(uid)}: ${snap.size} batches, ${mos.length} new ratings`);
  }
  fs.writeFileSync(cpFile, JSON.stringify(checkpoint, null, 2));
  console.log(`exported ${sampleCount} new samples from ${batches} batches (${dupes} duplicates skipped), ` +
    `${mosCount} new ratings; ~${reads} Firestore reads; output: ${OUT}`);
})().catch((e) => { console.error(e.message); process.exit(1); });
