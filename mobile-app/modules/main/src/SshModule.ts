import { NativeModule, requireNativeModule } from 'expo';

import type { SshPublicKeyInfo, SshSignResult } from './SshModule.types';

declare class SshModule extends NativeModule<{}> {
  /**
   * Generates an EC P-256 key pair in the Android Keystore (TEE).
   * Throws if a key already exists. The key is wiped only by uninstalling the app.
   */
  initializeKey(): Promise<void>;

  /**
   * Signs `data` with SHA256withECDSA using the stored private key.
   * Requires user authentication (biometric/PIN) on every call.
   *
   * Returns either `{ signature: Uint8Array }` on success (64 bytes for P-256),
   * or `{ error: { code: number, message: string } }` on failure / cancellation.
   */
  sign(data: Uint8Array): Promise<SshSignResult>;

  /**
   * Returns the public key in raw uncompressed-point form and SSH authorized_keys format.
   */
  getPublicKey(): Promise<SshPublicKeyInfo>;
}

export default requireNativeModule<SshModule>('SshModule');
