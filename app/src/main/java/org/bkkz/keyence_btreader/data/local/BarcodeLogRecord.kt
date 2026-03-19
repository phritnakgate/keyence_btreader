package org.bkkz.keyence_btreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "BarcodeLog")
data class BarcodeLogRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val barcode: String,
    var status: Short,
    val scannedTimeStamp: Long,
    var reachedGatewayTimestamp: Long? = null
)