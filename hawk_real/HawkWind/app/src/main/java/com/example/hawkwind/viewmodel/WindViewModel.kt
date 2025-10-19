package com.example.hawkwind.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.hawkwind.model.*
import com.example.hawkwind.repo.HawkRepo
class WindViewModel(mode: String) : ViewModel() {
  private val repo = HawkRepo(mode)
  private val _state = MutableStateFlow(WindUiState())
  val state: StateFlow<WindUiState> = _state
  init { viewModelScope.launch { repo.wind().collect { _state.value = it } } }
}
