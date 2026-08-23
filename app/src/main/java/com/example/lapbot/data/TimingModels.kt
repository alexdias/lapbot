package com.example.lapbot.data

import kotlinx.coroutines.flow.StateFlow

enum class ConnectionStatus {
  Disconnected,
  Connecting,
  Connected,
  Reconnecting,
}

data class ReconnectPolicy(
  val initialDelayMs: Long = 500,
  val maxDelayMs: Long = 60_000,
  val giveUpAfterMs: Long = 10 * 60_000,
)

enum class ToneMetric {
  PreviousLap,
  DriverBest,
  TheoreticalBest,
  RaceBest,
  BestRecent,
}

data class ToneSettings(
  val metric: ToneMetric = ToneMetric.DriverBest,
  val referenceDurationMs: Int = 200,
  val lapDurationMs: Int = 300,
  val sectorDurationMs: Int = 150,
)

data class TimingRow(
  val id: String,
  val number: String = "",
  val name: String = "",
  val position: Int? = null,
  val lap: Int? = null,
  val sector1Ms: Long? = null,
  val sector2Ms: Long? = null,
  val sector3Ms: Long? = null,
  val lapMs: Long? = null,
  val recentCompletedLap: Int? = null,
  val recentCompletedLapMs: Long? = null,
  val recentCompletedSector1Ms: Long? = null,
  val recentCompletedSector2Ms: Long? = null,
  val recentCompletedSector3Ms: Long? = null,
  val bestLap: Int? = null,
  val bestLapMs: Long? = null,
  val bestLapSector1Ms: Long? = null,
  val bestLapSector2Ms: Long? = null,
  val bestLapSector3Ms: Long? = null,
  val bestSector1Ms: Long? = null,
  val bestSector2Ms: Long? = null,
  val bestSector3Ms: Long? = null,
  val theoreticalBestMs: Long? = null,
  val lapHistory: List<LapHistoryEntry> = emptyList(),
  val lapTimeline: List<LapTimelineEntry> = emptyList(),
)

data class LapHistoryEntry(
  val lap: Int,
  val lapMs: Long,
  val sector1Ms: Long? = null,
  val sector2Ms: Long? = null,
  val sector3Ms: Long? = null,
)

data class LapTimelineEntry(
  val lap: Int,
  val lapMs: Long? = null,
  val sector1Ms: Long? = null,
  val sector2Ms: Long? = null,
  val sector3Ms: Long? = null,
)

data class TimingUiState(
  val status: ConnectionStatus = ConnectionStatus.Disconnected,
  val autoReconnect: Boolean = false,
  val rows: List<TimingRow> = emptyList(),
  val jsonTail: List<String> = emptyList(),
  val tailLimit: Int = 20,
  val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
  val selectedKartNumber: String? = null,
  val metricsSinceLap: Int? = null,
  val audioAnnouncements: Boolean = false,
  val toneSettings: ToneSettings = ToneSettings(),
  val error: String? = null,
)

interface TimingRepository : AutoCloseable {
  val state: StateFlow<TimingUiState>

  fun connect()

  fun disconnect()

  fun setAutoReconnect(enabled: Boolean)

  fun setTailLimit(limit: Int)

  fun setReconnectPolicy(policy: ReconnectPolicy)

  fun setSelectedKartNumber(kartNumber: String?)

  fun setMetricsSinceLap(lap: Int?)

  fun setAudioAnnouncements(enabled: Boolean)

  fun setToneSettings(settings: ToneSettings)

  fun playTestTones()
}

fun TimingRow.metricLapsSince(sinceLap: Int?): List<LapHistoryEntry> {
  if (sinceLap == null) return lapHistory
  return lapHistory.filter { it.lap >= sinceLap }.ifEmpty { lapHistory.take(1) }
}

fun canonicalKartNumber(value: String?): String? {
  val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
  return trimmed.toIntOrNull()?.toString() ?: trimmed
}
