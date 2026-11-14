// Free-tier exporter: replaces loadTelemetryBatch/exportMosResponse while the project has
// no billing. Run it on the researcher's PC whenever fresh data is needed. It only READS
// Firestore and writes local files. Incremental: a checkpoint per device means each batch
// document is read once (this matters for Spark's 50,000 reads/day).
//
//   node tools/export_firestore.js [--out DIR] [--exclude uid1,uid2] [--delete-exported]
//
// --delete-exported (Option A, 2026-11-14; off by default): after the files and the checkpoint
// are saved, deletes the telemetry batch documents already exported, so the free 1 GiB of
// Firestore storage never fills up. Those rows then exist ONLY in the export folder: back it
// up (docs/FREE_TIER_MIGRATION.md in QoESense-CrossPlatform). Ratings are never deleted.
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
const { csvHeaderMatches, decodeBatch, deleteExported, newRows, sampleKey, toCsv } = require("./export_lib");
const SAMPLE_COLUMNS = require("../test/fixtures/bigquery_samples_schema_v2.json").fields.map((f) => f.name);
const MOS_COLUMNS = require("../test/fixtures/bigquery_mos_responses_schema_v2.json").fields.map((f) => f.name);

const arg = (name, def) => {
  const i = process.argv.indexOf(name);
  return i > 0 ? process.argv[i + 1] : def;
};
const OUT = arg("--out", path.join(os.homedir(), "QoESense-exports"));
const EXCLUDE = new Set((arg("--exclude", "") || "").split(",").filter(Boolean));
const DELETE_EXPORTED = process.argv.includes("--delete-exported");

function readNdjsonKeys(file, keyFn) {
  const keys = new Set();
  if (!fs.existsSync(file)) return keys;
  for (const l of fs.readFileSync(file, "utf8").split("\n")) if (l.trim()) keys.add(keyFn(JSON.parse(l)));
  return keys;
}

/** Rebuilds base.csv from base.ndjson when its header predates a new column. */
function migrateCsv(base, columns) {
  const nd = path.join(OUT, `${base}.ndjson`);
  const csv = path.join(OUT, `${base}.csv`);
  if (!fs.existsSync(csv) || !fs.existsSync(nd)) return;
  const firstLine = fs.readFileSync(csv, "utf8").split("\n", 1)[0];
  if (csvHeaderMatches(firstLine, columns)) return;
  const rows = fs.readFileSync(nd, "utf8").split("\n").filter((l) => l.trim()).map((l) => JSON.parse(l));
  fs.writeFileSync(csv, toCsv(rows, columns, true));
  console.log(`rebuilt ${base}.csv with the current columns (${rows.length} rows)`);
}

/** Flushes a file to disk before anything is deleted from Firestore. */
function fsyncFile(file) {
  if (!fs.existsSync(file)) return;
  const fd = fs.openSync(file, "r+");
  try {
    fs.fsyncSync(fd);
  } finally {
    fs.closeSync(fd);
  }
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
  migrateCsv("samples", SAMPLE_COLUMNS);
  migrateCsv("mos_responses", MOS_COLUMNS);
  const cpFile = path.join(OUT, "checkpoint.json");
  const checkpoint = fs.existsSync(cpFile) ? JSON.parse(fs.readFileSync(cpFile, "utf8")) : {};
  const seenSamples = readNdjsonKeys(path.join(OUT, "samples.ndjson"), sampleKey);
  const seenMos = readNdjsonKeys(path.join(OUT, "mos_responses.ndjson"), (r) => r.responseId);

  initializeApp({ projectId: "qoesense" });
  const db = getFirestore();
  let reads = 0, batches = 0, sampleCount = 0, mosCount = 0, dupes = 0;
  // listDocuments() also returns devices that have subcollections but no document of their own.
  const devices = await db.collection("devices").listDocuments();
  const exportedRefs = new Map(); // uid -> batch documents written to the files in this run
  for (const device of devices) {
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
      if (!exportedRefs.has(uid)) exportedRefs.set(uid, []);
      exportedRefs.get(uid).push(doc.ref);
    }
    const mosSnap = await device.collection("mosResponses").get();
    reads += mosSnap.size;
    const mos = newRows(mosSnap.docs.map((d) => mosRow(uid, d.id, d.data())), seenMos, (r) => r.responseId);
    append("mos_responses", mos, MOS_COLUMNS);
    mosCount += mos.length;
    if (snap.size || mos.length) console.log(`  ${redactUid(uid)}: ${snap.size} batches, ${mos.length} new ratings`);
  }
  for (const f of ["samples.ndjson", "samples.csv", "mos_responses.ndjson", "mos_responses.csv"]) {
    fsyncFile(path.join(OUT, f));
  }
  fs.writeFileSync(cpFile, JSON.stringify(checkpoint, null, 2));
  fsyncFile(cpFile);
  console.log(`exported ${sampleCount} new samples from ${batches} batches (${dupes} duplicates skipped), ` +
    `${mosCount} new ratings; ~${reads} Firestore reads; output: ${OUT}`);

  if (DELETE_EXPORTED) {
    const deleted = await deleteExported({
      devices,
      checkpoint,
      exported: exportedRefs,
      exclude: EXCLUDE,
      newBatch: () => db.batch(),
      log: (uid, n) => console.log(`  ${redactUid(uid)}: deleted ${n} exported batch documents`),
    });
    console.log(`--delete-exported: ${deleted} batch documents deleted from Firestore. ` +
      `Those rows now exist only in ${OUT}: back that folder up.`);
  }
})().catch((e) => { console.error(e.message); process.exit(1); });
