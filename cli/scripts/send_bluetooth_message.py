from dotenv import load_dotenv

load_dotenv()

import asyncio
from bleak import BleakClient
import os

# Replace with your phone's Bluetooth MAC address
DEVICE_ADDRESS = os.environ["DEVICE_ADDRESS"]

# Replace with the specific GATT Characteristic UUID hosted on your phone's server
CHARACTERISTIC_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b" 

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