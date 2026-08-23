package com.example.lapbot.ui.main

import com.example.lapbot.data.ConnectionStatus
import com.example.lapbot.data.TimingRepository
import com.example.lapbot.data.TimingUiState
import com.example.lapbot.data.ReconnectPolicy
import com.example.lapbot.data.ToneSettings
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun connect_isForwardedToRepository() {
    val repository = FakeTimingRepository()
    val viewModel = MainScreenViewModel(repository)

    viewModel.connect()

    assertEquals(ConnectionStatus.Connecting, viewModel.uiState.value.status)
  }

  @Test
  fun autoReconnect_isOffByDefault() {
    val viewModel = MainScreenViewModel(FakeTimingRepository())

    assertEquals(false, viewModel.uiState.value.autoReconnect)
  }
}

private class FakeTimingRepository : TimingRepository {
  override val state = MutableStateFlow(TimingUiState())

  override fun connect() {
    state.value = state.value.copy(status = ConnectionStatus.Connecting)
  }

  override fun disconnect() {
    state.value = state.value.copy(status = ConnectionStatus.Disconnected)
  }

  override fun setAutoReconnect(enabled: Boolean) {
    state.value = state.value.copy(autoReconnect = enabled)
  }

  override fun setTailLimit(limit: Int) {
    state.value = state.value.copy(tailLimit = limit, jsonTail = state.value.jsonTail.takeLast(limit))
  }

  override fun setReconnectPolicy(policy: ReconnectPolicy) {
    state.value = state.value.copy(reconnectPolicy = policy)
  }

  override fun setSelectedKartNumber(kartNumber: String?) {
    state.value = state.value.copy(selectedKartNumber = kartNumber)
  }

  override fun setMetricsSinceLap(lap: Int?) {
    state.value = state.value.copy(metricsSinceLap = lap)
  }

  override fun setAudioAnnouncements(enabled: Boolean) {
    state.value = state.value.copy(audioAnnouncements = enabled)
  }

  override fun setToneSettings(settings: ToneSettings) {
    state.value = state.value.copy(toneSettings = settings)
  }

  override fun playTestTones() = Unit

  override fun close() = Unit
}
