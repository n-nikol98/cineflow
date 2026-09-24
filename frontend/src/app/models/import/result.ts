import {ImportTask} from './task';

export interface ImportResult extends Omit<ImportTask, 'id'> {
  id: number | string;
  submissionFailed?: boolean;
  pollingTimedOut?: boolean;
}
