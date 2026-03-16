package org.bkkz.keyence_btreader

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.keyence.autoid.sdk.scan.DecodeResult
import com.keyence.autoid.sdk.scan.ScanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ScannerService : Service(), ScanManager.DataListener {

    // Keyence ScanManager
    private lateinit var scanManager: ScanManager
    private val CHANNEL_ID = "ScannerServiceChannel"

    // --- BLE Connection Variables ---
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    // Define same UUIDs with ESP32 BLE UART
    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // ESP Rx = Android Tx

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scanner App Working")
            .setContentText("Listening for Barcodes (BLE Mode)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        setupScanner()
    }

    private fun setupScanner() {
        scanManager = ScanManager.createScanManager(this)
        scanManager.addDataListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_CONNECT_ESP") {
            val macAddress = intent.getStringExtra("EXTRA_MAC_ADDRESS")
            if (macAddress != null) {
                connectToDevice(macAddress)
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(macAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothGatt?.close()

                val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                val device = bluetoothManager.adapter?.getRemoteDevice(macAddress)

                bluetoothGatt = device?.connectGatt(this@ScannerService, false, gattCallback)

                Log.i("BLE", "Connecting to GATT Server: $macAddress")
            } catch (e: Exception) {
                Log.e("BLE", "Connection Initiation Failed", e)
                broadcastBtStatus("Connection Failed")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Connected to GATT server.")
                broadcastBtStatus("Connected")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Disconnected from GATT server.")
                broadcastBtStatus("Disconnected")
                rxCharacteristic = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    Log.i("BLE", "UART Service & RX Characteristic Found!")
                    gatt.requestMtu(256)
                } else {
                    Log.e("BLE", "UART Service NOT Found on device!")
                    broadcastBtStatus("Service Mismatch")
                }
            }
        }
    }

    private fun broadcastBtStatus(status: String) {
        val intent = Intent("ACTION_BT_STATUS")
        intent.putExtra("EXTRA_STATUS", status)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDataReceived(p0: DecodeResult?) {
        val data = p0?.data ?: ""
        Log.i("MyScannerService", "Read: $data")

        val intent = Intent("ACTION_BARCODE_SCANNED")
        intent.putExtra("EXTRA_BARCODE_DATA", data)
        intent.setPackage(packageName)
        sendBroadcast(intent)
        sendDataToESP32(data)
    }

    @SuppressLint("MissingPermission")
    private fun sendDataToESP32(data: String) {
        val char = rxCharacteristic
        val gatt = bluetoothGatt

        if (gatt != null && char != null) {
            val message = "$data\n"

            val payload = message.toByteArray(Charsets.UTF_8)
            val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            val success = gatt.writeCharacteristic(char, payload, writeType) == BluetoothStatusCodes.SUCCESS
            if (success) {
                Log.i("BLE", "Sent to ESP32: $data")
            } else {
                Log.e("BLE", "Failed to write characteristic")
                broadcastBtStatus("Error Sending")
            }
        } else {
            Log.w("BLE", "Cannot send, GATT or Characteristic not ready")
            broadcastBtStatus("Disconnected (No GATT)")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanManager.removeDataListener(this)
        scanManager.releaseScanManager()

        @SuppressLint("MissingPermission")
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Scanner Service Background",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}