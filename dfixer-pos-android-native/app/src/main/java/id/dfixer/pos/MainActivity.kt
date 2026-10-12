package id.dfixer.pos

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DFixerPosApp(this)
                }
            }
        }
    }
}

private enum class AppFlow {
    SETUP_PIN,
    LOGIN,
    DASHBOARD,
    TRANSACTION,
    PURCHASES,
    PRODUCTS,
    HISTORY,
    REPORTS,
    BACKUP,
    STOCK_LOG,
    PRINTER
}

private enum class ReportRange {
    TODAY,
    LAST_7_DAYS,
    ALL
}

private data class DashboardMetric(
    val title: String,
    val value: String,
    val detail: String,
    val tint: Color
)

private data class QuickAction(
    val label: String,
    val description: String
)

private data class Product(
    val id: Int,
    val name: String,
    val price: Long,
    val stock: Int
)

private data class CartItem(
    val product: Product,
    val quantity: Int
)

private data class ReceiptLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Long
)

private data class TransactionRecord(
    val id: Int,
    val date: String,
    val time: String,
    val total: Long,
    val cash: Long,
    val change: Long,
    val itemCount: Int,
    val items: List<ReceiptLine> = emptyList()
)

private fun formatCurrency(value: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    return formatter.format(value)
}

@Composable
private fun DFixerPosApp(context: Context) {
    val prefs = context.getSharedPreferences("dfixer_pos", Context.MODE_PRIVATE)
    val repository = remember { PosRepository(context.applicationContext) }
    var liveProducts by remember { mutableStateOf(listOf<ProductEntity>()) }
    var transactionHistory by remember { mutableStateOf(listOf<TransactionRecord>()) }
    var stockMovements by remember { mutableStateOf(listOf<StockMovement>()) }
    var reportStats by remember { mutableStateOf(ReportStats(0, 0, 0, "Belum ada data", 0)) }

    fun refreshProducts() {
        liveProducts = repository.getProducts()
    }

    fun refreshStockMovements() {
        stockMovements = repository.getStockMovements()
    }

    fun refreshTransactions() {
        transactionHistory = repository.getTransactionHistory().map { tx ->
            val dateTime = java.time.Instant.ofEpochMilli(tx.createdAt)
                .atZone(java.time.ZoneId.systemDefault())
            val date = dateTime.toLocalDate().toString()
            val time = dateTime.toLocalTime().toString().substring(0, 5)
            TransactionRecord(
                id = tx.id.toInt(),
                date = date,
                time = time,
                total = tx.total,
                cash = tx.cash,
                change = tx.change,
                itemCount = tx.itemCount,
                items = tx.items.map { item ->
                    ReceiptLine(
                        name = item.name,
                        quantity = item.quantity,
                        unitPrice = item.price
                    )
                }
            )
        }
        reportStats = repository.getReportStats()
    }

    LaunchedEffect(Unit) {
        repository.seedDemoProducts()
        refreshProducts()
        refreshTransactions()
        refreshStockMovements()
    }

    val savedPin = prefs.getString("owner_pin", null)
    var currentFlow by remember { mutableStateOf(if (savedPin == null) AppFlow.SETUP_PIN else AppFlow.LOGIN) }

    when (currentFlow) {
        AppFlow.SETUP_PIN -> PinSetupScreen(
            onSave = { pin ->
                prefs.edit().putString("owner_pin", pin).apply()
                currentFlow = AppFlow.DASHBOARD
            }
        )

        AppFlow.LOGIN -> PinLoginScreen(
            onUnlock = {
                currentFlow = AppFlow.DASHBOARD
            },
            savedPin = savedPin ?: "",
            onBack = { }
        )

        AppFlow.DASHBOARD -> PosDashboardScreen(
            products = liveProducts,
            stats = reportStats,
            stockMovements = stockMovements,
            onOpenTransaction = { currentFlow = AppFlow.TRANSACTION },
            onOpenPurchases = { currentFlow = AppFlow.PURCHASES },
            onOpenProducts = { currentFlow = AppFlow.PRODUCTS },
            onOpenHistory = { currentFlow = AppFlow.HISTORY },
            onOpenReports = { currentFlow = AppFlow.REPORTS },
            onOpenBackup = { currentFlow = AppFlow.BACKUP },
            onOpenStockLog = { currentFlow = AppFlow.STOCK_LOG },
            onOpenPrinter = { currentFlow = AppFlow.PRINTER },
            onLogout = {
                prefs.edit().remove("owner_pin").apply()
                currentFlow = AppFlow.SETUP_PIN
            }
        )

        AppFlow.TRANSACTION -> TransactionScreen(
            products = liveProducts.map { Product(it.id.toInt(), it.name, it.price, it.stock) },
            transactions = transactionHistory.toMutableList(),
            repository = repository,
            onBack = { currentFlow = AppFlow.DASHBOARD },
            onTransactionSaved = {
                refreshProducts()
                refreshTransactions()
                refreshStockMovements()
                currentFlow = AppFlow.DASHBOARD
            }
        )

        AppFlow.PURCHASES -> SupplierPurchaseScreen(
            repository = repository,
            products = liveProducts,
            onBack = {
                refreshProducts()
                refreshStockMovements()
                currentFlow = AppFlow.DASHBOARD
            },
            onSaved = {
                refreshProducts()
                refreshTransactions()
                refreshStockMovements()
                currentFlow = AppFlow.DASHBOARD
            }
        )

        AppFlow.PRODUCTS -> ProductManagementScreen(
            repository = repository,
            onBack = {
                refreshStockMovements()
                currentFlow = AppFlow.DASHBOARD
            },
            onProductsChanged = {
                liveProducts = it
                refreshTransactions()
                refreshStockMovements()
            }
        )

        AppFlow.HISTORY -> HistoryScreen(
            context = context,
            repository = repository,
            transactions = transactionHistory,
            onBack = { currentFlow = AppFlow.DASHBOARD },
            onTransactionChanged = {
                refreshProducts()
                refreshTransactions()
                refreshStockMovements()
            }
        )

        AppFlow.REPORTS -> ReportsScreen(
            repository = repository,
            transactions = transactionHistory,
            stats = reportStats,
            context = context,
            onBack = { currentFlow = AppFlow.DASHBOARD }
        )

        AppFlow.BACKUP -> BackupScreen(
            repository = repository,
            context = context,
            onBack = { currentFlow = AppFlow.DASHBOARD }
        )

        AppFlow.STOCK_LOG -> StockLogScreen(
            movements = stockMovements,
            onBack = { currentFlow = AppFlow.DASHBOARD }
        )

        AppFlow.PRINTER -> PrinterScreen(
            context = context,
            transactions = transactionHistory,
            onBack = { currentFlow = AppFlow.DASHBOARD }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupScreen(onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Setup PIN Owner") })
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Buat PIN toko", style = MaterialTheme.typography.headlineSmall)
            Text("Gunakan 6 digit agar toko aman dan transaksi lebih terkontrol.")
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
                label = { Text("PIN baru") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 6) confirmPin = it.filter(Char::isDigit) },
                label = { Text("Konfirmasi PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }
                Text(if (showPin) "Sembunyikan PIN" else "Tampilkan PIN")
            }

            if (error != null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    when {
                        pin.length < 6 -> error = "PIN minimal 6 digit"
                        pin != confirmPin -> error = "PIN tidak cocok"
                        else -> onSave(pin)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan PIN")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinLoginScreen(
    onUnlock: () -> Unit,
    savedPin: String,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Login Owner") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
        })
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Masukkan PIN", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }
                Text(if (showPin) "Sembunyikan PIN" else "Tampilkan PIN")
            }

            if (error != null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    if (pin == savedPin) {
                        onUnlock()
                    } else {
                        error = "PIN salah"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Masuk")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosDashboardScreen(
    products: List<ProductEntity>,
    stats: ReportStats,
    stockMovements: List<StockMovement>,
    onOpenTransaction: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenStockLog: () -> Unit,
    onOpenPrinter: () -> Unit,
    onLogout: () -> Unit
) {
    val lowStockProducts = products.filter { it.stock <= 5 }.sortedBy { it.stock }
    val metrics = listOf(
        DashboardMetric(
            "Penjualan Hari Ini",
            formatCurrency(stats.todaySales),
            if (stats.bestSeller == "Belum ada data") "Belum ada transaksi hari ini" else "Produk paling laris: ${stats.bestSeller}",
            Color(0xFF4CAF50)
        ),
        DashboardMetric(
            "Transaksi",
            stats.totalTransactions.toString(),
            "Rata-rata ${formatCurrency(stats.averageTicket)} per transaksi",
            Color(0xFF2196F3)
        ),
        DashboardMetric(
            "Produk Aktif",
            products.size.toString(),
            "${lowStockProducts.size} stok menipis",
            Color(0xFFFF9800)
        ),
        DashboardMetric("Printer", "Tersambung", "Bluetooth ready", Color(0xFF9C27B0))
    )

    val actions = listOf(
        QuickAction("Transaksi Baru", "Memulai penjualan kasir"),
        QuickAction("Riwayat", "Lihat struk dan jurnal"),
        QuickAction("Laporan", "Ringkasan harian"),
        QuickAction("Log Stok", "Audit masuk/keluar stok")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("D’Fixer POS", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Mode lokal siap dipakai",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status toko", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Semua modul utama sudah siap diproses dalam satu alur kasir dan laporan.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(metrics) { metric ->
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(metric.tint, CircleShape)
                                )
                                Text(metric.title, style = MaterialTheme.typography.labelLarge)
                            }
                            Text(metric.value, style = MaterialTheme.typography.headlineSmall)
                            Text(metric.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (lowStockProducts.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Peringatan stok", style = MaterialTheme.typography.titleMedium)
                        lowStockProducts.take(4).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name)
                                Text("Sisa ${item.stock}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Text("Aksi cepat", style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(actions) { action ->
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (action.label) {
                                        "Transaksi Baru" -> Icons.Default.ShoppingCart
                                        "Riwayat" -> Icons.Default.History
                                        "Laporan" -> Icons.Default.BarChart
                                        "Log Stok" -> Icons.Default.Sync
                                        else -> Icons.Default.Sync
                                    },
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    action.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(action.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onOpenTransaction, modifier = Modifier.weight(1f)) {
                    Text("Kasir")
                }
                OutlinedButton(onClick = onOpenProducts, modifier = Modifier.weight(1f)) {
                    Text("Produk")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                    Text("Riwayat")
                }
                OutlinedButton(onClick = onOpenReports, modifier = Modifier.weight(1f)) {
                    Text("Laporan")
                }
            }
            OutlinedButton(
                onClick = onOpenPurchases,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pembelian supplier")
            }

            OutlinedButton(
                onClick = onOpenPrinter,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("Printer receipt")
            }

            Button(
                onClick = onOpenBackup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Backup & Restore")
            }

            OutlinedButton(
                onClick = onOpenStockLog,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log stok")
            }

            if (stockMovements.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pergerakan stok terbaru", style = MaterialTheme.typography.titleMedium)
                        stockMovements.take(3).forEach { movement ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${movement.type.uppercase()} ${movement.quantity} ${movement.productName}")
                                Text(movement.reason)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionScreen(
    products: List<Product>,
    transactions: MutableList<TransactionRecord>,
    repository: PosRepository,
    onBack: () -> Unit,
    onTransactionSaved: () -> Unit
) {
    val cart = remember { mutableStateListOf<CartItem>() }
    var cashText by remember { mutableStateOf("0") }
    var lastSaved by remember { mutableStateOf<String?>(null) }
    var productQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }

    val categories = listOf("Semua") + products.map { it.category }.distinct()
    val filteredProducts = products.filter { product ->
        val query = productQuery.trim().lowercase()
        val matchesQuery = query.isEmpty() ||
            product.name.lowercase().contains(query) ||
            product.category.lowercase().contains(query)
        val matchesCategory = selectedCategory == "Semua" || product.category == selectedCategory
        matchesQuery && matchesCategory
    }.sortedBy { it.stock <= 5 }

    val subtotal = cart.sumOf { it.product.price * it.quantity }
    val cash = cashText.toLongOrNull() ?: 0L
    val change = (cash - subtotal).coerceAtLeast(0L)

    fun addToCart(product: Product) {
        val index = cart.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val existing = cart[index]
            val nextQuantity = existing.quantity + 1
            val currentProduct = products.firstOrNull { it.id == product.id }
            if (currentProduct != null && nextQuantity > currentProduct.stock) {
                lastSaved = "Stok ${product.name} tidak mencukupi"
                return
            }
            cart[index] = existing.copy(quantity = nextQuantity)
        } else {
            if (product.stock <= 0) {
                lastSaved = "Stok ${product.name} habis"
                return
            }
            cart.add(CartItem(product, 1))
        }
    }

    fun updateQuantity(productId: Int, delta: Int) {
        val index = cart.indexOfFirst { it.product.id == productId }
        if (index < 0) return

        val current = cart[index]
        val nextQuantity = current.quantity + delta
        val availableStock = products.firstOrNull { it.id == productId }?.stock ?: 0
        if (nextQuantity <= 0) {
            cart.removeAt(index)
        } else if (nextQuantity <= availableStock) {
            cart[index] = current.copy(quantity = nextQuantity)
        } else {
            lastSaved = "Stok tidak mencukupi untuk item ini"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kasir POS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Produk", style = MaterialTheme.typography.titleLarge)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${cart.size} item",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            OutlinedTextField(
                value = productQuery,
                onValueChange = { productQuery = it },
                label = { Text("Cari produk / kategori") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    OutlinedButton(
                        onClick = { selectedCategory = category },
                        colors = if (selectedCategory == category) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text(category)
                    }
                }
            }

            if (products.count { it.stock <= 5 } > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        "Stok menipis: ${products.count { it.stock <= 5 }} produk butuh restock",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredProducts) { product ->
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(formatCurrency(product.price), style = MaterialTheme.typography.bodyMedium)
                            Text("Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall)
                            if (product.stock <= 5) {
                                Text("Stok rendah", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { addToCart(product) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Tambah")
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Keranjang", style = MaterialTheme.typography.titleMedium)

                    if (cart.isEmpty()) {
                        Text("Belum ada item ditambahkan.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(cart) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name)
                                        Text(formatCurrency(item.product.price * item.quantity))
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(onClick = { updateQuantity(item.product.id, -1) }) {
                                            Icon(Icons.Default.Remove, contentDescription = "Kurangi")
                                        }
                                        Text(item.quantity.toString())
                                        IconButton(onClick = { updateQuantity(item.product.id, 1) }) {
                                            Icon(Icons.Default.Add, contentDescription = "Tambah")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal")
                        Text(formatCurrency(subtotal))
                    }

                    OutlinedTextField(
                        value = cashText,
                        onValueChange = { cashText = it.filter(Char::isDigit) },
                        label = { Text("Uang tunai") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kembalian")
                        Text(formatCurrency(change))
                    }

                    if (lastSaved != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            Text(lastSaved ?: "")
                        }
                    }

                    Button(
                        onClick = {
                            if (cart.isEmpty()) {
                                lastSaved = "Keranjang masih kosong"
                                return@Button
                            }
                            if (cash < subtotal) {
                                lastSaved = "Cash harus mencukupi subtotal"
                                return@Button
                            }

                            try {
                                val saleItems = cart.map {
                                    SaleItem(
                                        productId = it.product.id.toLong(),
                                        name = it.product.name,
                                        price = it.product.price,
                                        quantity = it.quantity
                                    )
                                }
                                repository.saveTransaction(saleItems, cash)
                                transactions.add(0, TransactionRecord(
                                    id = (transactions.firstOrNull()?.id ?: 0) + 1,
                                    date = java.time.LocalDate.now().toString(),
                                    time = java.time.LocalTime.now().toString().substring(0, 5),
                                    total = subtotal,
                                    cash = cash,
                                    change = change,
                                    itemCount = saleItems.sumOf { it.quantity },
                                    items = saleItems.map { item ->
                                        ReceiptLine(
                                            name = item.name,
                                            quantity = item.quantity,
                                            unitPrice = item.price
                                        )
                                    }
                                ))
                                cart.clear()
                                cashText = "0"
                                lastSaved = "Transaksi berhasil disimpan"
                                onTransactionSaved()
                            } catch (e: IllegalStateException) {
                                lastSaved = e.message ?: "Transaksi gagal"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan transaksi")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    context: Context,
    repository: PosRepository,
    transactions: List<TransactionRecord>,
    onBack: () -> Unit,
    onTransactionChanged: () -> Unit = {}
) {
    val printer = remember { BluetoothReceiptPrinter(context.applicationContext) }
    var selectedTransaction by remember { mutableStateOf<TransactionRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Transaksi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (transactions.isEmpty()) {
                Text("Belum ada transaksi.")
            } else {
                transactions.forEach { tx ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Transaksi #${tx.id}", style = MaterialTheme.typography.titleMedium)
                                    Text("${tx.date} • ${tx.time} • ${tx.itemCount} item")
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatCurrency(tx.total), fontWeight = FontWeight.SemiBold)
                                    Text("Uang: ${formatCurrency(tx.cash)}")
                                }
                            }

                            if (tx.items.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    tx.items.take(4).forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${item.quantity}x ${item.name}")
                                            Text(formatCurrency(item.unitPrice * item.quantity))
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedTransaction = tx },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null)
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Detail")
                                }
                                OutlinedButton(
                                    onClick = {
                                        repository.cancelTransaction(tx.id.toLong())
                                        selectedTransaction = null
                                        onTransactionChanged()
                                        onBack()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Refund")
                                }
                            }
                        }
                    }
                }
            }

            if (selectedTransaction != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Preview struk", style = MaterialTheme.typography.titleMedium)
                        Text("#${selectedTransaction!!.id} • ${selectedTransaction!!.date} • ${selectedTransaction!!.time}")

                        selectedTransaction!!.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.quantity}x ${item.name}")
                                Text(formatCurrency(item.unitPrice * item.quantity))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total")
                            Text(formatCurrency(selectedTransaction!!.total), fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tunai")
                            Text(formatCurrency(selectedTransaction!!.cash))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kembali")
                            Text(formatCurrency(selectedTransaction!!.change))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val tx = selectedTransaction ?: return@OutlinedButton
                                    val paired = printer.pairedDevices()
                                    if (paired.isEmpty()) {
                                        return@OutlinedButton
                                    }
                                    val selectedPrinter = paired.first()
                                    printer.connect(selectedPrinter.address)
                                    printer.printReceipt(tx)
                                    selectedTransaction = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Print")
                            }

                            OutlinedButton(
                                onClick = { selectedTransaction = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Tutup preview")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsScreen(
    repository: PosRepository,
    transactions: List<TransactionRecord>,
    stats: ReportStats,
    context: Context,
    onBack: () -> Unit
) {
    val csvFile = remember { java.io.File(context.filesDir, "dfixer_report.csv") }
    var exportStatus by remember { mutableStateOf("Belum ada ekspor laporan") }
    var selectedRange by remember { mutableStateOf(ReportRange.TODAY) }

    val filteredTransactions = when (selectedRange) {
        ReportRange.TODAY -> transactions.filter { tx ->
            java.time.LocalDate.parse(tx.date).isEqual(java.time.LocalDate.now())
        }
        ReportRange.LAST_7_DAYS -> transactions.filter { tx ->
            val date = java.time.LocalDate.parse(tx.date)
            !date.isBefore(java.time.LocalDate.now().minusDays(6))
        }
        ReportRange.ALL -> transactions
    }

    val totalSales = filteredTransactions.sumOf { it.total }
    val totalTransactions = filteredTransactions.size
    val avgTicket = if (totalTransactions > 0) totalSales / totalTransactions else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportRange.values().forEach { range ->
                    val selected = selectedRange == range
                    OutlinedButton(
                        onClick = { selectedRange = range },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when (range) {
                                ReportRange.TODAY -> "Hari ini"
                                ReportRange.LAST_7_DAYS -> "7 hari"
                                ReportRange.ALL -> "Semua"
                            }
                        )
                    }
                }
            }

            val reportItems = listOf(
                "Total penjualan" to formatCurrency(totalSales),
                "Jumlah transaksi" to totalTransactions.toString(),
                "Rata-rata transaksi" to formatCurrency(avgTicket),
                "Penjualan hari ini" to formatCurrency(stats.todaySales),
                "Produk paling laris" to stats.bestSeller
            )

            reportItems.forEach { (label, value) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label)
                        Text(value, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ringkasan operasional", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("- Semua angka diambil dari transaksi yang tersimpan di Room database.")
                    Text("- Aturan stok otomatis dijaga saat simpan transaksi baru.")
                    Text("- Produk paling laris dihitung berdasarkan item terjual pada riwayat transaksi.")
                    Text("- Filter laporan dapat dipakai untuk hari ini, 7 hari terakhir, atau seluruh riwayat.")
                }
            }

            Button(
                onClick = {
                    exportStatus = "Mengekspor CSV..."
                    val exported = java.util.concurrent.Executors.newSingleThreadExecutor().submit {
                        kotlinx.coroutines.runBlocking {
                            repository.exportCsvReport(csvFile)
                        }
                    }
                    exported.get()
                    exportStatus = "CSV laporan tersimpan: ${csvFile.absolutePath}"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export CSV laporan")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(exportStatus, modifier = Modifier.padding(16.dp))
            }

            if (filteredTransactions.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Transaksi terbaru", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        filteredTransactions.take(3).forEach { tx ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${tx.id} • ${tx.time}")
                                Text(formatCurrency(tx.total), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScreen(
    repository: PosRepository,
    context: Context,
    onBack: () -> Unit
) {
    var status by remember { mutableStateOf("Siap backup data lokal") }
    val backupFile = remember { java.io.File(context.filesDir, "dfixer_backup.json") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Penyimpanan lokal", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("File backup disimpan di folder aplikasi dan bisa dipulihkan kapan saja.")
                    Text("Lokasi: ${backupFile.absolutePath}")
                }
            }

            Button(
                onClick = {
                    status = "Backup sedang dibuat..."
                    val path = backupFile.absolutePath
                    val saved = java.util.concurrent.Executors.newSingleThreadExecutor().submit {
                        kotlinx.coroutines.runBlocking {
                            repository.exportBackup(backupFile)
                        }
                    }
                    saved.get()
                    status = "Backup berhasil dibuat: $path"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export backup")
            }

            OutlinedButton(
                onClick = {
                    status = "Restore data sedang diproses..."
                    val restored = java.util.concurrent.Executors.newSingleThreadExecutor().submit {
                        kotlinx.coroutines.runBlocking {
                            repository.restoreBackup(backupFile)
                        }
                    }
                    restored.get()
                    status = "Data berhasil dipulihkan dari backup"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore backup")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(status, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierPurchaseScreen(
    repository: PosRepository,
    products: List<ProductEntity>,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var supplierName by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("10") }
    var unitCostText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(products) {
        if (selectedProductId == null && products.isNotEmpty()) {
            selectedProductId = products.first().id
        }
    }

    val selectedProduct = products.firstOrNull { it.id == selectedProductId } ?: products.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pembelian Supplier") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Catat stok masuk dari pemasok", style = MaterialTheme.typography.titleMedium)
                    Text("Setiap pembelian supplier otomatis masuk ke log stok sebagai pergerakan IN.")
                }
            }

            if (products.isEmpty()) {
                Text("Belum ada produk untuk di-restok.")
            } else {
                Text("Pilih produk")
                OutlinedTextField(
                    value = selectedProduct?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                OutlinedButton(
                    onClick = {
                        val nextProduct = products[(products.indexOf(selectedProduct) + 1).coerceAtMost(products.size - 1)]
                        selectedProductId = nextProduct.id
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pilih produk lain")
                }
            }

            OutlinedTextField(
                value = supplierName,
                onValueChange = { supplierName = it },
                label = { Text("Nama supplier") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit) },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = unitCostText,
                    onValueChange = { unitCostText = it.filter(Char::isDigit) },
                    label = { Text("Harga/unit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Catatan pembelian") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val quantity = quantityText.toIntOrNull() ?: 0
                    val unitCost = unitCostText.toLongOrNull() ?: 0L
                    val product = selectedProduct

                    when {
                        product == null -> status = "Belum ada produk yang dipilih"
                        supplierName.isBlank() -> status = "Nama supplier wajib diisi"
                        quantity <= 0 -> status = "Jumlah pembelian harus lebih dari 0"
                        else -> {
                            repository.recordSupplierPurchase(
                                productId = product.id,
                                quantity = quantity,
                                supplier = supplierName,
                                unitCost = unitCost,
                                notes = notes
                            )
                            status = "Pembelian ${product.name} berhasil dicatat"
                            onSaved()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan pembelian")
            }

            if (status != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        status ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterScreen(
    context: Context,
    transactions: List<TransactionRecord>,
    onBack: () -> Unit
) {
    val printer = remember { BluetoothReceiptPrinter(context.applicationContext) }
    val pairedDevices = remember { printer.pairedDevices() }
    var selectedAddress by remember { mutableStateOf(pairedDevices.firstOrNull()?.address ?: "") }
    var status by remember { mutableStateOf("Siap mencetak struk ke printer Bluetooth") }

    LaunchedEffect(pairedDevices) {
        if (selectedAddress.isBlank() && pairedDevices.isNotEmpty()) {
            selectedAddress = pairedDevices.first().address
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Printer Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Printer Bluetooth terpasang", style = MaterialTheme.typography.titleMedium)
                    Text("Pilih perangkat yang sudah pair dan cetak transaksi terakhir dari app.")
                }
            }

            if (pairedDevices.isEmpty()) {
                Text("Belum ada printer Bluetooth yang tersedia. Pasangkan printer thermal di sistem Bluetooth Android Anda.")
            } else {
                pairedDevices.forEach { device ->
                    val isSelected = device.address == selectedAddress
                    OutlinedButton(
                        onClick = { selectedAddress = device.address },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (isSelected) androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(device.name)
                    }
                }
            }

            Button(
                onClick = {
                    val device = pairedDevices.firstOrNull { it.address == selectedAddress }
                    if (device == null) {
                        status = "Pilih printer terlebih dahulu"
                        return@Button
                    }

                    val connected = printer.connect(device.address)
                    if (!connected) {
                        status = "Gagal menghubungkan ke printer: ${device.name}"
                        return@Button
                    }

                    val tx = transactions.firstOrNull()
                    if (tx == null) {
                        status = "Belum ada transaksi yang bisa dicetak"
                        return@Button
                    }

                    val success = printer.printReceipt(tx)
                    status = if (success) {
                        "Struk berhasil dikirim ke ${device.name}"
                    } else {
                        "Gagal mengirim struk ke ${device.name}"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cetak transaksi terakhir")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(status, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductManagementScreen(
    repository: PosRepository,
    onBack: () -> Unit,
    onProductsChanged: (List<ProductEntity>) -> Unit = {}
) {
    var productName by remember { mutableStateOf("") }
    var productPrice by remember { mutableStateOf("0") }
    var productStock by remember { mutableStateOf("0") }
    var productCategory by remember { mutableStateOf("Umum") }
    var productQuery by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        products = repository.getProducts()
        onProductsChanged(products)
    }

    val filteredProducts = products.filter {
        val query = productQuery.trim().lowercase()
        if (query.isEmpty()) true else it.name.lowercase().contains(query) || it.category.lowercase().contains(query)
    }

    fun refreshProducts() {
        products = repository.getProducts()
        onProductsChanged(products)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manajemen Produk") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val lowStockCount = products.count { it.stock <= 5 }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Inventaris", style = MaterialTheme.typography.titleMedium)
                        Text("${products.size} produk aktif")
                    }
                    Text(
                        "$lowStockCount stok menipis",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedTextField(
                value = productQuery,
                onValueChange = { productQuery = it },
                label = { Text("Cari produk / kategori") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text("Nama produk") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = productCategory,
                onValueChange = { productCategory = it },
                label = { Text("Kategori") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = productPrice,
                    onValueChange = { productPrice = it.filter(Char::isDigit) },
                    label = { Text("Harga") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = productStock,
                    onValueChange = { productStock = it.filter(Char::isDigit) },
                    label = { Text("Stok") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    if (productName.isBlank()) {
                        status = "Nama produk wajib diisi"
                    } else {
                        val price = productPrice.toLongOrNull() ?: 0L
                        val stock = productStock.toIntOrNull() ?: 0

                        if (selectedProductId != null) {
                            repository.updateProduct(selectedProductId!!, productName, price, stock, productCategory)
                            status = "Produk berhasil diperbarui"
                        } else {
                            repository.addProduct(productName, price, stock, productCategory)
                            status = "Produk berhasil ditambahkan"
                        }

                        productName = ""
                        productPrice = "0"
                        productStock = "0"
                        productCategory = "Umum"
                        selectedProductId = null
                        refreshProducts()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedProductId != null) "Simpan perubahan" else "Tambah produk")
            }

            if (status != null) {
                Card {
                    Text(
                        status ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Daftar produk", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredProducts.isEmpty()) {
                        Text("Belum ada produk yang cocok dengan pencarian.")
                    } else {
                        filteredProducts.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    Text("${item.category} • ${formatCurrency(item.price)} • Stok ${item.stock}")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedProductId = item.id
                                            productName = item.name
                                            productPrice = item.price.toString()
                                            productStock = item.stock.toString()
                                            productCategory = item.category.ifBlank { "Umum" }
                                            status = "Mode edit produk aktif"
                                        }
                                    ) {
                                        Text("Edit")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val nextStock = item.stock + 10
                                            repository.adjustStock(item.id, 10, "Restok cepat +10")
                                            refreshProducts()
                                            status = "Stok ${item.name} ditambah menjadi ${item.stock + 10}"
                                        }
                                    ) {
                                        Text("Restok")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (item.stock > 0) {
                                                repository.adjustStock(item.id, -5, "Penjualan stok manual")
                                                refreshProducts()
                                                status = "Stok ${item.name} dikurangi 5 unit"
                                            } else {
                                                status = "Stok ${item.name} sudah habis"
                                            }
                                        }
                                    ) {
                                        Text("Keluar")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            repository.deleteProduct(item.id)
                                            if (selectedProductId == item.id) {
                                                selectedProductId = null
                                                productName = ""
                                                productPrice = "0"
                                                productStock = "0"
                                                productCategory = "Umum"
                                            }
                                            refreshProducts()
                                            status = "Produk berhasil dihapus"
                                        }
                                    ) {
                                        Text("Hapus")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Catatan", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("- Produk disimpan di Room database lokal.")
                    Text("- Cari, edit, dan hapus item langsung dari daftar produk.")
                    Text("- Langkah berikutnya: transaksi tersimpan di database dan laporan otomatis.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockLogScreen(
    movements: List<StockMovement>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Stok") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (movements.isEmpty()) {
                Text("Belum ada log stok.")
            } else {
                movements.forEach { movement ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(movement.productName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    movement.type.uppercase(),
                                    color = if (movement.type == "in") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Text("Qty: ${movement.quantity} • ${movement.reason}")
                            Text(
                                java.time.Instant.ofEpochMilli(movement.createdAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDateTime()
                                    .toString()
                            )
                        }
                    }
                }
            }
        }
    }
}

