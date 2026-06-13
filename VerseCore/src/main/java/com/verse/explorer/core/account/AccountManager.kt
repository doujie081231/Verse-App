package com.verse.explorer.core.account

import com.verse.explorer.core.database.AccountDao
import com.verse.explorer.core.database.AccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class AccountManager(private val accountDao: AccountDao) {

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    suspend fun loadAccounts() = withContext(Dispatchers.IO) {
        val entities = accountDao.getAll()
        val accountList = entities.map { it.toAccount() }
        _accounts.value = accountList

        val selected = entities.find { it.selected }
        _selectedAccount.value = selected?.toAccount()
    }

    suspend fun createOfflineAccount(username: String): Account = withContext(Dispatchers.IO) {
        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()).toString().replace("-", "")
        val entity = AccountEntity(
            username = username,
            uuid = uuid,
            accessToken = "0",
            accountType = AccountType.OFFLINE,
            selected = true
        )
        accountDao.clearSelection()
        val id = accountDao.insert(entity).toInt()
        val account = Account(
            id = id,
            username = username,
            uuid = uuid,
            accessToken = "0",
            type = AccountType.OFFLINE,
            selected = true
        )
        loadAccounts()
        account
    }

    suspend fun addAccount(account: Account) = withContext(Dispatchers.IO) {
        val entity = account.toEntity()
        if (account.selected) {
            accountDao.clearSelection()
        }
        accountDao.insert(entity)
        loadAccounts()
    }

    suspend fun removeAccount(accountId: Int) = withContext(Dispatchers.IO) {
        accountDao.deleteById(accountId)
        loadAccounts()
    }

    suspend fun selectAccount(accountId: Int) = withContext(Dispatchers.IO) {
        accountDao.clearSelection()
        accountDao.setSelected(accountId)
        loadAccounts()
    }

    suspend fun updateAccount(account: Account) = withContext(Dispatchers.IO) {
        accountDao.update(account.toEntity())
        loadAccounts()
    }

    private fun AccountEntity.toAccount() = Account(
        id = id,
        username = username,
        uuid = uuid,
        accessToken = accessToken,
        refreshToken = refreshToken,
        type = accountType,
        skinUrl = skinUrl,
        capeUrl = capeUrl,
        serverUrl = serverUrl,
        loginUsername = loginUsername,
        selected = selected
    )

    private fun Account.toEntity() = AccountEntity(
        id = if (id == 0) 0 else id,
        username = username,
        uuid = uuid,
        accessToken = accessToken,
        refreshToken = refreshToken,
        accountType = type,
        skinUrl = skinUrl,
        capeUrl = capeUrl,
        serverUrl = serverUrl,
        loginUsername = loginUsername,
        selected = selected
    )
}
