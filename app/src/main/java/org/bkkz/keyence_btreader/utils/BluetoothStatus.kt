package org.bkkz.keyence_btreader.utils

enum class BluetoothStatus(val isConnected: Boolean, val statusMessage: String) {
    CONNECTION_FAILED(false, "Connection Failed"),
    CONNECTION_SUCCESS(true, "Connected to "),
    DISCONNECT(false, "Disconnected from device"),
    SERVICE_MISMATCH(false, "UART Service Mismatch"),
    FAILED_TO_SEND_DATA(false, "Failed to send data via BLE.")
}