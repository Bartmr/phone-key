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

export function TestBluetooth() {

  useEffect(() => {
    const sub = BluetoothModule.addListener('onDataReceived', (event) => {
      const blob = event.data;

      console.log(
        `[Bluetooth] received ${blob.length} bytes:`,
        tryDecodeText(blob),
      );

      const largeResponse = 'A'.repeat(40000);
      const responseBytes = new TextEncoder().encode(largeResponse);
      console.log(`[Bluetooth] sending back ${responseBytes.length} bytes`);
      BluetoothModule.sendToClient(responseBytes);
    });

    return () => {
      sub.remove();
    };
  }, []);


  return (
    <></>
  );
}
