package expo.modules.main

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import no.nordicsemi.android.ble.observer.ServerObserver

class BluetoothModule : Module() {
  companion object {
    private const val TAG = "BluetoothModule"
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

    AsyncFunction("enqueueDataToRead") { data: ByteArray ->
      enqueueDataToRead(data)
    }
  }

  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("No React context available")

  private val bluetoothManager: BluetoothManager
    get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  private var serverManager: AppServerManager? = null
  private var bleManager: AppBleManager? = null
  private var advertiser: BluetoothLeAdvertiser? = null

  private val advertiseCallback = object : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
      Log.i(TAG, "GATT server advertise started successfully")
    }

    override fun onStartFailure(errorCode: Int) {
      Log.e(TAG, "GATT server advertise failed: errorCode=$errorCode")
    }
  }

  @SuppressLint("MissingPermission")
  private fun startGattServer() {
    val adapter = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) {
      throw IllegalStateException("Bluetooth is not enabled")
    }

    val serverManager = AppServerManager(context).also { sm ->
      sm.setServerObserver(object : ServerObserver {
        override fun onServerReady() {
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
            .addServiceUuid(ParcelUuid(AppServerManager.SERVICE_UUID))
            .build()

          advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
          this@BluetoothModule.advertiser = advertiser
        }

        override fun onDeviceConnectedToServer(device: BluetoothDevice) {
          // Reject new connections if busy
          if (bleManager != null) {
            sm.rejectConnection(device)
            return
          }

          val bleManager = AppBleManager(context).also { mgr ->
            mgr.useServer(sm)
            mgr.attachClientConnection(device)

            mgr.onWrite { _, data ->
              sendEvent("onDataReceived", mapOf("data" to data))
            }
          }

          this@BluetoothModule.bleManager = bleManager
        }

        override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
          bleManager?.close()
          bleManager = null
        }
      })
    }

    serverManager.open()

    this.serverManager = serverManager
  }

  @SuppressLint("MissingPermission")
  private fun enqueueDataToRead(data: ByteArray) {
    val bleManager = this.bleManager
      ?: throw IllegalStateException("No device connected")

    bleManager.enqueueDataToRead(data)
  }

  @SuppressLint("MissingPermission")
  private fun stopGattServer() {
    advertiser?.stopAdvertising(advertiseCallback)
    advertiser = null

    bleManager?.close()
    bleManager = null

    serverManager?.close()
    serverManager = null
  }
}
