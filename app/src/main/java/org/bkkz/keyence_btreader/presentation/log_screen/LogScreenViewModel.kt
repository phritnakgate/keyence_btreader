package org.bkkz.keyence_btreader.presentation.log_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bkkz.keyence_btreader.data.local.BarcodeLogRecord
import org.bkkz.keyence_btreader.data.local.BarcodeLogRecordDao
import org.bkkz.keyence_btreader.utils.BarcodeLogType
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogScreenViewModel(private val dao: BarcodeLogRecordDao) : ViewModel() {

    val logRecords: StateFlow<List<BarcodeLogRecord>> = dao.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAllLogs()
            dao.resetPrimaryKey()
        }
    }

    suspend fun generateCsvFile(directory: File, startDate: Long, endDate: Long): File? {
        return withContext(Dispatchers.IO) {
            try {
                val records: List<BarcodeLogRecord> = if(startDate == endDate){
                    dao.getRecordsForCsv(startDate, endDate + 86399999L)
                }else{
                    dao.getRecordsForCsv(startDate, endDate)
                }

                if (records.isEmpty()) {
                    return@withContext null
                }

                val fileName = "LogExport_${System.currentTimeMillis()}.csv"
                val file = File(directory, fileName)
                val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val dateHeaderFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                FileWriter(file).use { writer ->
                    writer.append('\ufeff')
                    writer.append("Keyence Scanned Log\n")
                    writer.append("From: ${dateHeaderFormat.format(Date(startDate))} - ${
                        dateHeaderFormat.format(
                            Date(endDate)
                        )
                    }\n")
                    writer.append("Status Description: \n")
                    writer.append("Status Code,Description\n")
                    for(logType in BarcodeLogType.entries){
                        writer.append("${logType.logCode},${logType.logDesc}\n")
                    }
                    writer.append("\n")
                    writer.append("Barcode,Status,Scanned Time,Gateway Time\n")

                    for (record in records) {
                        val scanDate = format.format(Date(record.scannedTimeStamp))
                        val gatewayDate = if (record.reachedGatewayTimestamp != null && record.reachedGatewayTimestamp!! > 0) {
                            format.format(Date(record.reachedGatewayTimestamp!!))
                        } else {
                            "N/A"
                        }
                        writer.append("${record.barcode},${record.status},$scanDate,$gatewayDate\n")
                    }
                }
                file

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}