from dotenv import load_dotenv

load_dotenv()

import asyncio
from bleak import BleakClient
import os

DEVICE_ADDRESS = os.environ["DEVICE_ADDRESS"]

CHARACTERISTIC_UUID = "e32d074c-7f47-4de0-8cdf-fcf79874311c"


async def main():
    print(f"Connecting to {DEVICE_ADDRESS}...")

    async with BleakClient(DEVICE_ADDRESS) as client:
        if not client.is_connected:
            print("Failed to connect.")
            return

        print("Connected successfully!")

        response_ready = asyncio.Event()

        def notification_handler(_handle: int, data: bytearray):
            response_ready.set()

        await client.start_notify(CHARACTERISTIC_UUID, notification_handler)
        print("Notifications enabled.")

        payload = b"Hello Phone Key!"
        print(f"Sending {len(payload)} bytes: {payload!r}")
        await client.write_gatt_char(CHARACTERISTIC_UUID, payload, response=True)


if __name__ == "__main__":
    asyncio.run(main())