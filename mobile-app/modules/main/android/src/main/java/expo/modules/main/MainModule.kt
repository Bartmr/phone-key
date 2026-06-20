package expo.modules.main

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import expo.modules.kotlin.modules.ModuleDefinitionData
import java.util.UUID

class MainModule : Module() {
  private val serviceUuid: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
  private val characteristicUuid: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

  private var gattServer: BluetoothGattServer? = null
  private var advertiser: BluetoothLeAdvertiser? = null

  private val advertiseCallback = object : AdvertiseCallback() {}

  override fun definition() = ModuleDefinition {
    Name("MainModule")

    Events("onMessageReceived")

    AsyncFunction("startGattServer") {
      startGattServer()
    }

    AsyncFunction("stopGattServer") {
      stopGattServer()
    }
  }

  @SuppressLint("MissingPermission")
  fun startGattServer() {
    val context: Context = appContext.reactContext ?: return
    val bluetoothManager: BluetoothManager =
      context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
    val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter ?: return

    gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {
      override fun onCharacteristicWriteRequest(
        device: android.bluetooth.BluetoothDevice?,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic?,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
      ) {
        if (characteristic?.uuid == characteristicUuid && value != null) {
          val message = String(value, Charsets.UTF_8)
          sendEvent("onMessageReceived", mapOf("message" to message))

          if (responseNeeded) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
          }
        }
      }
    })

    val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    val characteristic = BluetoothGattCharacteristic(
      characteristicUuid,
      BluetoothGattCharacteristic.PROPERTY_WRITE,
      BluetoothGattCharacteristic.PERMISSION_WRITE
    )
    service.addCharacteristic(characteristic)
    gattServer?.addService(service)

    advertiser = bluetoothAdapter.bluetoothLeAdvertiser

    val settings = AdvertiseSettings.Builder()
      .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
      .setConnectable(true)
      .build()

    val data = AdvertiseData.Builder()
      .setIncludeDeviceName(true)
      .addServiceUuid(ParcelUuid(serviceUuid))
      .build()

    advertiser?.startAdvertising(settings, data, advertiseCallback)
  }

  @SuppressLint("MissingPermission")
  fun stopGattServer() {
    advertiser?.stopAdvertising(advertiseCallback)
    advertiser = null
    gattServer?.close()
    gattServer = null
  }
}
