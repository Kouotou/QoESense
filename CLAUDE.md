# QoESense

Passive, background LTE drive-test / QoE telemetry collector for an MSc research project
predicting Quality of Experience from Layer 1/2/3 mobile network telemetry via deep learning.
Installed by volunteer participants; runs invisibly for weeks, uploading over Wi-Fi only.

Primary OTT focus for QoE labelling: social media (WhatsApp, Facebook, Instagram). Secondary,
adaptive-priority channel: video conferencing (Zoom, Google Meet, Google Classroom, WhatsApp
calls) - sampling gets denser during a detected video-conferencing session, then drops back to
standard. The app only *observes and tags* telemetry more densely; it never shapes, throttles,
or prioritizes actual network traffic.

## Build

- Kotlin 2.2.10, Compose, minSdk 26, target/compileSdk 37, AGP 9.3.2, KSP 2.3.11 (Kotlin-version-
  independent line - required for AGP 9's "built-in Kotlin" support, see git history for why).
- Debug APK: `./gradlew assembleDebug`. Unit tests: `./gradlew test`. Lint: `./gradlew lintDebug`.
- Requires `app/google-services.json` (not committed - get it from Firebase Console for local
  builds if missing).

## Architecture

```
data/
  model/           domain models: TelemetrySample, MosResponse, AppSessionType,
                   TelemetryCapabilityTier - all @Serializable (used for NDJSON batch upload)
  local/           Room: TelemetrySampleEntity, TelemetryDao, QoESenseDatabase,
                   TelemetryConverters, TelemetrySampleMapper (entity <-> domain)
  upload/          BatchSerializer (gzip NDJSON, pure/testable), TelemetryUploadDestination +
                   DeviceIdentityProvider seams with Firebase-backed implementations
  TelemetryRepository.kt      Room-backed buffer; a row's presence = "pending upload"
  MosSurveyRepository.kt      writes to Firestore devices/{uid}/mosResponses
  OnboardingPreferences.kt, CollectionStatePreferences.kt, MosSurveyPreferences.kt   DataStore

telemetry/         capture sources, one class per concern (Cellular, AppUsage, Location,
                   Throughput, Latency, Connectivity), each independently testable.
                   TelemetrySampler composes them all into one TelemetrySample - shared by the
                   manual "Run telemetry test" button and the foreground service's loop.
                   RootedTelemetrySource: disabled-by-default seam for a future rooted/DIAG tier.

service/
  TelemetryForegroundService.kt   continuous sampling loop, adaptive interval via
                                  SamplingScheduler, triggers MosSurveyScheduler
  SamplingScheduler.kt            pure: standard vs elevated (video-call) interval
  BootReceiver.kt                 restarts collection after reboot if it was enabled
  upload/          TelemetryUploadWorker (WorkManager CoroutineWorker, Wi-Fi-only), UploadScheduler
  survey/          MosSurveyScheduler (notification + trigger orchestration),
                   SurveyTriggerPolicy (pure: cooldown/daily-cap decision logic)

ui/
  onboarding/       4-screen wizard: welcome -> location -> background+notifications -> usage+battery
  status/           debug/status screen - permissions checklist, buffered count, last upload,
                    manual test buttons (telemetry capture, MOS survey, collection toggle)
  survey/           MosSurveyActivity - translucent lightweight activity (not a heads-up
                    notification layout, to stay Compose-only), 1-5 tap rating, auto-dismisses
```

## Firebase

Project: `qoesense` (Blaze plan - required for Storage, even at near-zero usage).

- **Auth**: anonymous sign-in per install (`FirebaseAuthDeviceIdentityProvider`); the resulting
  UID is the device identifier used everywhere below.
- **Firestore** (`devices/{uid}/mosResponses/{auto-id}`): MOS survey responses only - score,
  triggerType (PERIODIC/POST_SESSION), context app/session type, telemetry window bounds.
- **Cloud Storage** (`telemetry/{uid}/{yyyy-MM-dd}/{batchId}.json.gz`): gzipped NDJSON batches
  of raw telemetry, uploaded hourly by `TelemetryUploadWorker`, constrained to
  `NetworkType.UNMETERED`. Rows only ever deleted from Room after a confirmed upload.
- Security rules for both (`storage.rules`, `firestore.rules` in repo root) scope every
  read/write to `request.auth.uid == deviceId` - already published to the console.
- **Not yet built**: a Cloud Function (Storage `onFinalize` trigger) to load uploaded batches
  into BigQuery via a load job, for the offline ML pipeline. Console-side, not app code.

## Permissions / background-service constraints

- Foreground service type `dataSync|location`; low-priority silent notification (required
  Android 8+, correct type required Android 14+).
- `PhysicalChannelConfig` (cell bandwidth/channel numbers) requires carrier privileges no
  participant device will have - registering the listener is wrapped in try/catch and just
  degrades to a lower capability tier rather than crashing. Confirmed via on-device testing.
- `PACKAGE_USAGE_STATS` is a Play Store **restricted permission** - needs a declared, justified
  use-case in the Play Console Permissions Declaration Form before publishing. Not done yet.
- `ACCESS_BACKGROUND_LOCATION` needs its own onboarding screen/disclosure, separate from
  foreground location (Android enforces this on API 30+).
- No exact alarms anywhere; all scheduling via WorkManager or the foreground service's own loop.
- Signed AAB / Play Store release: not set up. Flag this explicitly before publishing - needs a
  privacy policy, permission declarations, and a demo video for the restricted permissions.

## Testing

Pure logic (mappers, `BatchSerializer`, `TelemetryCapabilityResolver`, `SamplingScheduler`,
`SurveyTriggerPolicy`) has unit tests under `app/src/test` - run via `./gradlew test`, no device
needed. Everything else (permissions flow, actual telemetry capture, foreground service
behavior, notification/activity flow) needs a real device - Android Studio's emulator has been
unreliable for the permission-dependent pieces in this project; test on a physical device.

## Known gaps / next steps

- BigQuery ingestion (Cloud Function) - console-side, not started.
- Play Store publishing prep - signing, privacy policy, permission declarations, demo video.
- MOS survey responses are fire-and-forget: a failed Firestore write (rare, e.g. rules
  misconfigured) is dropped rather than retried/buffered, unlike telemetry samples. Acceptable
  given the low volume, but worth knowing.
