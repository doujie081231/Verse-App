package com.verse.explorer.controller.observable

import com.verse.explorer.controller.data.ControlLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ObservableControlLayout(initial: ControlLayout) {
    private val _layout = MutableStateFlow(initial)
    val layout: StateFlow<ControlLayout> = _layout.asStateFlow()

    private val _isGrabbing = MutableStateFlow(false)
    val isGrabbing: StateFlow<Boolean> = _isGrabbing.asStateFlow()

    private val _opacity = MutableStateFlow(0.7f)
    val opacity: StateFlow<Float> = _opacity.asStateFlow()

    private val _isVisible = MutableStateFlow(true)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    fun updateLayout(layout: ControlLayout) { _layout.value = layout }
    fun setGrabbing(grabbing: Boolean) { _isGrabbing.value = grabbing }
    fun setOpacity(opacity: Float) { _opacity.value = opacity }
    fun setVisible(visible: Boolean) { _isVisible.value = visible }
}
