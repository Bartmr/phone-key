import { View } from 'react-native';
import { useBluetoothCommandsHandler } from './bluetooth-commands-handler';
import { Text } from 'react-native-paper';


export function IndexImpl() {
  const bluetoothHandler = useBluetoothCommandsHandler();

  return (
    <View>
      <Text>
        Listening...
      </Text>
    </View>
  );
}
