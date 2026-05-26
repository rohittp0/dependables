# Dependables — Domain Context

A monorepo of standalone Android libraries. Each library is independent — there is no
shared domain across libraries. This file collects terminology that spans more than
one source file inside a single library, so future readers (human or model) don't have
to re-derive it from code.

Add a section per library as terminology stabilises. Skip libraries whose language is
fully self-explanatory.

## remote-logger

A Timber tree + WorkManager worker that lets a developer pull recent logs from any
installed device by sending a Firebase Cloud Messaging data message.

| Term | Meaning |
|------|---------|
| **Process tag** | A short label derived from the current process name (`main`, `export`, `remote`, …). Becomes the filename suffix `HH-<tag>.ndjson` so child processes never contend with the main process on the same log file. Inferred by `ProcessTag.infer(context)`. |
| **Day bucket** | A directory `filesDir/logs/yyyy-MM-dd/` under which every file is one UTC hour's worth of logs for one process. |
| **Dump payload** | An FCM data message with `type == "dump_logs"`. Optional keys: `hours` (integer string, default `defaultDumpHours` from `RemoteLoggerConfig`), `deviceId` (string, default `Settings.Secure.ANDROID_ID`). |
| **Storage path** | Where the zipped logs land in Firebase Storage. Default: `user/<deviceId>/logs/<utc-yyyyMMdd_HHmmss>.zip`. Overridden via `RemoteLoggerConfig.storagePathBuilder`. |
| **Topic name** | `logdump_<sanitisedDeviceId>` — every installed app subscribes itself via `RemoteLogger.subscribeForDevice`. Lets the backend target a single device. |
| **Retention** | Number of days of log buckets kept on-disk before `FileLoggingTree.purgeOld` deletes them. Default 7. |

### Non-goals (intentional limitations)

- **No Crashlytics dependency.** The library is Crashlytics-free. If you want
  Timber-level breadcrumbs in Crashlytics, plant a small custom tree alongside.
- **No app data export.** The zip contains logs + `meta.json` only — never databases,
  shared_prefs, or DataStore content. That belongs in the consumer.
- **No upload encryption.** Access control is whatever your Firebase Storage rules
  enforce. Don't put PII in `Timber.i(...)` calls.
- **No pure-JVM target.** Android only. The tree uses `Context.filesDir`, the worker uses
  WorkManager, and the trigger goes through FCM.
