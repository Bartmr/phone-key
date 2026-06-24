package expo.modules.main

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothModule : Module() {
  companion object {
    val SERVICE_UUID = UUID.fromString("b29c86a2-ba7c-4593-810f-de579bfc054e")
    val CHARACTERISTIC_UUID = UUID.fromString("e32d074c-7f47-4de0-8cdf-fcf79874311c")
    val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  }

  override fun definition() = ModuleDefinition {
    Name("BluetoothModule")

    Events("onDataReceived")

    AsyncFunction("startGattServer") {
      startGattServer()
    }

    AsyncFunction("stopGattServer") {
      stopGattServer()
    }

    AsyncFunction("setReadData") { data: ByteArray ->
      setReadData(data)
    }
  }

  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("No React context available")

  private val bluetoothManager: BluetoothManager
    get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  private var gattServer: BluetoothGattServer? = null
  private var advertiser: BluetoothLeAdvertiser? = null
  private var characteristic: BluetoothGattCharacteristic? = null
  private val connectedDevices = ConcurrentHashMap.newKeySet<BluetoothDevice>()
  private val preparedWriteBuffers = ConcurrentHashMap<BluetoothDevice, MutableList<Pair<Int, ByteArray>>>()

  private val advertiseCallback = object : AdvertiseCallback() {}

  @SuppressLint("MissingPermission")
  private fun startGattServer() {
    val adapter = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) {
      throw IllegalStateException("Bluetooth is not enabled")
    }

    connectedDevices.clear()

    gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {
      override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        when (newState) {
          BluetoothGatt.STATE_CONNECTED -> connectedDevices.add(device)
          BluetoothGatt.STATE_DISCONNECTED -> {
            connectedDevices.remove(device)
            preparedWriteBuffers.remove(device)
          }
        }
      }

      override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
      ) {
        if (characteristic.uuid != CHARACTERISTIC_UUID) return

        if (preparedWrite) {
          preparedWriteBuffers
            .getOrPut(device) { mutableListOf() }
            .add(Pair(offset, value))
          if (responseNeeded) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
          }
          return
        }

        sendEvent("onDataReceived", mapOf("value" to value))

        if (responseNeeded) {
          gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
      }

      override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
        val chunks = preparedWriteBuffers.remove(device) ?: return
        if (!execute || chunks.isEmpty()) return

        chunks.sortBy { it.first }
        val totalSize = chunks.sumOf { it.second.size }
        val assembled = ByteArray(totalSize)
        var pos = 0
        for ((_, chunk) in chunks) {
          System.arraycopy(chunk, 0, assembled, pos, chunk.size)
          pos += chunk.size
        }
        sendEvent("onDataReceived", mapOf("value" to assembled))
      }
    })

    val characteristic = BluetoothGattCharacteristic(
      CHARACTERISTIC_UUID,
      BluetoothGattCharacteristic.PROPERTY_READ or
        BluetoothGattCharacteristic.PROPERTY_WRITE or
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
      BluetoothGattCharacteristic.PERMISSION_READ or
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    val cccDescriptor = BluetoothGattDescriptor(
      CCC_DESCRIPTOR_UUID,
      BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
    )
    characteristic.addDescriptor(cccDescriptor)

    val service = BluetoothGattService(
      SERVICE_UUID,
      BluetoothGattService.SERVICE_TYPE_PRIMARY
    )
    service.addCharacteristic(characteristic)
    gattServer?.addService(service)

    val advertiser = adapter.bluetoothLeAdvertiser
    val settings = AdvertiseSettings.Builder()
      .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
      .setConnectable(true)
      .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
      .build()

    val data = AdvertiseData.Builder()
      .setIncludeDeviceName(true)
      .build()

    val scanResponse = AdvertiseData.Builder()
      .addServiceUuid(ParcelUuid(SERVICE_UUID))
      .build()

    advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    this.advertiser = advertiser
  }

  private fun setReadData(data: ByteArray) {
    characteristic?.setValue(data)
    notifyConnectedDevices()
  }

  @SuppressLint("MissingPermission")
  private fun notifyConnectedDevices() {
    val server = gattServer ?: return
    val chr = characteristic ?: return
    for (device in connectedDevices) {
      server.notifyCharacteristicChanged(device, chr, false)
    }
  }

  @SuppressLint("MissingPermission")
  private fun stopGattServer() {
    advertiser?.stopAdvertising(advertiseCallback)
    advertiser = null
    connectedDevices.clear()
    preparedWriteBuffers.clear()
    gattServer?.close()
    gattServer = null
    characteristic = null
  }
}
