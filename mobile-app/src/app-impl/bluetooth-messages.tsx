import { useCallback, useEffect, useState } from 'react';
import { View } from 'react-native';
import { Button, Text, useTheme } from 'react-native-paper';

import MainModule from '@/modules/main/src/MainModule';
import { useSpacing } from '@/ui/use-spacing';

export function BluetoothMessages() {
  const theme = useTheme();
  const spacing = useSpacing();
  const [messages, setMessages] = useState<string[]>([]);
  const [serverRunning, setServerRunning] = useState(false);

  useEffect(() => {
    const subscription = MainModule.addListener('onMessageReceived', (event) => {
      setMessages((prev) => [...prev, event.message]);
    });
    return () => subscription.remove();
  }, []);

  const toggleServer = useCallback(async () => {
    if (serverRunning) {
      await MainModule.stopGattServer();
      setServerRunning(false);
    } else {
      await MainModule.startGattServer();
      setServerRunning(true);
    }
  }, [serverRunning]);

  return (
    <View style={{ flex: 1 }}>
      <Text
        variant="headlineSmall"
        style={{ color: theme.colors.onBackground, marginBottom: spacing.dp8 }}
      >
        Phone Key
      </Text>

      <Button
        mode="contained"
        onPress={toggleServer}
        style={{ marginBottom: spacing.dp8 }}
      >
        {serverRunning ? 'Stop GATT Server' : 'Start GATT Server'}
      </Button>

      {messages.length === 0 ? (
        <Text variant="bodyMedium" style={{ color: theme.colors.onSurfaceVariant }}>
          Waiting for Bluetooth messages...
        </Text>
      ) : (
        messages.map((msg, i) => (
          <View
            key={i}
            style={{
              padding: spacing.dp8,
              backgroundColor: theme.colors.surfaceVariant,
              borderRadius: 8,
              marginBottom: spacing.dp4,
            }}
          >
            <Text variant="bodyMedium" style={{ color: theme.colors.onSurfaceVariant }}>
              {msg}
            </Text>
          </View>
        ))
      )}
    </View>
  );
}
