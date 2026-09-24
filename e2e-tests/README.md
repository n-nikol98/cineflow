# CineFlow end-to-end (functional) tests

This folder is a self-contained [Bruno](https://www.usebruno.com/) collection that
exercises every real CineFlow backend HTTP endpoint over the network, against the
actual `docker-compose` stack (PostgreSQL + backend + mock outbound server) - unlike
`backend/src/test/java/.../integration`, which runs the Spring context in-process
inside the same JVM as the test.

It lives at the workspace root, next to `backend/`, `frontend/`, and `mock-server/`,
because it is a functional/system test suite for the whole running application, not
something internal to any one of those projects.

Bruno was chosen over Postman because Bruno's collections are just plain text `.bru`
files (no proprietary cloud account or paid tier needed), and its official
[`@usebruno/cli`](https://www.npmjs.com/package/@usebruno/cli) runner works headlessly
in CI or a terminal, with no GUI required - though you're welcome to open this same
folder in the Bruno desktop app instead if you prefer clicking around.

## Prerequisites

* The full stack running via Docker Compose from the workspace root:

  ```
  docker compose up -d --build
  ```

  Wait until `http://localhost:8080/api/movies` responds (a few seconds after the
  containers start).
* Node.js (any reasonably recent version) to run the Bruno CLI via `npx` - nothing to
  install ahead of time.

## Running the main suite

From this folder (`e2e-tests/`):

```
npx @usebruno/cli run --env Local -r 01-Imports 02-Movies 03-Deliveries
```

This runs, in order:

1. **`01-Imports`** - uploads `fixtures/valid-multi-movie.xml` and polls
   `GET /imports/{id}` until it completes, then fetches that same completed task
   again as its own dedicated `GET /imports/{id}` success-path request; uploads a
   file with an in-file duplicate movie; uploads a file missing a required field
   (schema validation failure); uploads a genuinely malformed (not well-formed)
   XML file; uploads an empty file (rejected synchronously); and checks the 404
   path for an unknown import id.
2. **`02-Movies`** - lists movies (default and explicit paging), looks up the movie
   imported in step 1 by its distinctive title, fetches it via a dedicated
   `GET /movies/{id}` request and asserts every field round-trips correctly,
   resolves that same movie via its natural key with `GET /movies/lookup`
   (`title` + `directorId` + `releaseYear` - the same fields a failed-import or
   duplicate error message contains, so this endpoint lets you go straight from
   that error to the movie's id), checks the 404 path for an unknown natural
   key, checks the 400 path for a missing/invalid natural key parameter, and
   checks the 404 path for an unknown movie id.
3. **`03-Deliveries`** - checks a freshly-imported movie's delivery status via a
   dedicated `GET /deliveries/{id}` request, checks the 404 path for an unknown
   movie id, lists `/deliveries/failed` (default and explicit paging), and
   exercises `POST /deliveries/{id}/retry` for both an existing movie and an
   unknown movie id (404).

`02-Movies` and `03-Deliveries` depend on runtime variables (`e2eMovieId`, etc.) set
while running `01-Imports`, so all three folders must be run together, in this order,
in the same CLI invocation, as shown above - which is also exactly the order Bruno
runs them in if you instead run the whole collection recursively
(`npx @usebruno/cli run --env Local -r .`), since folder order follows the `seq`
declared in each folder's `folder.bru`.

You should see all requests pass:

```
Requests      21 (21 Passed)
Tests         27/27
```

### Why some tests only assert response *shape*

Scheduled outbound delivery (cron) and the mock outbound server's configurable
failure rate (`MOCK_OUTBOUND_FAILURE_RATE`) are both timing-dependent, so a
delivery's actual status (`PENDING`/`SENT`/`FAILED`) at any given moment cannot be
predicted. Tests that touch delivery status (`03-Deliveries/01` and `.../03`) only
assert the response shape and that returned statuses are one of the valid enum
values, rather than asserting a specific status.

### Why fixtures are safe to re-run

The fixture XML files are static, and movies are unique by
`(title, director, releaseYear)`. Re-running this suite against a database that
already has these movies simply skips them as duplicates (`recordCount: 0`) rather
than failing - see `ImportWriter#persistIfNew` in the backend. Tests assert
`status: COMPLETED` and `recordCount >= 0`, never an exact count, so the suite stays
green whether it's the first run ever or the hundredth.

## `04-Race-Condition-Example`

A separate, dedicated folder demonstrating the backend's documented duplicate-movie
race condition (see `ImportWriter`'s class Javadoc in the backend). It is
**deliberately excluded** from the commands above - run it on its own, following the
instructions in `04-Race-Condition-Example/folder.bru`, since it needs a special
`--csv-file-path`/`--parallel` invocation to fire two genuinely concurrent uploads,
and its outcome is best-effort/non-deterministic rather than a guaranteed pass.

## Collection layout

```
e2e-tests/
  bruno.json                    Bruno collection manifest
  environments/Local.bru        baseUrl = http://localhost:8080/api
  fixtures/                     sample XML files used by the import tests
  01-Imports/                   POST /imports, GET /imports/{id}
  02-Movies/                    GET /movies, GET /movies/{id}, GET /movies/lookup
  03-Deliveries/                GET /deliveries/{id}, GET /deliveries/failed, POST /deliveries/{id}/retry
  04-Race-Condition-Example/    dedicated, opt-in duplicate-movie race demo
```

## Configuration

The `Local` environment (`environments/Local.bru`) builds the target URL from four
separate variables, rather than one opaque string, so any one piece can be
overridden independently:

```
protocol: http
host: localhost
port: 8080
contextPath: api
baseUrl: {{protocol}}://{{host}}:{{port}}/{{contextPath}}
```

The defaults match `docker-compose.yml`'s default port mapping and
`application.yml`'s `server.servlet.context-path` (`/api`) - `contextPath` itself
holds just the path segment, without a leading slash, since `baseUrl` supplies the
separator explicitly. Every request in this collection uses `{{baseUrl}}`, so to
point the suite elsewhere, edit whichever of `protocol`/`host`/`port`/`contextPath`
changed (or override it on the CLI, e.g.
`--env-var port=9090`) rather than editing `baseUrl` itself.
