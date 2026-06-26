package expo.modules.main

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.DataProvider

class AppBleManager(context: Context) : BleManager(context) {
  var serverCharacteristic: BluetoothGattCharacteristic? = null
    private set

  override fun onServerReady(server: BluetoothGattServer) {
    val service = server.getService(AppServerManager.SERVICE_UUID)
    serverCharacteristic = service.getCharacteristic(AppServerManager.CHARACTERISTIC_UUID)
  }

  override fun onServicesInvalidated() {
    serverCharacteristic = null
  }

  fun enqueueDataToRead(data: ByteArray) {
    val ch = serverCharacteristic ?: throw IllegalStateException("Not connected")
    setCharacteristicValue(ch, data).enqueue()
    sendNotification(ch, data).enqueue()
  }

  fun onWrite(callback: (device: BluetoothDevice, data: ByteArray) -> Unit) {
    val ch = serverCharacteristic ?: throw IllegalStateException("Not connected")
    setWriteCallback(ch).with { device, data -> callback(device, data.value!!) }
  }
}
