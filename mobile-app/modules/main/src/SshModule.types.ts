export interface SshPublicKeyInfo {
  /** Uncompressed EC point: 0x04 || x (32 bytes) || y (32 bytes) — 65 bytes total */
  raw: Uint8Array;
  /** SSH authorized_keys format: "ecdsa-sha2-nistp256 <base64>" */
  sshFormat: string;
}

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
