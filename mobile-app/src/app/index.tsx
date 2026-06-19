import { useEffect, useMemo, useState } from "react";
import { BleManager } from "react-native-ble-plx";
import {
  Button,
  Platform,
  PermissionsAndroid,
  StyleSheet,
  Text,
  View,
} from "react-native";

const SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0";
const CHARACTERISTIC_UUID = "abcdefab-1234-5678-1234-56789abcdef0";

function decodeBase64(value: string) {
  if (typeof globalThis.atob === "function") {
    const binary = globalThis.atob(value);
    let result = "";
    for (let i = 0; i < binary.length; i += 1) {
      result += String.fromCharCode(binary.charCodeAt(i));
    }
    return result;
  }

  return value;
}

export default function HomeScreen() {
  const [message, setMessage] = useState<string | null>(null);
  const [status, setStatus] = useState("Ready to scan");
  const manager = useMemo(() => new BleManager(), []);

  useEffect(() => {
    scanForMessage();
    return () => {
      manager.stopDeviceScan();
      manager.destroy();
    };
  }, []);

  async function requestPermissions() {
    if (Platform.OS !== "android") return true;

    const granted = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    ]);

    return (
      granted[PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN] ===
        PermissionsAndroid.RESULTS.GRANTED &&
      granted[PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT] ===
        PermissionsAndroid.RESULTS.GRANTED &&
      granted[PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION] ===
        PermissionsAndroid.RESULTS.GRANTED
    );
  }

  async function scanForMessage() {
    setStatus("Requesting permissions...");
    const granted = await requestPermissions();
    if (!granted) {
      setStatus("BLE permissions required");
      return;
    }

    setMessage(null);
    setStatus("Scanning for message sender...");

    manager.startDeviceScan([SERVICE_UUID], null, async (error, device) => {
      if (error) {
        setStatus(`Scan error: ${error.message}`);
        manager.stopDeviceScan();
        return;
      }

      if (!device) return;
      setStatus(`Found ${device.name || device.id}, connecting...`);

      try {
        manager.stopDeviceScan();
        const connected = await device.connect();
        await connected.discoverAllServicesAndCharacteristics();
        const characteristic = await connected.readCharacteristicForService(
          SERVICE_UUID,
          CHARACTERISTIC_UUID,
        );

        const decoded = characteristic.value
          ? decodeBase64(characteristic.value)
          : null;
        setMessage(decoded || "Empty message");
        setStatus("Message received");
        await connected.cancelConnection();
      } catch (readError) {
        setStatus(
          `Read error: ${readError instanceof Error ? readError.message : String(readError)}`,
        );
      }
    });
  }

  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Phone Key Message</Text>
      <Text style={styles.status}>{status}</Text>
      <View style={styles.messageBox}>
        <Text style={styles.messageLabel}>Last received message</Text>
        <Text style={styles.messageText}>{message ?? "No message yet"}</Text>
      </View>
      <Button title="Scan for message" onPress={scanForMessage} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    justifyContent: "center",
    padding: 24,
  },
  title: {
    fontSize: 24,
    marginBottom: 16,
    fontWeight: "700",
  },
  status: {
    marginBottom: 16,
    color: "#555",
  },
  messageBox: {
    borderWidth: 1,
    borderColor: "#888",
    borderRadius: 12,
    padding: 16,
    marginBottom: 24,
    backgroundColor: "#fafafa",
  },
  messageLabel: {
    fontSize: 12,
    color: "#888",
    marginBottom: 8,
  },
  messageText: {
    fontSize: 18,
    color: "#111",
  },
});

