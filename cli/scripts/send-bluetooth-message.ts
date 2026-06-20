import 'dotenv/config'

import { createBluetooth } from "node-ble";

const SERVICE_UUID = '4fafc201-1fb5-459e-8fcc-c5c9c331914b';
const CHARACTERISTIC_UUID = 'beb5483e-36e1-4688-b7f5-ea07361b26a8';



async function main() {
    const PHONE_ADDRESS = process.env.PHONE_ADDRESS;
    if (!PHONE_ADDRESS) throw new Error('PHONE_ADDRESS not set in environment');
    const message = 'ping';

    const { bluetooth, destroy } = createBluetooth();
    const adapter = await bluetooth.defaultAdapter();

    if (!(await adapter.isDiscovering())) {
        await adapter.startDiscovery();
    }

    const device = await adapter.waitDevice(PHONE_ADDRESS);
    await device.connect();

    const gattServer = await device.gatt();
    const service = await gattServer.getPrimaryService(SERVICE_UUID);
    const characteristic = await service.getCharacteristic(CHARACTERISTIC_UUID);

    await characteristic.writeValue(Buffer.from(message, 'utf-8'));
    console.log(`Sent: ${message}`);

    await device.disconnect();
    destroy();
}

main();