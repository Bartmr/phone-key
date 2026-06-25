import { registerWebModule, NativeModule } from 'expo';
import { BluetoothModuleEvents } from './Bluetooth.types';

class BluetoothModule extends NativeModule<BluetoothModuleEvents> {
  async startGattServer(): Promise<void> {}
  async stopGattServer(): Promise<void> {}
  async pushToReadData(_data: Uint8Array): Promise<void> {}
}

export default registerWebModule(BluetoothModule, 'BluetoothModule');
