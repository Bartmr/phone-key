import { requireNativeView } from 'expo';
import * as React from 'react';

import { MainViewProps } from './Main.types';

const NativeView: React.ComponentType<MainViewProps> = requireNativeView('Main');

export default function MainView(props: MainViewProps) {
  return <NativeView {...props} />;
}
