# CLAUDE.md — fantasy-espn-service

ESPN integration microservice for the fantasy hockey tool (SlapStat). It reads a user's
ESPN fantasy **league settings** (scoring categories, roster positions, league size) **and
owns the cached player read model** — identity, ESPN eligible positions and season stat
lines — exposing a REST API the BFF consumes. It mirrors `fantasy-yahoo-service`, but for
ESPN, and takes over the player pool that service can no longer fetch: Yahoo refuses the
game-wide player collection while our API access is pending.

> ⚠️ Guarded by a shared `X-Internal-Api-Key` header (see `InternalApiKeyFilter`). Unlike
> yahoo-service there is **no public endpoint and no OAuth callback** — ESPN has no public
> OAuth for fantasy, so this service is reached only by the BFF over the internal network.

## Why ESPN is different from Yahoo

ESPN has **no public OAuth** for fantasy. There is no "Connect" button, no browser redirect,
no token exchange/refresh, no signed state.

- **Public leagues:** read with just a **league id + season** — no auth.
- **Private leagues:** read using the user's **`espn_s2` + `SWID`** browser cookies, which the
  user copies manually. We store them **encrypted at rest** (`espn_credentials`, AES-GCM via
  `TokenCipher`) keyed by the app user id, so they aren't re-entered. They are never logged,
  and they leave the service decrypted in exactly two ways: to the league reader, which sends
  them to ESPN, and through `GET /api/v1/espn/credentials/values`, which returns them for the
  `appUserId` the BFF passes (the BFF takes it from the caller's JWT and hands the values to the
  web, whose ESPN import form prefills a user's own saved cookies). That read-back was a
  deliberate trade-off in #11, so a change to request logging or XSS handling in the BFF or web
  *can* expose these cookies.

## In one picture

```
web (season + leagueId + optional cookies) → BFF → GET /api/v1/espn/leagues/{leagueId}/settings?appUserId=&season=
   → service loads the user's stored cookies (if any) and reads ESPN's v3 API
   → lm-api-reads.fantasy.espn.com/apis/v3/games/fhl/seasons/{season}/segments/0/leagues/{leagueId}?view=mSettings
   → maps ESPN's JSON to LeagueSettingsResponse; the BFF maps stat ids/slots to the projection domain
```

## Tech stack

- Java 25, Spring Boot 4.1.1, Gradle (wrapper: `./gradlew`)
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
  - `EspnCredentialController` — `PUT/GET/DELETE /api/v1/espn/credentials` (the `GET` answers
    only `hasCredentials`), and `GET /api/v1/espn/credentials/values`, which returns the
    decrypted cookies (404 when none are stored).
- `league/` — fantasy league reads:
  - `EspnFantasyClient` — `RestClient` over the ESPN v3 API; returns `JsonNode`; attaches the
    cookie header for private leagues; maps ESPN 401/403 → private-league (400), 404 → not-found.
  - `EspnLeagueService` — parses ESPN's JSON into clean DTOs; holds the ESPN hockey **stat-id →
    abbreviation** and **lineup-slot-id → position code** maps.
  - `EspnLeagueController` — `GET /api/v1/espn/leagues/{leagueId}/{settings,teams}`.
  - `dto/` — `LeagueSettingsResponse` (+ `StatCategory`, `RosterSlot`), `LeagueTeamsResponse`
    (+ `LeagueTeam`).
- `player/` — the cached **player read model**: identity, ESPN's fantasy eligibility and one
  stat line per player and season. It is the app's player source now that Yahoo refuses the
  game-wide player collection, and it still serves the stats Yahoo never reported at all.
  - `EspnPlayerClient` — reads ESPN's *public* player endpoint
    (`/apis/v3/games/fhl/seasons/{season}/players?view=kona_player_info`). No league id and no
    cookies, which is what makes it usable for every user. The response is tens of MB (a split
    per game per player), so it is **stream-parsed** one player at a time. Maps `proTeamId` →
    the abbreviation the app shows, `eligibleSlots` → positions, and derives what ESPN doesn't
    report (shooting %, faceoff %, `avgToi` as "MM:SS"). `FetchedPlayer` / `FetchedSkaterSeason`
    / `FetchedGoalieSeason` are what it returns.
  - `EspnPlayer` + `EspnSkaterSeason` / `EspnGoalieSeason` (`@IdClass(PlayerSeasonId)`) — JPA
    entities (`espn_players`, `espn_skater_seasons`, `espn_goalie_seasons`). Identity and
    numbers are separate because their lifetimes are: the pool turns over between seasons
    while a finished season's totals never change again.
  - `EspnPlayerSyncService` — replace-all in one transaction, and never on thin evidence: it
    refuses an empty fetch, a suspiciously short one, and a payload with no games played for
    the reference season. Stores only the seasons that have actually been played — ESPN answers
    for an unplayed season with all-zero rows, which would read as a scoreless season. Drops
    ESPN's occasional duplicate player records, keeping the one that played — keyed by name,
    position **and jersey**, because two different people do share a name and a position (two
    Matt Murrays in goal, two Connor Murphys on defence) and collapsing them would throw away a
    real season. A player ESPN no longer lists as active is kept while a stored season still
    holds their numbers, because a projection still references them.
  - `EspnHeadshotVerifier` — the headshot URL is built from the player's id, so one can be
    produced for anybody, but ESPN has no picture for roughly **one player in seven** and
    answers those with a 404. Handing out such a URL puts a broken image in the app where a
    player with no headshot at all would have drawn a placeholder, so the sync asks the CDN
    (one HEAD each, eight at a time) and drops the ones that do not resolve. **Only a definite
    404 drops a URL** — a timeout or a refusal keeps it, because a rate-limited sync must not
    be able to strip the pool of every picture. Off with `ESPN_VERIFY_HEADSHOTS=false`.
  - `EspnPlayerService` — reads: the pool with a chosen season's stats, and the narrow
    reference-season lines the BFF joins onto its own player list by name.
  - `EspnPlayerSyncScheduler` — nightly, plus once at startup when the cache is **missing or
    stale** (`espn.player-stats-max-age`, 36h). Staleness rather than emptiness: a deployment
    that changes what the sync stores leaves a full but outdated cache, and an empty-only check
    would sit on it until the next nightly run with nothing to show the data didn't match the
    code. That happened twice while this service was being built.
  - `EspnPlayerController` — `GET /api/v1/espn/players/{skaters,goalies}?season=`,
    `GET /api/v1/espn/players`, `GET /api/v1/espn/players/sync/latest` (when the pool was last
    written and whether one is running — for a caller whose only question is whether it has
    moved, and for watching a triggered sync), `POST /api/v1/espn/players/sync` → **202**, which
    starts the work and answers. The work is minutes long (tens of megabytes fetched and parsed,
    then a headshot check over the whole pool), and holding an HTTP request open across every
    hop for that long is what made the synchronous version fragile — a client that gave up early
    reported a failure that had not happened, while the write finished regardless. A second
    trigger while one runs is **409**: `startAsync` makes the claim, so two callers cannot both
    be told they started it.
- `config/` — `OpenApiConfig` (pins server URL to `/`), `EspnProperties`
  (`@ConfigurationProperties("espn")`), `EspnRestClientConfig` (the ESPN `RestClient`),
  `InternalApiKeyFilter` (API-key auth; exempts only the actuator health/info probes).
- `exception/` — `EspnPrivateLeagueException` (→400), `EspnLeagueNotFoundException` (→404),
  `EspnUpstreamException` (→502, thrown only where ESPN itself failed: `EspnFantasyClient`,
  `EspnPlayerClient`, and a league document with no settings), `ErrorDto`, `GlobalExceptionHandler`.
  Anything else, `IllegalStateException` included, is this service's own fault and answers 500.

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
- **A season is keyed by the year it ends in:** `seasons/2027` is the 2026-27 season. That
  convention stops at `EspnPlayerClient`, which converts: everything below it — the stored
  rows, the `season` query param, `espn.player-pool-season`, `espn.player-reference-season` —
  is a **start year**, the way the rest of the app says a season. `espn.season` (leagues) is
  still ESPN's end-year id.
- ESPN opens a season months before it is played, so the pool season and the reference season
  are **not** the same value for most of the year. ESPN reports all-zero season totals for a
  season that hasn't started, so pointing a stat read at the pool season would replace real
  numbers with nothing rather than fail — hence the sync's refusal to store a season nobody
  played, and its refusal to write at all unless the reference season has real games in it.
- One fetch of the pool season carries **both** seasons: the upcoming one (identity and
  eligibility) and the one just played (the totals a projection is seeded from).
- **scoringType** lives at `settings.scoringSettings.scoringType`; the BFF collapses it to
  points vs category. League size = `settings.size`.
- ESPN's v3 API is **unofficial** and can change shape without notice — keep all shape
  assumptions inside `EspnFantasyClient` + `EspnLeagueService`, covered by JSON fixtures.

## Database & config

- `application.yaml`: datasource `jdbc:postgresql://…/${DB_NAME:fantasy_espn}`,
  `ddl-auto: validate` (Flyway owns the schema), `server.port=${PORT:8090}`.
- `espn.*` — `api-base-url` (lm-api-reads.fantasy.espn.com), `game-key` (fhl),
  `token-encryption-key` (secret, no default; `TokenCipher` refuses to start the service when
  it is missing or doesn't decode to 32 bytes, so a bad key fails the deploy rather than the
  first cookie save. The test yaml supplies a non-secret key).
- Secrets come **only** from env (`DB_PASSWORD`, `INTERNAL_API_KEY`, `TOKEN_ENCRYPTION_KEY`) —
  never committed. Migrations live in `src/main/resources/db/migration/` (`V1`–`V4`). Schema
  changes = a new `V__` migration, never edit an applied one. Tests use H2 (`create-drop`,
  Flyway off) — the migrations themselves are first exercised by the staging deploy.

## Conventions

- **No code comments unless they aid the reader.** Prefer self-explanatory names.
- Feature-package layout. Keep endpoints under `/api/v1`.
- Cookies are **always encrypted at rest** — never store or log a raw cookie. Decrypted, they
  go only to ESPN and back through `/credentials/values`; don't add a third way out. The
  encryption key comes only from env.

### Error handling

The monorepo-wide rule (never silence an error; `ERROR` for 5xx, quiet for 4xx) lives in
the root `CLAUDE.md`. The expected 4xx here are: private league / missing cookies → 400,
no such league → 404, malformed id or season → 400. Only `EspnUpstreamException` is a 502; don't
throw it for a local fault, or the log blames ESPN for something ESPN never saw.

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
- `promote-to-prod.yml`: manual, version-pinned prod promotion. It checks the version production
  actually serves, and a failure opens a `prod-promotion-failed` issue that the next promotion of
  the latest release closes.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

## Monorepo conventions

The full set lives in the monorepo root `CLAUDE.md`: input validation at every boundary,
logging & error handling, secrets only from env, one worktree per agent, and the merge
procedure. In short — the web talks only to the BFF; inter-service calls carry a shared
`X-Internal-Api-Key` header. Branch → push → PR → checks pass → **squash merge** to `master`
(the PR title becomes the commit message; make it a proper `feat:`/`fix:` message and merge
with an explicit `--subject`). No attribution trailers. Secrets only from env, never
committed. Never merge a PR titled "wip"/"draft".

## Deployment

Dockerized (multi-stage `Dockerfile`), deployed via **Coolify** (Hetzner) with its own
dedicated Coolify Postgres, on staging + prod. **Internal-only** — no public domain (there is
no OAuth callback); the BFF reaches it at the internal alias `espn-service:8090`. Set
`INTERNAL_API_KEY` (same value the BFF sends as `ESPN_INTERNAL_API_KEY`) and
`TOKEN_ENCRYPTION_KEY`. Health check: `/actuator/health`.
