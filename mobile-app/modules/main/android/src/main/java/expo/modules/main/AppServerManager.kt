package expo.modules.main

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleServerManager
import java.util.UUID

class AppServerManager(context: Context) : BleServerManager(context) {
  companion object {
    val SERVICE_UUID: UUID = UUID.fromString("b29c86a2-ba7c-4593-810f-de579bfc054e")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("e32d074c-7f47-4de0-8cdf-fcf79874311c")
  }

  override fun initializeServer(): List<BluetoothGattService> {
    return listOf(
      service(
        SERVICE_UUID,
        characteristic(
          CHARACTERISTIC_UUID,
          BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
          BluetoothGattCharacteristic.PERMISSION_READ or
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
      )
    )
  }

  override fun log(priority: Int, message: String) {
    Log.println(priority, "AppBleServerManager", message);
  }

  fun rejectConnection(device: BluetoothDevice) {
    cancelConnection(device)
  }
}
