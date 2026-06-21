from dotenv import load_dotenv

load_dotenv()

import asyncio
from bleak import BleakClient
import os

DEVICE_ADDRESS = os.environ["DEVICE_ADDRESS"]

CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" 

async def main():
    print(f"Connecting to {DEVICE_ADDRESS}...")
    
    async with BleakClient(DEVICE_ADDRESS) as client:
        if client.is_connected:
            print("Connected successfully!")
            
            # Data must be sent as bytes or bytearray
            data_to_send = b"Hello Phone!"
            
            print(f"Sending data: {data_to_send}")
            # write_gatt_char sends data. 
            # Use response=True for Write Request, response=False for Write Command (No Response)
            await client.write_gatt_char(CHARACTERISTIC_UUID, data_to_send, response=True)
            
            print("Data sent successfully.")
        else:
            print("Failed to connect.")

if __name__ == "__main__":
    asyncio.run(main())