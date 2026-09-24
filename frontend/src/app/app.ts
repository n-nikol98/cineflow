import {CommonModule} from '@angular/common';
import {Component, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {finalize, Subscription, switchMap, take, takeWhile, timer} from 'rxjs';
import {ApiError} from './models/api-error';
import {IMPORTS_ENDPOINT, MAX_POLL_ATTEMPTS} from './config/imports-api';
import {ImportResult} from './models/import/result';
import {ImportStatus} from './models/import/status';
import {ImportTask} from './models/import/task';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly http = inject(HttpClient);
  private readonly maxPollAttempts = inject(MAX_POLL_ATTEMPTS);

  selectedFiles: File[] = [];
  imports: ImportResult[] = [];
  submitting = false;
  pendingSubmissions = 0;

  /**
   * Subscriptions for the poll loop of every import currently tracked in {@link imports}.
   * Torn down in {@link upload} before starting a new batch, so a previous batch's polling
   * never keeps running (and hitting the backend) once its imports are no longer displayed.
   * Replaced (not reused) after each teardown: an already-unsubscribed {@link Subscription}
   * immediately unsubscribes anything added to it afterwards.
   */
  private pollSubscriptions = new Subscription();

  pick(input: HTMLInputElement) {
    this.selectedFiles = Array.from(input.files ?? []);
  }

  get buttonLabel() {
    if (this.selectedFiles.length <= 1) {
      return 'Start Import';
    }
    return `Start ${this.selectedFiles.length} Imports`;
  }

  upload(fileInput: HTMLInputElement) {
    if (this.selectedFiles.length === 0) {
      return;
    }

    this.submitting = true;
    const files = this.selectedFiles;
    this.selectedFiles = [];
    this.imports = [];
    this.pendingSubmissions = files.length;
    this.pollSubscriptions.unsubscribe();
    this.pollSubscriptions = new Subscription();
    fileInput.value = '';

    for (const file of files) {
      this.submit(file);
    }
  }

  private submit(file: File) {
    const data = new FormData();
    data.append('file', file);

    this.http.post<ImportTask>(IMPORTS_ENDPOINT, data).pipe(
      finalize(() => {
        this.pendingSubmissions--;
        this.submitting = this.pendingSubmissions > 0;
      })
    ).subscribe({
      next: task => {
        this.imports.unshift(task);
        this.pollTask(task.id);
      },
      error: (response: HttpErrorResponse) => {
        this.imports.unshift({
          id: `failed-${Date.now()}-${file.name}`,
          fileName: file.name,
          status: ImportStatus.FAILED,
          recordCount: 0,
          error: this.apiErrorMessage(response, 'The import request could not be accepted.'),
          submissionFailed: true
        });
      }
    });
  }

  private pollTask(taskId: number) {
    const subscription = timer(0, 1000).pipe(
      take(this.maxPollAttempts),
      switchMap(() => this.http.get<ImportTask>(`${IMPORTS_ENDPOINT}/${taskId}`)),
      takeWhile(task => this.isInProgress(task.status), true)
    ).subscribe({
      next: task => {
        this.updateTask(task);
      },
      error: (response: HttpErrorResponse) => {
        this.updateTask({
          id: taskId,
          fileName: this.findTask(taskId)?.fileName ?? `Task #${taskId}`,
          status: ImportStatus.FAILED,
          recordCount: 0,
          error: this.apiErrorMessage(response, 'The import status could not be retrieved.')
        });
      },
      complete: () => {
        // Only reached without an intervening COMPLETED/FAILED update if MAX_POLL_ATTEMPTS
        // was exhausted while the task was still in progress; takeWhile's own completion
        // (a genuine terminal status) already leaves the task out of progress by this point.
        const task = this.findTask(taskId);
        if (task && this.isInProgress(task.status)) {
          this.updateTask({
            ...task,
            status: ImportStatus.FAILED,
            error: 'Gave up waiting for this import to finish. It may still be running on '
                + 'the server; check back later or contact an administrator.',
            pollingTimedOut: true
          });
        }
      }
    });
    this.pollSubscriptions.add(subscription);
  }

  private apiErrorMessage(response: HttpErrorResponse, fallback: string) {
    const apiError = response.error as ApiError | null;
    return apiError?.message || fallback;
  }

  private updateTask(task: ImportResult) {
    const index = this.imports.findIndex(existing => existing.id === task.id);
    this.imports[index] = task;
  }

  private findTask(taskId: number) {
    return this.imports.find(task => task.id === taskId);
  }

  isInProgress(status: ImportStatus) {
    return status === ImportStatus.QUEUED || status === ImportStatus.PROCESSING;
  }

  taskMessage(task: ImportResult) {
    if (task.submissionFailed || task.pollingTimedOut) {
      return task.error;
    }

    switch (task.status) {
      case ImportStatus.QUEUED:
        return 'Queued for import.';
      case ImportStatus.PROCESSING:
        return 'Importing…';
      case ImportStatus.COMPLETED:
        return `Imported ${task.recordCount} movie(s).`;
      case ImportStatus.FAILED:
        return `Import failed: ${task.error || 'Unknown error.'}`;
    }
  }
}
