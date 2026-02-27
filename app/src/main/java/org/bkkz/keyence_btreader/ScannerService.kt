package org.bkkz.keyence_btreader

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
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
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class ScannerService : Service(), ScanManager.DataListener {

    //Keyence ScanManager
    private lateinit var scanManager: ScanManager
    private val CHANNEL_ID = "ScannerServiceChannel"

    //Bluetooth Connection Service
    private var btSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val sppUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scanner App Working")
            .setContentText("TESTTEST")
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
                btSocket?.close()

                val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                val device = bluetoothManager.adapter?.getRemoteDevice(macAddress)

                bluetoothManager.adapter?.cancelDiscovery()

                btSocket = device?.createRfcommSocketToServiceRecord(sppUUID)
                btSocket?.connect()
                outputStream = btSocket?.outputStream

                Log.i("Bluetooth", "Connected to $macAddress")
                broadcastBtStatus("Connected With (${device?.name})")

            } catch (e: IOException) {
                Log.e("Bluetooth", "Connection Failed", e)
                broadcastBtStatus("Connection Failed")
                try { btSocket?.close() } catch (ex: Exception) { }
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

    override fun onDestroy() {
        super.onDestroy()
        scanManager.removeDataListener(this)
        scanManager.releaseScanManager()
        try { btSocket?.close() } catch (e: Exception) { }
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

    private fun sendDataToESP32(data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (btSocket?.isConnected == true && outputStream != null) {
                    val message = "$data\n"
                    outputStream?.write(message.toByteArray())
                    outputStream?.flush()
                    Log.i("Bluetooth", "Sent: $data")
                } else {
                    Log.w("Bluetooth", "Cannot send, socket not connected")
                    broadcastBtStatus("Disconnected")
                }
            } catch (e: IOException) {
                Log.e("Bluetooth", "Send Error", e)
                broadcastBtStatus("Error Sending to ESP32")
            }
        }
    }
}