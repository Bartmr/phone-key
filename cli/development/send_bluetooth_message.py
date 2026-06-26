from dotenv import load_dotenv

load_dotenv()

import asyncio
from bleak import BleakClient
import os
import subprocess

DEVICE_ADDRESS = os.environ["DEVICE_ADDRESS"]

CHARACTERISTIC_UUID = "e32d074c-7f47-4de0-8cdf-fcf79874311c"


async def main():
    print(f"Connecting to {DEVICE_ADDRESS}...")

    async with BleakClient(DEVICE_ADDRESS) as client:
        if not client.is_connected:
            print("Failed to connect.")
            return

        print("Connected successfully!")

        print("Starting discovery...")
        
        subprocess.run(
            ["busctl", "call", "org.bluez", "/org/bluez/hci0", "org.bluez.Adapter1", "StartDiscovery"],
            check=True,
        )
        print("Discovery started.")

        # Generate a large payload (e.g., 10 KB of repeating pattern)
        chunk = b"The quick brown fox jumps over the lazy dog. "  # 45 bytes
        payload_size = 10_240  # 10 KB
        payload = (chunk * (payload_size // len(chunk) + 1))[:payload_size]

        print(f"Sending {len(payload)} bytes...")
        await client.write_gatt_char(CHARACTERISTIC_UUID, payload, response=True)
        print("Done sending.")


if __name__ == "__main__":
    asyncio.run(main())