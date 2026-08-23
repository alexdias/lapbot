package com.example.lapbot.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.lapbot.service.TimingStreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal object TimingServiceState {
  val mutableState = MutableStateFlow(TimingUiState())
  var running: Boolean = false
}

class TimingServiceRepository(context: Context) : TimingRepository {
  private val applicationContext = context.applicationContext
  override val state: StateFlow<TimingUiState> = TimingServiceState.mutableState

  override fun connect() {
    ContextCompat.startForegroundService(
      applicationContext,
      serviceIntent(TimingStreamService.ACTION_CONNECT),
    )
  }

  override fun disconnect() {
    if (TimingServiceState.running) {
      applicationContext.startService(serviceIntent(TimingStreamService.ACTION_DISCONNECT))
    } else {
      TimingServiceState.mutableState.value =
        TimingServiceState.mutableState.value.copy(status = ConnectionStatus.Disconnected, error = null)
    }
  }

  override fun setAutoReconnect(enabled: Boolean) {
    TimingServiceState.mutableState.value = TimingServiceState.mutableState.value.copy(autoReconnect = enabled)
    if (TimingServiceState.running) {
      applicationContext.startService(
        serviceIntent(TimingStreamService.ACTION_SET_AUTO_RECONNECT)
          .putExtra(TimingStreamService.EXTRA_AUTO_RECONNECT, enabled),
      )
    }
  }

  override fun setTailLimit(limit: Int) {
    val boundedLimit = limit.coerceIn(5, 100)
    TimingServiceState.mutableState.value =
      TimingServiceState.mutableState.value.copy(
        tailLimit = boundedLimit,
        jsonTail = TimingServiceState.mutableState.value.jsonTail.takeLast(boundedLimit),
      )
    if (TimingServiceState.running) {
      applicationContext.startService(
        serviceIntent(TimingStreamService.ACTION_SET_TAIL_LIMIT)
          .putExtra(TimingStreamService.EXTRA_TAIL_LIMIT, boundedLimit),
      )
    }
  }

  override fun setReconnectPolicy(policy: ReconnectPolicy) {
    TimingServiceState.mutableState.value = TimingServiceState.mutableState.value.copy(reconnectPolicy = policy)
    if (TimingServiceState.running) {
      applicationContext.startService(
        serviceIntent(TimingStreamService.ACTION_SET_RECONNECT_POLICY)
          .putExtra(TimingStreamService.EXTRA_INITIAL_DELAY_MS, policy.initialDelayMs)
          .putExtra(TimingStreamService.EXTRA_MAX_DELAY_MS, policy.maxDelayMs)
          .putExtra(TimingStreamService.EXTRA_GIVE_UP_AFTER_MS, policy.giveUpAfterMs),
      )
    }
  }

  override fun setSelectedKartNumber(kartNumber: String?) {
    TimingServiceState.mutableState.value =
      TimingServiceState.mutableState.value.copy(selectedKartNumber = canonicalKartNumber(kartNumber))
  }

  override fun setMetricsSinceLap(lap: Int?) {
    TimingServiceState.mutableState.value = TimingServiceState.mutableState.value.copy(metricsSinceLap = lap)
  }

  override fun setAudioAnnouncements(enabled: Boolean) {
    TimingServiceState.mutableState.value = TimingServiceState.mutableState.value.copy(audioAnnouncements = enabled)
  }

  override fun setToneSettings(settings: ToneSettings) {
    val bounded =
      settings.copy(
        referenceDurationMs = settings.referenceDurationMs.coerceIn(50, 1_000),
        lapDurationMs = settings.lapDurationMs.coerceIn(50, 1_000),
        sectorDurationMs = settings.sectorDurationMs.coerceIn(50, 1_000),
      )
    TimingServiceState.mutableState.value = TimingServiceState.mutableState.value.copy(toneSettings = bounded)
    if (TimingServiceState.running) {
      applicationContext.startService(
        serviceIntent(TimingStreamService.ACTION_SET_TONE_SETTINGS)
          .putExtra(TimingStreamService.EXTRA_TONE_METRIC, bounded.metric.name)
          .putExtra(TimingStreamService.EXTRA_REFERENCE_DURATION_MS, bounded.referenceDurationMs)
          .putExtra(TimingStreamService.EXTRA_LAP_DURATION_MS, bounded.lapDurationMs)
          .putExtra(TimingStreamService.EXTRA_SECTOR_DURATION_MS, bounded.sectorDurationMs),
      )
    }
  }

  override fun playTestTones() {
    if (TimingServiceState.running) {
      applicationContext.startService(serviceIntent(TimingStreamService.ACTION_PLAY_TEST_TONES))
    }
  }

  override fun close() = Unit

  private fun serviceIntent(action: String) =
    Intent(applicationContext, TimingStreamService::class.java).setAction(action)
}
