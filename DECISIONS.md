# Decision log

Non-obvious choices in fantasy-espn-service, one line each. The format and rules are in the
monorepo root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-espn-service: `.github/dependabot.yml` is a verbatim copy of fantasy-bff's and fantasy-db-service's (gradle + github-actions, weekly, one group each), and one catch-up bump to their versions landed first (fantasy-workspace#59). Rejected: switching Dependabot on at the old versions, which would make its first PR Boot 4.0→4.1, Sentry 8.16→8.53 and four action majors in one.
