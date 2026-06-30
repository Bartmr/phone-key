import { registerWebModule, NativeModule } from 'expo';
import { BluetoothModuleEvents } from './Bluetooth.types';

class BluetoothModule extends NativeModule<BluetoothModuleEvents> {
  startGattServer(): void {}
  stopGattServer(): void {}
  sendToClient(_data: Uint8Array): void {}
}

export default registerWebModule(BluetoothModule, 'BluetoothModule');
