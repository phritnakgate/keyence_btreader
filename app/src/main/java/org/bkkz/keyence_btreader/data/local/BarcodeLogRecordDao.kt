package org.bkkz.keyence_btreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BarcodeLogRecordDao {
    @Insert
    suspend fun insertLogRecord(record: BarcodeLogRecord) : Long

    @Update
    suspend fun updateLogRecord(record: BarcodeLogRecord)

    @Query("SELECT * FROM BarcodeLog WHERE id=:id")
    suspend fun getRecordById(id: Int) : BarcodeLogRecord

    @Query("SELECT * FROM BarcodeLog ORDER BY scannedTimeStamp DESC")
    fun getAllLogs(): Flow<List<BarcodeLogRecord>>

    @Query("SELECT * FROM BarcodeLog WHERE status < 2")
    suspend fun getPendingRecords() : List<BarcodeLogRecord>

    @Query("SELECT * FROM BarcodeLog WHERE scannedTimeStamp >= :startDate AND scannedTimeStamp <= :endDate ORDER BY scannedTimeStamp")
    suspend fun getRecordsForCsv(startDate: Long, endDate: Long): List<BarcodeLogRecord>

    @Query("DELETE FROM BarcodeLog")
    suspend fun clearAllLogs()

    @Query("DELETE FROM sqlite_sequence WHERE name = 'BarcodeLog'")
    suspend fun resetPrimaryKey()
}