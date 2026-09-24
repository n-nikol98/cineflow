import {InjectionToken} from '@angular/core';

/** REST endpoint for submitting and polling import tasks. */
export const IMPORTS_ENDPOINT = '/api/imports';

/**
 * Gives up polling a task after this many one-second checks (~5 minutes), so a task
 * stuck in {@code QUEUED}/{@code PROCESSING} forever (e.g. a crashed async worker)
 * does not poll the backend indefinitely.
 *
 * Provided as a DI token (rather than a plain constant) so tests can substitute a much
 * smaller cap for a single {@code TestBed} module, without mutating shared module state.
 */
export const MAX_POLL_ATTEMPTS = new InjectionToken<number>('MAX_POLL_ATTEMPTS', {
  providedIn: 'root',
  factory: () => 300
});
