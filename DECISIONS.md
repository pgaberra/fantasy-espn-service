# Decision log

Non-obvious choices in fantasy-espn-service, one line each. The format and rules are in the
monorepo root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-espn-service: `TOKEN_ENCRYPTION_KEY` has no default, and `TokenCipher` decodes and checks it once in its constructor (set, base64, 32 bytes), so a bad key stops startup (#20). Before shipping, staging and prod were checked to hold a 44-character key that decodes to 32 bytes, without printing it. Rejected: `@Validated` constraints on `EspnProperties`, which cannot say "decodes to 32 bytes" without a custom validator and would still leave `TokenCipher` decoding the key on every call.
