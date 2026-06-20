import { NativeModule, requireNativeModule } from 'expo';

import { MainModuleEvents } from './Main.types';

declare class MainModule extends NativeModule<MainModuleEvents> {
    startGattServer: () => Promise<void>;
    stopGattServer: () => Promise<void>;
}

export default requireNativeModule<MainModule>('MainModule');
