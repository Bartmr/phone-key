import { NativeModule, requireNativeModule } from 'expo';

import { Key, SshKeyModuleEvents } from './SshKey.types';

declare class SshKeyModule extends NativeModule<SshKeyModuleEvents> {
  getKey(): Promise<Key | null>;
  generateKeyPair(): Promise<Key>;
  getKeyPassphrase(): Promise<string>;
}

export default requireNativeModule<SshKeyModule>('SshKeyModule');
