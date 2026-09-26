# Patient SpO2 transport preservation — 2026-09-26

## OBSERVED FACTS

Incremental baseline: ff278f4cb3cd3f4b662d9d88e96b179fc46b7743.
Core schema baseline: 75e5e02c839f381069212bb7c7d3a2befa491b83.
SpO2 is optional, numeric, inclusive 50..100. Current Core defaults absence to
98 and retains null; the patient leaves both representations unchanged for flat
payloads. Existing legacy absence/null/nonpositive omission remains unchanged.
These are API acceptance bounds, not a clinical classification.

Synthetic regression on the baseline: legacy 98.75 became 98; a mixed queue
synchronized 0 instead of 10 compatible readings after a fake whole-batch 422.
An explicit retry could also replace a persisted authorization pause with 422.

The increment preserves explicit legacy SpO2 values, including fractions, and applies a narrow SpO2
transport check before batching. Incompatible normalized readings remain FAILED
locally with the original payload, source, ID and creation time. Existing auth
pause handling remains in force. No schema migration, captured reading deletion,
new identity or source, or full ingest validator is introduced. Explicit malformed
legacy SpO2 stays explicit for review instead of disappearing or being coerced to
a valid number. Precedence remains metrics.spO2 then root.spo2, falling back only
for absence/null. Numeric legacy zero/negative sentinels still omit the field.

A review regression caught Java numeric strings such as 98f/hexadecimal escaping
the initial range check, plus supported Core underscore strings being rejected.
The narrow decimal parser now distinguishes them without changing the wire value.
The string matrix is checked against a copied Core model with local Pydantic2.13.5;
Core requirements are not pinned, so this does not prove all deployed runtimes.

Tests use synthetic file-backed Room under Robolectric and fake transport. They
cover bounds, fractions, absent/null, flat empty/object/nonfinite representations,
local retention, unaffected neighbors, close/reopen, retry, lost receipt followed
by duplicate with identical body/key, and persisted authorization pause. Short
method/database names avoid Windows native SQLite test path length failures.
No Core request, SQLCipher native test, emulator, device or pilot update is used.

IDs and the key algorithm are unchanged. Batch keys depend on membership/order;
removing an incompatible row can therefore change the key of the resulting batch.
Same-composition retries are deterministic. This does not repair any value already
accepted by Core, or prove a receipt-loss recovery across an application upgrade.

## RECOMMENDATIONS / classification

PROPOSED / CONCEPTUAL local candidate, with LOCAL / DEMO test evidence. This
increment requires independent review; it does not approve the accumulated
composition over patient PR5. Integration, merge and distribution remain human.

Patient/VE30: queue transport only, no claim of physical BLE capability. Web,
ACS tablet/cellular and WhatsApp/SM Click gain no API or permission. ACS REAL
features lacking confirmed contracts remain BACKEND CONTRACT REQUIRED.
No dependency on credentials, new Leandro deliverables or production access.
Full SHA, commands, results, hashes and review belong to the accompanying delivery:
C:/CDev/Next2U-Pilot-2026-09-26-spo2-preservation.