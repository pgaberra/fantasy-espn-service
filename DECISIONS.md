# Decision log

Non-obvious choices in fantasy-espn-service, one line each. The format and rules are in the
monorepo root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-espn-service: `docker-compose.yml` runs `postgres:16-alpine` to match the staging and prod databases (both 16), not 17. Rejected: moving the servers to 17, a data migration on two databases for nothing this service uses. A local volume created under 17 will not start on 16 and has to be recreated (`docker compose down -v`).
[2026-09-11] fantasy-espn-service: only a dedicated `EspnUpstreamException`, thrown by `EspnFantasyClient`, `EspnPlayerClient` and `EspnLeagueService` when a league comes back with no settings object, answers 502, and `IllegalStateException` falls to the catch-all 500 (#21). A league with no settings counts as upstream because ESPN answered with a document we cannot read. Rejected: telling ESPN failures apart by message or cause, because the JDK type means nothing on its own (db-service maps it to 409) and a string check breaks silently.
