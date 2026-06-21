import { NativeModule, requireNativeModule } from 'expo';

import { BluetoothModuleEvents } from './Bluetooth.types';

declare class BluetoothModule extends NativeModule<BluetoothModuleEvents> {
}

export default requireNativeModule<BluetoothModule>('BluetoothModule');
