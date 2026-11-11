const crypto = require("crypto");

/**
 * Fields in `wanted` that are missing from `existing`, by name. BigQuery can append NULLABLE
 * and REPEATED columns to a live table without touching existing rows; a missing REQUIRED
 * field can't be added to a table with data, so it is a programming error and throws.
 */
function missingNullableFields(existing, wanted) {
  const have = new Set(existing.map((f) => f.name));
  const missing = wanted.filter((f) => !have.has(f.name));
  const required = missing.filter((f) => (f.mode || "NULLABLE") === "REQUIRED");
  if (required.length > 0) {
    throw new Error(`Cannot add REQUIRED field(s) to an existing table: ${required.map((f) => f.name).join(", ")}`);
  }
  return missing;
}

/** The subset of `fields` that `data` actually has (not undefined or null). */
function pickPresent(data, fields) {
  const out = {};
  for (const f of fields) if (data[f] !== undefined && data[f] !== null) out[f] = data[f];
  return out;
}

/**
 * Replaces an anonymous UID (a raw participant identifier) in a log line with a short,
 * stable hash, so logs never carry it in clear but lines from one device still correlate.
 * Accepts a bare UID or a "telemetry/{uid}/..." path.
 */
function redactUid(value) {
  const s = String(value);
  const m = s.match(/^telemetry\/([^/]+)\/(.*)$/);
  const hash = (uid) => "uid#" + crypto.createHash("sha256").update(uid).digest("hex").slice(0, 8);
  if (m) return `telemetry/${hash(m[1])}/${m[2]}`;
  return hash(s);
}

// Optional MOS document fields copied to BigQuery only when the document has them: older
// clients don't write them, and a streaming insert of an unknown column can be briefly
// rejected right after the column is added.
const MOS_OPTIONAL_FIELDS = [
  "platform", "schemaVersion", "contextSource", "presentationMode", "appId", "appVersion",
  "appBuild", "utcOffsetMinutes", "promptedAtMs",
];

/** One mos_responses row from a Firestore MOS document (deviceId/responseId from its path). */
function mosRow(deviceId, responseId, data) {
  return {
    deviceId,
    responseId,
    score: data.score,
    submittedAtMs: data.submittedAtMs,
    triggerType: data.triggerType,
    contextAppPackage: data.contextAppPackage ?? null,
    contextSessionType: data.contextSessionType,
    windowStartMs: data.windowStartMs,
    windowEndMs: data.windowEndMs,
    ...pickPresent(data, MOS_OPTIONAL_FIELDS),
  };
}

module.exports = { missingNullableFields, pickPresent, redactUid, mosRow, MOS_OPTIONAL_FIELDS };
