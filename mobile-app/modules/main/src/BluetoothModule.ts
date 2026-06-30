import { NativeModule, requireNativeModule } from 'expo';

import { BluetoothModuleEvents } from './Bluetooth.types';

declare class BluetoothModule extends NativeModule<BluetoothModuleEvents> {
  startGattServer(): void;
  stopGattServer(): void;
  sendToClient(data: Uint8Array): void;
}

export default requireNativeModule<BluetoothModule>('BluetoothModule');
