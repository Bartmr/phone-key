export type MessageReceivedEvent = {
  message: string;
};

export type GattServerErrorEvent = {
  reason: string;
};

export type MainModuleEvents = {
    onMessageReceived: (event: MessageReceivedEvent) => void;
    onGattServerError: (event: GattServerErrorEvent) => void;
}
