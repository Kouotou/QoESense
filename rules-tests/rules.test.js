// Security-rules tests on the local Firebase emulators (project "demo-qoesense": the demo-
// prefix guarantees the emulators never reach production). Run: npm test (in rules-tests/).
// Part 1 characterises the CURRENT production rules; part 2 checks the PROPOSED rules.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");

const FIX = path.join(__dirname, "..", "functions", "test", "fixtures", "mos_v2.ndjson");
const MOS = fs.readFileSync(FIX, "utf8").trim().split(/\r?\n/).map((l) => JSON.parse(l));
const body = (d) => { const { responseId, ...b } = d; return b; };
const V1_KEYS = ["score", "submittedAtMs", "triggerType", "contextAppPackage", "contextSessionType", "windowStartMs", "windowEndMs"];
const v1 = (d) => Object.fromEntries(V1_KEYS.map((k) => [k, d[k]]));
const GZ = new Uint8Array([0x1f, 0x8b, 8, 0, 0, 0, 0, 0, 0, 3, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0]); // empty gzip
const FILE = "telemetry/alice/2026-11-11/3f2a9c1e-7b4d-4e5f-9a8b-0c1d2e3f4a5b.json.gz";

async function env(firestoreRules, storageRules) {
  return initializeTestEnvironment({
    projectId: "demo-qoesense",
    firestore: { rules: fs.readFileSync(firestoreRules, "utf8"), host: "127.0.0.1", port: 8085 },
    storage: { rules: fs.readFileSync(storageRules, "utf8"), host: "127.0.0.1", port: 9199 },
  });
}

const mosDoc = (ctx, uid, id) => ctx.firestore().doc(`devices/${uid}/mosResponses/${id}`);

test("CURRENT production rules", async (t) => {
  const e = await env(path.join(__dirname, "..", "firestore.rules"), path.join(__dirname, "..", "storage.rules"));
  const alice = e.authenticatedContext("alice");
  const bob = e.authenticatedContext("bob");
  const anon = e.unauthenticatedContext();

  await t.test("owner can write a v1 (Android reference) and a v2 (iOS) rating", async () => {
    await assertSucceeds(mosDoc(alice, "alice", "r1").set(v1(MOS[0])));
    await assertSucceeds(mosDoc(alice, "alice", "r2").set(body(MOS[1])));
  });
  await t.test("another device or a signed-out client cannot", async () => {
    await assertFails(mosDoc(bob, "alice", "r3").set(v1(MOS[0])));
    await assertFails(mosDoc(anon, "alice", "r3").set(v1(MOS[0])));
    await assertFails(mosDoc(bob, "alice", "r1").get());
  });
  await t.test("consent merge works", async () => {
    await assertSucceeds(alice.firestore().doc("devices/alice").set({ consentGivenAtMs: 1 }, { merge: true }));
  });
  await t.test("WEAKNESS: an out-of-range score and deleting a rating are both allowed today", async () => {
    await assertSucceeds(mosDoc(alice, "alice", "bad").set({ ...v1(MOS[0]), score: 9 }));
    await assertSucceeds(mosDoc(alice, "alice", "r1").delete());
  });
  await t.test("storage: owner can upload a batch, others cannot, nobody can read", async () => {
    await assertSucceeds(alice.storage().ref(FILE).put(GZ));
    await assertFails(bob.storage().ref(FILE).put(GZ));
    await assertFails(alice.storage().ref(FILE).getMetadata());
  });
  await t.test("WEAKNESS: today an existing batch can be overwritten or deleted by its owner", async () => {
    await assertSucceeds(alice.storage().ref(FILE).put(GZ));
    await assertSucceeds(alice.storage().ref(FILE).delete());
  });
  await e.clearFirestore();
  await e.clearStorage();
  await e.cleanup();
});

test("PROPOSED rules", async (t) => {
  const e = await env(path.join(__dirname, "firestore.proposed.rules"), path.join(__dirname, "storage.proposed.rules"));
  const alice = e.authenticatedContext("alice");
  const bob = e.authenticatedContext("bob");
  const anon = e.unauthenticatedContext();

  await t.test("every current client keeps working: v1 Android, v2 Android, v2 iOS ratings", async () => {
    await assertSucceeds(mosDoc(alice, "alice", "old").set(v1(MOS[0])));
    for (const [i, d] of MOS.entries()) await assertSucceeds(mosDoc(alice, "alice", `n${i}`).set(body(d)));
  });
  await t.test("idempotent retry: re-sending the identical document is allowed", async () => {
    await assertSucceeds(mosDoc(alice, "alice", "n0").set(body(MOS[0])));
  });
  await t.test("a stored rating can't be changed or deleted", async () => {
    await assertFails(mosDoc(alice, "alice", "n0").set({ ...body(MOS[0]), score: 1 }));
    await assertFails(mosDoc(alice, "alice", "n0").delete());
  });
  await t.test("invalid ratings are refused: score 0 or 6, unknown field, bad trigger, bad window", async () => {
    await assertFails(mosDoc(alice, "alice", "x1").set({ ...v1(MOS[0]), score: 0 }));
    await assertFails(mosDoc(alice, "alice", "x2").set({ ...v1(MOS[0]), score: 6 }));
    await assertFails(mosDoc(alice, "alice", "x3").set({ ...v1(MOS[0]), latitude: 4.05 }));
    await assertFails(mosDoc(alice, "alice", "x4").set({ ...v1(MOS[0]), triggerType: "MANUAL" }));
    await assertFails(mosDoc(alice, "alice", "x5").set({ ...v1(MOS[0]), windowStartMs: 9e12 }));
  });
  await t.test("ownership unchanged: other devices and signed-out clients are refused", async () => {
    await assertFails(mosDoc(bob, "alice", "y").set(v1(MOS[0])));
    await assertFails(mosDoc(anon, "alice", "y").set(v1(MOS[0])));
    await assertFails(mosDoc(bob, "alice", "n0").get());
  });
  await t.test("consent record: the merge both apps do works; extra fields are refused", async () => {
    await assertSucceeds(alice.firestore().doc("devices/alice").set({ consentGivenAtMs: 1 }, { merge: true }));
    await assertFails(alice.firestore().doc("devices/alice").set({ name: "Alice" }, { merge: true }));
  });
  await t.test("storage: the real batch path uploads; wrong owner, bad name, overwrite, delete, read refused", async () => {
    await assertSucceeds(alice.storage().ref(FILE).put(GZ));
    await assertFails(bob.storage().ref(FILE).put(GZ));
    await assertFails(alice.storage().ref("telemetry/alice/2026-11-11/notes.txt").put(GZ));
    await assertFails(alice.storage().ref("telemetry/alice/latest/x.json.gz").put(GZ));
    await assertFails(alice.storage().ref(FILE).put(GZ));
    await assertFails(alice.storage().ref(FILE).delete());
    await assertFails(alice.storage().ref(FILE).getMetadata());
  });
  await t.test("storage: an empty or oversized object is refused", async () => {
    await assertFails(alice.storage().ref(FILE.replace("3f2a", "aaaa")).put(new Uint8Array(0)));
    await assertFails(alice.storage().ref(FILE.replace("3f2a", "bbbb")).put(new Uint8Array(20 * 1024 * 1024 + 1)));
  });
  await e.cleanup();
});
