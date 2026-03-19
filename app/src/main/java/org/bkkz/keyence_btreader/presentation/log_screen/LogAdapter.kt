package org.bkkz.keyence_btreader.presentation.log_screen

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.bkkz.keyence_btreader.data.local.BarcodeLogRecord
import org.bkkz.keyence_btreader.databinding.LogRecyclerBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : ListAdapter<BarcodeLogRecord, LogAdapter.LogViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy\nHH:mm", Locale.getDefault())

    inner class LogViewHolder(private val binding: LogRecyclerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: BarcodeLogRecord) {
            binding.txtviewReyclerTimestamp.text = dateFormat.format(Date(record.scannedTimeStamp))
            binding.txtviewReyclerBarcode.text = record.barcode
            binding.txtviewReyclerStatus.text = record.status.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = LogRecyclerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BarcodeLogRecord>() {
            override fun areItemsTheSame(oldItem: BarcodeLogRecord, newItem: BarcodeLogRecord): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: BarcodeLogRecord, newItem: BarcodeLogRecord): Boolean {
                return oldItem == newItem
            }
        }
    }
}