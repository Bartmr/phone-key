import { NativeModule, registerWebModule } from 'expo';
import type { SshPublicKeyInfo } from './SshModule.types';

class SshModule extends NativeModule<{}> {
  async initializeKey(): Promise<void> {
    throw new Error('SshModule is not available on web');
  }

  async sign(_data: Uint8Array): Promise<Uint8Array> {
    throw new Error('SshModule is not available on web');
  }

  async getPublicKey(): Promise<SshPublicKeyInfo> {
    throw new Error('SshModule is not available on web');
  }
}

export default registerWebModule(SshModule, 'SshModule');
