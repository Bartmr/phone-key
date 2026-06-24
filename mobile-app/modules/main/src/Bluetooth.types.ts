export type BluetoothModuleEvents = {
  onDataReceived(event: { data: Uint8Array }): void;
}
