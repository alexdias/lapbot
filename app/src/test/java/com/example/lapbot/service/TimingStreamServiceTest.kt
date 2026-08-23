package com.example.lapbot.service

import junit.framework.TestCase.assertEquals
import org.junit.Test

class TimingStreamServiceTest {
  @Test
  fun lapAnnouncementUsesTotalSecondsAndOneDecimalPlace() {
    assertEquals("61 point 4", formatLapAnnouncement(61_499))
    assertEquals("52 point 0", formatLapAnnouncement(52_000))
  }
}
