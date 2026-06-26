from dotenv import load_dotenv

load_dotenv()

import asyncio
from bleak import BleakClient, BleakScanner
import os
import subprocess
import uuid

DEVICE_ADDRESS = os.environ["DEVICE_ADDRESS"]

CHARACTERISTIC_UUID = uuid.UUID("69924d24-8e47-4d43-9e86-dde30201a474")


async def main():
    print(f"Connecting to {DEVICE_ADDRESS}...")


    async with BleakClient(DEVICE_ADDRESS) as client:
    
        print("Connected successfully!")


        # Generate a large payload (e.g., 10 KB of repeating pattern)
        chunk = b"The quick brown fox jumps over the lazy dog. "  # 45 bytes
        payload_size = 10_240  # 10 KB
        payload = (chunk * (payload_size // len(chunk) + 1))[:payload_size]

        print(f"Sending {len(payload)} bytes...")
        await client.write_gatt_char(CHARACTERISTIC_UUID, payload, response=True)
        print("Done sending.")


if __name__ == "__main__":
    asyncio.run(main())