package expo.modules.main.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager

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

  fun sendToClient(data: ByteArray) {
    val ch = this.serverCharacteristic ?: throw IllegalStateException("Not connected")
    ch.setValue(data)
    sendNotification(ch, ByteArray(0))
      .enqueue()
  }

  fun onWrite(callback: (device: BluetoothDevice, data: ByteArray) -> Unit) {
    val ch = serverCharacteristic ?: throw IllegalStateException("Not connected")
    setWriteCallback(ch).with { device, data -> callback(device, data.value!!) }
  }
}
