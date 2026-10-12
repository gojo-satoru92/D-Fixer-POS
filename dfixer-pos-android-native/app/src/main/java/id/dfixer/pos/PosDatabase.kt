package id.dfixer.pos

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val price: Long,
    val stock: Int,
    val category: String = "Umum"
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long,
    val total: Long,
    val cash: Long,
    val change: Long,
    val status: String = "completed"
)

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"]) ]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long
)

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getProducts(): List<ProductEntity>

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteById(productId: Long)

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertItems(items: List<TransactionItemEntity>)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    suspend fun getTransactionsWithItems(): List<TransactionWithItems>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    suspend fun getTransactions(): List<TransactionEntity>

    @Query("DELETE FROM transaction_items")
    suspend fun clearItems()

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun deleteItemsForTransaction(transactionId: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransaction(transactionId: Long)

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun getItemsForTransaction(transactionId: Long): List<TransactionItemEntity>
}

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: String,
    val quantity: Int,
    val reason: String,
    val createdAt: Long
)

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC")
    suspend fun getMovements(): List<StockMovementEntity>

    @Query("DELETE FROM stock_movements")
    suspend fun clear()
}

data class TransactionWithItems(
    @androidx.room.Embedded val transaction: TransactionEntity,
    @Relation(parentColumn = "id", entityColumn = "transactionId")
    val items: List<TransactionItemEntity>
)

@Database(
    entities = [ProductEntity::class, TransactionEntity::class, TransactionItemEntity::class, StockMovementEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stockMovementDao(): StockMovementDao

    companion object {
        @Volatile
        private var INSTANCE: PosDatabase? = null

        fun getInstance(context: Context): PosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PosDatabase::class.java,
                    "dfixer_pos.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
