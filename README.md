
# CineFlow


### Upload your favorite movies using XML files!


They will be submitted with the help of a minimal Angular frontend and pass through a Spring Boot 3 / Java 21 / PostgreSQL backend which supports multipart uploads, asynchronous persistence and retryable scheduled outbound delivery.

## Run

### Option A: Full stack via Docker Compose

`docker compose up -d --build` builds and starts all four services: `postgres`, `backend` (multi-stage `backend/Dockerfile`, waits for Postgres to report healthy), `mock-server` (see below), and `frontend` (multi-stage `frontend/Dockerfile`: `npm run build` then served by Nginx, which also proxies `/api` to the backend). Open `http://localhost:4200`. By default the backend is configured to deliver movies to the bundled mock server every 30 seconds; to use custom credentials, set `DB_NAME`, `DB_USER`, and `DB_PASSWORD` in your shell (or a local git-ignored `.env` file) before running compose.

Note: this serves a static production build, so it does not hot-reload — use Option B while actively developing the frontend.

### Option B: Postgres via Docker, backend/frontend run locally (recommended for development)

1. Start PostgreSQL only: `docker compose up -d postgres`. To use custom credentials, set `DB_NAME`, `DB_USER`, and `DB_PASSWORD` in your shell (or a local git-ignored `.env` file) before running compose, matching the backend's `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` settings below.
2. (Optional) Start the mock server so scheduled deliveries have somewhere to send to: `cd mock-server && npm start` (listens on `http://localhost:9090`; see below for details).
3. Run backend: `cd backend && mvn spring-boot:run`. If you set `DB_PASSWORD` (or other overrides) in `.env`, export them into your shell first, e.g. `export $(grep -v '^#' ../.env | xargs)`, so the backend process picks up the same values docker compose used. Set `DELIVERY_URL=http://localhost:9090` (and `DELIVERY_CRON`, e.g. `*/30 * * * * *`) if you started the mock server in step 2 and want to see outbound delivery run.
4. Run frontend: `cd frontend && npm install && npm start`; open `http://localhost:4200`.

Upload XML shaped like:

```xml
<movies>
  <movie>
    <title>Example Movie</title>
    <releaseYear>2024</releaseYear>
    <durationMinutes>120</durationMinutes>
    <genres>
      <genre>Drama</genre>
      <genre>Thriller</genre>
    </genres>
    <director id="nm-director-1">Example Director</director>
    <actors>
      <actor id="nm0000001">Example Actor</actor>
      <actor id="nm0000002">Another Actor</actor>
    </actors>
    <description>A movie description.</description>
  </movie>
</movies>
```

The importer persists each movie into the normalized `movie` table, with shared rows in the normalized `genre` table and a `movie_genre` join table. Movies are unique by title, director, and release year (all required fields), so the same title from the same director in a different year is treated as a distinct movie. Directors and actors are stored once in their own role tables, identified by their required external `id`, and related to movies through foreign keys and the `movie_actor` join table. A person may have both roles, so the same external identity may appear in both role tables. Names are display attributes and are not used as identity. It stores typed columns for the movie fields rather than retaining the XML as an opaque payload. API endpoints use the shared `/api` prefix: `POST /api/imports` returns HTTP 202 and a task id; poll `GET /api/imports/{id}`.

Uploaded XML is unmarshalled into JAXB model classes and validated against `backend/src/main/resources/import.xsd` before records are persisted. The schema validates the document structure, required movie titles/directors, numeric year/duration fields, and actor/director IDs; the typed JAXB model is then converted into the normalized database entities.

## Mock outbound server

`mock-server/` is a minimal dependency-free Node script that stands in for the (unimplemented) second/downstream API, so outbound delivery has somewhere real to send to. It:

- accepts `POST` requests with a JSON movie payload and returns `200` on success;
- rejects malformed JSON with `400`, non-`POST` requests with `405`and payloads missing `id`/`title` with `422`, so schema/method-type problems are visible;
- randomly fails a configurable percentage of otherwise-valid requests with `500` (`FAILURE_RATE`, default `0.2`), in order to exercise the backend's bounded exponential backoff retries.

To make it easy to see the backend handle `400`/`405`/`422` responses too (without sending it genuinely malformed data, which the real backend never does), each of those statuses can also be simulated on an otherwise perfectly valid request, via its own independent probability env var: `SIMULATED_400_RATE`, `SIMULATED_405_RATE`, `SIMULATED_422_RATE` (all default `0`, i.e. off). For example, `SIMULATED_405_RATE=0.3` makes ~30% of valid deliveries receive a simulated `405` instead.

Run it standalone with `cd mock-server && npm start` (listens on `PORT`, default `9090`; `FAILURE_RATE`/`SIMULATED_400_RATE`/`SIMULATED_405_RATE`/`SIMULATED_422_RATE` all configurable via env var), or via `docker compose up -d mock-server` (also started automatically as part of the full Option A stack, at `http://mock-server:9090` inside the Docker network). It logs each accepted/rejected/failed request to the console; it does not persist or validate anything beyond `id`/`title`, since its only purpose is to give the retry/backoff and scheduled delivery logic something realistic to talk to.

## Configuration

Environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `IMPORT_CONCURRENCY` (default `4`; size of the bounded thread pool that processes uploaded XML files asynchronously — see below), `IMPORT_MAX_FILE_SIZE`/`IMPORT_MAX_REQUEST_SIZE` (default `10MB`/`11MB`; the per-upload-file and total multipart-request limits, respectively), `DELIVERY_URL`, `DELIVERY_CRON` (Spring six-field cron expression; for example `0 0 22-23,0-8 * * *` to run once per hour from 22:00 through 08:00), `DELIVERY_CONCURRENCY` (default `8`; how many eligible movies a single scheduled run sends in parallel — see below), `RETRY_MAX_ATTEMPTS`, `RETRY_INITIAL_DELAY`, `RETRY_MULTIPLIER`, `RETRY_MAX_DELAY`, and `IMPORT_STALE_AFTER`/`IMPORT_STALE_CHECK_INTERVAL` (ISO-8601 durations, default `PT10M`/`PT5M` — see below). `RETRY_INITIAL_DELAY` and `RETRY_MAX_DELAY` are integer **milliseconds** (defaults: `1000` and `30000`); unlike the import stale task settings, they do not accept ISO-8601 duration values such as `PT1S`. `DELIVERY_URL` and `DELIVERY_CRON` are read when the application starts. If either is blank, outbound delivery is disabled.

`NGINX_MAX_BODY_SIZE` (default `12M`) configures the frontend container's nginx reverse proxy `client_max_body_size` for `/api/`. It is only relevant when serving the frontend via Docker (nginx's own default is 1MB, well under the backend's limits above) and should be kept slightly above `IMPORT_MAX_REQUEST_SIZE` so oversized uploads are rejected by the backend's dedicated `413 FILE_TOO_LARGE` handler rather than by nginx directly.

Uploaded XML files are parsed and persisted asynchronously on a dedicated, bounded thread pool (sized by `IMPORT_CONCURRENCY`), separate from the delivery pool below, so imports and outbound deliveries can never starve each other's concurrency budget.

An import task can be left stuck in `QUEUED`/`PROCESSING` if the application restarts or crashes mid-import (e.g. an out-of-memory kill on a huge file), since nothing is left running to ever finish it. `ImportReconciler` runs every `IMPORT_STALE_CHECK_INTERVAL` and fails any task still in progress after `IMPORT_STALE_AFTER` (by `createdAt`), with a message explaining what happened. Re-upload the file only after confirming that the original application instance or worker is no longer running; a task that completed successfully before reconciliation does not need to be re-uploaded. This is a heuristic based on age, not a cancellation mechanism: a genuinely still-running import of an unusually large file older than the threshold may also be marked failed, but its worker will continue running and can later overwrite the task status or persist data. Pick `IMPORT_STALE_AFTER` comfortably above how long a real import takes, and treat a reconciled task as safe to re-upload only after the original worker has stopped.

Outbound delivery is tracked per movie and uses the configured `DELIVERY_URL`. New movies are `PENDING`; successful deliveries become `SENT`, while deliveries that exhaust the bounded retry policy become `FAILED` with their attempt count, last attempt time, and error retained. Find a movie via `GET /api/movies` (paged listing), `GET /api/movies/{id}` (single movie), or `GET /api/movies/lookup?title=&directorId=&releaseYear=` (natural-key lookup - handy for tracing a movie from a failed delivery straight to its id), then read its delivery state with `GET /api/deliveries/{id}` and reset it with `POST /api/deliveries/{id}/retry`. To find failed deliveries directly, use the paged `GET /api/deliveries/failed` endpoint (standard Spring `page`/`size`/`sort` query parameters).

Each scheduled run sends all eligible movies concurrently on a bounded thread pool (sized by `DELIVERY_CONCURRENCY`), instead of one at a time: with a naive sequential loop, a single slow or retrying delivery would block every other eligible movie behind it, and — since Spring runs `@Scheduled` methods on a single shared thread by default — could stall every other scheduled task in the application for the duration of the run. The pool is bounded (not one thread per movie) to cap how many concurrent outbound HTTP calls and DB connections the delivery pipeline can use at once. A run still waits for its whole batch to finish before returning, so two runs can never overlap and resend the same `PENDING` movie.

## Logging

The backend uses Log4j2 (configured in `backend/src/main/resources/log4j2.xml`), replacing Spring Boot's default Logback. Application code under `com.nedko.cineflow` logs at `DEBUG`; everything else logs at `INFO`. Output goes to the console and three rolling files (each rotated daily and at 10MB, keeping the last 10 archives):

- `logs/app.log` — general application log (everything not covered by the two files below).
- `logs/import.log` — the import path only: `ImportService`, `ImportProcessor`, `ImportWriter` (file receipt and task queuing, per-import success/failure with record counts, skipped duplicate movies).
- `logs/delivery.log` — the delivery path only: `DeliveryClient`, `DeliveryScheduler` (delivery attempts, manual retries, and permanent delivery failures with the full exception once retries are exhausted).

To change the log directory, set the `cineflow.log.dir` system property, for example `java -Dcineflow.log.dir=/var/log/cineflow -jar cineflow-backend.jar`. It defaults to `logs/` relative to the working directory. To change log levels without editing the XML, use standard Spring Boot properties, for example `--logging.level.com.nedko.cineflow=INFO`.

When run via Docker Compose, the backend's working directory is `/app`, so logs are written to `/app/logs` inside the container. `docker-compose.yml` bind-mounts `./backend/logs` (on the host) to that path, so the same three log files end up in `backend/logs/` on your machine and survive `docker compose down`/container recreation, just like when running the backend locally via `mvn spring-boot:run`.

## Tests

Backend: `cd backend && mvn test`. Includes unit tests for the async pipeline, retry/backoff, delivery scheduling, config binding, and the stale import reconciler, plus in-process Spring integration tests (`backend/src/test/java/.../integration`) hitting the real controllers with a full application context backed by an in-memory H2 database.

Frontend: `cd frontend && npm test` runs the Karma/Jasmine unit test suite (covers file selection, submission, polling — including the timeout and error handling paths — and status messaging) once headlessly (`ng test`'s default `watch: true` is overridden to `singleRun`/non-watch in `karma.conf.js`). It uses `puppeteer`'s bundled Chromium via `karma.conf.js` (falling back to a cached download at `~/.cache/puppeteer`), so no system Chrome/Chromium install is required; the first `npm install` may take a bit longer as it downloads Chromium once.

End-to-end (functional): `e2e-tests/` is a [Bruno](https://www.usebruno.com/) collection that exercises every backend HTTP endpoint over the network against the real `docker compose` stack, including the actual async XML import pipeline (valid/duplicate/invalid/malformed/empty file uploads, polling to a terminal status) and delivery endpoints. See `e2e-tests/README.md` for prerequisites and how to run it.

## Known limitations

Duplicate detection (same title/director/releaseYear) is enforced two ways: `ImportWriter` skips duplicates it already sees *within* the file currently being parsed, and the database has a unique constraint as the final safety net. If two different files are uploaded concurrently and both contain the same new movie for the first time, both imports can pass the in-memory duplicate check (neither has persisted the movie yet), then race to insert it: one import commits successfully, the other fails its whole transaction on the database's unique-constraint violation. This is by design, not a bug — imports are all-or-nothing (a failure keeps only already-committed reference data, never a partially persisted movie list), so the failing import can simply be re-uploaded safely. `ImportProcessor` catches this specific case (`DataIntegrityViolationException`) and records a clear, actionable message on the task explaining what happened and that a re-upload will succeed, instead of the raw, unreadable JDBC/Hibernate exception text.