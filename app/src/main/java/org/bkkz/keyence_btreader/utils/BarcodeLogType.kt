package org.bkkz.keyence_btreader.utils

enum class BarcodeLogType(val logCode: Short, val logDesc: String) {
    BARCODE_RECEIVED(0, "App received barcode, waiting to send to controller."),
    BARCODE_SENT_VIA_BLUETOOTH(1, "Barcode sent to controller, waiting for response."),
    BARCODE_TRANSFER_SUCCESS(2, "Barcode transferred to central platform successfully.")
}