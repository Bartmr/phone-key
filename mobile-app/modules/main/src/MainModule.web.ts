import { registerWebModule, NativeModule } from 'expo';

// MainModule is not available on the web platform.
class MainModule extends NativeModule<{}> {}

export default registerWebModule(MainModule, 'MainModule');
