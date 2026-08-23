package com.example.lapbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.lapbot.MainActivity
import com.example.lapbot.data.AlphaRaceHubRepository
import com.example.lapbot.data.ConnectionStatus
import com.example.lapbot.data.ReconnectPolicy
import com.example.lapbot.data.TimingServiceState
import com.example.lapbot.data.ToneMetric
import com.example.lapbot.data.ToneSettings
import com.example.lapbot.data.canonicalKartNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TimingStreamService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var repository: AlphaRaceHubRepository
  private lateinit var notificationManager: NotificationManager
  private var foreground = false
  private var textToSpeech: TextToSpeech? = null
  private var textToSpeechReady = false
  private var pendingAnnouncement: PendingAnnouncement? = null
  private lateinit var tonePlayer: TonePlayer
  private val utteranceLaps = ConcurrentHashMap<String, Int>()
  private var pendingToneLap: Int? = null
  private var pendingToneSpeechComplete = false
  private val observedCompletedLaps = mutableMapOf<String, Int>()
  private val knownDriverIds = mutableSetOf<String>()

  override fun onCreate() {
    super.onCreate()
    Log.i(TAG, "Service created")
    TimingServiceState.running = true
    tonePlayer = TonePlayer(scope)
    repository = AlphaRaceHubRepository()
    notificationManager = getSystemService(NotificationManager::class.java)
    createNotificationChannel()
    textToSpeech =
      TextToSpeech(this) { status ->
        if (status == TextToSpeech.SUCCESS) {
          textToSpeech?.language = Locale.UK
          textToSpeechReady = true
          pendingAnnouncement?.let(::speak)
          pendingAnnouncement = null
        }
      }
    textToSpeech?.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) {
          val lap = utteranceLaps.remove(utteranceId) ?: return
          scope.launch {
            if (pendingToneLap == lap) {
              pendingToneSpeechComplete = true
              playPendingTones(TimingServiceState.mutableState.value)
            }
          }
        }

        override fun onError(utteranceId: String) {
          val lap = utteranceLaps.remove(utteranceId)
          scope.launch { clearPendingTone(lap) }
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
          val lap = utteranceLaps.remove(utteranceId)
          scope.launch { clearPendingTone(lap) }
        }
      },
    )

    val current = TimingServiceState.mutableState.value
    repository.setAutoReconnect(current.autoReconnect)
    repository.setTailLimit(current.tailLimit)
    repository.setReconnectPolicy(current.reconnectPolicy)
    scope.launch {
      repository.state.collectLatest { repositoryState ->
        val settings = TimingServiceState.mutableState.value
        val state =
          repositoryState.copy(
            autoReconnect = settings.autoReconnect,
            tailLimit = settings.tailLimit,
            reconnectPolicy = settings.reconnectPolicy,
            selectedKartNumber = settings.selectedKartNumber,
            metricsSinceLap = settings.metricsSinceLap,
            audioAnnouncements = settings.audioAnnouncements,
            toneSettings = settings.toneSettings,
            jsonTail = repositoryState.jsonTail.takeLast(settings.tailLimit),
          )
        observeCompletedLaps(state)
        TimingServiceState.mutableState.value = state
        playPendingTones(state)
        if (foreground) notificationManager.notify(NOTIFICATION_ID, streamNotification(state.status))
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(TAG, "Service command: ${intent?.action ?: "restart"}")
    when (intent?.action) {
      ACTION_CONNECT -> {
        startInForeground()
        val settings = TimingServiceState.mutableState.value
        repository.setAutoReconnect(settings.autoReconnect)
        repository.setTailLimit(settings.tailLimit)
        repository.setReconnectPolicy(settings.reconnectPolicy)
        repository.connect()
      }
      ACTION_DISCONNECT -> stopStreaming()
      ACTION_SET_AUTO_RECONNECT ->
        repository.setAutoReconnect(intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, false))
      ACTION_SET_TAIL_LIMIT ->
        repository.setTailLimit(intent.getIntExtra(EXTRA_TAIL_LIMIT, DEFAULT_TAIL_LIMIT))
      ACTION_SET_RECONNECT_POLICY ->
        repository.setReconnectPolicy(
          ReconnectPolicy(
            initialDelayMs = intent.getLongExtra(EXTRA_INITIAL_DELAY_MS, 500),
            maxDelayMs = intent.getLongExtra(EXTRA_MAX_DELAY_MS, 60_000),
            giveUpAfterMs = intent.getLongExtra(EXTRA_GIVE_UP_AFTER_MS, 10 * 60_000),
          ),
        )
      ACTION_SET_TONE_SETTINGS ->
        TimingServiceState.mutableState.value =
          TimingServiceState.mutableState.value.copy(
            toneSettings =
              ToneSettings(
                metric =
                  intent.getStringExtra(EXTRA_TONE_METRIC)?.let { name ->
                    runCatching { ToneMetric.valueOf(name) }.getOrNull()
                  } ?: ToneMetric.DriverBest,
                referenceDurationMs = intent.getIntExtra(EXTRA_REFERENCE_DURATION_MS, 200),
                lapDurationMs = intent.getIntExtra(EXTRA_LAP_DURATION_MS, 300),
                sectorDurationMs = intent.getIntExtra(EXTRA_SECTOR_DURATION_MS, 150),
              ),
          )
      ACTION_PLAY_TEST_TONES -> playTones(TimingServiceState.mutableState.value)
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onTimeout(startId: Int, fgsType: Int) {
    Log.w(TAG, "Foreground data-sync timeout")
    TimingServiceState.mutableState.value =
      TimingServiceState.mutableState.value.copy(
        status = ConnectionStatus.Disconnected,
        error = "Android's background streaming limit was reached. Open Lapbot to reconnect.",
      )
    repository.disconnect()
    stopForeground(STOP_FOREGROUND_REMOVE)
    foreground = false
    notificationManager.notify(NOTIFICATION_ID, timeoutNotification())
    stopSelf(startId)
  }

  override fun onDestroy() {
    Log.i(TAG, "Service destroyed")
    TimingServiceState.running = false
    repository.close()
    tonePlayer.stop()
    textToSpeech?.shutdown()
    scope.cancel()
    super.onDestroy()
  }

  private fun startInForeground() {
    if (foreground) return
    val notification = streamNotification(ConnectionStatus.Connecting)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    foreground = true
  }

  private fun observeCompletedLaps(state: com.example.lapbot.data.TimingUiState) {
    state.rows.forEach { driver ->
      val isNewDriver = knownDriverIds.add(driver.id)
      val isSelected = canonicalKartNumber(driver.number) == state.selectedKartNumber
      if (isNewDriver && state.status == ConnectionStatus.Connected && isSelected) {
        observedCompletedLaps[driver.id] =
          if (driver.lapMs != null) (driver.lap ?: 1) - 1
          else driver.recentCompletedLap ?: ((driver.lap ?: 1) - 1)
      }
      val completedLap = driver.lap ?: return@forEach
      val lapTime = driver.lapMs ?: return@forEach
      val previousLap = observedCompletedLaps.put(driver.id, completedLap)
      if (
        state.audioAnnouncements &&
          isSelected &&
          previousLap != null &&
          completedLap > previousLap
      ) {
        pendingToneLap = completedLap
        pendingToneSpeechComplete = false
        announce(formatLapAnnouncement(lapTime), completedLap)
      }
    }
  }

  private fun announce(text: String, lap: Int) {
    val announcement = PendingAnnouncement(text, lap)
    if (textToSpeechReady) speak(announcement) else pendingAnnouncement = announcement
  }

  private fun speak(announcement: PendingAnnouncement) {
    val utteranceId = "lap-${System.nanoTime()}"
    utteranceLaps[utteranceId] = announcement.lap
    textToSpeech?.speak(announcement.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
  }

  private fun playPendingTones(state: com.example.lapbot.data.TimingUiState) {
    val lap = pendingToneLap ?: return
    if (!pendingToneSpeechComplete) return
    val sequence = buildToneSequence(state, lap) ?: return
    pendingToneLap = null
    pendingToneSpeechComplete = false
    tonePlayer.play(sequence)
  }

  private fun clearPendingTone(lap: Int?) {
    if (lap != null && pendingToneLap == lap) {
      pendingToneLap = null
      pendingToneSpeechComplete = false
    }
  }

  private fun playTones(state: com.example.lapbot.data.TimingUiState) {
    val sequence = buildToneSequence(state)
    if (sequence == null) {
      Log.w(TAG, "Test tones unavailable for selected metric")
    } else {
      tonePlayer.play(sequence)
    }
  }

  private fun stopStreaming() {
    repository.disconnect()
    TimingServiceState.mutableState.value =
      TimingServiceState.mutableState.value.copy(status = ConnectionStatus.Disconnected, error = null)
    stopForeground(STOP_FOREGROUND_REMOVE)
    foreground = false
    stopSelf()
  }

  private fun streamNotification(status: ConnectionStatus): Notification =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("Lapbot live timing")
      .setContentText(status.notificationText)
      .setContentIntent(openAppIntent())
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .addAction(0, "Disconnect", servicePendingIntent(ACTION_DISCONNECT))
      .build()

  private fun timeoutNotification(): Notification =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_error)
      .setContentTitle("Lapbot stream paused")
      .setContentText("Open Lapbot to restore background streaming.")
      .setContentIntent(openAppIntent())
      .setAutoCancel(true)
      .build()

  private fun openAppIntent(): PendingIntent =
    PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun servicePendingIntent(action: String): PendingIntent =
    PendingIntent.getService(
      this,
      action.hashCode(),
      Intent(this, TimingStreamService::class.java).setAction(action),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notificationManager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Live timing", NotificationManager.IMPORTANCE_LOW),
      )
    }
  }

  companion object {
    private const val TAG = "LapbotService"
    const val ACTION_CONNECT = "com.example.lapbot.action.CONNECT"
    const val ACTION_DISCONNECT = "com.example.lapbot.action.DISCONNECT"
    const val ACTION_SET_AUTO_RECONNECT = "com.example.lapbot.action.SET_AUTO_RECONNECT"
    const val ACTION_SET_TAIL_LIMIT = "com.example.lapbot.action.SET_TAIL_LIMIT"
    const val ACTION_SET_RECONNECT_POLICY = "com.example.lapbot.action.SET_RECONNECT_POLICY"
    const val ACTION_SET_TONE_SETTINGS = "com.example.lapbot.action.SET_TONE_SETTINGS"
    const val ACTION_PLAY_TEST_TONES = "com.example.lapbot.action.PLAY_TEST_TONES"
    const val EXTRA_AUTO_RECONNECT = "autoReconnect"
    const val EXTRA_TAIL_LIMIT = "tailLimit"
    const val EXTRA_INITIAL_DELAY_MS = "initialDelayMs"
    const val EXTRA_MAX_DELAY_MS = "maxDelayMs"
    const val EXTRA_GIVE_UP_AFTER_MS = "giveUpAfterMs"
    const val EXTRA_TONE_METRIC = "toneMetric"
    const val EXTRA_REFERENCE_DURATION_MS = "referenceDurationMs"
    const val EXTRA_LAP_DURATION_MS = "lapDurationMs"
    const val EXTRA_SECTOR_DURATION_MS = "sectorDurationMs"
    private const val DEFAULT_TAIL_LIMIT = 20
    private const val CHANNEL_ID = "live_timing"
    private const val NOTIFICATION_ID = 1001
  }
}

private data class PendingAnnouncement(val text: String, val lap: Int)

internal fun formatLapAnnouncement(milliseconds: Long): String =
  (milliseconds / 100).let { tenths -> "${tenths / 10} point ${tenths % 10}" }

private val ConnectionStatus.notificationText: String
  get() =
    when (this) {
      ConnectionStatus.Disconnected -> "Disconnected"
      ConnectionStatus.Connecting -> "Connecting to Buckmore..."
      ConnectionStatus.Connected -> "Receiving live timing"
      ConnectionStatus.Reconnecting -> "Connection interrupted; reconnecting..."
    }
