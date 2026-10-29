# APK replacement laboratory, schema 7 to 8

LOCAL/DEMO evidence only, candidate PROPOSED / CONCEPTUAL for pilot distribution.
No production code change in this increment over a5fd85c80eee1a6f008af15aaf926ecb24e68f52.

The exact archived storageLab app 466d79a8ef0229adb72f36f56a9743a5db99960f
(SHA256 7b36f18fc9ae1c8785d09fa2ef529a8cd442c93e6934526315e56eb95545cb73)
is installed on a new, explicitly named Android emulator. Frozen test APK from
82b395015ddf6d5f7557f75bfd63bd77b59f3f67 (SHA256
a9a854b587af96980867ffc217003152c4bb3b1ae69522f1b255a99b53663f23) seeds and
reopens thirteen synthetic rows using that old application's real Room database.

The app and instrumentation are then replaced using install -r, without clear,
uninstall, reseed, credential replacement or restoring a database. Both apps
have the same lab package, versionCode 1 and debug certificate. This exercises
Android package replacement with equal versionCode, not a store release/update.

The new test method requires the untouched persisted baseline to have schema7;
only the expected snapshot's in-memory version field changes to8. Every other
field/type/null across all six tables, all four queue rows, authorization blocks,
the original baseline bytes and wrapped-key digest must remain identical. A
second process reopen verifies the same state again. Runner also compares exact
snapshots with only the schema field replaced, verifies unique process IDs and
checks installed APK hashes before/after every phase.

Four APKs must have the pinned certificate and no INTERNET permission. Both app
manifests must agree apart from XML line numbers, use neutral Application and
have no Firebase/AndroidX startup provider. Guards require fresh emulator target
Next2U_UpgradeRoom8_Lab_20260926 / emulator-5576, no existing lab packages, exact
source SHA/clean tree and new evidence directory. Preserve the AVD on failure.

The old installed app's schema7 is the canonical local variant. This actual APK
test does not exercise replacement from published PR5's other schema7 variant;
that variant was covered separately by the native fixture migration laboratory.
No Web/Core/ACS changes, contract extensions, network/BLE tests or SM Click work.
No runtime startup/transport means this does not validate operational concurrency,
old queue replay or receipt reconciliation. No arm64 phone, installed pilot
version, release signature, app-store update, backend or REAL pilot acceptance.
Full cumulative review and human publication/distribution decisions remain separate.
