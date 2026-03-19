package org.bkkz.keyence_btreader.utils

enum class BarcodeLogType(val logCode: Short) {
    BARCODE_RECEIVED(0),
    BARCODE_SENT_VIA_BLUETOOTH(1),
    BARCODE_TRANSFER_SUCCESS(2)
}