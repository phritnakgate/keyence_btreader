package org.bkkz.keyence_btreader.presentation.log_screen

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import org.bkkz.keyence_btreader.R
import org.bkkz.keyence_btreader.data.local.AppDatabase
import org.bkkz.keyence_btreader.presentation.ScannerService
import java.io.File

class LogScreenActivity : AppCompatActivity() {

    private lateinit var imgBack: ImageView
    private lateinit var clearLogBtn: Button
    private lateinit var recyclerLog: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var imgBtnSearchDate : ImageButton
    private lateinit var btnClearFilter : Button
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
        edtSearch = findViewById(R.id.edttxt_search_barcode)
        imgBtnSearchDate = findViewById(R.id.imgbtn_search_date)
        btnClearFilter = findViewById(R.id.btn_filter_clear)
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
                viewModel.state.collect { state ->
                    val records = state.filteredLogRecords
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
        edtSearch.doOnTextChanged { text, _, _, _ ->
            val query = if(text.isNullOrEmpty()) "" else text.toString()
            viewModel.onSearchQueryChanged(query)
        }
        imgBtnSearchDate.setOnClickListener {
            showDateRangePicker { start, stop ->
                viewModel.onDateRangeSelected(start, stop)
            }
        }
        btnClearFilter.setOnClickListener {
            viewModel.clearFilters()
        }
        exportCsvBtn.setOnClickListener {
            showDateRangePicker { start, end ->
                lifecycleScope.launch {
                    val directory = cacheDir
                    val csvFile = viewModel.generateCsvFile(directory, start, end)
                    if (csvFile != null) {
                        shareCsvFile(csvFile)
                        csvFile.delete()
                    } else {
                        Toast.makeText(this@LogScreenActivity, "No data in past 30 days!", Toast.LENGTH_SHORT).show()
                    }

                }
            }
        }
    }

    private fun showDateRangePicker(onDateSelected: (Long, Long) -> Unit) {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Dates")
            .build()

        dateRangePicker.show(supportFragmentManager, "DateRangePicker")

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            onDateSelected(selection.first, selection.second)
        }
    }

    private fun shareCsvFile(file: File) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KeyenceLogs")
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.alert_file_log_title))
                    .setMessage(getString(R.string.alert_file_log_desc))
                    .setPositiveButton(getString(R.string.confirm)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                Toast.makeText(this, "Cannot create CSV file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}