# CLAUDE.md — fantasy-espn-service

ESPN integration microservice for the fantasy hockey tool (SlapStat). It reads a user's
ESPN fantasy **league settings** (scoring categories, roster positions, league size) and
exposes a REST API the BFF consumes — mirroring `fantasy-yahoo-service`, but for ESPN.

> ⚠️ Guarded by a shared `X-Internal-Api-Key` header (see `InternalApiKeyFilter`). Unlike
> yahoo-service there is **no public endpoint and no OAuth callback** — ESPN has no public
> OAuth for fantasy, so this service is reached only by the BFF over the internal network.

## Why ESPN is different from Yahoo

ESPN has **no public OAuth** for fantasy. There is no "Connect" button, no browser redirect,
no token exchange/refresh, no signed state.

- **Public leagues:** read with just a **league id + season** — no auth.
- **Private leagues:** read using the user's **`espn_s2` + `SWID`** browser cookies, which the
  user copies manually. We store them **encrypted at rest** (`espn_credentials`, AES-GCM via
  `TokenCipher`) keyed by the app user id, so they aren't re-entered. They are never returned
  over the API (only a `hasCredentials` flag) and never logged.

## In one picture

```
web (season + leagueId + optional cookies) → BFF → GET /api/v1/espn/leagues/{leagueId}/settings?appUserId=&season=
   → service loads the user's stored cookies (if any) and reads ESPN's v3 API
   → lm-api-reads.fantasy.espn.com/apis/v3/games/fhl/seasons/{season}/segments/0/leagues/{leagueId}?view=mSettings
   → maps ESPN's JSON to LeagueSettingsResponse; the BFF maps stat ids/slots to the projection domain
```

## Tech stack

- Java 25, Spring Boot 4.0.5, Gradle (wrapper: `./gradlew`)
- Spring WebMVC (virtual threads), Spring Data JPA, Bean Validation, Actuator
- `RestClient` for ESPN's v3 API; JDK `Cipher` (AES-GCM) for cookie encryption
- PostgreSQL (runtime), Flyway migrations
- Tests: JUnit 5, H2 in-memory (PostgreSQL mode), `MockRestServiceServer`
- springdoc OpenAPI / Swagger UI

## Common commands

```bash
docker compose up -d     # start Postgres (DB fantasy_espn, host port 5435)
./gradlew build          # compile + test + spotbugs (CI: ./gradlew build --no-daemon)
./gradlew test           # tests only (H2, no Postgres needed)
./gradlew bootRun        # run locally (requires Postgres via docker compose above)
```

Swagger UI (when running): `http://localhost:8090/swagger-ui.html`

## Architecture (`src/main/java/com/fantasy/espn/`)

- `credential/` — per-user ESPN cookie storage:
  - `EspnCredential` / `EspnCredentialRepository` — JPA entity (`espn_credentials`), keyed by
    `app_user_id`; cookies stored AES-GCM encrypted.
  - `TokenCipher` — AES-GCM encrypt/decrypt (`TOKEN_ENCRYPTION_KEY`, base64 256-bit).
  - `EspnCredentialService` — save / delete / `hasCredentials` / `find` (decrypts to `EspnCookies`).
  - `EspnCredentialController` — `PUT/GET/DELETE /api/v1/espn/credentials`.
- `league/` — fantasy league reads:
  - `EspnFantasyClient` — `RestClient` over the ESPN v3 API; returns `JsonNode`; attaches the
    cookie header for private leagues; maps ESPN 401/403 → private-league (400), 404 → not-found.
  - `EspnLeagueService` — parses ESPN's JSON into clean DTOs; holds the ESPN hockey **stat-id →
    abbreviation** and **lineup-slot-id → position code** maps.
  - `EspnLeagueController` — `GET /api/v1/espn/leagues/{leagueId}/{settings,teams}`.
  - `dto/` — `LeagueSettingsResponse` (+ `StatCategory`, `RosterSlot`), `LeagueTeamsResponse`
    (+ `LeagueTeam`).
- `player/` — cached season stat lines for the stats **Yahoo does not report**:
  - `EspnPlayerClient` — reads ESPN's *public* player endpoint
    (`/apis/v3/games/fhl/seasons/{season}/players?view=kona_player_info`). No league id and no
    cookies, which is what makes it usable for every user. The response is tens of MB (a split
    per game per player), so it is **stream-parsed** one player at a time.
  - `EspnPlayerStats` / `EspnPlayerStatsRepository` — JPA entity (`espn_player_stats`).
  - `EspnPlayerStatsService` — sync (replace-all in one transaction; never wipes on an empty
    fetch) + read. Drops ESPN's occasional duplicate player records, keeping the one that played.
  - `EspnPlayerSyncScheduler` — nightly, plus once at startup when the cache is empty.
  - `EspnPlayerController` — `GET /api/v1/espn/players`, `POST /api/v1/espn/players/sync`.
- `config/` — `OpenApiConfig` (pins server URL to `/`), `EspnProperties`
  (`@ConfigurationProperties("espn")`), `EspnRestClientConfig` (the ESPN `RestClient`),
  `InternalApiKeyFilter` (API-key auth; exempts only the actuator health/info probes).
- `exception/` — `EspnPrivateLeagueException` (→400), `EspnLeagueNotFoundException` (→404),
  `ErrorDto`, `GlobalExceptionHandler`.

## ESPN → projection mapping (hockey = `fhl`)

- **Lineup-slot ids → position:** `0 C · 1 LW · 2 RW · 3 F · 4 D · 5 G · 6 Util · 7 BN · 8 IR`.
- **defaultPositionId → position:** `1 C · 2 LW · 3 RW · 4 D · 5 G`.
- **Stat ids** (verified against real season lines, not guessed — ESPN publishes no glossary):
  - skaters: `13 G · 14 A · 15 +/- · 16 P · 17 PIM · 18 PPG · 19 PPA · 20 SHG · 21 SHA ·
    22 GWG · 23 FOW · 24 FOL · 25 shifts · 26 TOI (s) · 27 ATOI (s) · 28 hat tricks ·
    29 SOG · 31 HIT · 32 BLK · 33 defenseman points · 34 GP · 35/36/37 special-teams G/A/P ·
    38 PPP · 39 SHP`
  - goalies: `0 GS · 1 W · 2 L · 3 SA · 4 GA · 6 SV · 7 SO · 8 TOI (s) · 9 OTL · 10 GAA ·
    11 SV% · 12 win %`
  - `30 GP` is the universal games-played (skaters *and* goalies); `34` is skater-only.
- **A season is keyed by the year it ends in:** `seasons/2026` is the 2025-26 season.
- **scoringType** lives at `settings.scoringSettings.scoringType`; the BFF collapses it to
  points vs category. League size = `settings.size`.
- ESPN's v3 API is **unofficial** and can change shape without notice — keep all shape
  assumptions inside `EspnFantasyClient` + `EspnLeagueService`, covered by JSON fixtures.

## Database & config

- `application.yaml`: datasource `jdbc:postgresql://…/${DB_NAME:fantasy_espn}`,
  `ddl-auto: validate` (Flyway owns the schema), `server.port=${PORT:8090}`.
- `espn.*` — `api-base-url` (lm-api-reads.fantasy.espn.com), `game-key` (fhl),
  `token-encryption-key` (secret; defaults to empty so the app boots for tests/CI, credential
  endpoints just fail at call time when unset).
- Secrets come **only** from env (`DB_PASSWORD`, `INTERNAL_API_KEY`, `TOKEN_ENCRYPTION_KEY`) —
  never committed. Migrations live in `src/main/resources/db/migration/` (`V1`). Schema changes
  = a new `V__` migration, never edit an applied one. Tests use H2 (`create-drop`, Flyway off).

## Conventions

- **No code comments unless they aid the reader.** Prefer self-explanatory names.
- Feature-package layout. Keep endpoints under `/api/v1`.
- Cookies are **always encrypted at rest** — never store or log a raw cookie; never return
  them (only `hasCredentials`). The encryption key comes only from env.

### Logging & error handling

**Never silence an error.** The `@RestControllerAdvice` logs the full stack trace
(`log.error`) for genuine faults and returns a consistent `ErrorDto`:

- **5xx / genuine faults** (unexpected exceptions, ESPN upstream failing): log at `ERROR`.
- **4xx / expected client outcomes** (private league / missing cookies → 400, no such league
  → 404, malformed id/season → 400): do **not** log as errors.

### OpenAPI annotations & spec snapshot (`specs/openapi.yaml`)

This service's spec is consumed by `fantasy-bff` to generate a typed client. Annotate
controllers with `@Tag`/`@Operation`/`@ApiResponse`. `OpenApiSpecSnapshotTest` boots the app
and asserts the committed `specs/openapi.yaml` matches the live spec. Regenerate:
```
./gradlew test -DupdateSpec=true   # rewrites specs/openapi.yaml
git add specs/openapi.yaml
```
`OpenApiConfig` pins the server URL to `/` so the spec is deterministic.

## CI / workflow

- `.github/workflows/pr-checks.yml`: `./gradlew build --no-daemon` on PRs to `master`.
- `tag-on-merge.yml`: SemVer auto-tag + staging deploy on squash-merge (baseline `v0.1.0`).
- `promote-to-prod.yml`: manual, version-pinned prod promotion.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

## Monorepo conventions

The web talks only to the BFF; inter-service calls use a shared `X-Internal-Api-Key` header.
Branch → push → PR → checks pass → **squash merge** to `master` (the PR title becomes the
commit message; make it a proper `feat:`/`fix:` message and merge with an explicit subject).
No attribution trailers. Secrets only from env, never committed.

## Deployment

Dockerized (multi-stage `Dockerfile`), deployed via **Coolify** (Hetzner) with its own
dedicated Coolify Postgres, on staging + prod. **Internal-only** — no public domain (there is
no OAuth callback); the BFF reaches it at the internal alias `espn-service:8090`. Set
`INTERNAL_API_KEY` (same value the BFF sends as `ESPN_INTERNAL_API_KEY`) and
`TOKEN_ENCRYPTION_KEY`. Health check: `/actuator/health`.
