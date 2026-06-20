import { useEffect, useState } from 'react';
import { PermissionsAndroid, View } from 'react-native';
import { Button, Text, useTheme } from 'react-native-paper';

import { useSpacing } from '@/ui/use-spacing';
import { BluetoothMessages } from './bluetooth-messages';
const BLUETOOTH_PERMISSIONS = [
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_ADVERTISE,
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
    ]
export function IndexImpl() {
  const theme = useTheme();
  const spacing = useSpacing();
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  const [permissionsRequested, setPermissionsRequested] = useState(false);

  const requestPermissions = () => {
    PermissionsAndroid.requestMultiple(BLUETOOTH_PERMISSIONS).then((result) => {
      setPermissionsGranted(
        BLUETOOTH_PERMISSIONS.every((perm) => result[perm] === PermissionsAndroid.RESULTS.GRANTED),
      );
      setPermissionsRequested(true);
    });
  };

  useEffect(() => {
    requestPermissions();
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.background, padding: spacing.dp8 }}>
      {permissionsGranted ? (
        <BluetoothMessages />
      ) : permissionsRequested ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', gap: spacing.dp8 }}>
          <Text variant="bodyLarge" style={{ color: theme.colors.onBackground, textAlign: 'center' }}>
            Bluetooth permissions are required to receive messages.
          </Text>
          <Button mode="contained" onPress={requestPermissions}>
            Grant Permissions
          </Button>
        </View>
      ) : (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Text variant="bodyLarge" style={{ color: theme.colors.onSurfaceVariant }}>
            Requesting permissions...
          </Text>
        </View>
      )}
    </View>
  );
}