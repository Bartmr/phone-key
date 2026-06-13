import { MainViewProps } from './Main.types';

// MainView is not available on the web platform.
export default function MainView(_props: MainViewProps) {
  throw new Error('MainView is not available on the web platform.');
}
