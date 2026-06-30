import { NativeModule, registerWebModule } from 'expo';

class SshModule extends NativeModule<{}> {
  async initializeKey(): Promise<void> {
    throw new Error('SshModule is not available on web');
  }

  async sign(_data: Uint8Array): Promise<Uint8Array> {
    throw new Error('SshModule is not available on web');
  }

  async getPublicKey(): Promise<string> {
    throw new Error('SshModule is not available on web');
  }
}

export default registerWebModule(SshModule, 'SshModule');
