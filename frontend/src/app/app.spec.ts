import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed, fakeAsync, tick} from '@angular/core/testing';
import {App} from './app';
import {IMPORTS_ENDPOINT, MAX_POLL_ATTEMPTS} from './config/imports-api';
import {ImportResult} from './models/import/result';
import {ImportStatus} from './models/import/status';
import {ImportTask} from './models/import/task';

describe('App', () => {
  let component: App;
  let httpMock: HttpTestingController;

  function createApp(maxPollAttempts?: number) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...(maxPollAttempts === undefined
          ? []
          : [{provide: MAX_POLL_ATTEMPTS, useValue: maxPollAttempts}])
      ]
    });

    const fixture = TestBed.createComponent(App);

    return {
      component: fixture.componentInstance,
      httpMock: TestBed.inject(HttpTestingController)
    };
  }

  describe('with the default poll limit', () => {
    beforeEach(() => {
      ({component, httpMock} = createApp());
    });

    afterEach(() => {
      httpMock.verify();
    });

    describe('pick', () => {
      it('captures the selected files from the input change event', () => {
        const file = new File(['<xml/>'], 'movies.xml');
        const input = document.createElement('input');
        Object.defineProperty(input, 'files', {value: [file]});

        component.pick(input);

        expect(component.selectedFiles).toEqual([file]);
      });

      it('clears the selection when the input has no files', () => {
        component.selectedFiles = [new File([], 'stale.xml')];
        const input = document.createElement('input');
        Object.defineProperty(input, 'files', {value: null});

        component.pick(input);

        expect(component.selectedFiles).toEqual([]);
      });
    });

    describe('buttonLabel', () => {
      it('reads "Start Import" with no files selected', () => {
        component.selectedFiles = [];
        expect(component.buttonLabel).toBe('Start Import');
      });

      it('reads the singular label with exactly one file selected', () => {
        component.selectedFiles = [new File([], 'a.xml')];
        expect(component.buttonLabel).toBe('Start Import');
      });

      it('reads the plural, counted label with more than one file selected', () => {
        component.selectedFiles = [new File([], 'a.xml'), new File([], 'b.xml')];
        expect(component.buttonLabel).toBe('Start 2 Imports');
      });
    });

    describe('isInProgress', () => {
      it('is true for QUEUED and PROCESSING', () => {
        expect(component.isInProgress(ImportStatus.QUEUED)).toBeTrue();
        expect(component.isInProgress(ImportStatus.PROCESSING)).toBeTrue();
      });

      it('is false for COMPLETED and FAILED', () => {
        expect(component.isInProgress(ImportStatus.COMPLETED)).toBeFalse();
        expect(component.isInProgress(ImportStatus.FAILED)).toBeFalse();
      });
    });

    describe('taskMessage', () => {
      const baseTask: ImportResult = {
        id: 1,
        fileName: 'a.xml',
        status: ImportStatus.QUEUED,
        recordCount: 0,
        error: ''
      };

      it('surfaces the submission error, ignoring status, when submissionFailed is set', () => {
        const message = component.taskMessage({
          ...baseTask,
          status: ImportStatus.FAILED,
          error: 'boom',
          submissionFailed: true
        });
        expect(message).toBe('boom');
      });

      it('surfaces the timeout message, ignoring status, when pollingTimedOut is set', () => {
        const message = component.taskMessage({
          ...baseTask,
          status: ImportStatus.PROCESSING,
          pollingTimedOut: true,
          error: 'Gave up waiting for this import to finish.'
        });
        expect(message).toContain('Gave up waiting');
      });

      it('describes a QUEUED task', () => {
        expect(component.taskMessage({...baseTask, status: ImportStatus.QUEUED})).toBe(
          'Queued for import.'
        );
      });

      it('describes a PROCESSING task', () => {
        expect(
          component.taskMessage({
            ...baseTask,
            status: ImportStatus.PROCESSING
          })
        ).toBe('Importing…');
      });

      it('describes a COMPLETED task with its record count', () => {
        expect(
          component.taskMessage({
            ...baseTask,
            status: ImportStatus.COMPLETED,
            recordCount: 7
          })
        ).toBe('Imported 7 movie(s).');
      });

      it('describes a FAILED task with its error', () => {
        expect(
          component.taskMessage({
            ...baseTask,
            status: ImportStatus.FAILED,
            error: 'db down'
          })
        ).toBe('Import failed: db down');
      });

      it('describes a FAILED task with a fallback when no error is set', () => {
        expect(
          component.taskMessage({
            ...baseTask,
            status: ImportStatus.FAILED,
            error: ''
          })
        ).toBe('Import failed: Unknown error.');
      });
    });

    describe('upload', () => {
      it('does nothing when no files are selected', () => {
        component.selectedFiles = [];
        component.upload(document.createElement('input'));

        httpMock.expectNone(() => true);
        expect(component.submitting).toBeFalse();
        expect(component.imports).toEqual([]);
      });

      it('submits the selected file, resets the picker, and tracks the returned task', fakeAsync(() => {
        const file = new File(['<xml/>'], 'movies.xml');
        component.selectedFiles = [file];
        const fileInput = document.createElement('input');
        fileInput.value = 'movies.xml';

        component.upload(fileInput);

        expect(component.submitting).toBeTrue();
        expect(component.selectedFiles).toEqual([]);
        expect(fileInput.value).toBe('');

        const postRequest = httpMock.expectOne(IMPORTS_ENDPOINT);
        expect(postRequest.request.method).toBe('POST');
        expect(postRequest.request.body instanceof FormData).toBeTrue();
        const queuedTask: ImportTask = {
          id: 1,
          fileName: 'movies.xml',
          status: ImportStatus.QUEUED,
          recordCount: 0,
          error: ''
        };
        postRequest.flush(queuedTask);

        expect(component.submitting).toBeFalse();
        expect(component.imports.length).toBe(1);
        expect(component.imports[0].status).toBe(ImportStatus.QUEUED);

        tick(0);
        const getRequest = httpMock.expectOne(`${IMPORTS_ENDPOINT}/1`);
        expect(getRequest.request.method).toBe('GET');
        const completedTask: ImportTask = {
          id: 1,
          fileName: 'movies.xml',
          status: ImportStatus.COMPLETED,
          recordCount: 12,
          error: ''
        };
        getRequest.flush(completedTask);

        expect(component.imports[0].status).toBe(ImportStatus.COMPLETED);
        expect(component.imports[0].recordCount).toBe(12);
      }));

      it('submits every selected file independently', fakeAsync(() => {
        component.selectedFiles = [new File([], 'a.xml'), new File([], 'b.xml')];
        component.upload(document.createElement('input'));

        const requests = httpMock.match(IMPORTS_ENDPOINT);
        expect(requests.length).toBe(2);
        requests.forEach((request, index) => {
          const task: ImportTask = {
            id: index + 1,
            fileName: `file-${index}.xml`,
            status: ImportStatus.QUEUED,
            recordCount: 0,
            error: ''
          };
          request.flush(task);
        });

        tick(0);
        httpMock
          .match(() => true)
          .forEach((request) =>
            request.flush({
              id: 1,
              fileName: 'a.xml',
              status: ImportStatus.COMPLETED,
              recordCount: 1,
              error: ''
            } as ImportTask)
          );

        expect(component.imports.length).toBe(2);
        expect(component.submitting).toBeFalse();
      }));

      it('records a submission failure using the backend ApiErrorDto message', fakeAsync(() => {
        component.selectedFiles = [new File([], 'empty.xml')];
        component.upload(document.createElement('input'));

        httpMock
          .expectOne(IMPORTS_ENDPOINT)
          .flush(
            {code: 'EMPTY_FILE', message: 'The uploaded file is empty.'},
            {status: 400, statusText: 'Bad Request'}
          );

        expect(component.imports.length).toBe(1);
        expect(component.imports[0].submissionFailed).toBeTrue();
        expect(component.imports[0].error).toBe('The uploaded file is empty.');
        expect(component.submitting).toBeFalse();
      }));

      it('falls back to a generic message when the backend response has no ApiErrorDto body', fakeAsync(() => {
        component.selectedFiles = [new File([], 'a.xml')];
        component.upload(document.createElement('input'));

        httpMock.expectOne(IMPORTS_ENDPOINT).flush('Internal Server Error', {
          status: 500,
          statusText: 'Internal Server Error'
        });

        expect(component.imports[0].error).toBe('The import request could not be accepted.');
      }));

      it('marks a task FAILED when polling itself errors', fakeAsync(() => {
        component.selectedFiles = [new File([], 'a.xml')];
        component.upload(document.createElement('input'));

        const queuedTask: ImportTask = {
          id: 5,
          fileName: 'a.xml',
          status: ImportStatus.QUEUED,
          recordCount: 0,
          error: ''
        };
        httpMock.expectOne(IMPORTS_ENDPOINT).flush(queuedTask);

        tick(0);
        httpMock
          .expectOne(`${IMPORTS_ENDPOINT}/5`)
          .flush(
            {code: 'NOT_FOUND', message: 'Import task not found.'},
            {status: 404, statusText: 'Not Found'}
          );

        expect(component.imports[0].status).toBe(ImportStatus.FAILED);
        expect(component.imports[0].error).toBe('Import task not found.');
      }));

      it('stops polling as soon as a task reaches a terminal status, without a timeout', fakeAsync(() => {
        component.selectedFiles = [new File([], 'a.xml')];
        component.upload(document.createElement('input'));

        const queuedTask: ImportTask = {
          id: 3,
          fileName: 'a.xml',
          status: ImportStatus.QUEUED,
          recordCount: 0,
          error: ''
        };
        httpMock.expectOne(IMPORTS_ENDPOINT).flush(queuedTask);

        tick(0);
        const failedTask: ImportTask = {
          id: 3,
          fileName: 'a.xml',
          status: ImportStatus.FAILED,
          recordCount: 0,
          error: 'Bad XML'
        };
        httpMock.expectOne(`${IMPORTS_ENDPOINT}/3`).flush(failedTask);

        expect(component.imports[0].status).toBe(ImportStatus.FAILED);
        expect(component.imports[0].pollingTimedOut).toBeFalsy();

        // No further polling GET should ever be issued once a terminal status is reached.
        tick(5000);
        httpMock.expectNone(`${IMPORTS_ENDPOINT}/3`);
      }));

      it('cancels a still-in-progress batch\'s polling once a new batch is submitted', fakeAsync(() => {
        component.selectedFiles = [new File([], 'first.xml')];
        component.upload(document.createElement('input'));

        const firstQueuedTask: ImportTask = {
          id: 10,
          fileName: 'first.xml',
          status: ImportStatus.QUEUED,
          recordCount: 0,
          error: ''
        };
        httpMock.expectOne(IMPORTS_ENDPOINT).flush(firstQueuedTask);

        // First batch is still QUEUED/PROCESSING (never reaches a terminal status) when a
        // second batch is submitted.
        tick(0);
        httpMock.expectOne(`${IMPORTS_ENDPOINT}/10`).flush({...firstQueuedTask, status: ImportStatus.PROCESSING});

        component.selectedFiles = [new File([], 'second.xml')];
        component.upload(document.createElement('input'));

        const secondQueuedTask: ImportTask = {
          id: 11,
          fileName: 'second.xml',
          status: ImportStatus.QUEUED,
          recordCount: 0,
          error: ''
        };
        httpMock.expectOne(IMPORTS_ENDPOINT).flush(secondQueuedTask);

        // Only the new batch's task is polled; the first batch's poll loop was cancelled.
        tick(5000);
        httpMock.expectNone(`${IMPORTS_ENDPOINT}/10`);
        const pendingSecondBatchRequests = httpMock.match(`${IMPORTS_ENDPOINT}/11`);
        pendingSecondBatchRequests[pendingSecondBatchRequests.length - 1].flush(
          {...secondQueuedTask, status: ImportStatus.COMPLETED, recordCount: 1}
        );

        expect(component.imports.length).toBe(1);
        expect(component.imports[0].id).toBe(11);
      }));
    });
  });

  describe('with a short poll limit', () => {
    beforeEach(() => {
      ({component, httpMock} = createApp(2));
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('gives up and marks the task FAILED after exhausting poll attempts while still in progress', fakeAsync(() => {
      component.selectedFiles = [new File([], 'slow.xml')];
      component.upload(document.createElement('input'));

      const queuedTask: ImportTask = {
        id: 9,
        fileName: 'slow.xml',
        status: ImportStatus.QUEUED,
        recordCount: 0,
        error: ''
      };
      httpMock.expectOne(IMPORTS_ENDPOINT).flush(queuedTask);

      const processingTask: ImportTask = {
        id: 9,
        fileName: 'slow.xml',
        status: ImportStatus.PROCESSING,
        recordCount: 0,
        error: ''
      };

      tick(0);
      httpMock.expectOne(`${IMPORTS_ENDPOINT}/9`).flush(processingTask);

      tick(1000);
      httpMock.expectOne(`${IMPORTS_ENDPOINT}/9`).flush(processingTask);

      expect(component.imports[0].status).toBe(ImportStatus.FAILED);
      expect(component.imports[0].pollingTimedOut).toBeTrue();
    }));
  });
});
