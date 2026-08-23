package com.example.lapbot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lapbot.data.ConnectionStatus
import com.example.lapbot.data.LapHistoryEntry
import com.example.lapbot.data.LapTimelineEntry
import com.example.lapbot.data.ReconnectPolicy
import com.example.lapbot.data.TimingRow
import com.example.lapbot.data.TimingServiceRepository
import com.example.lapbot.data.TimingUiState
import com.example.lapbot.data.ToneMetric
import com.example.lapbot.data.ToneSettings
import com.example.lapbot.data.canonicalKartNumber
import com.example.lapbot.data.metricLapsSince
import com.example.lapbot.theme.LapbotTheme
import kotlin.math.roundToInt
import kotlin.math.absoluteValue

@Composable
fun MainScreen(
  onDriverClick: (String) -> Unit,
  onAnnouncementsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = timingViewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  MainScreen(
    state = state,
    onConnect = viewModel::connect,
    onDisconnect = viewModel::disconnect,
    onAutoReconnectChange = viewModel::setAutoReconnect,
    onTailLimitChange = viewModel::setTailLimit,
    onReconnectPolicyChange = viewModel::setReconnectPolicy,
    onDriverClick = onDriverClick,
    onAnnouncementsClick = onAnnouncementsClick,
    modifier = modifier,
  )
}

@Composable
internal fun MainScreen(
  state: TimingUiState,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onAutoReconnectChange: (Boolean) -> Unit,
  onTailLimitChange: (Int) -> Unit,
  onReconnectPolicyChange: (ReconnectPolicy) -> Unit,
  onDriverClick: (String) -> Unit,
  onAnnouncementsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showConfiguration by remember { mutableStateOf(false) }
  var pendingTailLimit by remember { mutableFloatStateOf(state.tailLimit.toFloat()) }
  var pendingInitialDelaySeconds by remember { mutableFloatStateOf(state.reconnectPolicy.initialDelayMs / 1_000f) }
  var pendingMaxDelaySeconds by remember { mutableFloatStateOf(state.reconnectPolicy.maxDelayMs / 1_000f) }
  var pendingGiveUpMinutes by remember { mutableFloatStateOf(state.reconnectPolicy.giveUpAfterMs / 60_000f) }
  Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ConnectionPanel(
      state = state,
      onConnect = onConnect,
      onDisconnect = onDisconnect,
      onAutoReconnectChange = onAutoReconnectChange,
      onAnnouncementsClick = onAnnouncementsClick,
      onConfigure = {
        pendingTailLimit = state.tailLimit.toFloat()
        pendingInitialDelaySeconds = state.reconnectPolicy.initialDelayMs / 1_000f
        pendingMaxDelaySeconds = state.reconnectPolicy.maxDelayMs / 1_000f
        pendingGiveUpMinutes = state.reconnectPolicy.giveUpAfterMs / 60_000f
        showConfiguration = true
      },
    )
    TimingTable(state.rows, onDriverClick, Modifier.weight(1.15f))
    JsonTail(state.jsonTail, Modifier.weight(0.85f))
  }
  if (showConfiguration) {
    ConfigurationDialog(
      tailLimit = pendingTailLimit,
      onTailLimitChange = { pendingTailLimit = it },
      initialDelaySeconds = pendingInitialDelaySeconds,
      onInitialDelayChange = { pendingInitialDelaySeconds = it },
      maxDelaySeconds = pendingMaxDelaySeconds,
      onMaxDelayChange = { pendingMaxDelaySeconds = it },
      giveUpMinutes = pendingGiveUpMinutes,
      onGiveUpChange = { pendingGiveUpMinutes = it },
      onApply = {
        onTailLimitChange(pendingTailLimit.roundToInt())
        onReconnectPolicyChange(
          ReconnectPolicy(
            initialDelayMs = (pendingInitialDelaySeconds * 1_000).roundToInt().toLong(),
            maxDelayMs = (pendingMaxDelaySeconds * 1_000).roundToInt().toLong(),
            giveUpAfterMs = (pendingGiveUpMinutes * 60_000).roundToInt().toLong(),
          ),
        )
        showConfiguration = false
      },
      onDismiss = { showConfiguration = false },
    )
  }
}

@Composable
fun DriverScreen(
  driverId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = timingViewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val driver = state.rows.firstOrNull { it.id == driverId }
  val focusManager = LocalFocusManager.current
  Column(modifier.clearFocusOnTap(focusManager).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    PageHeader("Driver", onBack)
    Text(driver?.displayName ?: "Driver unavailable", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    MetricWindowControl(state.metricsSinceLap, viewModel::setMetricsSinceLap)
    DriverComparisonTable(state.rows, driver, state.metricsSinceLap)
    LapHistoryTable(driver?.lapTimeline.orEmpty(), Modifier.weight(1f))
  }
}

@Composable
fun AnnouncementScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = timingViewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val drivers = state.rows.filter { canonicalKartNumber(it.number) != null }.sortedByKartNumber()
  val selected = drivers.firstOrNull { canonicalKartNumber(it.number) == state.selectedKartNumber }
  val focusManager = LocalFocusManager.current
  var expanded by remember { mutableStateOf(false) }
  var showKartEntry by remember { mutableStateOf(false) }
  var showToneConfiguration by remember { mutableStateOf(false) }

  Column(modifier.clearFocusOnTap(focusManager).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    PageHeader("Announcements", onBack)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Box {
        OutlinedButton(onClick = { expanded = true }) {
          Text(
            selected?.displayName
              ?: state.selectedKartNumber?.let { "#$it Waiting" }
              ?: "Select kart",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
          DropdownMenuItem(
            text = { Text("Enter kart number...") },
            onClick = {
              expanded = false
              showKartEntry = true
            },
          )
          if (drivers.isNotEmpty()) HorizontalDivider()
          drivers.forEach { driver ->
            DropdownMenuItem(
              text = { Text(driver.displayName) },
              onClick = {
                viewModel.setSelectedKartNumber(driver.number)
                expanded = false
              },
            )
          }
        }
      }
      Row {
        Switch(checked = state.audioAnnouncements, onCheckedChange = viewModel::setAudioAnnouncements)
        Text("Speak laps", modifier = Modifier.padding(start = 5.dp, top = 13.dp), style = MaterialTheme.typography.bodySmall)
      }
    }
    MetricWindowControl(state.metricsSinceLap, viewModel::setMetricsSinceLap)
    ToneControls(
      settings = state.toneSettings,
      onSettingsChange = viewModel::setToneSettings,
      onConfigure = { showToneConfiguration = true },
    )
    DriverComparisonTable(state.rows, selected, state.metricsSinceLap)
    LapHistoryTable(selected?.lapTimeline.orEmpty(), Modifier.weight(1f))
  }
  if (showKartEntry) {
    KartNumberDialog(
      initialValue = state.selectedKartNumber.orEmpty(),
      onApply = {
        viewModel.setSelectedKartNumber(it)
        showKartEntry = false
      },
      onDismiss = { showKartEntry = false },
    )
  }
  if (showToneConfiguration) {
    ToneConfigurationDialog(
      settings = state.toneSettings,
      testEnabled = selected != null,
      onApply = {
        viewModel.setToneSettings(it)
        showToneConfiguration = false
      },
      onTest = {
        viewModel.setToneSettings(it)
        viewModel.playTestTones()
      },
      onDismiss = { showToneConfiguration = false },
    )
  }
}

@Composable
private fun KartNumberDialog(initialValue: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
  var value by remember(initialValue) { mutableStateOf(initialValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Announcer kart") },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.length <= 5 && next.all(Char::isDigit)) value = next },
        label = { Text("Kart number") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { onApply(value) }, enabled = canonicalKartNumber(value) != null) { Text("Select") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier =
  pointerInput(focusManager) { detectTapGestures { focusManager.clearFocus() } }

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    TextButton(onClick = onBack) { Text("Race") }
  }
}

@Composable
private fun DriverComparisonTable(
  rows: List<TimingRow>,
  selected: TimingRow?,
  sinceLap: Int?,
) {
  val horizontalScroll = rememberScrollState()

  Column(Modifier.fillMaxWidth()) {
    if (selected == null) {
      Text("No driver timing is available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      return@Column
    }

    val metricLaps = selected.metricLapsSince(sinceLap)
    val latest = metricLaps.firstOrNull()
    val previous = metricLaps.getOrNull(1)
    val driverBest = metricLaps.minByOrNull(LapHistoryEntry::lapMs)
    val bestSector1 = metricLaps.mapNotNull(LapHistoryEntry::sector1Ms).minOrNull()
    val bestSector2 = metricLaps.mapNotNull(LapHistoryEntry::sector2Ms).minOrNull()
    val bestSector3 = metricLaps.mapNotNull(LapHistoryEntry::sector3Ms).minOrNull()
    val raceBest = rows.filter { it.bestLapMs != null }.minByOrNull { it.bestLapMs ?: Long.MAX_VALUE }
    val bestRecent =
      rows.filter { it.recentCompletedLapMs != null }.minByOrNull { it.recentCompletedLapMs ?: Long.MAX_VALUE }
    val comparisons =
      listOf(
        LapComparison(
          "Most recent",
          selected,
          latest?.lap,
          latest?.lapMs,
          latest?.sector1Ms,
          latest?.sector2Ms,
          latest?.sector3Ms,
        ),
        LapComparison(
          "Previous lap",
          selected,
          previous?.lap,
          previous?.lapMs,
          previous?.sector1Ms,
          previous?.sector2Ms,
          previous?.sector3Ms,
        ),
        LapComparison(
          "Driver best",
          selected,
          driverBest?.lap,
          driverBest?.lapMs,
          driverBest?.sector1Ms,
          driverBest?.sector2Ms,
          driverBest?.sector3Ms,
        ),
        LapComparison(
          "Theoretical",
          selected,
          null,
          if (bestSector1 != null && bestSector2 != null && bestSector3 != null) bestSector1 + bestSector2 + bestSector3 else null,
          bestSector1,
          bestSector2,
          bestSector3,
        ),
        LapComparison(
          "Race best",
          raceBest,
          raceBest?.bestLap,
          raceBest?.bestLapMs,
          raceBest?.bestLapSector1Ms,
          raceBest?.bestLapSector2Ms,
          raceBest?.bestLapSector3Ms,
        ),
        LapComparison(
          "Best recent",
          bestRecent,
          bestRecent?.recentCompletedLap,
          bestRecent?.recentCompletedLapMs,
          bestRecent?.recentCompletedSector1Ms,
          bestRecent?.recentCompletedSector2Ms,
          bestRecent?.recentCompletedSector3Ms,
        ),
      )
    FocusHeader(horizontalScroll)
    comparisons.forEach { comparison ->
      FocusRow(comparison, latest, horizontalScroll)
      HorizontalDivider()
    }
  }
}

@Composable
private fun MetricWindowControl(sinceLap: Int?, onSinceLapChange: (Int?) -> Unit) {
  var value by remember(sinceLap) { mutableStateOf(sinceLap?.toString().orEmpty()) }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text("Metrics since lap", modifier = Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodyMedium)
    BasicTextField(
      value = value,
      onValueChange = { next ->
        if (next.length <= 5 && next.all(Char::isDigit)) {
          value = next
          onSinceLapChange(next.toIntOrNull())
        }
      },
      modifier =
        Modifier.width(68.dp)
          .height(34.dp)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 7.dp),
      textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      singleLine = true,
      decorationBox = { innerTextField ->
        Box {
          if (value.isEmpty()) {
            Text("All", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
          }
          innerTextField()
        }
      },
    )
  }
}

@Composable
private fun ToneControls(
  settings: ToneSettings,
  onSettingsChange: (ToneSettings) -> Unit,
  onConfigure: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text("Tone metric", modifier = Modifier.padding(top = 13.dp), style = MaterialTheme.typography.bodyMedium)
    Row {
      Box {
        OutlinedButton(onClick = { expanded = true }) { Text(settings.metric.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
          ToneMetric.entries.forEach { metric ->
            DropdownMenuItem(
              text = { Text(metric.label) },
              onClick = {
                onSettingsChange(settings.copy(metric = metric))
                expanded = false
              },
            )
          }
        }
      }
      TextButton(onClick = onConfigure) { Text("Tone config") }
    }
  }
}

@Composable
private fun ToneConfigurationDialog(
  settings: ToneSettings,
  testEnabled: Boolean,
  onApply: (ToneSettings) -> Unit,
  onTest: (ToneSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  var referenceDuration by remember(settings) { mutableFloatStateOf(settings.referenceDurationMs.toFloat()) }
  var lapDuration by remember(settings) { mutableFloatStateOf(settings.lapDurationMs.toFloat()) }
  var sectorDuration by remember(settings) { mutableFloatStateOf(settings.sectorDurationMs.toFloat()) }
  val pending =
    settings.copy(
      referenceDurationMs = referenceDuration.roundToDuration(),
      lapDurationMs = lapDuration.roundToDuration(),
      sectorDurationMs = sectorDuration.roundToDuration(),
    )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Tone configuration") },
    text = {
      Column {
        DurationSlider("Reference tone", referenceDuration, { referenceDuration = it })
        DurationSlider("Lap tone", lapDuration, { lapDuration = it })
        DurationSlider("Sector tones", sectorDuration, { sectorDuration = it })
        TextButton(onClick = { onTest(pending) }, enabled = testEnabled) { Text("Test latest lap") }
      }
    },
    confirmButton = { TextButton(onClick = { onApply(pending) }) { Text("Apply") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun DurationSlider(label: String, duration: Float, onDurationChange: (Float) -> Unit) {
  Text("$label: ${duration.roundToDuration()} ms")
  Slider(value = duration, onValueChange = onDurationChange, valueRange = 50f..1_000f, steps = 18)
}

private fun Float.roundToDuration(): Int = (roundToInt() / 50 * 50).coerceIn(50, 1_000)

private val ToneMetric.label: String
  get() =
    when (this) {
      ToneMetric.PreviousLap -> "Previous lap"
      ToneMetric.DriverBest -> "Driver best"
      ToneMetric.TheoreticalBest -> "Theoretical"
      ToneMetric.RaceBest -> "Race best"
      ToneMetric.BestRecent -> "Best recent"
    }

@Composable
private fun LapHistoryTable(history: List<LapTimelineEntry>, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth()) {
    Text("Lap history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
      HistoryCell("LAP", 40, true)
      HistoryCell("TIME", 90, true)
      HistoryCell("S1", 70, true)
      HistoryCell("S2", 70, true)
      HistoryCell("S3", 70, true)
    }
    if (history.isEmpty()) {
      Text("No laps available.", modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      LazyColumn(Modifier.fillMaxSize()) {
        items(history, key = LapTimelineEntry::lap) { lap ->
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            HistoryCell(lap.lap.toString(), 40)
            HistoryCell(formatMillis(lap.lapMs), 90)
            HistoryCell(formatMillis(lap.sector1Ms), 70)
            HistoryCell(formatMillis(lap.sector2Ms), 70)
            HistoryCell(formatMillis(lap.sector3Ms), 70)
          }
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun RowScope.HistoryCell(value: String, width: Int, header: Boolean = false) {
  Text(
    text = value,
    modifier = Modifier.width(width.dp).padding(horizontal = 3.dp),
    style = MaterialTheme.typography.labelSmall,
    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
    maxLines = 1,
  )
}

@Composable
private fun FocusHeader(horizontalScroll: ScrollState) {
  Row(Modifier.horizontalScroll(horizontalScroll).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
    FocusCell("METRIC", 100, true)
    FocusCell("TIME / DELTA", 130, true)
    FocusCell("S1 / DELTA", 105, true)
    FocusCell("S2 / DELTA", 105, true)
    FocusCell("S3 / DELTA", 105, true)
    FocusCell("DRIVER / LAP", 180, true)
  }
}

@Composable
private fun FocusRow(
  comparison: LapComparison,
  latest: LapHistoryEntry?,
  horizontalScroll: ScrollState,
) {
  Row(Modifier.horizontalScroll(horizontalScroll).padding(vertical = 4.dp)) {
    FocusCell(comparison.label, 100)
    TimeDeltaCell(comparison.timeMs, latest?.lapMs, 130)
    TimeDeltaCell(comparison.sector1Ms, latest?.sector1Ms, 105, compact = true)
    TimeDeltaCell(comparison.sector2Ms, latest?.sector2Ms, 105, compact = true)
    TimeDeltaCell(comparison.sector3Ms, latest?.sector3Ms, 105, compact = true)
    FocusCell(
      comparison.driver?.let { "${it.name} / ${comparison.lap?.let { lap -> "L$lap" } ?: "mixed"}" }.orDash(),
      180,
    )
  }
}

@Composable
private fun RowScope.TimeDeltaCell(
  metricMs: Long?,
  latestMs: Long?,
  width: Int,
  compact: Boolean = false,
) {
  val delta = if (metricMs != null && latestMs != null) latestMs - metricMs else null
  Row(Modifier.width(width.dp).padding(horizontal = 4.dp)) {
    Text(formatMillis(metricMs), style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall)
    Spacer(Modifier.width(if (compact) 3.dp else 5.dp))
    Text(
      formatDelta(metricMs, latestMs),
      color =
        when {
          delta == null || delta == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
          delta < 0 -> FasterGreen
          else -> MaterialTheme.colorScheme.error
        },
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
    )
  }
}

@Composable
private fun RowScope.FocusCell(value: String, width: Int, header: Boolean = false) {
  Text(
    text = value,
    modifier = Modifier.width(width.dp).padding(horizontal = 4.dp),
    style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

private data class LapComparison(
  val label: String,
  val driver: TimingRow?,
  val lap: Int?,
  val timeMs: Long?,
  val sector1Ms: Long?,
  val sector2Ms: Long?,
  val sector3Ms: Long?,
)

private val FasterGreen = Color(0xFF2EAD62)

private val TimingRow.displayName: String
  get() = if (number.isBlank()) name else "#$number $name"

private fun List<TimingRow>.sortedByKartNumber(): List<TimingRow> =
  sortedWith(compareBy<TimingRow> { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number }.thenBy { it.name })

@Composable
private fun timingViewModel(): MainScreenViewModel {
  val context = LocalContext.current
  return viewModel { MainScreenViewModel(TimingServiceRepository(context)) }
}

@Composable
private fun ConnectionPanel(
  state: TimingUiState,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onAutoReconnectChange: (Boolean) -> Unit,
  onAnnouncementsClick: () -> Unit,
  onConfigure: () -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Lapbot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      TextButton(onClick = onAnnouncementsClick) { Text("Announcer") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(state.status.label, modifier = Modifier.padding(top = 13.dp), style = MaterialTheme.typography.labelLarge)
      Row {
        Switch(checked = state.autoReconnect, onCheckedChange = onAutoReconnectChange)
        Text("Auto-reconnect", modifier = Modifier.padding(start = 4.dp, top = 13.dp), style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onConfigure) { Text("Config") }
        TextButton(
          onClick = if (state.status == ConnectionStatus.Disconnected) onConnect else onDisconnect,
          enabled = state.status != ConnectionStatus.Connecting && state.status != ConnectionStatus.Reconnecting,
        ) {
          Text(if (state.status == ConnectionStatus.Disconnected) "Connect" else "Disconnect")
        }
      }
    }
    state.error?.let {
      Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
  }
}

@Composable
private fun TimingTable(
  rows: List<TimingRow>,
  onDriverClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val horizontalScroll = rememberScrollState()
  Column(modifier) {
    Text("Current timing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(
      Modifier.fillMaxWidth().horizontalScroll(horizontalScroll).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp),
    ) {
      TableCell("POS", 36, true)
      TableCell("NO", 40, true)
      TableCell("DRIVER", 130, true)
      TableCell("LAP", 40, true)
      TableCell("LAP TIME", 84, true)
      TableCell("S1", 72, true)
      TableCell("S2", 72, true)
      TableCell("S3", 72, true)
    }
    if (rows.isEmpty()) {
      Box(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Connect to load live timing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = TimingRow::id) { row ->
          val previousLap = row.lapTimeline.getOrNull(1)
          Row(Modifier.clickable { onDriverClick(row.id) }.horizontalScroll(horizontalScroll).padding(vertical = 4.dp)) {
            TableCell(row.position?.toString().orDash(), 36)
            TableCell(row.number.orDash(), 40)
            TableCell(row.name.orDash(), 130)
            TableCell(row.lap?.toString().orDash(), 40)
            RaceTimeCell(row.lapMs, previousLap?.lapMs, 84)
            RaceTimeCell(row.sector1Ms, previousLap?.sector1Ms, 72)
            RaceTimeCell(row.sector2Ms, previousLap?.sector2Ms, 72)
            RaceTimeCell(row.sector3Ms, previousLap?.sector3Ms, 72)
          }
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun RowScope.RaceTimeCell(currentMs: Long?, previousMs: Long?, width: Int) {
  TableCell(
    value = formatMillis(currentMs ?: previousMs),
    width = width,
    color =
      if (currentMs == null && previousMs != null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
      else Color.Unspecified,
  )
}

@Composable
private fun RowScope.TableCell(value: String, width: Int, header: Boolean = false, color: Color = Color.Unspecified) {
  Text(
    text = value,
    modifier = Modifier.width(width.dp).padding(horizontal = 3.dp),
    style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
    color = color,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun JsonTail(
  records: List<String>,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  LaunchedEffect(records.size) {
    if (records.isNotEmpty()) listState.scrollToItem(records.lastIndex)
  }
  Column(modifier) {
    Text("JSON stream tail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
      if (records.isEmpty()) {
        Text("Live updates will appear here. The initial snapshot is omitted.", style = MaterialTheme.typography.bodySmall)
      } else {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(5.dp)) {
          items(records) { record ->
            Text(record, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }
  }
}

@Composable
private fun ConfigurationDialog(
  tailLimit: Float,
  onTailLimitChange: (Float) -> Unit,
  initialDelaySeconds: Float,
  onInitialDelayChange: (Float) -> Unit,
  maxDelaySeconds: Float,
  onMaxDelayChange: (Float) -> Unit,
  giveUpMinutes: Float,
  onGiveUpChange: (Float) -> Unit,
  onApply: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Configuration") },
    text = {
      Column {
        Text("JSON tail: latest ${tailLimit.roundToInt()} messages")
        Slider(
          value = tailLimit,
          onValueChange = onTailLimitChange,
          valueRange = 5f..100f,
          steps = 18,
        )
        Text("Reconnect initially after ${formatSeconds(initialDelaySeconds)}")
        Slider(
          value = initialDelaySeconds,
          onValueChange = onInitialDelayChange,
          valueRange = 0.5f..10f,
          steps = 18,
        )
        Text("Maximum reconnect delay: ${maxDelaySeconds.roundToInt()} seconds")
        Slider(
          value = maxDelaySeconds,
          onValueChange = onMaxDelayChange,
          valueRange = 5f..60f,
          steps = 10,
        )
        Text("Give up after ${giveUpMinutes.roundToInt()} minutes")
        Slider(
          value = giveUpMinutes,
          onValueChange = onGiveUpChange,
          valueRange = 1f..30f,
          steps = 28,
        )
      }
    },
    confirmButton = { TextButton(onClick = onApply) { Text("Apply") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun formatSeconds(seconds: Float): String =
  if (seconds % 1f == 0f) "${seconds.roundToInt()} seconds" else "${seconds}s"

private val ConnectionStatus.label: String
  get() =
    when (this) {
      ConnectionStatus.Disconnected -> "Disconnected"
      ConnectionStatus.Connecting -> "Connecting..."
      ConnectionStatus.Connected -> "Live"
      ConnectionStatus.Reconnecting -> "Reconnecting..."
    }

private fun String?.orDash(): String = if (isNullOrBlank()) "--" else this

private fun formatMillis(milliseconds: Long?): String {
  if (milliseconds == null) return "--"
  val minutes = milliseconds / 60_000
  val seconds = (milliseconds % 60_000) / 1_000
  val millis = milliseconds % 1_000
  return if (minutes > 0) "%d:%02d.%03d".format(minutes, seconds, millis) else "%d.%03d".format(seconds, millis)
}

private fun formatDelta(metricMs: Long?, latestMs: Long?): String {
  if (metricMs == null || latestMs == null) return "--"
  val delta = latestMs - metricMs
  val sign = if (delta >= 0) "+" else "-"
  return sign + formatMillis(delta.absoluteValue)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun MainScreenPreview() {
  LapbotTheme {
    MainScreen(
      state =
        TimingUiState(
          status = ConnectionStatus.Connected,
          rows =
            listOf(
              TimingRow("1", "9", "Jameel Sesay", 1, 13, 22_425, 16_360, 16_025, 54_868),
              TimingRow("2", "7", "Jamele McIntosh", 2, 12, 23_176, 18_257, 18_091, 59_524),
            ),
          jsonTail = listOf("{\"type\":\"lap_update\",\"number\":\"7\",\"lap\":12,\"lap_ms\":59524}"),
        ),
      onConnect = {},
      onDisconnect = {},
      onAutoReconnectChange = {},
      onTailLimitChange = {},
      onReconnectPolicyChange = {},
      onDriverClick = {},
      onAnnouncementsClick = {},
    )
  }
}
