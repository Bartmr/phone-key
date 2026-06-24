export type BluetoothModuleEvents = {
  onDataReceived(value: { value: Uint8Array }): void;
}
