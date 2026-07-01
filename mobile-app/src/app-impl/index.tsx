import { View } from 'react-native';
import { useBluetoothMessagesHandler } from './bluetooth-messages-handler';
import { Text } from 'react-native-paper';
import { TestBluetooth } from './test-bluetooth';


export function IndexImpl() {

  return (
      <TestBluetooth />
  );
}
