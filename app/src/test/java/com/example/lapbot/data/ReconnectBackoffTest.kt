package com.example.lapbot.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class ReconnectBackoffTest {
  @Test
  fun delayDoublesUpToMaximum() {
    var now = 0L
    val backoff =
      ReconnectBackoff(
        policy = ReconnectPolicy(initialDelayMs = 500, maxDelayMs = 2_000, giveUpAfterMs = 10_000),
        nowMs = { now },
      )

    assertEquals(500L, backoff.nextDelay())
    assertEquals(1_000L, backoff.nextDelay())
    assertEquals(2_000L, backoff.nextDelay())
    assertEquals(2_000L, backoff.nextDelay())
  }

  @Test
  fun reconnectStopsAtTimeLimit() {
    var now = 1_000L
    val backoff =
      ReconnectBackoff(
        policy = ReconnectPolicy(initialDelayMs = 500, maxDelayMs = 60_000, giveUpAfterMs = 600_000),
        nowMs = { now },
      )

    assertEquals(500L, backoff.nextDelay())
    now += 600_000

    assertNull(backoff.nextDelay())
  }
}
