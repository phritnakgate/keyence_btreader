package org.bkkz.keyence_btreader

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(){

    //UI
    private lateinit var edtBarcode: EditText

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if(permissions[Manifest.permission.BLUETOOTH_CONNECT] == true){
            val serviceIntent = Intent(this, ScannerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }else{
            Toast.makeText(this@MainActivity, "Allow Bluetooth First!", Toast.LENGTH_SHORT)
        }

    }

    private val barcodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity","Broadcast Received")
            if (intent?.action == "ACTION_BARCODE_SCANNED") {
                val scannedData = intent.getStringExtra("EXTRA_BARCODE_DATA") ?: ""

                runOnUiThread {
                    edtBarcode.setText(scannedData)
                    edtBarcode.setSelection(edtBarcode.text.length)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupData()
        findView()
        setupView()
        setupEvents()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun setupData(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            val serviceIntent = Intent(this, ScannerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun findView(){
        edtBarcode = findViewById(R.id.edttxt_barcode)
    }

    private fun setupView(){

    }

    private fun setupEvents(){

    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("ACTION_BARCODE_SCANNED")
        ContextCompat.registerReceiver(
            this,
            barcodeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(barcodeReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Unregister failed", e)
        }
    }


}