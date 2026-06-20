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
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.modules.ModuleDefinitionData
import java.util.UUID

class MainModule : Module() {
  private val serviceUuid: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
  private val characteristicUuid: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

  private var gattServer: BluetoothGattServer? = null
  private var advertiser: BluetoothLeAdvertiser? = null
  private var activeAdvertiseCallback: AdvertiseCallback? = null

  private fun makeAdvertiseCallback() = object : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
      Log.i("MainModule", "BLE advertising started successfully")
    }

    override fun onStartFailure(errorCode: Int) {
      val reason = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertising data too large"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers active"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising already started"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising not supported on this device"
        else -> "Unknown error ($errorCode)"
      }
      Log.e("MainModule", "BLE advertising failed: $reason")
      sendEvent("onGattServerError", mapOf("reason" to reason))
    }
  }

  override fun definition() = ModuleDefinition {
    Name("MainModule")

    Events("onMessageReceived", "onGattServerError")

    AsyncFunction("startGattServer") {
      startGattServer()
    }

    AsyncFunction("stopGattServer") {
      stopGattServer()
    }
  }

  private fun hasBlePermissions(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
      ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
  }

  @SuppressLint("MissingPermission")
  fun startGattServer() {
    val context: Context = appContext.reactContext ?: return
    val bluetoothManager: BluetoothManager =
      context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
    val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter ?: return

    if (!hasBlePermissions(context)) {
      Log.e("MainModule", "Missing BLUETOOTH_ADVERTISE or BLUETOOTH_CONNECT runtime permissions")
      sendEvent("onGattServerError", mapOf("reason" to "Missing Bluetooth permissions"))
      return
    }

    if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
      Log.e("MainModule", "This device does not support BLE advertising (peripheral mode)")
      sendEvent("onGattServerError", mapOf("reason" to "BLE advertising not supported on this device"))
      return
    }

    if (bluetoothAdapter.bluetoothLeAdvertiser == null) {
      Log.e("MainModule", "bluetoothLeAdvertiser is null")
      sendEvent("onGattServerError", mapOf("reason" to "BLE advertiser unavailable"))
      return
    }

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
      .addServiceUuid(ParcelUuid(serviceUuid))
      .build()

    activeAdvertiseCallback = makeAdvertiseCallback()
    activeAdvertiseCallback?.let { cb -> advertiser?.startAdvertising(settings, data, cb) }
  }

  @SuppressLint("MissingPermission")
  fun stopGattServer() {
    activeAdvertiseCallback?.let { advertiser?.stopAdvertising(it) }
    activeAdvertiseCallback = null
    advertiser = null
    gattServer?.close()
    gattServer = null
  }
}
