# Keep-alive does not create a new measurement

## OBSERVED FACTS

Base: f9535e587139185c91022765faabe999a6471b9e, preserving the prior candidate and
PR5 e9a80ef386d207a1bc6fe66bef3969eafa84aae5 as ancestors. New isolated checkout:
C:/CDev/Next2U-Patient-Keepalive-Provenance, branch codex/patient-keepalive-provenance.

The generic GATT keep-alive timer requested battery data every five seconds and
also published createTelemetrySnapshot from cached vitals. That snapshot assigned
the current wall-clock time and retained isRealSensorData after a previous reading.
The application subscribes to latestTelemetry for durable recording. Its existing
deduper allows equal values again after its interval; it does not establish that
a new sensor sample arrived. A timer publication can therefore be mistaken for a
new measurement. This is distinct from the Veepoo SDK battery-polling path.

A Robolectric test on the base plus new test file reproduced replacement of the
old 72 bpm sample with the same vitals and a new timestamp after one real timer
cycle. Battery read was observed first. The Bluetooth boundary is synthetic;
no physical device, patient database or backend was used. The archived failing
test invoked the production publisher directly to seed the sample. Final tests
strengthen this by invoking the actual GATT notification callback/parser with
synthetic HR bytes before and after the timer.

Minimal change: remove cached-telemetry publication from startKeepAliveLoop and
its now-unused private device argument. Keep the five-second interval, connection
check, battery lookup/read, cancellation and genuine notification path unchanged.
No new timestamp/source inference, clinical rule, queue key, schema or permission.

Two regressions check that the timer preserves the last published sample (or
null when none exists), the battery request and simulated response still work,
and a subsequent synthetic GATT HR notification still publishes through the
existing callback/parser. The tests wait for the production timer to reach the
GATT shadow, then cancel/join that loop before inspecting effects; they do not
advance the host clock or rely on a blind sleep to infer timer execution.

## RECOMMENDATIONS / limits

Candidate PROPOSED / CONCEPTUAL; evidence LOCAL / DEMO. No assertion that all
callbacks, BLE protocols, manual snapshot paths or cached vital combinations
have perfect provenance. triggerSpotCheck, generateCurrentTelemetry, manual
capture and other sensor callback paths are unchanged and require separate
evidence. No correction/deletion of already stored measurements is attempted.

Tests do not prove physical GATT/VE30, keep-alive effectiveness on hardware,
remote deduplication, process recovery or a native upgrade of this candidate.
The historic upgrade evidence remains exclusively
5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1 →
466d79a8ef0229adb72f36f56a9743a5db99960f in its existing laboratory.

Only App Patient's generic GATT timer changes. Web, ACS, WhatsApp/SM Click,
canonical IDs, central ownership and permissions are unchanged. ACS REAL remains
BACKEND CONTRACT REQUIRED. Accounts, sanitized Core report, installed version,
distribution/signature, first accepted isolation and physical tests remain pending.
No backend send, installation, emulator startup, automation, merge or distribution.
Full SHA, checks and independent delta review are recorded in the new delivery
C:/CDev/Next2U-Pilot-2026-09-25-keepalive-provenance. Pilot not ready.
