'use strict';

/**
 * A very simple mock "downstream" server for CineFlow's outbound delivery feature.
 *
 * It accepts POSTed movie JSON payloads (the shape produced by the backend's
 * MovieDto), logs each one, and returns a response. To make it useful for exercising
 * the backend's retry logic, it:
 *   - rejects obviously invalid payloads (missing id/title) with 422,
 *   - rejects malformed JSON with 400,
 *   - rejects non-POST requests with 405, and
 *   - randomly fails a configurable percentage of otherwise-valid requests with a
 *     transient-looking 500, so retries/backoff have something to do.
 *
 * On top of that, every one of these responses can also be *simulated* on an
 * otherwise perfectly valid request, purely for testing the backend's handling of
 * each status code without needing to send it genuinely-malformed data (which the
 * real backend never does). This is controlled via env vars, each a 0.0-1.0
 * probability checked independently and in this priority order:
 *   - SIMULATED_400_RATE (default 0):   respond 400 as if the JSON were malformed
 *   - SIMULATED_405_RATE (default 0):   respond 405 as if the method were disallowed
 *   - SIMULATED_422_RATE (default 0):   respond 422 as if the payload were invalid
 *   - FAILURE_RATE       (default 0.2): respond 500 (transient failure)
 *
 * No framework/dependencies are used on purpose, to keep this genuinely minimal;
 * it is not meant to be a realistic or production-like service.
 */

import http from 'node:http';

const PORT = Number(process.env.PORT || 9090);
const FAILURE_RATE = Number(process.env.FAILURE_RATE ?? 0.2); // 0.0-1.0
const SIMULATED_400_RATE = Number(process.env.SIMULATED_400_RATE ?? 0);
const SIMULATED_405_RATE = Number(process.env.SIMULATED_405_RATE ?? 0);
const SIMULATED_422_RATE = Number(process.env.SIMULATED_422_RATE ?? 0);

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
    });
    req.on('end', () => resolve(raw));
    req.on('error', reject);
  });
}

function isValidMovie(movie) {
  return (
    movie !== null &&
    typeof movie === 'object' &&
    movie.id !== undefined &&
    movie.id !== null &&
    typeof movie.title === 'string' &&
    movie.title.trim().length > 0
  );
}

function respondJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

const server = http.createServer(async (req, res) => {
  if (req.method !== 'POST') {
    respondJson(res, 405, { error: 'Only POST is supported by this mock server.' });
    return;
  }

  const raw = await readBody(req);

  let movie;
  try {
    movie = JSON.parse(raw || 'null');
  } catch (parseError) {
    console.warn('[mock-server] rejected invalid JSON:', parseError.message);
    respondJson(res, 400, { error: 'Invalid JSON payload.' });
    return;
  }

  if (!isValidMovie(movie)) {
    console.warn('[mock-server] rejected invalid movie payload:', raw);
    respondJson(res, 422, { error: 'Movie payload must include at least id and title.' });
    return;
  }

  if (Math.random() < SIMULATED_400_RATE) {
    console.warn(`[mock-server] simulated 400 for otherwise-valid movie ${movie.id} (${movie.title})`);
    respondJson(res, 400, { error: 'Simulated invalid JSON payload.' });
    return;
  }

  if (Math.random() < SIMULATED_405_RATE) {
    console.warn(`[mock-server] simulated 405 for otherwise-valid movie ${movie.id} (${movie.title})`);
    respondJson(res, 405, { error: 'Simulated method not allowed.' });
    return;
  }

  if (Math.random() < SIMULATED_422_RATE) {
    console.warn(`[mock-server] simulated 422 for otherwise-valid movie ${movie.id} (${movie.title})`);
    respondJson(res, 422, { error: 'Simulated unprocessable movie payload.' });
    return;
  }

  if (Math.random() < FAILURE_RATE) {
    console.warn(`[mock-server] simulated transient failure for movie ${movie.id} (${movie.title})`);
    respondJson(res, 500, { error: 'Simulated downstream failure, please retry.' });
    return;
  }

  console.log(`[mock-server] accepted movie ${movie.id} (${movie.title})`);
  respondJson(res, 200, { status: 'accepted', id: movie.id });
});

server.listen(PORT, () => {
  console.log(`Mock server listening on port ${PORT} ` +
      `(FAILURE_RATE=${FAILURE_RATE}, SIMULATED_400_RATE=${SIMULATED_400_RATE}, ` +
      `SIMULATED_405_RATE=${SIMULATED_405_RATE}, SIMULATED_422_RATE=${SIMULATED_422_RATE})`,
  );
});
