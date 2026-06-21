import { NativeModule, requireNativeModule } from 'expo';

import { MainModuleEvents } from './Main.types';

declare class MainModule extends NativeModule<MainModuleEvents> {
}

export default requireNativeModule<MainModule>('MainModule');
