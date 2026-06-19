import bleno from "@abandonware/bleno";

const MESSAGE = process.env.MESSAGE || process.argv[2] || "hello";
const SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0";
const CHARACTERISTIC_UUID = "abcdefab-1234-5678-1234-56789abcdef0";

class MessageCharacteristic extends bleno.Characteristic {
  message: string;

  constructor(message: string) {
    super({
      uuid: CHARACTERISTIC_UUID,
      properties: ["read"],
      descriptors: [
        new bleno.Descriptor({
          uuid: "2901",
          value: "Phone Key CLI message",
        }),
      ],
    });

    this.message = message;
  }

  
  override onReadRequest(offset: number, callback: (result: number, data?: Buffer) => void) {
    const data = Buffer.from(this.message, "utf-8");
    if (offset > data.length) {
      callback(bleno.Characteristic.RESULT_INVALID_OFFSET);
      return;
    }
    callback(bleno.Characteristic.RESULT_SUCCESS, data.slice(offset));
  }
}

const messageCharacteristic = new MessageCharacteristic(MESSAGE);
const messageService = new bleno.PrimaryService({
  uuid: SERVICE_UUID,
  characteristics: [messageCharacteristic],
});

bleno.on("stateChange", (state) => {
  console.log("Bluetooth state:", state);
  if (state === "poweredOn") {
    bleno.startAdvertising("PhoneKeyCLI", [SERVICE_UUID], (error: any) => {
      if (error) {
        console.error("Advertising error:", error);
        process.exit(1);
      }
      console.log("Advertising service for message delivery...");
    });
  } else {
    bleno.stopAdvertising();
  }
});

bleno.on("advertisingStart", (error) => {
  if (error) {
    console.error("Advertising failed:", error);
    process.exit(1);
  }
  bleno.setServices([messageService], (setError) => {
    if (setError) {
      console.error("Failed to set BLE services:", setError);
      process.exit(1);
    }
    console.log(`Message ready: "${MESSAGE}"`);
    console.log("Phone Key CLI is now advertising. Open the mobile app and scan.");
  });
});

bleno.on("accept", (clientAddress) => {
  console.log(`Accepted connection from ${clientAddress}`);
});

bleno.on("disconnect", (clientAddress) => {
  console.log(`Disconnected ${clientAddress}`);
});
