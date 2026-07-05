package com.bartmr.phonekey.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import no.nordicsemi.android.ble.observer.ServerObserver

class BleServer(private val context: Context) {
    private val bluetoothManager: BluetoothManager
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var serverManager: AppServerManager? = null
    private var bleManager: AppBleManager? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var currentAdvertiseCallback: AdvertiseCallback? = null

    var onDataReceived: ((ByteArray) -> Unit)? = null
    var onAdapterStateChanged: ((Boolean) -> Unit)? = null

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR,
                )
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        onAdapterStateChanged?.invoke(true)
                    }

                    BluetoothAdapter.STATE_OFF -> {
                        onAdapterStateChanged?.invoke(false)
                    }
                }
            }
        }
    }

    fun registerAdapterStateReceiver() {
        context.registerReceiver(
            adapterStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        )
    }

    fun unregisterAdapterStateReceiver() {
        context.unregisterReceiver(adapterStateReceiver)
    }

    fun isAdapterEnabled(): Boolean {
        val adapter = bluetoothManager.adapter
        return adapter != null && adapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun startGattServer() {
        if (serverManager != null) {
            return
        }

        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            throw IllegalStateException("Bluetooth is not enabled")
        }

        val serverManager = AppServerManager(context)

        serverManager.setServerObserver(object : ServerObserver {
            override fun onServerReady() {
                val advertiser = adapter.bluetoothLeAdvertiser
                    ?: throw IllegalStateException("BLE advertising is not supported on this device")

                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(true)
                    .build()

                val data = AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(AppServerManager.SERVICE_UUID))
                    .build()

                val scanResponse = AdvertiseData.Builder()
                    .build()

                val callback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        Log.i(TAG, "GATT server advertise started successfully")
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.e(TAG, "GATT server advertise failed: errorCode=$errorCode")
                    }
                }

                advertiser.startAdvertising(settings, data, scanResponse, callback)
                this@BleServer.advertiser = advertiser
                this@BleServer.currentAdvertiseCallback = callback
            }

            override fun onDeviceConnectedToServer(device: BluetoothDevice) {
                if (bleManager != null) {
                    serverManager.rejectConnection(device)
                    return
                }



                val bleManager = AppBleManager(context).apply {
                    useServer(serverManager)
                    attachClientConnection(device)
                    onWrite { _, data ->
                        onDataReceived?.invoke(data)
                    }
                }

                this@BleServer.bleManager = bleManager
            }

            override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
                bleManager?.close()
                bleManager = null
            }
        })

        serverManager.open()
        this.serverManager = serverManager
    }

    fun sendToClient(data: ByteArray) {
        val bleManager = this.bleManager
            ?: throw IllegalStateException("No device connected")

        bleManager.sendToClient(data)
    }

    @SuppressLint("MissingPermission")
    fun stopGattServer() {
        val currentAdvertiseCallback = currentAdvertiseCallback

        if (currentAdvertiseCallback != null) {
            advertiser?.stopAdvertising(currentAdvertiseCallback)
        }

        this.currentAdvertiseCallback = null
        advertiser = null

        serverManager?.close()
        serverManager = null
    }

    companion object {
        private const val TAG = "BleServer"
    }
}
