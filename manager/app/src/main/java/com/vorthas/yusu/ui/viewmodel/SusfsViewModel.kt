package com.vorthas.yusu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorthas.yusu.data.susfs.SusfsRepository
import com.vorthas.yusu.data.susfs.SusfsScriptState
import com.vorthas.yusu.data.susfs.SusfsStatus
import com.vorthas.yusu.ui.screen.susfs.SusfsConfigUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SusfsUiState(
    val status: SusfsStatus? = null,
    val configs: List<SusfsConfigUi> = emptyList(),
    val scripts: List<SusfsScriptState> = emptyList(),
    val rulesExpanded: Boolean = false,
    val scriptsExpanded: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class SusfsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SusfsUiState())
    val uiState: StateFlow<SusfsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = SusfsRepository.status()
            val configs = SusfsRepository.editableConfigs().map { name ->
                SusfsConfigUi(name, SusfsRepository.readActiveLines(name))
            }
            val scripts = SusfsRepository.scriptStates()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(status = status, configs = configs, scripts = scripts) }
            }
        }
    }

    fun setMessage(message: String?) {
        _uiState.update { it.copy(message = message) }
    }

    fun toggleRules() {
        _uiState.update { it.copy(rulesExpanded = !it.rulesExpanded) }
    }

    fun toggleScripts() {
        _uiState.update { it.copy(scriptsExpanded = !it.scriptsExpanded) }
    }

    fun setConfigValue(name: String, value: String) {
        _uiState.update { state ->
            state.copy(configs = state.configs.map { if (it.name == name) it.copy(value = value) else it })
        }
    }

    private fun runAction(block: () -> Result<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _uiState.update { it.copy(busy = true) } }
            val result = runCatching { block() }.getOrElse { Result.failure(it) }
            val message = result.getOrElse { it.message ?: "Unknown error" }
            withContext(Dispatchers.Main) { _uiState.update { it.copy(busy = false, message = message) } }
            refresh()
        }
    }

    fun loadLkm() = runAction { SusfsRepository.loadLkm() }

    fun apply() = runAction { SusfsRepository.applyNow() }

    fun setToggle(key: String, enabled: Boolean) =
        runAction { SusfsRepository.setToggle(key, enabled) }

    fun saveConfig(name: String) {
        val value = _uiState.value.configs.firstOrNull { it.name == name }?.value.orEmpty()
        runAction { SusfsRepository.saveActiveLines(name, value) }
    }

    fun setScriptStage(name: String, stage: String, enabled: Boolean) =
        runAction { SusfsRepository.setScriptStage(name, stage, enabled).map { "Script list updated" } }

    fun resetConfigs() = runAction { SusfsRepository.resetConfigs().map { "Configs reset" } }
}
