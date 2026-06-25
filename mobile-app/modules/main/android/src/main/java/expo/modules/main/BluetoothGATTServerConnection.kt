package expo.modules.main

import android.bluetooth.BluetoothDevice
import java.util.concurrent.ConcurrentLinkedQueue

class BluetoothGATTServerConnection {
  var connectedDevice: BluetoothDevice? = null
  var connectedDeviceMtu: Int = 23
  val preparedWriteChunks: MutableList<Pair<Int, ByteArray>> = mutableListOf()
  val readQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()
  var readData: ByteArray? = null
}
