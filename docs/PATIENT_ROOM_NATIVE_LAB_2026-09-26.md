# Native encrypted schema 7 to 8 lab

PROPOSED / CONCEPTUAL verification harness, synthetic development only; no REAL
pilot readiness implied. Production code is unchanged from candidate
d8d48ceb445047b08aedb50577b30defb1381053. The two frozen Room 7 fixtures mirror
PR5 e9a80ef386d207a1bc6fe66bef3969eafa84aae5 and local
ffe5210e553b810fb0105703543a297d2b7b2719 queue definitions; other five entities
were unchanged between those versions. Fixture creation is not execution of an
old APK or proof of the version installed on Rafael's phone.

Build app and instrumentation with `-PstorageLab=true`. Use only a newly created,
explicitly identified emulator, with no previous package or database. The runner
requires exact source/APK hashes, checks both packages have no INTERNET permission,
and checks lab application/startup isolation before installing. No operational
configuration, credential or user data is needed. Preserve earlier deliveries,
checkouts and emulators; never clear data, uninstall, or reseed retained evidence.

Three separate process invocations seed, migrate/reject, and reopen three encrypted
databases using the application's Android Keystore-wrapped key. For each known
schema, nine original rows across all six tables survive migration, including all
queue payloads, IDs, statuses, retries, authorization blocks, timestamps and nulls.
The deleted synthetic row 900 establishes the AUTOINCREMENT watermark. Duplicate
clientReadingId is refused; a new synthetic row receives 901. Subsequent process
reopen must retain the resulting ten rows, schema identity and wrapped-key digest.

A third published-schema fixture adds an unknown hydration column. Room must
reject it during post-migration schema validation and roll back queue rebuild,
version, identity and sequence. All nine rows and the extra column must remain,
also in a subsequent process. This is transactional rollback after a caught
validation error, not sudden power loss or process termination inside migration.

No change to Web, Core, ACS, transport, shared contracts or clinical behavior.
Existing production migration has no network dependency; this harness does not
test concurrent consumers, old-queue replay, live synchronization, BLE or physical
update. Native x86_64 evidence does not prove arm64 hardware. Final results and
independent incremental review belong in a new external delivery tied to the
complete source SHA. Local results are not CI, independent cumulative review,
human merge approval, distribution approval or pilot acceptance.
