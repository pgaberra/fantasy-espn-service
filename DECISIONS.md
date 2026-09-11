# Decision log

Non-obvious choices in fantasy-espn-service, one line each. The format and rules are in the
monorepo root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-espn-service: `docker-compose.yml` runs `postgres:16-alpine` to match the staging and prod databases (both 16), not 17. Rejected: moving the servers to 17, a data migration on two databases for nothing this service uses. A local volume created under 17 will not start on 16 and has to be recreated (`docker compose down -v`).
