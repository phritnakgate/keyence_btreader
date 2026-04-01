package org.bkkz.keyence_btreader.presentation.log_screen.state

import org.bkkz.keyence_btreader.data.local.BarcodeLogRecord

data class LogState(
    val filteredLogRecords: List<BarcodeLogRecord> = emptyList(),
    val searchQuery: String? = null,
    val searchStartDate: Long? = null,
    val searchEndDate: Long? = null,
)
