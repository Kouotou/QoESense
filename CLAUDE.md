# QoESense

Passive, background LTE drive-test / QoE telemetry collector for an MSc research project
predicting Quality of Experience from Layer 1/2/3 mobile network telemetry via deep learning.
Installed by volunteer participants; runs invisibly for weeks, uploading over Wi-Fi only.

Primary OTT focus for QoE labelling: social media (WhatsApp, Facebook, Instagram). Secondary,
adaptive-priority channel: video conferencing (Zoom, Google Meet, Google Classroom, WhatsApp
calls) - sampling gets denser during a detected video-conferencing session, then drops back to
standard. The app only *observes and tags* telemetry more densely; it never shapes, throttles,
or prioritizes actual network traffic.

## Sampling interval (authoritative - overrides older prompts/docs)

**60 s at rest, 15 s during a detected video call** (`SamplingScheduler`, changed Nov 2026).
Older prompts, the Developer Handbook (.docx) and early commits say 20 s / 5 s - that is
superseded: 20 s produced long runs of duplicate rows. The loop is fixed-rate (capture time is
subtracted from the wait); location and RTT run concurrently, location wait bounded to 8 s in a
call / 15 s at rest. RTT at rest every 5th sample (~5 min), every tick in a call.

## Build

- Kotlin 2.2.10, Compose, minSdk 26, target/compileSdk 37, AGP 9.3.3, Gradle 9.5.0, KSP 2.3.11
  (Kotlin-version-independent line - required for AGP 9's "built-in Kotlin" support).
- Debug APK: `./gradlew assembleDebug`. Unit tests: `./gradlew test`. Lint: `./gradlew lintDebug`.
  On this Windows machine pass `-Dorg.gradle.java.home="C:/Program Files/Android/Android Studio/jbr"`
  (toolchain auto-discovery otherwise picks a broken VS Code JRE).
- `app/google-services.json` **is committed** (Firebase client config, not a secret).
- Release signing reads gitignored `keystore.properties` + `keystore/` - never commit these.

## Architecture

```
data/
  model/           domain models: TelemetrySample, MosResponse, AppSessionType,
                   TelemetryCapabilityTier - @Serializable (NDJSON batch upload)
  local/           Room (version 1, exportSchema=false, no migrations - any entity change
                   needs a real Migration): TelemetrySampleEntity, TelemetryDao, ...
  upload/          BatchSerializer (gzip NDJSON, pure/testable), TelemetryUploadDestination +
                   DeviceIdentityProvider seams with Firebase-backed implementations
  TelemetryRepository.kt      Room-backed buffer; a row's presence = "pending upload"
  MosSurveyRepository.kt      writes Firestore devices/{uid}/mosResponses/{responseId}
  ConsentRepository.kt        best-effort devices/{uid}.consentGivenAtMs
  OnboardingPreferences.kt, CollectionStatePreferences.kt, MosSurveyPreferences.kt   DataStore

telemetry/         capture sources, one class per concern (Cellular, AppUsage, Location,
                   Throughput, Latency, Connectivity). TelemetrySampler composes them into one
                   TelemetrySample (shared by the Status test button and the service loop).
                   TelemetryAnomalyDetector: pure RSRP drop/spike/stability detector.
                   RootedTelemetrySource: disabled-by-default seam for a future rooted/DIAG tier.

service/
  TelemetryForegroundService.kt   fixed-rate sampling loop, drives MosSurveyScheduler
  SamplingScheduler.kt            pure: intervals, location budget, RTT cadence, tick delay
  BootReceiver.kt                 restarts collection after reboot if it was enabled
  upload/          TelemetryUploadWorker (hourly, Wi-Fi + battery-not-low; drains up to
                   20 x 2000 rows per run, then enforces 45-day retention), UploadScheduler
  survey/          MosSurveyScheduler (overlay activity, or notification fallback),
                   SurveyTriggerPolicy (pure: PERIODIC 2h / POST_SESSION 15min / ANOMALY 10min,
                   6/day cap on the participant's *local* day), MosSubmitWorker (WorkManager,
                   idempotent Firestore write with a tap-time UUID, retries up to 10x)

ui/
  onboarding/       5-screen wizard: welcome -> consent -> location -> background+notifications
                    -> usage/battery/overlay
  status/           permissions checklist, buffered count, last upload, device ID, test buttons
  survey/           MosSurveyActivity - translucent, 1-5 tap rating, auto-dismisses after 8 s
res/values, values-fr  English + French strings (consent text included)
```

## Firebase / GCP

Project `qoesense` (Blaze), region africa-south1. Do not replace Firebase/GCP.

- **Auth**: anonymous sign-in per install; the UID is the device identifier everywhere.
- **Firestore** `devices/{uid}/mosResponses/{responseId}`: score, triggerType
  (PERIODIC/POST_SESSION/ANOMALY), context app/session type, windowStartMs/windowEndMs (10 min).
- **Cloud Storage** `telemetry/{uid}/{yyyy-MM-dd}/{batchId}.json.gz`: gzipped NDJSON, one sample
  per line plus `deviceId`. Rows deleted from Room only after a confirmed upload.
- **Cloud Functions** (`functions/index.js`, Node 22, v2): `loadTelemetryBatch` (Storage
  finalize -> BigQuery load job, `ignoreUnknownValues: false`) and `exportMosResponse`
  (Firestore create -> streaming insert).
- **BigQuery** `qoesense_telemetry.samples` / `.mos_responses`. Schema changes must be additive
  and nullable only. `platform` (STRING, NULLABLE: "ANDROID"/"IOS") added Nov 2026; NULL = an
  Android row from before clients sent it. **Deploy functions before any client sends a new
  field** - an unknown field fails the whole batch load.
- Rules (`storage.rules`, `firestore.rules`) scope every read/write to `request.auth.uid`.

## Permissions / background-service constraints

- Foreground service type `dataSync|location`; low-priority silent notification.
- `PhysicalChannelConfig` requires carrier privileges no participant device has - degrades to a
  lower capability tier. Do not expect PHYSCHAN_30 data.
- `PACKAGE_USAGE_STATS` and `SYSTEM_ALERT_WINDOW` are Play Store restricted permissions - need
  Permissions Declaration Form + demo video before publishing. Not done.
- `applicationId` is `com.example.qoesense` - Play Store rejects `com.example.*`; changing it
  later makes a separate app for existing installs. Decide before any store release.
- No exact alarms; all scheduling via WorkManager or the service's own loop.

## Cross-platform migration (in progress)

Branch `xplat/main`, checked out as worktree `../QoESense-xplat`. The Android app on `main`
must keep working throughout. iOS cannot read RSRP/RSRQ/SINR/CQI/TA/cell ID, the foreground
app, or show overlays - approved alternatives: radio-access-technology only, survey "what were
you doing?" + CallKit call detection, actionable 1-5 notification, RTT/throughput/path-change
anomaly triggers. iOS work needs a Mac (borrowed, on demand) - plan for it explicitly.

## Testing

Pure logic (mappers, BatchSerializer, TelemetryCapabilityResolver, SamplingScheduler,
SurveyTriggerPolicy, TelemetryAnomalyDetector) has JVM unit tests - `./gradlew test`.
Everything else needs a **physical device** (emulator unreliable for permission-dependent
behavior). A green build never proves on-device telemetry works.

## Known gaps / next steps

- Play Store publishing prep - package name, AAB, privacy policy URL, declarations, demo video.
- Retention is 45 days (widened during the Sept 2026 billing outage; billing restored Nov 2026).
  Consider returning to 14 days once uploads are confirmed flowing from all devices.
- `CellInfo` is read via `getAllCellInfo()`, which can return modem-cached values - a likely
  contributor to duplicate rows. `requestCellInfoUpdate()` (API 29+) is a candidate fix.
