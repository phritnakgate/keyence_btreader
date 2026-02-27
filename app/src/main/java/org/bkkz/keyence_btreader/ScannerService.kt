package org.bkkz.keyence_btreader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.keyence.autoid.sdk.scan.DecodeResult
import com.keyence.autoid.sdk.scan.ScanManager

class ScannerService : Service(), ScanManager.DataListener {

    private lateinit var scanManager: ScanManager
    private val CHANNEL_ID = "ScannerServiceChannel"

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

    override fun onDataReceived(p0: DecodeResult?) {
        val data = p0?.data ?: ""
        Log.i("MyScannerService", "Read: $data")
        val intent = Intent("ACTION_BARCODE_SCANNED")
        intent.putExtra("EXTRA_BARCODE_DATA", data)
        intent.setPackage(packageName)
        sendBroadcast(intent)
        //TODO Implement ESP32
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scanManager.removeDataListener(this)
        scanManager.releaseScanManager()
        // TODO Close Bluetooth Socket
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