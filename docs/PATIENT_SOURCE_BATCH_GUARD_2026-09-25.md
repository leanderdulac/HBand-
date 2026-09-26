# Preserve incompatible ingest sources without blocking valid neighbors

## OBSERVED FACTS

Base: `7b26934fb94b488d504a6fa45c6d4e5e1ef3fbfe`, preserving HBand PR5
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5` as an ancestor. This is a local
candidate increment, not an integrated release or a cumulative review of PR5.

Core main was confirmed on GitHub at
`75e5e02c839f381069212bb7c7d3a2befa491b83`. Its PR18 exposes the existing enum
in OpenAPI without changing acceptance. `saude_responsiva_secure/app/models/schemas.py`
accepts the exact strings `companion_manual`, `ble_sim`, `ble_hband`, `http`.
Absence uses the Core default; explicit null and empty string are also accepted
by its validator. Unknown strings and other JSON types are incompatible. The
batch model validates every reading before processing; one incompatible source
can reject the whole batch with 422. `tests/test_wearable_openapi_enums.py`
covers whole-batch rejection and omitted/null sources. The YAML alone does not
describe all null/empty runtime behavior; current code and tests take precedence.

A synthetic repository test on the base plus new tests reproduced expected
14 valid confirmations versus actual zero. No request reached a real backend.
The simulated transport checks only source compatibility; it is not a complete
Core validator. Evidence is archived outside the checkout in the new delivery.

The change adds a source-only predicate to IngestPayloadMapper and uses the
existing local rejection path in WearableRepository before chunking. Incompatible
readings become FAILED with a review message. Existing auth-pause evidence wins
over the source message. Durable payload, source, row ID, client reading ID and
creation time are unchanged; no attempt is sent for a locally rejected item.
Absent/null/empty remain distinct in the outgoing body; the client creates no
default and performs no trimming, case folding or provenance inference.

Four added tests exercise flat and legacy payloads, all four allowed strings,
absence/null/empty, unknown/case/whitespace and non-string values, explicit retry,
previous whole-batch 422 recovery, lost-receipt replay and retained authorization
pause. Room is local under Robolectric; receipts and transport are synthetic.
The same selected valid chunk retains its body and deterministic flush key on
retry. Selecting a different subset naturally derives that subset's existing
flush key; individual IDs and key derivation are not changed to bypass caching.

## RECOMMENDATIONS / limits

Classification: candidate **PROPOSED / CONCEPTUAL**, evidence **LOCAL / DEMO**.
This is not full schema validation or a promise that every other Core field
will be accepted. First-write-wins, remote provenance and deduplication remain
Core-owned. The change does not repair previously stored remote data.

Patient/VE30: only queue selection changes, not capture eligibility, BLE,
association, database schema, encryption, authentication or credentials. Web,
ACS and WhatsApp/SM Click have no functional changes, new permissions, messages
or synchronization. Canonical patient/device IDs and central authority remain.
ACS REAL operations and offline contracts remain **BACKEND CONTRACT REQUIRED**.

No APK is installed and no existing laboratory is started. Historical Android
upgrade evidence applies exclusively to
`5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1` →
`466d79a8ef0229adb72f36f56a9743a5db99960f`, not this candidate or a pilot device.
Accounts, sanitized Core evidence, isolated first accepted procedure, physical
VE30, installed version and distribution signature/candidate remain pending.
Merge, integration and distribution remain human. The pilot is not ready.

Exact candidate SHA, commands, results and independent delta review belong to
`C:/CDev/Next2U-Pilot-2026-09-25-source-batch-guard/`. No automation is created or
resumed by this work. No governance files, Core sources or frozen deliveries change.
