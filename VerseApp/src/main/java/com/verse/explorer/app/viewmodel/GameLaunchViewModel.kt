package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.verse.explorer.app.launch.GameLaunchController

class GameLaunchViewModel(application: Application) : AndroidViewModel(application) {
    val controller = GameLaunchController(application)
    override fun onCleared() { controller.destroy() }
}
