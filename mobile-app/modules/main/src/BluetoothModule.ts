import { NativeModule, requireNativeModule } from 'expo';

import { BluetoothModuleEvents } from './Bluetooth.types';

declare class BluetoothModule extends NativeModule<BluetoothModuleEvents> {
  startGattServer(): Promise<void>;
  stopGattServer(): Promise<void>;
}

export default requireNativeModule<BluetoothModule>('BluetoothModule');
