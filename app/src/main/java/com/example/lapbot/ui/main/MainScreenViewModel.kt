package com.example.lapbot.ui.main

import androidx.lifecycle.ViewModel
import com.example.lapbot.data.TimingRepository
import com.example.lapbot.data.TimingUiState
import com.example.lapbot.data.ReconnectPolicy
import com.example.lapbot.data.ToneSettings
import kotlinx.coroutines.flow.StateFlow

class MainScreenViewModel(private val repository: TimingRepository) : ViewModel() {
  val uiState: StateFlow<TimingUiState> = repository.state

  fun connect() = repository.connect()

  fun disconnect() = repository.disconnect()

  fun setAutoReconnect(enabled: Boolean) = repository.setAutoReconnect(enabled)

  fun setTailLimit(limit: Int) = repository.setTailLimit(limit)

  fun setReconnectPolicy(policy: ReconnectPolicy) = repository.setReconnectPolicy(policy)

  fun setSelectedKartNumber(kartNumber: String?) = repository.setSelectedKartNumber(kartNumber)

  fun setMetricsSinceLap(lap: Int?) = repository.setMetricsSinceLap(lap)

  fun setAudioAnnouncements(enabled: Boolean) = repository.setAudioAnnouncements(enabled)

  fun setToneSettings(settings: ToneSettings) = repository.setToneSettings(settings)

  fun playTestTones() = repository.playTestTones()

  override fun onCleared() {
    repository.close()
  }
}
