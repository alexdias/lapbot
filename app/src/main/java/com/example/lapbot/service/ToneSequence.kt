package com.example.lapbot.service

import com.example.lapbot.data.LapHistoryEntry
import com.example.lapbot.data.TimingRow
import com.example.lapbot.data.TimingUiState
import com.example.lapbot.data.ToneMetric
import com.example.lapbot.data.canonicalKartNumber
import com.example.lapbot.data.metricLapsSince
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class ToneSequence(
  val frequenciesHz: List<Double>,
  val durationsMs: List<Int>,
)

internal fun buildToneSequence(state: TimingUiState, expectedLap: Int? = null): ToneSequence? {
  val selected = state.rows.firstOrNull { canonicalKartNumber(it.number) == state.selectedKartNumber } ?: return null
  val metricLaps = selected.metricLapsSince(state.metricsSinceLap)
  val latest = metricLaps.firstOrNull() ?: return null
  if (expectedLap != null && latest.lap != expectedLap) return null
  val target = metricLap(state, selected, latest, metricLaps) ?: return null
  val settings = state.toneSettings
  return ToneSequence(
    frequenciesHz =
      listOf(
        REFERENCE_HZ,
        comparisonFrequency(latest.lapMs, target.lapMs),
        comparisonFrequency(latest.sector1Ms, target.sector1Ms),
        comparisonFrequency(latest.sector2Ms, target.sector2Ms),
        comparisonFrequency(latest.sector3Ms, target.sector3Ms),
      ),
    durationsMs =
      listOf(
        settings.referenceDurationMs,
        settings.lapDurationMs,
        settings.sectorDurationMs,
        settings.sectorDurationMs,
        settings.sectorDurationMs,
      ),
  )
}

private fun metricLap(
  state: TimingUiState,
  selected: TimingRow,
  latest: LapHistoryEntry,
  metricLaps: List<LapHistoryEntry>,
): LapHistoryEntry? =
  when (state.toneSettings.metric) {
    ToneMetric.PreviousLap -> metricLaps.getOrNull(1)
    ToneMetric.DriverBest -> metricLaps.drop(1).minByOrNull(LapHistoryEntry::lapMs)
    ToneMetric.TheoreticalBest -> {
      val previousLaps = metricLaps.drop(1)
      val sector1 = previousLaps.mapNotNull(LapHistoryEntry::sector1Ms).minOrNull()
      val sector2 = previousLaps.mapNotNull(LapHistoryEntry::sector2Ms).minOrNull()
      val sector3 = previousLaps.mapNotNull(LapHistoryEntry::sector3Ms).minOrNull()
      if (sector1 != null && sector2 != null && sector3 != null) {
        LapHistoryEntry(0, sector1 + sector2 + sector3, sector1, sector2, sector3)
      } else {
        null
      }
    }
    ToneMetric.RaceBest -> previousRaceLaps(state, selected.id, latest.lap).minByOrNull(LapHistoryEntry::lapMs)
    ToneMetric.BestRecent ->
      state.rows
        .mapNotNull { row -> row.lapHistory.firstOrNull { row.id != selected.id || it.lap != latest.lap } }
        .minByOrNull(LapHistoryEntry::lapMs)
  }

private fun previousRaceLaps(state: TimingUiState, selectedId: String, latestLap: Int): List<LapHistoryEntry> =
  state.rows.flatMap { row -> row.lapHistory.filterNot { row.id == selectedId && it.lap == latestLap } }

private fun comparisonFrequency(latestMs: Long?, metricMs: Long?): Double {
  if (latestMs == null || metricMs == null) return REFERENCE_HZ
  val semitones = ((metricMs - latestMs) / 100.0).roundToInt().coerceIn(-12, 12)
  return REFERENCE_HZ * 2.0.pow(semitones / 12.0)
}

private const val REFERENCE_HZ = 440.0
