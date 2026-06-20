import { registerWebModule, NativeModule } from 'expo';

class MainModule extends NativeModule<{}> {}

export default registerWebModule(MainModule, 'MainModule');
