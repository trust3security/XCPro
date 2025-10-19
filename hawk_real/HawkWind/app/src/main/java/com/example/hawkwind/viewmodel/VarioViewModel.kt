package com.example.hawkwind.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.hawkwind.model.VarioUiState
import com.example.hawkwind.repo.HawkRepo
class VarioViewModel(mode: String) : ViewModel() {
  private val repo = HawkRepo(mode)
  private val _state = MutableStateFlow(VarioUiState())
  val state: StateFlow<VarioUiState> = _state
  init { viewModelScope.launch { repo.vario().collect { _state.value = it } } }
}
