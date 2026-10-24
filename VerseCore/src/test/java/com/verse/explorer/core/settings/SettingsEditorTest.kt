package com.verse.explorer.core.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsEditorTest {
    private class FakeStore : SettingsStore {
        var saved = SettingsUiState(renderer = "plugin:custom", maxMemory = 3072f)
        var saves = 0
        var advanced: AdvancedSetting? = null
        override fun load() = saved
        override fun save(state: SettingsUiState) { saved = state; saves++ }
        override fun reset() = SettingsUiState().also { saved = it }
        override fun persistAdvanced(field: AdvancedSetting, state: SettingsUiState) { advanced = field }
    }

    @Test fun editingDraftDoesNotWriteUntilSave() {
        val store = FakeStore()
        val editor = SettingsEditor(store)
        assertEquals("plugin:custom", editor.state.value.renderer)
        editor.onEvent(SettingsEvent.Edit { it.copy(maxMemory = 4096f) })
        assertEquals(0, store.saves)
        assertEquals(3072f, store.saved.maxMemory)
        editor.onEvent(SettingsEvent.Save)
        assertEquals(4096f, SettingsEditor(store).state.value.maxMemory)
    }

    @Test fun resetReloadsPersistedDefaultsAndAdvancedWritesStayExplicit() {
        val store = FakeStore()
        val editor = SettingsEditor(store)
        editor.onEvent(SettingsEvent.Edit { it.copy(graphicsBackend = "vulkan") })
        assertNull(store.advanced)
        editor.onEvent(SettingsEvent.PersistAdvanced(AdvancedSetting.GRAPHICS_BACKEND))
        assertEquals(AdvancedSetting.GRAPHICS_BACKEND, store.advanced)
        assertEquals(0, store.saves)
        editor.onEvent(SettingsEvent.Reset)
        assertEquals(SettingsUiState(), editor.state.value)
        assertEquals(editor.state.value, SettingsEditor(store).state.value)
    }
}
