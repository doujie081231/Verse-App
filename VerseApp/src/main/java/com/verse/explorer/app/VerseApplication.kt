package com.verse.explorer.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.verse.explorer.core.account.AccountManager
import com.verse.explorer.core.account.AuthlibAuthenticator
import com.verse.explorer.core.account.MicrosoftAuthenticator
import com.verse.explorer.core.context.Contexts
import com.verse.explorer.core.database.AppDatabase
import com.verse.explorer.core.java.JavaRuntimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VerseApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Contexts.initialize(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "verse_explorer.db")
            .fallbackToDestructiveMigration()
            .build()
        accountManager = AccountManager(database.accountDao())
        microsoftAuthenticator = MicrosoftAuthenticator()
        authlibAuthenticator = AuthlibAuthenticator()

        initAllJreRuntimes()
    }

    private fun initAllJreRuntimes() {
        applicationScope.launch {
            val versionKeys = listOf("java8", "java11", "java17", "java21", "java25")
            Log.i("VerseApp", "开始自动初始化所有JRE运行时: ${versionKeys.joinToString()}")

            for (key in versionKeys) {
                try {
                    val result = JavaRuntimeManager.installJre(this@VerseApplication, key) { progress ->
                        Log.d("VerseApp", "JRE $key: ${progress.status} ${progress.progress}% - ${progress.message}")
                    }
                    result.fold(
                        onSuccess = { dir ->
                            Log.i("VerseApp", "JRE $key 初始化完成: ${dir.absolutePath}")
                        },
                        onFailure = { e ->
                            Log.w("VerseApp", "JRE $key 初始化失败: ${e.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e("VerseApp", "JRE $key 初始化异常: ${e.message}")
                }
            }

            Log.i("VerseApp", "所有JRE运行时初始化流程结束")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.coroutineContext[Job]?.cancel()
    }

    companion object {
        lateinit var instance: VerseApplication
            private set
        lateinit var database: AppDatabase
            private set
        lateinit var accountManager: AccountManager
            private set
        lateinit var microsoftAuthenticator: MicrosoftAuthenticator
            private set
        lateinit var authlibAuthenticator: AuthlibAuthenticator
            private set
    }
}
