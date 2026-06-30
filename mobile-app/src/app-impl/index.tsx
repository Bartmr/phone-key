import { View } from 'react-native';
import { useBluetoothMessagesHandler } from './bluetooth-messages-handler';
import { Text } from 'react-native-paper';


export function IndexImpl() {
  const bluetoothHandler = useBluetoothMessagesHandler();

  return (
    <View>
      <Text>
        Listening...
      </Text>
    </View>
  );
}
