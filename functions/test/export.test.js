// Free-tier exporter helpers, tested against the same synthetic Android + iOS batches as the
// function tests.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const { csvHeaderMatches, decodeBatch, deleteExported, newRows, sampleKey, toCsv } = require("../tools/export_lib");
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

test("CSV header check: a new column (cellChangesSinceLastSample) forces a rebuild", () => {
  const cols = V2_FIELDS.map((f) => f.name);
  assert.equal(cols[cols.length - 1], "cellChangesSinceLastSample");
  assert.ok(csvHeaderMatches(cols.join(",") + "\r", cols), "CRLF tolerated");
  assert.ok(!csvHeaderMatches(cols.slice(0, -1).join(","), cols));
  assert.ok(!csvHeaderMatches(undefined, cols));
});

/** In-memory stand-in for devices/{uid}/telemetryBatches, with the API deleteExported uses. */
function fakeFirestore(docsByDevice) {
  const store = new Map(Object.entries(docsByDevice).map(([uid, docs]) => [uid, new Map(docs.map((d) => [d.id, d]))]));
  const commits = [];
  let reads = 0;
  const devices = [...store.keys()].map((uid) => ({
    id: uid,
    collection: (name) => {
      assert.equal(name, "telemetryBatches");
      return {
        where: (field, op, value) => {
          assert.deepEqual([field, op], ["createdAtMs", "<="]);
          return {
            get: async () => {
              const docs = [...store.get(uid).values()]
                .filter((d) => d.createdAtMs <= value)
                .map((d) => ({ ref: { uid, id: d.id } }));
              reads += Math.max(1, docs.length); // Firestore bills an empty query as 1 read
              return { docs };
            },
          };
        },
      };
    },
  }));
  const newBatch = () => {
    const pending = [];
    return {
      delete: (ref) => pending.push(ref),
      commit: async () => {
        commits.push(pending.length);
        for (const ref of pending) store.get(ref.uid).delete(ref.id);
      },
    };
  };
  return { store, devices, newBatch, commits, reads: () => reads };
}

test("--delete-exported removes only batches at or before each checkpoint, in bounded chunks", async () => {
  const many = Array.from({ length: 5 }, (_, i) => ({ id: `a${i}`, createdAtMs: 100 + i }));
  const db = fakeFirestore({
    uidA: [...many, { id: "a-new", createdAtMs: 999 }],
    uidB: [{ id: "b1", createdAtMs: 50 }],
    uidTest: [{ id: "t1", createdAtMs: 10 }],
    uidNoCheckpoint: [{ id: "n1", createdAtMs: 10 }],
  });
  const deleted = await deleteExported({
    devices: db.devices,
    checkpoint: { uidA: 104, uidB: 50, uidTest: 10 },
    exclude: new Set(["uidTest"]),
    newBatch: db.newBatch,
    chunk: 2,
  });
  assert.equal(deleted, 6);
  assert.deepEqual([...db.store.get("uidA").keys()], ["a-new"], "a batch newer than the checkpoint is kept");
  assert.equal(db.store.get("uidB").size, 0);
  assert.equal(db.store.get("uidTest").size, 1, "excluded devices are untouched");
  assert.equal(db.store.get("uidNoCheckpoint").size, 1, "never exported, never deleted");
  assert.deepEqual(db.commits, [2, 2, 1, 1]);
  assert.equal(db.reads(), 5 + 1, "without known refs every leftover costs a read; skipped devices cost nothing");
  const again = await deleteExported({ devices: db.devices, checkpoint: { uidA: 104 }, newBatch: db.newBatch });
  assert.equal(again, 0, "idempotent: a second pass finds nothing");
});

test("--delete-exported deletes this run's batches without reading them again", async () => {
  const docs = Array.from({ length: 450 }, (_, i) => ({ id: `d${i}`, createdAtMs: i + 1 }));
  const db = fakeFirestore({ uidA: docs });
  const exported = new Map([["uidA", docs.map((d) => ({ uid: "uidA", id: d.id }))]]);
  const deleted = await deleteExported({ devices: db.devices, checkpoint: { uidA: 450 }, exported, newBatch: db.newBatch });
  assert.equal(deleted, 450);
  assert.equal(db.store.get("uidA").size, 0);
  assert.deepEqual(db.commits, [400, 50], "Firestore batches stay under the 500-write limit");
  assert.equal(db.reads(), 1, "only the (empty) leftover sweep is read");
});
