

export interface SshSignError {
  code: number;
  message: string;
}

export interface SshSignResult {
  /** Raw r||s signature (64 bytes for P-256). Only present on success. */
  signature?: Uint8Array;
  /** Error details. Only present on failure / cancellation. */
  error?: SshSignError;
}
