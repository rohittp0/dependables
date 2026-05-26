# Dependables — Domain Context

A monorepo of standalone Android libraries. Each library is independent — there is no
shared domain across libraries. This file collects terminology that spans more than
one source file inside a single library, so future readers (human or model) don't have
to re-derive it from code.

Add a section per library as terminology stabilises. Skip libraries whose language is
fully self-explanatory.

## remote-logger

A Timber tree + WorkManager worker + zip exporter that lets a developer pull recent
app data (databases, shared_prefs, datastore, logs) from any installed device by
sending a Firebase Cloud Messaging data message — or by streaming the same zip into
any local `OutputStream` from inside the app.

| Term | Meaning |
|------|---------|
| **Process tag** | A short label derived from the current process name (`main`, `export`, `remote`, …). Becomes the filename suffix `HH-<tag>.ndjson` so child processes never contend with the main process on the same log file. Inferred by `ProcessTag.infer(context)`. |
| **Day bucket** | A directory `filesDir/logs/yyyy-MM-dd/` under which every file is one UTC hour's worth of logs for one process. |
| **Device id** | Deterministic per-device identifier derived from Widevine `PROPERTY_DEVICE_UNIQUE_ID` (Base64-URL, last 32 chars) with a Build-fingerprint hash fallback. Exposed publicly via `DeviceId.get()`. |
| **Dump payload** | An FCM data message with `type == "dump_logs"`. Optional key: `hours` (integer string, default `defaultDumpHours` from `RemoteLoggerConfig`). |
| **Export zip** | The artifact uploaded on `dump_logs` or written by `ExportZipWriter.writeTo`. Contains `meta.json` at the root, plus `databases/`, `shared_prefs/`, `datastore/`, and `logs/<day>/<file>` directory entries. Local filename `log_dump_<utc-yyyyMMdd_HHmmss>.zip`. |
| **Storage path** | Where the zipped export lands in Firebase Storage. Default: `user/<deviceId>/logs/<utc-yyyyMMdd_HHmmss>.zip`. Overridden via `RemoteLoggerConfig.storagePathBuilder`. |
| **Topic name** | `logdump_<deviceId>` — every installed app subscribes itself via `RemoteLogger.subscribeForDevice`. Lets the backend target a single device. |
| **Retention** | Number of days of log buckets kept on-disk before `FileLoggingTree.purgeOld` deletes them. Default 7. |

### Public API surface

| Symbol | Purpose |
|--------|---------|
| `FileLoggingTree` | The Timber tree. Plant one per process. |
| `ProcessTag.infer(context)` | Derive the per-process filename suffix. |
| `DeviceId.get()` / `DeviceId.name()` | Stable id + human-readable label. Useful outside the library too (support tickets, backend correlation). |
| `ExportZipWriter.writeTo(context, out, hours)` | Stream the export zip into any `OutputStream`. Use for in-app "export my data" UIs. |
| `RemoteLogger.init / configure / subscribeForDevice / handleMessage / enqueueDump` | Push-triggered upload flow facade. |
| `RemoteLoggerConfig` | Storage path, retention, default dump hours. |

### Non-goals (intentional limitations)

- **No Crashlytics dependency.** The library is Crashlytics-free. If you want
  Timber-level breadcrumbs in Crashlytics, plant a small custom tree alongside.
- **No upload encryption.** Access control is whatever your Firebase Storage rules
  enforce. Don't put PII in `Timber.i(...)` calls — and remember the zip also
  contains `databases/` and `shared_prefs/`.
- **No pure-JVM target.** Android only. The tree uses `Context.filesDir`, the worker
  uses WorkManager, and the trigger goes through FCM.
