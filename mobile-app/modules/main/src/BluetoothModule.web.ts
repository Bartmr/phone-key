import { registerWebModule, NativeModule } from 'expo';

class BluetoothModule extends NativeModule<{}> {}

export default registerWebModule(BluetoothModule, 'BluetoothModule');
