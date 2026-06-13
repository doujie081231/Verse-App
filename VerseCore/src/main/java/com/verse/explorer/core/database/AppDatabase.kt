package com.verse.explorer.core.database

import androidx.room.*
import com.verse.explorer.core.account.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "access_token") val accessToken: String = "",
    @ColumnInfo(name = "refresh_token") val refreshToken: String = "",
    @ColumnInfo(name = "account_type") val accountType: AccountType = AccountType.OFFLINE,
    @ColumnInfo(name = "skin_url") val skinUrl: String? = null,
    @ColumnInfo(name = "cape_url") val capeUrl: String? = null,
    @ColumnInfo(name = "server_url") val serverUrl: String? = null,
    @ColumnInfo(name = "login_username") val loginUsername: String? = null,
    @ColumnInfo(name = "selected") val selected: Boolean = false,
    @ColumnInfo(name = "create_time") val createTime: Long = System.currentTimeMillis()
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY create_time DESC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE selected = 1 LIMIT 1")
    suspend fun getSelected(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Int): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("UPDATE accounts SET selected = 0")
    suspend fun clearSelection()

    @Query("UPDATE accounts SET selected = 1 WHERE id = :id")
    suspend fun setSelected(id: Int)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Entity(tableName = "auth_servers")
data class AuthServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "server_url") val serverUrl: String,
    @ColumnInfo(name = "register_url") val registerUrl: String? = null,
    @ColumnInfo(name = "is_yggdrasil") val isYggdrasil: Boolean = true,
    @ColumnInfo(name = "create_time") val createTime: Long = System.currentTimeMillis()
)

@Dao
interface AuthServerDao {
    @Query("SELECT * FROM auth_servers ORDER BY create_time DESC")
    suspend fun getAll(): List<AuthServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: AuthServerEntity): Long

    @Delete
    suspend fun delete(server: AuthServerEntity)
}

@Database(
    entities = [AccountEntity::class, AuthServerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun authServerDao(): AuthServerDao
}
