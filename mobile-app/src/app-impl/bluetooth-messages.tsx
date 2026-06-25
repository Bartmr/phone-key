import { useCallback, useEffect, useRef, useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button, Text, useTheme } from 'react-native-paper';

import BluetoothModule from '@/modules/main/src/BluetoothModule';
import { useSpacing } from '@/ui/use-spacing';

type ReceivedMessage = {
  id: number;
  text: string | null;
  length: number;
};


function tryDecodeText(bytes: Uint8Array): string | null {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return null;
  }
}

export function BluetoothMessages() {
  const theme = useTheme();
  const spacing = useSpacing();
  const [serverRunning, setServerRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [messages, setMessages] = useState<ReceivedMessage[]>([]);
  const nextId = useRef(0);

  useEffect(() => {
    const sub = BluetoothModule.addListener('onDataReceived', (event) => {
      const blob = event.data;
      const id = nextId.current++;
      setMessages((prev) => [
        ...prev,
        {
          id,
          text: tryDecodeText(blob),
          length: blob.length,
        },
      ]);
    });

    return () => {
      sub.remove();
    };
  }, []);



  const toggleServer = useCallback(async () => {
    setError(null);
    if (serverRunning) {
      await BluetoothModule.stopGattServer();
      setServerRunning(false);
    } else {
      await BluetoothModule.startGattServer();
      setServerRunning(true);
    }
  }, [serverRunning]);



  const sendData = useCallback(async () => {
    setError(null);
    // Generate a ~1KB (1024 bytes) repeating pattern
    const chunk = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\n';
    const repetitions = Math.ceil(4000 / chunk.length);
    const payload = new TextEncoder().encode(chunk.repeat(repetitions));
    
    await BluetoothModule.enqueueDataToRead(payload);
  }, []);

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

      {serverRunning && (
        <View style={{ flexDirection: 'row', gap: spacing.dp8, marginBottom: spacing.dp8 }}>
          <Button mode="outlined" onPress={sendData}>
            Send large
          </Button>
        </View>
      )}

      {error && (
        <Text
          variant="bodyMedium"
          style={{ color: theme.colors.error, marginBottom: spacing.dp8 }}
        >
          {error}
        </Text>
      )}

      <ScrollView style={{ flex: 1 }}>
        {messages.length === 0 ? (
          <Text variant="bodyMedium" style={{ color: theme.colors.onSurfaceVariant }}>
            Waiting for Bluetooth messages...
          </Text>
        ) : (
          messages.map((msg) => (
            <View
              key={msg.id}
              style={{
                padding: spacing.dp8,
                backgroundColor: theme.colors.surfaceVariant,
                borderRadius: 8,
                marginBottom: spacing.dp4,
              }}
            >
              <Text
                variant="labelSmall"
                style={{ color: theme.colors.onSurfaceVariant }}
              >
                {msg.length} bytes
              </Text>
              {msg.text !== null && (
                <Text
                  variant="bodyMedium"
                  style={{ color: theme.colors.onSurface, marginTop: 4 }}
                >
                  {msg.text}
                </Text>
              )}
            </View>
          ))
        )}
      </ScrollView>
    </View>
  );
}
