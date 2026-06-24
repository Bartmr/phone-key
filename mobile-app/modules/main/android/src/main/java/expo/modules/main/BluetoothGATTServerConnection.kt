package expo.modules.main

import android.bluetooth.BluetoothDevice

class BluetoothGATTServerConnection {
  var connectedDevice: BluetoothDevice? = null
  var connectedDeviceMtu: Int = 23
  val preparedWriteChunks: MutableList<Pair<Int, ByteArray>> = mutableListOf()
  var readData: ByteArray? = null
}
