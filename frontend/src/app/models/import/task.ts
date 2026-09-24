import {ImportStatus} from './status';

export interface ImportTask {
  id: number;
  fileName: string;
  status: ImportStatus;
  recordCount: number;
  error: string;
}
