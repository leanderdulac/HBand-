# Consistent optional duplicate flag in batch receipts

## OBSERVED FACTS

Base ca7b95cc57616ab0f734174dc954902e97b8f7aa, isolated checkout
C:/CDev/Next2U-Patient-Receipt-Consistency, branch codex/patient-receipt-consistency.
Core contract WEARABLE_INGEST_IDEMPOTENCY sections2/3, YAML boolean definition and
wearables.py::_decorate_frame at75e5e02c839f381069212bb7c7d3a2befa491b83 define
duplicate as a boolean equivalent to ingest_status == duplicate. The batch
handler uses that decorated frame. No clinical endpoint was called.

The patient's single receipt reconciler already rejects a conflicting duplicate
flag. Its batch reconciler ignored the flag, so an accepted receipt carrying
duplicate=true could mark a row SYNCED. Before the fix, a synthetic transport
test expected zero synchronized rows and observed one. A pure policy test also
failed; a compatible-response test passed. Log/XML and the test delta are archived.

The minimum guard checks result.duplicate only when present: it must be the
boolean matching the item's accepted/duplicate status. Missing remains compatible.
Explicit null, strings or numbers are not a confirmed boolean; no coercion.
An inconsistent receipt leaves the whole chunk unconfirmed, preserving the
reconciler's existing conservative envelope policy. It does not infer success
from HTTP200 or counters. Consistent accepted and duplicate still confirm.

Three tests cover contradictory/malformed flags, absence/consistent flags, and
the repository's recovery path with simulated transport. The repository test
checks PENDING after the first response, unchanged stored payload/IDs, and an
identical request body/idempotency key on a later consistent response. It recreates
the repository over the same in-memory fake DAO; this is not a process restart,
Room/SQLCipher durability test or actual remote deduplication proof.

The final compatibility test limits its older embedded frame client ID to the
duplicate scenario; the initially archived passing test had also set it for
accepted. The two failing scenarios/reproducer assertions are unchanged. No
validation of result.client_reading_id was added: the envelope carries the current
request identity, while a natural-key duplicate can legitimately retain an older
frame identity. No complete response validator was introduced.

## RECOMMENDATIONS / limits

Candidate PROPOSED / CONCEPTUAL; synthetic evidence LOCAL / DEMO. No assertion
that Core actually emitted contradictory receipts; this is client recovery
hardening against a reproduced simulated response. No new source/null semantics,
payload normalization, ID/key/schema/auth changes, queue pause or isolated-first-
accepted protocol. Retrying an uncertain chunk can resend its valid neighbors;
that is existing conservative behavior, not selective receipt acceptance.

Web, ACS and WhatsApp/SM Click unchanged; ACS REAL is BACKEND CONTRACT REQUIRED.
No stored clinical data, emulator, device, credentials, infrastructure or automation
touched. Accounts/reportv2/permission diagnostics, distribution candidate/signature,
installed version, integration/physical VE30 and cumulative PR5 review remain pending.
Historical native proof stays only5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1 →
466d79a8ef0229adb72f36f56a9743a5db99960f, not this candidate or pilot device.
Exact SHA, local checks and independent delta review are in
C:/CDev/Next2U-Pilot-2026-09-26-receipt-consistency. No pilot-readiness claim.
