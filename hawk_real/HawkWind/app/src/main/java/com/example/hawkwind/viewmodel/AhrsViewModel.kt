package com.example.hawkwind.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.hawkwind.model.AhrsUiState
import com.example.hawkwind.repo.HawkRepo
class AhrsViewModel(mode: String) : ViewModel() {
  private val repo = HawkRepo(mode)
  private val _state = MutableStateFlow(AhrsUiState())
  val state: StateFlow<AhrsUiState> = _state
  init { viewModelScope.launch { repo.ahrs().collect { _state.value = it } } }
}
