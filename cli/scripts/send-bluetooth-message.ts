import noble from "@abandonware/noble";

const DEVICE_ADDRESS = process.env.DEVICE_ADDRESS;

if (!DEVICE_ADDRESS) {
  throw new Error("DEVICE_ADDRESS environment variable is not set");
}

async function main(): Promise<void> {
  if (noble._state !== "poweredOn") {
    await new Promise<void>((resolve) => {
      noble.once("stateChange", (state) => {
        if (state === "poweredOn") resolve();
      });
    });
  }

  console.log(`Scanning for device ${DEVICE_ADDRESS}...`);

  await noble.startScanningAsync();

  const peripheral = await new Promise<noble.Peripheral>((resolve, reject) => {
    const timeout = setTimeout(() => {
      noble.stopScanningAsync();
      reject(new Error("Device not found within 15 seconds"));
    }, 15000);

    noble.on("discover", (discovered: noble.Peripheral) => {
      if (discovered.address.toUpperCase() === DEVICE_ADDRESS!.toUpperCase()) {
        clearTimeout(timeout);
        noble.stopScanningAsync();
        resolve(discovered);
      }
    });
  });

  console.log(`Found device: ${peripheral.advertisement?.localName || peripheral.id}`);

  await peripheral.connectAsync();
  console.log("Connected");

  const services = await peripheral.discoverServicesAsync();

  for (const service of services) {
    const characteristics = await service.discoverCharacteristicsAsync();
    for (const characteristic of characteristics) {
      if (characteristic.properties.includes("write") || characteristic.properties.includes("writeWithoutResponse")) {
        const data = Buffer.from("hello", "utf-8");
        await characteristic.writeAsync(data, false);
        console.log(`Sent "hello" to characteristic ${characteristic.uuid} on service ${service.uuid}`);
        await peripheral.disconnectAsync();
        console.log("Disconnected");
        process.exit(0);
      }
    }
  }

  throw new Error("No writable characteristic found on the device");
}

main();