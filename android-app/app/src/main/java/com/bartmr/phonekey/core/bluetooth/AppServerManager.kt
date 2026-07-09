package com.bartmr.phonekey.core.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleServerManager
import java.util.UUID

class AppServerManager(context: Context) : BleServerManager(context) {
  companion object {
    val SERVICE_UUID: UUID = UUID.fromString("a667f940-6a50-49ac-9b75-2b9639564972")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("69924d24-8e47-4d43-9e86-dde30201a474")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
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
          BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM or
                  BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM,
          BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM
                    or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM,
          ),
        ),
      ),
    )
  }

  override fun log(priority: Int, message: String) {
    Log.println(priority, "AppBleServerManager", message)
  }

  fun rejectConnection(device: BluetoothDevice) {
    cancelConnection(device)
  }
}
