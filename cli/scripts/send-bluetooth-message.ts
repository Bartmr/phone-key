import { createBluetooth } from "node-ble";

const MESSAGE = process.env.MESSAGE || process.argv[2] || "hello";
const SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0";
const CHARACTERISTIC_UUID = "abcdefab-1234-5678-1234-56789abcdef0";
const PHONE_ADDRESS = process.env.PHONE_ADDRESS;
const PHONE_NAME = process.env.PHONE_NAME || "PhoneKey";

async function findDevice(
  adapter: import("node-ble").Adapter,
  timeoutMs = 30_000,
): Promise<import("node-ble").Device | null> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const ids = await adapter.devices();

    for (const id of ids) {
      try {
        const device = await adapter.getDevice(id);
        const name = await device.getName().catch(() => null);

        if (name?.includes(PHONE_NAME)) {
          return device;
        }
      } catch {
        // skip inaccessible devices
      }
    }

    await new Promise((r) => setTimeout(r, 1000));
  }

  return null;
}

async function main() {
  const { bluetooth, destroy } = createBluetooth();

  try {
    const adapter = await bluetooth.defaultAdapter();

    if (!(await adapter.isPowered())) {
      console.error("Bluetooth is not powered on");
      process.exit(1);
    }

    if (!(await adapter.isDiscovering())) {
      await adapter.startDiscovery();
    }
    console.log("Scanning for phone...");

    let device: import("node-ble").Device;
    if (PHONE_ADDRESS) {
      console.log(`Looking for ${PHONE_ADDRESS}...`);
      device = await adapter.waitDevice(PHONE_ADDRESS);
    } else {
      const found = await findDevice(adapter);
      if (!found) {
        console.error(
          "Phone not found. Set PHONE_ADDRESS (MAC) or PHONE_NAME to help discovery.",
        );
        process.exit(1);
      }
      device = found;
    }

    const deviceName = await device.getName();
    const deviceAddress = await device.getAddress();
    console.log(`Connecting to ${deviceName} (${deviceAddress})...`);
    await device.connect();
    console.log("Connected");

    const gattServer = await device.gatt();
    const service = await gattServer.getPrimaryService(SERVICE_UUID);
    const characteristic =
      await service.getCharacteristic(CHARACTERISTIC_UUID);

    const buffer = Buffer.from(MESSAGE, "utf-8");
    await characteristic.writeValueWithResponse(buffer);
    console.log(`Sent message: "${MESSAGE}"`);

    await device.disconnect();
    console.log("Disconnected");
  } catch (error) {
    console.error("Error:", error);
    process.exit(1);
  } finally {
    destroy();
  }
}

main();
