const { onObjectFinalized } = require("firebase-functions/v2/storage");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const { BigQuery } = require("@google-cloud/bigquery");
const { Storage } = require("@google-cloud/storage");
const { missingNullableFields, mosRow, redactUid } = require("./schema");

const bigquery = new BigQuery();
const storage = new Storage();

const DATASET_ID = "qoesense_telemetry";
const SAMPLES_TABLE_ID = "samples";
const MOS_TABLE_ID = "mos_responses";

/**
 * REGION and BQ_LOCATION both match where the Firebase project's Storage bucket and Firestore
 * database actually live (africa-south1, Johannesburg) - both Storage and Firestore triggers
 * need to be co-located with their source, and a BigQuery load job from GCS requires the
 * dataset location to match the bucket's region.
 */
const REGION = "africa-south1";
const BQ_LOCATION = "africa-south1";

// Mirrors com.example.qoesense.data.model.TelemetrySample, plus deviceId (which the app
// embeds into each NDJSON line at upload time - see BatchSerializer.toGzippedNdjson).
const SAMPLES_SCHEMA = [
  { name: "deviceId", type: "STRING", mode: "REQUIRED" },
  { name: "timestampMs", type: "INTEGER", mode: "REQUIRED" },
  { name: "rsrp", type: "INTEGER" },
  { name: "rsrq", type: "INTEGER" },
  { name: "rssnr", type: "INTEGER" },
  { name: "timingAdvance", type: "INTEGER" },
  { name: "networkType", type: "STRING" },
  { name: "cellIdentityHash", type: "STRING" },
  { name: "trackingAreaCode", type: "INTEGER" },
  { name: "signalStrengthLevel", type: "INTEGER" },
  { name: "cqi", type: "INTEGER" },
  { name: "cqiSupported", type: "BOOLEAN", mode: "REQUIRED" },
  { name: "cellBandwidthKhz", type: "INTEGER" },
  { name: "downlinkChannelNumber", type: "INTEGER" },
  { name: "uplinkChannelNumber", type: "INTEGER" },
  { name: "connectionType", type: "STRING" },
  { name: "isRoaming", type: "BOOLEAN" },
  { name: "throughputBytesPerSecond", type: "INTEGER" },
  { name: "rttMs", type: "INTEGER" },
  { name: "foregroundAppPackage", type: "STRING" },
  { name: "appSessionType", type: "STRING", mode: "REQUIRED" },
  { name: "latitude", type: "FLOAT" },
  { name: "longitude", type: "FLOAT" },
  { name: "locationAccuracyMeters", type: "FLOAT" },
  { name: "deviceModel", type: "STRING", mode: "REQUIRED" },
  { name: "androidVersion", type: "INTEGER", mode: "REQUIRED" },
  { name: "appVersion", type: "STRING", mode: "REQUIRED" },
  { name: "telemetryCapabilityTier", type: "STRING", mode: "REQUIRED" },
  // Additive, nullable (Nov 2026, cross-platform migration): "ANDROID" or "IOS". NULL on every
  // row written before clients started sending it - all of those rows are Android.
  { name: "platform", type: "STRING" },
  // Contract v2 (approved 2026-11-07, QoESense-CrossPlatform docs/DATA_CONTRACT.md §4). All
  // additive: NULLABLE, plus capabilitySet REPEATED. v1 lines leave them NULL.
  { name: "schemaVersion", type: "INTEGER" },
  { name: "radioStatus", type: "STRING" },
  { name: "cqiStatus", type: "STRING" },
  { name: "physicalChannelStatus", type: "STRING" },
  { name: "networkTypeStatus", type: "STRING" },
  { name: "connectivityStatus", type: "STRING" },
  { name: "throughputStatus", type: "STRING" },
  { name: "rttStatus", type: "STRING" },
  { name: "foregroundAppStatus", type: "STRING" },
  { name: "locationStatus", type: "STRING" },
  { name: "capabilitySet", type: "STRING", mode: "REPEATED" },
  { name: "appId", type: "STRING" },
  { name: "appBuild", type: "INTEGER" },
  { name: "osVersion", type: "STRING" },
  { name: "utcOffsetMinutes", type: "INTEGER" },
  { name: "captureDurationMs", type: "INTEGER" },
  { name: "cellInfoAgeMs", type: "INTEGER" },
  { name: "locationAgeMs", type: "INTEGER" },
  { name: "throughputWindowMs", type: "INTEGER" },
  { name: "rttMethod", type: "STRING" },
];

// Mirrors com.example.qoesense.data.model.MosResponse, plus deviceId/responseId pulled from
// the Firestore path (devices/{deviceId}/mosResponses/{responseId}) rather than the document
// body - join to `samples` on deviceId + a timestamp within [windowStartMs, windowEndMs].
const MOS_SCHEMA = [
  { name: "deviceId", type: "STRING", mode: "REQUIRED" },
  { name: "responseId", type: "STRING", mode: "REQUIRED" },
  { name: "score", type: "INTEGER", mode: "REQUIRED" },
  { name: "submittedAtMs", type: "INTEGER", mode: "REQUIRED" },
  { name: "triggerType", type: "STRING", mode: "REQUIRED" },
  { name: "contextAppPackage", type: "STRING" },
  { name: "contextSessionType", type: "STRING", mode: "REQUIRED" },
  { name: "windowStartMs", type: "INTEGER", mode: "REQUIRED" },
  { name: "windowEndMs", type: "INTEGER", mode: "REQUIRED" },
  // Additive, nullable - same meaning and NULL convention as samples.platform.
  { name: "platform", type: "STRING" },
  // Contract v2 (DATA_CONTRACT §10), all NULLABLE.
  { name: "schemaVersion", type: "INTEGER" },
  { name: "contextSource", type: "STRING" },
  { name: "presentationMode", type: "STRING" },
  { name: "appId", type: "STRING" },
  { name: "appVersion", type: "STRING" },
  { name: "appBuild", type: "INTEGER" },
  { name: "utcOffsetMinutes", type: "INTEGER" },
  { name: "promptedAtMs", type: "INTEGER" },
];

// The Firestore -> BigQuery row mapping lives in schema.js (mosRow), shared with the
// one-off backfill tool (tools/backfill_mos.js), so both write identical rows.

// Per-instance memo so the schema check costs one metadata read per table per cold start.
const reconciledTables = new Set();

/**
 * Creates the dataset/table on first use if they don't exist yet. If the table already
 * exists, appends any NULLABLE columns from schemaFields that it doesn't have yet (additive
 * only - nothing is ever removed, renamed or retyped), so a newly added nullable column such
 * as `platform` reaches the live tables on the first invocation after a deploy.
 */
async function ensureTable(tableId, schemaFields) {
  const dataset = bigquery.dataset(DATASET_ID);

  const [datasetExists] = await dataset.exists();
  if (!datasetExists) {
    await bigquery.createDataset(DATASET_ID, { location: BQ_LOCATION });
    logger.info(`Created dataset ${DATASET_ID}`);
  }

  const table = dataset.table(tableId);
  const [tableExists] = await table.exists();
  if (!tableExists) {
    await dataset.createTable(tableId, { schema: { fields: schemaFields } });
    logger.info(`Created table ${DATASET_ID}.${tableId}`);
  } else if (!reconciledTables.has(tableId)) {
    const [metadata] = await table.getMetadata();
    const existing = (metadata.schema && metadata.schema.fields) || [];
    const toAdd = missingNullableFields(existing, schemaFields);
    if (toAdd.length > 0) {
      await table.setMetadata({ schema: { fields: [...existing, ...toAdd] } });
      logger.info(`Added nullable column(s) ${toAdd.map((f) => f.name).join(", ")} to ${DATASET_ID}.${tableId}`);
    }
  }
  reconciledTables.add(tableId);
  return table;
}

/**
 * Fires whenever TelemetryUploadWorker finishes uploading a batch to Cloud Storage. Loads
 * that one gzipped NDJSON file straight into BigQuery - append-only, so this table only ever
 * grows. Skips anything outside telemetry/ so unrelated bucket activity doesn't error out here.
 */
exports.loadTelemetryBatch = onObjectFinalized({ region: REGION }, async (event) => {
  const filePath = event.data.name;
  const bucketName = event.data.bucket;

  if (!filePath.startsWith("telemetry/") || !filePath.endsWith(".json.gz")) {
    logger.info("Ignoring non-telemetry object");
    return;
  }

  const table = await ensureTable(SAMPLES_TABLE_ID, SAMPLES_SCHEMA);

  const sourceFile = storage.bucket(bucketName).file(filePath);
  const [job] = await table.load(sourceFile, {
    sourceFormat: "NEWLINE_DELIMITED_JSON",
    compression: "GZIP",
    writeDisposition: "WRITE_APPEND",
    createDisposition: "CREATE_NEVER",
    schema: { fields: SAMPLES_SCHEMA },
    ignoreUnknownValues: false,
  });

  const errors = job.status && job.status.errors;
  if (errors && errors.length > 0) {
    logger.error(`Load job for ${redactUid(filePath)} finished with errors`, errors);
    throw new Error(`BigQuery load failed for ${redactUid(filePath)}: ${JSON.stringify(errors)}`);
  }

  logger.info(`Loaded ${redactUid(filePath)} into ${DATASET_ID}.${SAMPLES_TABLE_ID}`);
});

/**
 * Fires whenever MosSurveyRepository writes a new response to Firestore. Streams that one
 * response into BigQuery so it's queryable/joinable alongside `samples` in the same
 * dataset, instead of being stranded in Firestore.
 */
exports.exportMosResponse = onDocumentCreated(
  { document: "devices/{deviceId}/mosResponses/{responseId}", region: REGION },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      logger.warn("exportMosResponse: no document snapshot on event, skipping");
      return;
    }
    const data = snapshot.data();
    const { deviceId, responseId } = event.params;

    const table = await ensureTable(MOS_TABLE_ID, MOS_SCHEMA);

    try {
      await table.insert([mosRow(deviceId, responseId, data)]);
    } catch (error) {
      logger.error(`Failed to insert MOS response ${responseId} for device ${redactUid(deviceId)}`, error);
      throw error;
    }

    logger.info(`Exported MOS response ${responseId} for device ${redactUid(deviceId)} into ${DATASET_ID}.${MOS_TABLE_ID}`);
  },
);
