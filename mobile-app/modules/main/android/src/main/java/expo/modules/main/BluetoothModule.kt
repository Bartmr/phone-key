package expo.modules.main

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
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
  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("No React context available")

  private val bluetoothManager: BluetoothManager
    get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  private var gattServer: BluetoothGattServer? = null
  private var advertiser: BluetoothLeAdvertiser? = null

  private val advertiseCallback = object : AdvertiseCallback() {}

  companion object {
    val SERVICE_UUID = UUID.fromString("b29c86a2-ba7c-4593-810f-de579bfc054e")
    val CHARACTERISTIC_UUID = UUID.fromString("e32d074c-7f47-4de0-8cdf-fcf79874311c")
  }

  override fun definition() = ModuleDefinition {
    Name("BluetoothModule")

    Events("onStringReceived")

    AsyncFunction("startGattServer") {
      stopGattServer()
      startGattServer()
    }

    AsyncFunction("stopGattServer") {
      stopGattServer()
    }
  }


  @SuppressLint("MissingPermission")
  private fun startGattServer() {
    val adapter = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) {
      throw IllegalStateException("Bluetooth is not enabled")
    }

    gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {
      override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        println("BluetoothModule: Connection state changed for ${device.address}: $newState")
      }

      override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        println("BluetoothModule: Service added: ${service.uuid}, status: $status")
      }

      override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
      ) {
        if (characteristic.uuid == CHARACTERISTIC_UUID) {
          gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "OK".toByteArray())
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
        if (characteristic.uuid == CHARACTERISTIC_UUID) {
          val received = String(value, Charsets.UTF_8)
          sendEvent("onStringReceived", mapOf("value" to received))
        }
        if (responseNeeded) {
          gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
      }
    })

    val service = BluetoothGattService(
      SERVICE_UUID,
      BluetoothGattService.SERVICE_TYPE_PRIMARY
    )

    val characteristic = BluetoothGattCharacteristic(
      CHARACTERISTIC_UUID,
      BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
      BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
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

  @SuppressLint("MissingPermission")
  private fun stopGattServer() {
    advertiser?.stopAdvertising(advertiseCallback)
    advertiser = null
    gattServer?.close()
    gattServer = null
  }
}
