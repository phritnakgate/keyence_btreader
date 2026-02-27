package org.bkkz.keyence_btreader

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.keyence.autoid.sdk.scan.DecodeResult
import com.keyence.autoid.sdk.scan.ScanManager

class MainActivity : AppCompatActivity(), ScanManager.DataListener{

    //Data
    private lateinit var scanManager: ScanManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    //UI
    private lateinit var edtBarcode: EditText

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
        scanManager = ScanManager.createScanManager(this)
        scanManager.addDataListener(this)
    }

    private fun findView(){
        edtBarcode = findViewById(R.id.edttxt_barcode)
    }

    private fun setupView(){

    }

    private fun setupEvents(){

    }

    override fun onDataReceived(p0: DecodeResult?) {
        val data = p0?.data ?: ""
        runOnUiThread {
            edtBarcode.setText(data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanManager.removeDataListener(this)
        scanManager.releaseScanManager()
    }

}