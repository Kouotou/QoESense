// One-off backfill: MOS ratings that are in Firestore but missing from BigQuery because
// exportMosResponse could not run while billing was disabled (Firestore never replays
// onDocumentCreated events). Uses the same mosRow() mapping as the live function.
//
// SAFE BY DEFAULT: without --apply it only READS (Firestore + BigQuery) and prints a report.
// With --apply it inserts only the responseIds not already in BigQuery (idempotent: running
// it twice inserts nothing the second time). Needs Application Default Credentials for the
// qoesense project (gcloud auth application-default login).
//
//   node tools/backfill_mos.js            # dry run (default)
//   node tools/backfill_mos.js --apply    # after approval, after the v2 functions deploy
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { BigQuery } = require("@google-cloud/bigquery");
const { mosRow, redactUid } = require("../schema");

const PROJECT = "qoesense";
const apply = process.argv.includes("--apply");

async function main() {
  initializeApp({ projectId: PROJECT });
  const bq = new BigQuery({ projectId: PROJECT });
  const [existing] = await bq.query({
    query: "SELECT DISTINCT responseId FROM `qoesense.qoesense_telemetry.mos_responses`",
    location: "africa-south1",
  });
  const have = new Set(existing.map((r) => r.responseId));
  const snap = await getFirestore().collectionGroup("mosResponses").get();
  const missing = [];
  for (const doc of snap.docs) {
    if (have.has(doc.id)) continue;
    const deviceId = doc.ref.parent.parent.id;
    missing.push(mosRow(deviceId, doc.id, doc.data()));
  }
  const byDevice = {};
  for (const r of missing) byDevice[redactUid(r.deviceId)] = (byDevice[redactUid(r.deviceId)] || 0) + 1;
  console.log(`Firestore ratings: ${snap.size}; already in BigQuery: ${have.size}; missing: ${missing.length}`);
  console.log("missing per device (redacted):", byDevice);
  if (!apply) {
    console.log("Dry run - nothing written. Re-run with --apply after approval.");
    return;
  }
  const table = bq.dataset("qoesense_telemetry").table("mos_responses");
  for (let i = 0; i < missing.length; i += 500) await table.insert(missing.slice(i, i + 500));
  console.log(`Inserted ${missing.length} rows.`);
}

main().catch((e) => { console.error(e.message); process.exit(1); });
