package id.dfixer.pos

import android.content.Context
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SaleItem(
    val productId: Long,
    val name: String,
    val price: Long,
    val quantity: Int
)

data class TransactionSummary(
    val id: Long,
    val createdAt: Long,
    val total: Long,
    val cash: Long,
    val change: Long,
    val itemCount: Int,
    val items: List<SaleItem> = emptyList()
)

data class ReportStats(
    val totalSales: Long,
    val totalTransactions: Int,
    val averageTicket: Long,
    val bestSeller: String,
    val todaySales: Long
)

data class StockMovement(
    val id: Long,
    val productId: Long,
    val productName: String,
    val type: String,
    val quantity: Int,
    val reason: String,
    val createdAt: Long
)

class PosRepository(context: Context) {
    private val database = PosDatabase.getInstance(context)

    fun observeProducts(): Flow<List<ProductEntity>> = database.productDao().observeProducts()

    suspend fun getProducts(): List<ProductEntity> = database.productDao().getProducts()

    suspend fun seedDemoProducts() {
        val existing = database.productDao().getProducts()
        if (existing.isNotEmpty()) return

        val demoProducts = listOf(
            ProductEntity(name = "Kopi Hitam", price = 12000, stock = 24, category = "Minuman"),
            ProductEntity(name = "Roti Cokelat", price = 18000, stock = 16, category = "Makanan"),
            ProductEntity(name = "Ayam Geprek", price = 26000, stock = 12, category = "Makanan"),
            ProductEntity(name = "Es Teh", price = 8000, stock = 30, category = "Minuman"),
            ProductEntity(name = "Mie Goreng", price = 22000, stock = 15, category = "Makanan"),
            ProductEntity(name = "Nasi Uduk", price = 19000, stock = 18, category = "Makanan")
        )

        database.productDao().upsertAll(demoProducts)
    }

    suspend fun addProduct(name: String, price: Long, stock: Int, category: String = "Umum") {
        if (name.isBlank()) return
        val entity = ProductEntity(
            name = name.trim(),
            price = price.coerceAtLeast(0L),
            stock = stock.coerceAtLeast(0),
            category = category.ifBlank { "Umum" }
        )
        database.productDao().upsert(entity)
    }

    suspend fun updateProduct(
        productId: Long,
        name: String,
        price: Long,
        stock: Int,
        category: String = "Umum"
    ) {
        if (name.isBlank()) return
        val existing = database.productDao().getProducts().firstOrNull { it.id == productId } ?: return
        database.productDao().upsert(
            existing.copy(
                name = name.trim(),
                price = price.coerceAtLeast(0L),
                stock = stock.coerceAtLeast(0),
                category = category.ifBlank { "Umum" }
            )
        )
    }

    suspend fun deleteProduct(productId: Long) {
        database.productDao().deleteById(productId)
    }

    suspend fun adjustStock(productId: Long, delta: Int, reason: String = "Penyesuaian manual") {
        val product = database.productDao().getProducts().firstOrNull { it.id == productId } ?: return
        val safeDelta = delta.coerceAtLeast(-product.stock)
        val nextStock = (product.stock + safeDelta).coerceAtLeast(0)
        database.productDao().upsert(product.copy(stock = nextStock))

        val movementType = if (safeDelta >= 0) "in" else "out"
        val movementQty = kotlin.math.abs(safeDelta)
        database.stockMovementDao().insertMovement(
            StockMovementEntity(
                productId = productId,
                productName = product.name,
                type = movementType,
                quantity = movementQty,
                reason = reason.ifBlank { "Penyesuaian manual" },
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordSupplierPurchase(
        productId: Long,
        quantity: Int,
        supplier: String,
        unitCost: Long = 0L,
        notes: String = ""
    ) {
        if (quantity <= 0) return
        val safeSupplier = supplier.trim().ifBlank { "Supplier umum" }
        val detail = buildString {
            append("Pembelian supplier $safeSupplier")
            if (notes.isNotBlank()) append(" • $notes")
            if (unitCost > 0L) append(" • Rp$unitCost/unit")
        }
        adjustStock(productId, quantity, detail)
    }

    suspend fun getStockMovements(): List<StockMovement> {
        return database.stockMovementDao().getMovements().map { movement ->
            StockMovement(
                id = movement.id,
                productId = movement.productId,
                productName = movement.productName,
                type = movement.type,
                quantity = movement.quantity,
                reason = movement.reason,
                createdAt = movement.createdAt
            )
        }
    }

    suspend fun saveTransaction(items: List<SaleItem>, cash: Long): Long {
        if (items.isEmpty()) return 0L

        val total = items.sumOf { it.price * it.quantity.toLong() }
        val change = (cash - total).coerceAtLeast(0L)

        val inventory = database.productDao().getProducts().associateBy { it.id }
        for (item in items) {
            val current = inventory[item.productId] ?: continue
            val updatedStock = current.stock - item.quantity
            if (updatedStock < 0) {
                throw IllegalStateException("Stok ${current.name} tidak mencukupi")
            }
            database.productDao().upsert(current.copy(stock = updatedStock))
        }

        val transactionId = database.transactionDao().insertTransaction(
            TransactionEntity(
                createdAt = System.currentTimeMillis(),
                total = total,
                cash = cash,
                change = change,
                status = "completed"
            )
        )

        val entityItems = items.map { item ->
            TransactionItemEntity(
                transactionId = transactionId,
                productId = item.productId,
                productName = item.name,
                quantity = item.quantity,
                unitPrice = item.price
            )
        }

        database.transactionDao().insertItems(entityItems)

        items.forEach { item ->
            database.stockMovementDao().insertMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.name,
                    type = "out",
                    quantity = item.quantity,
                    reason = "Penjualan #$transactionId",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        return transactionId
    }

    suspend fun cancelTransaction(transactionId: Long): Boolean {
        val tx = database.transactionDao().getTransactionsWithItems().firstOrNull { it.transaction.id == transactionId } ?: return false
        val items = tx.items

        items.forEach { item ->
            val product = database.productDao().getProducts().firstOrNull { it.id == item.productId }
            if (product != null) {
                database.productDao().upsert(product.copy(stock = product.stock + item.quantity))
                database.stockMovementDao().insertMovement(
                    StockMovementEntity(
                        productId = item.productId,
                        productName = item.productName,
                        type = "in",
                        quantity = item.quantity,
                        reason = "Refund transaksi #$transactionId",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        database.transactionDao().deleteTransaction(transactionId)
        return true
    }

    suspend fun getTransactionHistory(): List<TransactionSummary> {
        return database.transactionDao().getTransactionsWithItems().map { tx ->
            TransactionSummary(
                id = tx.transaction.id,
                createdAt = tx.transaction.createdAt,
                total = tx.transaction.total,
                cash = tx.transaction.cash,
                change = tx.transaction.change,
                itemCount = tx.items.sumOf { it.quantity },
                items = tx.items.map { item ->
                    SaleItem(
                        productId = item.productId,
                        name = item.productName,
                        price = item.unitPrice,
                        quantity = item.quantity
                    )
                }
            )
        }
    }

    suspend fun getReportStats(): ReportStats {
        val transactions = database.transactionDao().getTransactionsWithItems()
        val totalSales = transactions.sumOf { it.transaction.total }
        val totalTransactions = transactions.size
        val averageTicket = if (totalTransactions > 0) totalSales / totalTransactions else 0L

        val productCounts = mutableMapOf<String, Int>()
        for (tx in transactions) {
            for (item in tx.items) {
                productCounts[item.productName] = productCounts.getOrDefault(item.productName, 0) + item.quantity
            }
        }

        val bestSeller = productCounts.maxByOrNull { it.value }?.key ?: "Belum ada data"

        val now = System.currentTimeMillis()
        val startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todaySales = transactions.filter { it.transaction.createdAt >= startOfToday && it.transaction.createdAt <= now }
            .sumOf { it.transaction.total }

        return ReportStats(
            totalSales = totalSales,
            totalTransactions = totalTransactions,
            averageTicket = averageTicket,
            bestSeller = bestSeller,
            todaySales = todaySales
        )
    }

    suspend fun exportBackup(file: File): String {
        val products = database.productDao().getProducts()
        val transactions = database.transactionDao().getTransactionsWithItems()

        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())

        val productArray = JSONArray()
        for (product in products) {
            val item = JSONObject()
            item.put("id", product.id)
            item.put("name", product.name)
            item.put("price", product.price)
            item.put("stock", product.stock)
            item.put("category", product.category)
            productArray.put(item)
        }
        root.put("products", productArray)

        val transactionArray = JSONArray()
        for (tx in transactions) {
            val obj = JSONObject()
            obj.put("id", tx.transaction.id)
            obj.put("createdAt", tx.transaction.createdAt)
            obj.put("total", tx.transaction.total)
            obj.put("cash", tx.transaction.cash)
            obj.put("change", tx.transaction.change)
            obj.put("status", tx.transaction.status)

            val itemsArray = JSONArray()
            for (item in tx.items) {
                val itemObj = JSONObject()
                itemObj.put("productId", item.productId)
                itemObj.put("productName", item.productName)
                itemObj.put("quantity", item.quantity)
                itemObj.put("unitPrice", item.unitPrice)
                itemsArray.put(itemObj)
            }
            obj.put("items", itemsArray)
            transactionArray.put(obj)
        }
        root.put("transactions", transactionArray)

        file.writeText(root.toString())
        return file.absolutePath
    }

    suspend fun exportCsvReport(file: File): String {
        val transactions = database.transactionDao().getTransactionsWithItems()
        val csv = buildString {
            appendLine("id,createdAt,total,cash,change,itemCount")
            for (tx in transactions) {
                append(tx.transaction.id)
                append(',')
                append(tx.transaction.createdAt)
                append(',')
                append(tx.transaction.total)
                append(',')
                append(tx.transaction.cash)
                append(',')
                append(tx.transaction.change)
                append(',')
                append(tx.items.sumOf { it.quantity })
                appendLine()
            }
        }
        file.writeText(csv)
        return file.absolutePath
    }

    suspend fun restoreBackup(file: File): Int {
        if (!file.exists()) return 0

        val root = JSONObject(file.readText())
        val productArray = root.optJSONArray("products") ?: JSONArray()
        val transactionArray = root.optJSONArray("transactions") ?: JSONArray()

        database.productDao().clear()
        database.transactionDao().clearItems()
        database.transactionDao().clearTransactions()

        val productList = mutableListOf<ProductEntity>()
        for (index in 0 until productArray.length()) {
            val item = productArray.getJSONObject(index)
            productList.add(
                ProductEntity(
                    id = item.optLong("id", 0L),
                    name = item.optString("name", ""),
                    price = item.optLong("price", 0L),
                    stock = item.optInt("stock", 0),
                    category = item.optString("category", "Umum")
                )
            )
        }
        if (productList.isNotEmpty()) {
            database.productDao().upsertAll(productList)
        }

        for (index in 0 until transactionArray.length()) {
            val txObj = transactionArray.getJSONObject(index)
            val transactionId = database.transactionDao().insertTransaction(
                TransactionEntity(
                    id = txObj.optLong("id", 0L),
                    createdAt = txObj.optLong("createdAt", System.currentTimeMillis()),
                    total = txObj.optLong("total", 0L),
                    cash = txObj.optLong("cash", 0L),
                    change = txObj.optLong("change", 0L),
                    status = txObj.optString("status", "completed")
                )
            )

            val itemsArray = txObj.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<TransactionItemEntity>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                items.add(
                    TransactionItemEntity(
                        id = 0,
                        transactionId = transactionId,
                        productId = itemObj.optLong("productId", 0L),
                        productName = itemObj.optString("productName", ""),
                        quantity = itemObj.optInt("quantity", 0),
                        unitPrice = itemObj.optLong("unitPrice", 0L)
                    )
                )
            }
            if (items.isNotEmpty()) {
                database.transactionDao().insertItems(items)
            }
        }

        return productArray.length() + transactionArray.length()
    }
}
