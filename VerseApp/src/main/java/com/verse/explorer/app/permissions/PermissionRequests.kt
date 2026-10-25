package com.verse.explorer.app.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.verse.explorer.app.appContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class AppPermissionRequest { NOTIFICATIONS }

/** 应用只发布权限意图，不保存前台 Activity 引用。 */
class PermissionRequests {
    private val requests = Channel<AppPermissionRequest>(Channel.CONFLATED)
    val events = requests.receiveAsFlow()
    fun requestNotifications() { requests.trySend(AppPermissionRequest.NOTIFICATIONS) }
}

fun ComponentActivity.observeAppPermissionRequests() {
    val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            appContainer.permissions.events.collect { request ->
                if (request == AppPermissionRequest.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@observeAppPermissionRequests, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
