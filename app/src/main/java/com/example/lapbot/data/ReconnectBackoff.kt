package com.example.lapbot.data

import android.os.SystemClock

internal class ReconnectBackoff(
  policy: ReconnectPolicy = ReconnectPolicy(),
  private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
  private var policy = policy
  private var firstFailureMs: Long? = null
  private var nextDelayMs = policy.initialDelayMs

  fun updatePolicy(policy: ReconnectPolicy) {
    this.policy = policy
    nextDelayMs = nextDelayMs.coerceIn(policy.initialDelayMs, policy.maxDelayMs)
  }

  fun reset() {
    firstFailureMs = null
    nextDelayMs = policy.initialDelayMs
  }

  fun nextDelay(): Long? {
    val now = nowMs()
    val firstFailure = firstFailureMs ?: now.also { firstFailureMs = it }
    val remaining = policy.giveUpAfterMs - (now - firstFailure)
    if (remaining <= 0) return null
    val delay = nextDelayMs.coerceAtMost(remaining)
    nextDelayMs = (nextDelayMs * 2).coerceAtMost(policy.maxDelayMs)
    return delay
  }

  fun expired(): Boolean =
    firstFailureMs?.let { nowMs() - it >= policy.giveUpAfterMs } ?: false
}
