package com.verse.explorer.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** 页签共享 Activity 的 ViewModelStore，Activity 销毁时仍会正常调用 onCleared。 */
@Composable
fun <T : AndroidViewModel> activityViewModel(
    modelClass: Class<T>,
    key: String = modelClass.name,
    factory: ViewModelProvider.Factory? = null
): T {
    val context = LocalContext.current
    val owner = remember(context) { context.requireActivity() }
    val resolvedFactory = factory ?: remember(owner) { ViewModelProvider.AndroidViewModelFactory(owner.application) }
    return remember(owner, key, resolvedFactory) { ViewModelProvider(owner, resolvedFactory)[key, modelClass] }
}

@Composable
fun resourceViewModel(projectType: String): ResourceViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application, projectType) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == ResourceViewModel::class.java)
                return ResourceViewModel(application, projectType) as T
            }
        }
    }
    return activityViewModel(ResourceViewModel::class.java, "resources:$projectType", factory)
}

private tailrec fun Context.requireActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.requireActivity()
    else -> error("ViewModel owner must be a ComponentActivity")
}

@Composable
fun modDetailViewModel(projectId: String, source: String): ModDetailViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application, projectId, source) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == ModDetailViewModel::class.java)
                return ModDetailViewModel(application, projectId, source) as T
            }
        }
    }
    return activityViewModel(ModDetailViewModel::class.java, "detail:$source:$projectId", factory)
}
