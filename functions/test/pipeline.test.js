// Phase 5 compatibility tests: the REAL Cloud Function code (three versions) against
// synthetic Android and iOS payloads, with in-memory BigQuery/Storage (see harness.js).
// No network, no production resources. Run: cd functions && npm test
//
// Versions under test:
//   deployed - functions as deployed to production today (commit 63debb0, no `platform`)
//   main     - main branch today (adds nullable `platform`, never deployed)
//   proposed - this branch (contract v2: +20 sample columns, +8 MOS columns, redacted logs)
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");
const { loadFunctions, seedLiveTable, zlib } = require("./harness");

const FIX = path.join(__dirname, "fixtures");
const lines = (f) => fs.readFileSync(path.join(FIX, f), "utf8").replace(/\r\n/g, "\n").trim().split("\n");
const V1_BATCH = lines("batch_v1.ndjson"); // [Android CQI_29 sample, iOS IOS_RAT sample], v1 format
const V2_BATCH = lines("samples_v2.ndjson"); // [Android full, Android degraded, iOS in call, iOS on Wi-Fi]
const MOS_V2 = lines("mos_v2.ndjson").map((l) => JSON.parse(l));
const V1_MOS_KEYS = ["score", "submittedAtMs", "triggerType", "contextAppPackage", "contextSessionType", "windowStartMs", "windowEndMs"];

const VERSIONS = {
  deployed: path.join(__dirname, "versions", "deployed", "index.js"),
  main: path.join(__dirname, "versions", "main", "index.js"),
  proposed: path.join(__dirname, "..", "index.js"),
};

async function upload(env, name, ndjsonLines) {
  env.gcs.put("demo-bucket", name, zlib.gzipSync(Buffer.from(ndjsonLines.join("\n"), "utf8")));
  return env.fns.loadTelemetryBatch.run({ data: { name, bucket: "demo-bucket" } });
}

async function writeMos(env, deviceId, doc) {
  const { responseId, ...body } = doc;
  return env.fns.exportMosResponse.run({ data: { data: () => body }, params: { deviceId, responseId } });
}

const table = (env, id) => env.bq.dataset("qoesense_telemetry").table(id);
const v1Doc = (d) => Object.fromEntries([["responseId", d.responseId], ...V1_MOS_KEYS.map((k) => [k, d[k]])]);

/** Production-shaped tables: schema created by the deployed function, plus existing rows. */
async function productionLikeTables() {
  const seed = loadFunctions(VERSIONS.deployed);
  await upload(seed, "telemetry/old-device/2026-09-03/a.json.gz", V1_BATCH.slice(0, 1));
  await writeMos(seed, "old-device", v1Doc(MOS_V2[0]));
  return {
    samplesFields: table(seed, "samples").fields,
    mosFields: table(seed, "mos_responses").fields,
    sampleRows: table(seed, "samples").rows,
    mosRows: table(seed, "mos_responses").rows,
  };
}

test("deployed (production today): Android + iOS v1 batches load - iOS radio fields simply NULL", async () => {
  const env = loadFunctions(VERSIONS.deployed);
  await upload(env, "telemetry/uid-123/2026-11-11/b1.json.gz", V1_BATCH);
  const rows = table(env, "samples").rows;
  assert.equal(rows.length, 2);
  const ios = rows.find((r) => r.telemetryCapabilityTier === "IOS_RAT");
  assert.equal(ios.rsrp, null);
  assert.equal(ios.cqiSupported, false);
  assert.equal(table(env, "samples").fields.length, 28);
});

test("deployed: a v2 batch is rejected as a whole (unknown fields) - why clients stay on v1 until the deploy", async () => {
  const env = loadFunctions(VERSIONS.deployed);
  await assert.rejects(upload(env, "telemetry/uid-123/2026-11-11/b2.json.gz", V2_BATCH), /no such field: schemaVersion/);
  assert.equal(table(env, "samples").rows.length, 0, "load jobs are atomic: nothing appended");
});

test("deployed: a v2 MOS document is exported with its 9 known columns; the extra fields are ignored", async () => {
  const env = loadFunctions(VERSIONS.deployed);
  await writeMos(env, "uid-ios", MOS_V2[1]);
  const row = table(env, "mos_responses").rows[0];
  assert.deepEqual(Object.keys(row).sort(), ["deviceId", "responseId", ...V1_MOS_KEYS].sort());
  assert.equal(row.contextAppPackage, "com.whatsapp");
});

test("main (never deployed): adds `platform` to a live table; v1 still loads, v2 still rejected", async () => {
  const live = await productionLikeTables();
  const env = loadFunctions(VERSIONS.main);
  seedLiveTable(env.bq, "samples", live.samplesFields, live.sampleRows);
  await upload(env, "telemetry/uid-123/2026-11-11/b1.json.gz", V1_BATCH);
  assert.ok(table(env, "samples").fields.some((f) => f.name === "platform"));
  assert.equal(table(env, "samples").rows.length, 3);
  await assert.rejects(upload(env, "telemetry/uid-123/2026-11-11/b2.json.gz", V2_BATCH), /no such field: schemaVersion/);
});

test("proposed: reconciles a production-shaped table additively (+21 columns, REPEATED allowed, old rows untouched)", async () => {
  const live = await productionLikeTables();
  const env = loadFunctions(VERSIONS.proposed);
  const t = seedLiveTable(env.bq, "samples", live.samplesFields, live.sampleRows);
  const before = JSON.stringify(t.rows);
  await upload(env, "telemetry/uid-123/2026-11-11/b1.json.gz", V1_BATCH);
  assert.equal(t.fields.length, 49);
  assert.deepEqual(t.fields.slice(0, 28), live.samplesFields, "v1 columns unchanged, same order");
  assert.equal(t.fields.find((f) => f.name === "capabilitySet").mode, "REPEATED");
  assert.ok(t.fields.slice(28).every((f) => (f.mode || "NULLABLE") !== "REQUIRED"));
  assert.equal(JSON.stringify(t.rows.slice(0, 1)), before, "existing rows are not rewritten");
});

test("proposed: old clients keep working - v1 Android and iOS lines load after the v2 deploy", async () => {
  const env = loadFunctions(VERSIONS.proposed);
  await upload(env, "telemetry/uid-123/2026-11-11/b1.json.gz", V1_BATCH);
  const rows = table(env, "samples").rows;
  assert.equal(rows.length, 2);
  assert.ok(rows.every((r) => r.schemaVersion === undefined), "v2 columns stay NULL for v1 rows");
});

test("proposed: v2 Android + iOS batches load, with statuses, capabilitySet arrays and no iOS radio values", async () => {
  const env = loadFunctions(VERSIONS.proposed);
  await upload(env, "telemetry/uid-123/2026-11-11/b2.json.gz", V2_BATCH);
  const rows = table(env, "samples").rows;
  assert.equal(rows.length, 4);
  const ios = rows.filter((r) => r.platform === "IOS");
  assert.equal(ios.length, 2);
  for (const r of ios) {
    for (const k of ["rsrp", "rsrq", "rssnr", "timingAdvance", "cellIdentityHash", "cqi", "isRoaming", "foregroundAppPackage"]) {
      assert.equal(r[k], null, `${k} must be NULL on iOS`);
    }
    assert.equal(r.radioStatus, "NOT_SUPPORTED");
    assert.equal(r.telemetryCapabilityTier, "IOS_RAT");
    assert.ok(Array.isArray(r.capabilitySet) && !r.capabilitySet.includes("RADIO"));
  }
  assert.equal(rows.find((r) => r.radioStatus === "STALE").cellInfoAgeMs, 95000);
});

test("proposed: a batch with a broken line is rejected as a whole (REQUIRED field missing) - nothing half-loaded", async () => {
  const env = loadFunctions(VERSIONS.proposed);
  const broken = JSON.parse(V2_BATCH[0]);
  delete broken.deviceModel;
  await assert.rejects(
    upload(env, "telemetry/uid-123/2026-11-11/bad.json.gz", [V2_BATCH[1], JSON.stringify(broken)]),
    /missing required field: deviceModel/,
  );
  assert.equal(table(env, "samples").rows.length, 0);
});

test("all versions ignore objects outside telemetry/ or not .json.gz", async () => {
  for (const v of Object.values(VERSIONS)) {
    const env = loadFunctions(v);
    await env.fns.loadTelemetryBatch.run({ data: { name: "uploads/x.png", bucket: "demo-bucket" } });
    assert.equal(table(env, "samples").fields, null, "no table touched");
  }
});

test("proposed MOS: v1 documents export unchanged; v2 documents export all 18 columns", async () => {
  const live = await productionLikeTables();
  const env = loadFunctions(VERSIONS.proposed);
  const t = seedLiveTable(env.bq, "mos_responses", live.mosFields, live.mosRows);
  await writeMos(env, "uid-android-old", v1Doc(MOS_V2[0]));
  await writeMos(env, "uid-ios", MOS_V2[1]);
  assert.equal(t.fields.length, 18);
  const [, oldClient, newClient] = t.rows;
  assert.deepEqual(Object.keys(oldClient).sort(), ["deviceId", "responseId", ...V1_MOS_KEYS].sort());
  assert.equal(newClient.platform, "IOS");
  assert.equal(newClient.contextSource, "SELF_REPORTED");
  assert.equal(newClient.presentationMode, "NOTIFICATION_ACTION");
  assert.equal(newClient.promptedAtMs, 1762500250000);
});

test("proposed MOS: null optional fields are omitted, not inserted as explicit NULL", async () => {
  const env = loadFunctions(VERSIONS.proposed);
  await writeMos(env, "uid-ios", MOS_V2[2]); // contextAppPackage null, UNKNOWN context
  const row = table(env, "mos_responses").rows[0];
  assert.equal(row.contextAppPackage, null);
  assert.equal(row.contextSource, "UNKNOWN");
});

test("privacy: proposed logs never contain a raw participant UID (deployed logs do)", async () => {
  const uid = "UD1DfRM9K9Wow7GaL8BrGfbSFiG2";
  const run = async (v) => {
    const env = loadFunctions(v);
    await upload(env, `telemetry/${uid}/2026-11-11/b1.json.gz`, V1_BATCH);
    await writeMos(env, uid, MOS_V2[0]);
    return env.logs.join("\n");
  };
  assert.ok((await run(VERSIONS.deployed)).includes(uid), "characterises today's behaviour");
  const proposed = await run(VERSIONS.proposed);
  assert.ok(!proposed.includes(uid), proposed);
  assert.match(proposed, /uid#[0-9a-f]{8}/);
});

test("characterisation: the same batch file finalized twice loads twice (why a dedup view is proposed)", async () => {
  const env = loadFunctions(VERSIONS.proposed);
  await upload(env, "telemetry/uid-123/2026-11-11/same.json.gz", V1_BATCH);
  await upload(env, "telemetry/uid-123/2026-11-11/same.json.gz", V1_BATCH);
  assert.equal(table(env, "samples").rows.length, 4);
});
