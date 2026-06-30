import { registerWebModule, NativeModule } from 'expo';
import { BluetoothModuleEvents } from './Bluetooth.types';

class BluetoothModule extends NativeModule<BluetoothModuleEvents> {
  startGattServer(): void {}
  stopGattServer(): void {}
  enqueueDataToRead(_data: Uint8Array): void {}
}

export default registerWebModule(BluetoothModule, 'BluetoothModule');
