package org.bkkz.keyence_btreader.presentation.log_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.bkkz.keyence_btreader.data.local.BarcodeLogRecord
import org.bkkz.keyence_btreader.data.local.BarcodeLogRecordDao

class LogScreenViewModel(dao: BarcodeLogRecordDao) : ViewModel() {

    val logRecords: StateFlow<List<BarcodeLogRecord>> = dao.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

}