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

class BluetoothModule : Module() {
  companion object {
    val SERVICE_UUID: UUID = UUID.fromString("b29c86a2-ba7c-4593-810f-de579bfc054e")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("e32d074c-7f47-4de0-8cdf-fcf79874311c")
    val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
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

  private var connection: BluetoothGATTServerConnection? = null

  private val advertiseCallback = object : AdvertiseCallback() {}

  @SuppressLint("MissingPermission")
  private fun startGattServer() {
    val adapter = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) {
      throw IllegalStateException("Bluetooth is not enabled")
    }

    val gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {
      override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        val gattServer = gattServer ?: throw IllegalStateException("GATT server is not running")

        when (newState) {
          BluetoothGatt.STATE_CONNECTED -> {
            // Reject new connections if busy
            if (connection != null) {
              gattServer.cancelConnection(device)
              return
            }

            connection = BluetoothGATTServerConnection().also { it.connectedDevice = device }
          }
          BluetoothGatt.STATE_DISCONNECTED -> {
            connection = null
          }
        }
      }

      override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        val connection = connection ?: throw IllegalStateException("GATT server is not running")

        connection.connectedDeviceMtu = mtu
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
        val gattServer = gattServer ?: throw IllegalStateException("GATT server is not running")
        val connection = connection ?: throw IllegalStateException("GATT server is not running")

        if (characteristic.uuid != CHARACTERISTIC_UUID) {
          if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
          }
          return
        }
        
        if (preparedWrite) {
          connection.preparedWriteChunks.add(Pair(offset, value))
          if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
          }
        } else {
          sendEvent("onDataReceived", mapOf("data" to value))

          if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
          }
        }
      }

      override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
        val conn = connection ?: throw IllegalStateException("GATT server is not running")

        if (!execute || conn.preparedWriteChunks.isEmpty()) return

        val chunks = conn.preparedWriteChunks.sortedBy { it.first }
        conn.preparedWriteChunks.clear()

        val totalSize = chunks.sumOf { it.second.size }
        val assembled = ByteArray(totalSize)
        var pos = 0
        for ((_, chunk) in chunks) {
          System.arraycopy(chunk, 0, assembled, pos, chunk.size)
          pos += chunk.size
        }
        sendEvent("onDataReceived", mapOf("data" to assembled))
      }

      override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
      ) {
        val gattServer = gattServer ?: throw IllegalStateException("GATT server is not running")
        val conn = connection ?: throw IllegalStateException("GATT server is not running")

        if (characteristic.uuid != CHARACTERISTIC_UUID) {
          gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
          return
        }

        val data = conn.readData
        if (data == null || data.isEmpty()) {
          gattServer.sendResponse(
            device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null
          )
          return
        }

        if (offset >= data.size) {
          gattServer.sendResponse(
            device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null
          )
          return
        }

        val chunkSize = conn.connectedDeviceMtu - 1

        gattServer.sendResponse(
          device,
          requestId,
          BluetoothGatt.GATT_SUCCESS,
          offset,
          data.copyOfRange(offset, minOf(data.size, offset + chunkSize))
        )
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
    this.characteristic = characteristic
    this.advertiser = advertiser
    this.gattServer = gattServer
  }

  @SuppressLint("MissingPermission")
  private fun setReadData(data: ByteArray) {
    val gattServer = gattServer ?: throw IllegalStateException("GATT server is not running")
    val characteristic = characteristic ?: throw IllegalStateException("Characteristic is not initialized")
    val conn = connection ?: throw IllegalStateException("No device connected")
    val device = conn.connectedDevice ?: throw IllegalStateException("No device connected")

    conn.readData = data

    gattServer.notifyCharacteristicChanged(device, characteristic, true)
  }

  @SuppressLint("MissingPermission")
  private fun stopGattServer() {
    advertiser?.stopAdvertising(advertiseCallback)
    advertiser = null
    connection = null
    gattServer?.close()
    gattServer = null
    characteristic = null
  }
}
