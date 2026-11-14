// Read-only backup of every Firestore document under devices/ (consent records, MOS ratings,
// telemetry batches) to a local JSON file. Bytes fields are written as base64.
//   node tools/backup_firestore.js <output.json>
const fs = require("fs");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

async function dump(ref, out) {
  const snap = await ref.get();
  if (snap.exists) out.push({ path: ref.path, data: snap.data() });
  for (const sub of await ref.listCollections()) {
    for (const child of await sub.listDocuments()) await dump(child, out);
  }
}

(async () => {
  initializeApp({ projectId: "qoesense" });
  const db = getFirestore();
  const out = [];
  for (const top of await db.listCollections()) {
    for (const d of await top.listDocuments()) await dump(d, out);
  }
  const json = JSON.stringify(out, (k, v) => (v && v.type === "Buffer" ? { base64: Buffer.from(v.data).toString("base64") } : v), 2);
  fs.writeFileSync(process.argv[2], json);
  const byKind = {};
  for (const d of out) {
    const kind = d.path.split("/").length === 2 ? "device docs" : d.path.split("/")[2];
    byKind[kind] = (byKind[kind] || 0) + 1;
  }
  console.log(`backed up ${out.length} documents`, byKind);
})().catch((e) => { console.error(e.message); process.exit(1); });
