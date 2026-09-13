package com.promptforge.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.promptforge.AppContainer
import com.promptforge.ui.screens.BuilderViewModel
import com.promptforge.ui.screens.HomeViewModel
import com.promptforge.ui.screens.LibraryViewModel
import com.promptforge.ui.screens.PlaygroundViewModel
import com.promptforge.ui.screens.SettingsViewModel
import com.promptforge.ui.screens.TemplatesViewModel

/**
 * Single factory wiring all ViewModels to the container.
 */
class ForgeVmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(c) as T
        BuilderViewModel::class.java -> BuilderViewModel(c) as T
        TemplatesViewModel::class.java -> TemplatesViewModel(c) as T
        PlaygroundViewModel::class.java -> PlaygroundViewModel(c) as T
        LibraryViewModel::class.java -> LibraryViewModel(c) as T
        SettingsViewModel::class.java -> SettingsViewModel(c) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
