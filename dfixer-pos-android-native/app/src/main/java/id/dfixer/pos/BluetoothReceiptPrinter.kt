package id.dfixer.pos

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private data class PrinterDevice(
    val name: String,
    val address: String
)

class BluetoothReceiptPrinter(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    fun pairedDevices(): List<PrinterDevice> {
        return try {
            adapter?.bondedDevices
                ?.map { device -> PrinterDevice(device.name ?: "Bluetooth Printer", device.address) }
                ?.sortedBy { it.name }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun connect(address: String): Boolean {
        val device = findDevice(address) ?: return false

        return try {
            close()
            val uuid = UUID.fromString("00001101-0000-0000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            outputStream = socket?.outputStream
            true
        } catch (_: Exception) {
            false
        }
    }

    fun printReceipt(transaction: TransactionRecord): Boolean {
        val stream = outputStream ?: return false
        val payload = buildReceipt(transaction)

        return try {
            stream.write(payload)
            stream.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun close() {
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        outputStream = null
        socket = null
    }

    private fun findDevice(address: String): BluetoothDevice? {
        return try {
            adapter?.bondedDevices?.firstOrNull { it.address == address }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun formatCurrency(value: Long): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return formatter.format(value)
    }

    private fun buildReceipt(transaction: TransactionRecord): ByteArray {
        val builder = StringBuilder()
        builder.append("\n")
        builder.append("==============================\n")
        builder.append("D'Fixer POS\n")
        builder.append("Receipt Kasir\n")
        builder.append("==============================\n")
        builder.append("#${transaction.id}  ${transaction.date} ${transaction.time}\n")
        builder.append("------------------------------\n")

        transaction.items.forEach { item ->
            val line = "${item.quantity}x ${item.name}"
            val amount = formatCurrency(item.unitPrice * item.quantity)
            builder.append("$line\n")
            builder.append("${" "}${amount}\n")
        }

        builder.append("------------------------------\n")
        builder.append("Total      ${formatCurrency(transaction.total)}\n")
        builder.append("Tunai      ${formatCurrency(transaction.cash)}\n")
        builder.append("Kembali    ${formatCurrency(transaction.change)}\n")
        builder.append("==============================\n")
        builder.append("Terima kasih\n")
        builder.append("\n\n")
        builder.append(0x1D.toChar())
        builder.append("V")
        builder.append(0x41.toChar())
        builder.append(0x01.toChar())
        return builder.toString().toByteArray(Charsets.UTF_8)
    }
}
