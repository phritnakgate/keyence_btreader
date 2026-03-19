package org.bkkz.keyence_btreader.presentation.log_screen

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.bkkz.keyence_btreader.R
import org.bkkz.keyence_btreader.data.local.AppDatabase
import org.bkkz.keyence_btreader.databinding.ActivityMainBinding

class LogScreenActivity : AppCompatActivity() {

    private lateinit var imgBack: ImageView
    private lateinit var clearLogBtn: Button
    private lateinit var recyclerLog: RecyclerView
    private lateinit var exportCsvBtn: Button


    private lateinit var adapter: LogAdapter
    private lateinit var viewModel: LogScreenViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_log)

        findView()
        setupData()
        setupView()
        setupEvents()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun findView(){
        imgBack = findViewById(R.id.imgview_log_back)
        clearLogBtn = findViewById(R.id.btn_log_clear)
        recyclerLog = findViewById(R.id.recyclerview_log)
        exportCsvBtn = findViewById(R.id.btn_log_export)
    }
    private fun setupData(){
        val dao = AppDatabase.getDatabase(this).barcodeDao()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return LogScreenViewModel(dao) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[LogScreenViewModel::class.java]
    }

    private fun setupView(){
        adapter = LogAdapter()
        recyclerLog.layoutManager = LinearLayoutManager(this@LogScreenActivity)
        recyclerLog.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logRecords.collect { records ->
                    adapter.submitList(records)
                    if (records.isNotEmpty()) {
                        recyclerLog.scrollToPosition(0)
                    }
                }
            }
        }
    }
    private fun setupEvents(){
        imgBack.setOnClickListener {
            finish()
        }
        exportCsvBtn.setOnClickListener {

        }
        clearLogBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.alert_del_log_title))
                .setMessage(getString(R.string.alert_del_log_desc))
                .setPositiveButton(getString(R.string.confirm)) { dialog, _ ->
                    viewModel.clearAllLogs()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }
}