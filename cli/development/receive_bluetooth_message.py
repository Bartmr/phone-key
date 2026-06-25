from dotenv import load_dotenv

load_dotenv()

import asyncio
import os
from bleak import BleakClient

DEVICE_ADDRESS = os.environ["DEVICE_ADDRESS"]
CHARACTERISTIC_UUID = "e32d074c-7f47-4de0-8cdf-fcf79874311c"

received_chunks: list[bytes] = []
total_bytes = 0


def handle_notification(_sender: int, data: bytearray):
    global total_bytes
    chunk = bytes(data)
    received_chunks.append(chunk)
    total_bytes += len(chunk)
    print(f"Received chunk: {len(chunk)} bytes (total: {total_bytes})")


async def main():
    print(f"Connecting to {DEVICE_ADDRESS}...")

    async with BleakClient(DEVICE_ADDRESS) as client:
        if not client.is_connected:
            print("Failed to connect.")
            return

        print("Connected successfully! Waiting for data from mobile app...")
        print("(Press Ctrl+C to stop)")

        await client.start_notify(CHARACTERISTIC_UUID, handle_notification)

        try:
            while True:
                await asyncio.sleep(1)
        except KeyboardInterrupt:
            pass
        finally:
            await client.stop_notify(CHARACTERISTIC_UUID)

        full_payload = b"".join(received_chunks)
        print(f"\nDone. Received {total_bytes} bytes total in {len(received_chunks)} chunks.")
        print(f"First 200 bytes: {full_payload[:200]!r}")


if __name__ == "__main__":
    asyncio.run(main())
