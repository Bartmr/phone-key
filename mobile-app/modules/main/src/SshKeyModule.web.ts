import { registerWebModule, NativeModule } from 'expo';
import { Key, SshKeyModuleEvents } from './SshKey.types';

class SshKeyModule extends NativeModule<SshKeyModuleEvents> {
  async getKey(): Promise<Key | null> {
    throw new Error()
  }
  async generateKeyPair(): Promise<Key> {
    throw new Error()
  }
  async getKeyPassphrase(): Promise<string> {
    throw new Error()
  }
}

export default registerWebModule(SshKeyModule, 'SshKeyModule');
