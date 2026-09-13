# Decision log

Non-obvious choices in fantasy-espn-service, one line each. The format and rules are in the
monorepo root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-espn-service: `docker-compose.yml` runs `postgres:16-alpine` to match the staging and prod databases (both 16), not 17. Rejected: moving the servers to 17, a data migration on two databases for nothing this service uses. A local volume created under 17 will not start on 16 and has to be recreated (`docker compose down -v`).
[2026-09-11] fantasy-espn-service: only a dedicated `EspnUpstreamException`, thrown by `EspnFantasyClient`, `EspnPlayerClient` and `EspnLeagueService` when a league comes back with no settings object, answers 502, and `IllegalStateException` falls to the catch-all 500 (#21). A league with no settings counts as upstream because ESPN answered with a document we cannot read. Rejected: telling ESPN failures apart by message or cause, because the JDK type means nothing on its own (db-service maps it to 409) and a string check breaks silently.
[2026-09-11] fantasy-espn-service: `.github/dependabot.yml` is a verbatim copy of fantasy-bff's and fantasy-db-service's (gradle + github-actions, weekly, one group each), and one catch-up bump to their versions landed first (fantasy-workspace#59). Rejected: switching Dependabot on at the old versions, which would make its first PR Boot 4.0→4.1, Sentry 8.16→8.53 and four action majors in one.
[2026-09-11] fantasy-espn-service: `TOKEN_ENCRYPTION_KEY` has no default, and `TokenCipher` decodes and checks it once in its constructor (set, base64, 32 bytes), so a bad key stops startup (#20). Before shipping, staging and prod were checked to hold a 44-character key that decodes to 32 bytes, without printing it. Rejected: `@Validated` constraints on `EspnProperties`, which cannot say "decodes to 32 bytes" without a custom validator and would still leave `TokenCipher` decoding the key on every call.
[2026-09-13] fantasy-espn-service: a busy `EspnPlayerSyncService.sync()` returns empty instead of throwing, and the nightly scheduler logs that overlap as a skip at INFO, ported from fantasy-yahoo-service#49. Rejected: an `isRunning()` pre-check in the scheduler, which leaves a check-then-act gap, and keeping the throw with a quieter log, which would also quieten real failures sharing the catch.
