package org.bkkz.keyence_btreader.presentation

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bkkz.keyence_btreader.R
import org.bkkz.keyence_btreader.data.local.AppDatabase
import org.bkkz.keyence_btreader.data.local.BarcodeLogRecord
import org.bkkz.keyence_btreader.data.local.BarcodeLogRecordDao
import org.bkkz.keyence_btreader.utils.BluetoothStatus
import java.util.UUID

class ScannerService : Service(), ScanManager.DataListener {

    private lateinit var db: AppDatabase
    private lateinit var barcodeDao: BarcodeLogRecordDao

    // Keyence ScanManager
    private lateinit var scanManager: ScanManager
    private val CHANNEL_ID = "ScannerServiceChannel"

    // --- BLE Connection Variables ---
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    // Define same UUIDs with ESP32 BLE UART
    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // For testing purpose
    companion object {
        const val IS_APP_ON_EMULATOR = true
    }

    override fun onCreate() {
        super.onCreate()

        db = AppDatabase.getDatabase(this)
        barcodeDao = db.barcodeDao()

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
        if(!IS_APP_ON_EMULATOR){
            setupScanner()
        }

        startQueueWorker()
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
                broadcastBtStatus(BluetoothStatus.CONNECTION_FAILED)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Connected to GATT server (${gatt.device.name}).")
                broadcastBtStatus(BluetoothStatus.CONNECTION_SUCCESS, gatt.device.name)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Disconnected from GATT server.")
                broadcastBtStatus(BluetoothStatus.DISCONNECT)
                rxCharacteristic = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)

                    if (txCharacteristic != null) {
                        gatt.setCharacteristicNotification(txCharacteristic, true)
                        val descriptor = txCharacteristic!!.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        }
                    }
                    Log.i("BLE", "UART TX RX Characteristic Found!")
                    gatt.requestMtu(256)
                } else {
                    Log.e("BLE", "UART Service NOT Found on device!")
                    broadcastBtStatus(BluetoothStatus.SERVICE_MISMATCH)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == TX_CHAR_UUID) {
                processAckResponse(value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Notification enabled")
            }
        }
    }

    private fun processAckResponse(value: ByteArray) {
        val response = String(value, Charsets.UTF_8).trim()
        Log.i("BLE_RECV", "Received from ESP32: $response")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (response.startsWith("ACK:")) {
                    val idStr = response.substringAfter("ACK:")
                    val recordId = idStr.toIntOrNull()

                    if (recordId != null) {
                        val record = barcodeDao.getRecordById(recordId)
                        if (record.status.toInt() != 2) {
                            record.status = 2
                            record.reachedGatewayTimestamp = System.currentTimeMillis()
                            barcodeDao.updateLogRecord(record)

                            Log.i("DB_UPDATE", "Record ID $recordId status updated to 2 (Reached Gateway)")
                        }
                    }
                } else if (response.startsWith("FAIL:")) {
                    Log.w("BLE_RECV", "Gateway rejected or LoRa failed for: $response")
                }
            } catch (e: Exception) {
                Log.e("BLE_RECV", "Error parsing response", e)
            }
        }
    }

    private fun broadcastBtStatus(status: BluetoothStatus, deviceName: String? = null) {
        val intent = Intent("ACTION_BT_STATUS")
        intent.putExtra("EXTRA_STATUS", status)
        intent.putExtra("EXTRA_DEVICE_NAME", deviceName)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDataReceived(p0: DecodeResult?) {
        val data = p0?.data ?: ""
        Log.i("ScannerService", "Read: $data")
        if(data.isNotEmpty()){
            val intent = Intent("ACTION_BARCODE_SCANNED")
            intent.putExtra("EXTRA_BARCODE_DATA", data)
            intent.setPackage(packageName)
            sendBroadcast(intent)

            CoroutineScope(Dispatchers.IO).launch {
                val newRecord = BarcodeLogRecord(
                    barcode = data,
                    status = 0,
                    scannedTimeStamp = System.currentTimeMillis()
                )
                barcodeDao.insertLogRecord(newRecord)
            }
        }

    }

    @SuppressLint("MissingPermission")
    private suspend fun sendDataToESP32(record: BarcodeLogRecord) {
        val char = rxCharacteristic
        val gatt = bluetoothGatt

        if (IS_APP_ON_EMULATOR) {
            record.status = 1
            barcodeDao.updateLogRecord(record)
            delay(2500)
            val mockAckMessage = "ACK:${record.id}".toByteArray(Charsets.UTF_8)
            processAckResponse(mockAckMessage)
            return
        }

        if (gatt != null && char != null) {
            val message = "${record.id}:${record.barcode}\n"
            val payload = message.toByteArray(Charsets.UTF_8)
            val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val success =
                gatt.writeCharacteristic(char, payload, writeType) == BluetoothStatusCodes.SUCCESS

            if (success) {
                record.status = 1
                barcodeDao.updateLogRecord(record)
                Log.i("BLE", "Sent to ESP32: ${record.barcode}")
            } else {
                Log.e("BLE", "Failed to write characteristic")
                broadcastBtStatus(BluetoothStatus.FAILED_TO_SEND_DATA)
            }
        } else {
            Log.w("BLE", "Cannot send, GATT or Characteristic not ready")
            broadcastBtStatus(BluetoothStatus.DISCONNECT)
        }
    }

    private var isSending = false

    private fun startQueueWorker() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (bluetoothGatt != null && rxCharacteristic != null && !isSending) {
                    val pendingList = barcodeDao.getPendingRecords()

                    if (pendingList.isNotEmpty()) {
                        isSending = true
                        for (record in pendingList) {
                            if (bluetoothGatt == null || rxCharacteristic == null) break
                            val freshRecord = barcodeDao.getRecordById(record.id)
                            if (freshRecord.status.toInt() < 2) {
                                sendDataToESP32(freshRecord)
                                delay(3000)
                            }
                        }
                        isSending = false
                    }
                }
                delay(1000)
            }
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