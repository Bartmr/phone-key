package com.bartmr.phonekey.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class UsbAccessoryManager(private val context: Context) {
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pendingAccessory: UsbAccessory? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var onDataReceived: ((ByteArray) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                    val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                    if (accessory != null) {
                        Log.i(TAG, "USB accessory attached: ${accessory.manufacturer} ${accessory.model}")
                        openAccessory(accessory)
                    }
                }
                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    Log.i(TAG, "USB accessory detached")
                    closeAccessory()
                }
                ACTION_USB_PERMISSION -> {
                    val accessory = pendingAccessory
                    pendingAccessory = null
                    if (accessory == null) return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(TAG, "USB permission granted")
                        connectToAccessory(accessory)
                    } else {
                        Log.w(TAG, "USB permission denied")
                    }
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun handleIntent(intent: Intent) {
        val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
        if (accessory != null) {
            openAccessory(accessory)
        }
    }

    fun unregister() {
        context.unregisterReceiver(usbReceiver)
        closeAccessory()
    }

    private fun openAccessory(accessory: UsbAccessory) {
        if (!usbManager.hasPermission(accessory)) {
            pendingAccessory = accessory
            // Request permission — the system will show a dialog or grant automatically
            // if the accessory_filter.xml matches.
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE,
            )
            usbManager.requestPermission(accessory, permissionIntent)
            return
        }
        connectToAccessory(accessory)
    }

    private fun connectToAccessory(accessory: UsbAccessory) {
        scope.launch {
            try {
                fileDescriptor = usbManager.openAccessory(accessory)
                if (fileDescriptor == null) {
                    Log.e(TAG, "Failed to open accessory")
                    return@launch
                }

                val fd = fileDescriptor!!.fileDescriptor
                inputStream = FileInputStream(fd)
                outputStream = FileOutputStream(fd)

                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "Accessory connection established")

                readLoop()
            } catch (e: IOException) {
                Log.e(TAG, "Error opening accessory", e)
                closeAccessory()
            }
        }
    }

    private fun readLoop() {
        val input = inputStream ?: return
        val lenBuf = ByteArray(4)

        try {
            while (true) {
                // Read 4-byte length prefix (big-endian).
                var totalRead = 0
                while (totalRead < 4) {
                    val n = input.read(lenBuf, totalRead, 4 - totalRead)
                    if (n < 0) {
                        Log.i(TAG, "End of stream reached")
                        closeAccessory()
                        return
                    }
                    totalRead += n
                }

                val payloadLen = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                        ((lenBuf[1].toInt() and 0xFF) shl 16) or
                        ((lenBuf[2].toInt() and 0xFF) shl 8) or
                        (lenBuf[3].toInt() and 0xFF)

                if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_SIZE) {
                    Log.e(TAG, "Invalid payload length: $payloadLen")
                    closeAccessory()
                    return
                }

                // Read payload.
                val payload = ByteArray(payloadLen)
                totalRead = 0
                while (totalRead < payloadLen) {
                    val n = input.read(payload, totalRead, payloadLen - totalRead)
                    if (n < 0) {
                        Log.i(TAG, "End of stream during payload read")
                        closeAccessory()
                        return
                    }
                    totalRead += n
                }

                onDataReceived?.invoke(payload)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Read error", e)
            closeAccessory()
        }
    }

    fun sendToClient(data: ByteArray) {
        val output = outputStream
        if (output == null) {
            Log.w(TAG, "sendToClient called but not connected")
            return
        }

        synchronized(output) {
            try {
                // Write 4-byte length prefix (big-endian).
                val lenBuf = byteArrayOf(
                    ((data.size shr 24) and 0xFF).toByte(),
                    ((data.size shr 16) and 0xFF).toByte(),
                    ((data.size shr 8) and 0xFF).toByte(),
                    (data.size and 0xFF).toByte(),
                )
                output.write(lenBuf)
                output.write(data)
                output.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Write error", e)
                closeAccessory()
            }
        }
    }

    private fun closeAccessory() {
        try {
            inputStream?.close()
        } catch (_: IOException) {}
        try {
            outputStream?.close()
        } catch (_: IOException) {}
        try {
            fileDescriptor?.close()
        } catch (_: IOException) {}

        inputStream = null
        outputStream = null
        fileDescriptor = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "Accessory closed")
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTED,
    }

    companion object {
        private const val TAG = "UsbAccessoryManager"
        private const val ACTION_USB_PERMISSION = "com.bartmr.phonekey.USB_PERMISSION"
        private const val MAX_PAYLOAD_SIZE = 65536
    }
}
