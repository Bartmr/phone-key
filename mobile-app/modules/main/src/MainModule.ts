import { NativeModule, requireNativeModule } from 'expo';

declare class MainModule extends NativeModule<{}> {}

export default requireNativeModule<MainModule>('Main');
