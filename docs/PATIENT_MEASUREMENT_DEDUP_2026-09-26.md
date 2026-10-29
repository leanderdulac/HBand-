# Preserve changed recorded measurements inside the deduplication interval

## OBSERVED FACTS

Base d1250aa08c36ccd973237a9b9fe44a0bf36304ef, isolated checkout
C:/CDev/Next2U-Patient-Measurement-Dedup, branch codex/patient-measurement-dedup.
GitHubPR5 remains e9a80ef386d207a1bc6fe66bef3969eafa84aae5 and Core main remains
75e5e02c839f381069212bb7c7d3a2befa491b83. Scope is local recording only.

IngestDeduper compared deviceId, heart rate, SpO2, blood pressure and steps.
It omitted temperature, HRV, calories, distance and the three sleep durations
that WearableRepository.persistTelemetry stores. A change only in these values
was incorrectly treated as a repeated reading inside the existing30-second
interval. The documented policy already allows changed measurements immediately;
it is not a general maximum sample rate.

Before production changes, three tests failed. The pure test dropped a changed
temperature; a failed-save test never reached the save because the changed
sample was suppressed; Room/SQLite captured only one of eight differing synthetic
samples. The reproducer fixes elapsed time and does not move the host clock.

The minimal fix includes those seven existing recorded values in the signature.
No new measurement field, timestamp conversion, origin inference, precision,
clinical threshold or filtering rule. Interval, monotonic clock, existing
eligibility and rollback after save failure remain unchanged. Timestamp-only and
device-model-only changes still coalesce; equal measurements coalesce until the
same30-second boundary. Changed values may now cause more local writes and sends
than the defective implementation; no new maximum rate is introduced.

Tests cover each omitted value individually, equal readings/metadata and interval
boundary, failed temperature-save recovery, and saveIfNeeded→persistTelemetry
with an actual synthetic Room/SQLite file. Eight distinct samples are preserved,
exact repeats coalesce, history and queued rows including IDs/payloads survive
closing/reopening that test database. No network call is made by that path.

## RECOMMENDATIONS / limits

Candidate PROPOSED / CONCEPTUAL; evidence LOCAL / DEMO. The database test is
Robolectric Room/SQLite, not native SQLCipher, process death, Android upgrade,
BLE/VE30 hardware, StateFlow losslessness or central persistence. It exercises
the gate and transaction directly, not the whole live collector/transport path.
Existing live eligibility still requires a real-marked sample and positive HR;
this does not enable HR-less collection. Synthetic isRealSensorData=true is a
fixture, never physical proof.

Sleep durations remain local fields; the existing outgoing payload does not
serialize them. Including sleep in the local signature does not prove delivery
of sleep to Core or define a new backend contract. No retrospective recovery of
previously dropped samples. No complete deduplication or provenance guarantee;
callbacks can still carry cached combinations under existing semantics.

No database schema, stored row, queue key/ID, payload mapping, remote receipt,
endpoint, permission, authority, infrastructure or automation changed. Web,
ACS and WhatsApp/SM Click untouched; ACS REAL is BACKEND CONTRACT REQUIRED.
Accounts/reportv2, cumulative PR5 review, distribution/installedversion/signature,
isolated-first-accepted/queuepause and physical/integrated tests remain pending.
Historical native proof remains only5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1 →
466d79a8ef0229adb72f36f56a9743a5db99960f. Exact candidate SHA, checks and
independent delta review are recorded in
C:/CDev/Next2U-Pilot-2026-09-26-measurement-dedup. Pilot not declared ready.
