export type MessageReceivedEvent = {
  message: string;
};

export type MainModuleEvents = {
    onMessageReceived: (event: MessageReceivedEvent) => void;
}
