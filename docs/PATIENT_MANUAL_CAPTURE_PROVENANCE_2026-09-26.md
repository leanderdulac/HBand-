# Manual capture preserves received measurement time

## OBSERVED FACTS

Base: 51b7b5d18c34ae346a0b6c1a019590a83058647d; isolated checkout
C:/CDev/Next2U-Patient-Manual-Capture-Provenance on
codex/patient-manual-capture-provenance. GitHub PR5 remains at
e9a80ef386d207a1bc6fe66bef3969eafa84aae5; Core main remains at
75e5e02c839f381069212bb7c7d3a2befa491b83. Existing authorities and ADR-007/008
require distinguishing an attempted read from a received measurement.

triggerSpotCheck requested a GATT read, immediately built a cache snapshot with
Date now, published it and returned it to MainViewModel for manual enqueue/sync.
A request does not prove that a new measurement arrived. On the base, three
synthetic tests failed: an old 72 bpm sample was replaced with 72 bpm at the
button press time; cached vitals without a published sample became a sample;
and no vitals became a zero-valued sample. The first test observed an actual
production GATT read call through a shadow before its provenance assertion.

The fix keeps the sensor request and returns the last published sample unchanged,
or null when there is none. It does not publish anything itself. MainViewModel
handles null using its existing unavailable-reading message and returns before
enqueue. With an existing ingestible sample, manual persistence/sync continues
using that sample's original timestamp, identity and values. Genuine callbacks
remain responsible for publishing new samples. The production parser/callback is
exercised with synthetic HR bytes before and after the manual request.

## RECOMMENDATIONS / limits

Candidate PROPOSED / CONCEPTUAL; tests LOCAL / DEMO. Three tests use Robolectric,
a synthetic GATT and pending response; no radio, backend or patient data.
This is not a promise of freshness or a new-reading-only operation: manual capture
can still enqueue the previous received sample and process the generic queue.
Its existing queue-row creation and receipt reconciliation are unchanged.
No global pause, isolated-first-accepted protocol or new retry identity policy.

No claim that all sensor callbacks or cache combinations have correct provenance.
generateCurrentTelemetry/createTelemetrySnapshot remain available to the existing
simulator consumer and were not changed. No stored records, payloads, sources,
canonical IDs, permissions, schemas, keys or database versions were modified.
No Web, ACS or WhatsApp/SM Click change; ACS REAL remains BACKEND CONTRACT REQUIRED.
Physical VE30/manual-read effectiveness, accounts/report v2, distribution/installed
version/signature, integrated tests and cumulative PR5 review remain pending.

Historic native upgrade proof remains only
5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1 →
466d79a8ef0229adb72f36f56a9743a5db99960f in the existing laboratory, not this
candidate or the pilot device. Full candidate SHA, local checks, patches/bundle
and independent delta review are recorded in
C:/CDev/Next2U-Pilot-2026-09-26-manual-capture-provenance. No pilot readiness claim.
