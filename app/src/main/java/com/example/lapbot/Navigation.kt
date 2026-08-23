package com.example.lapbot

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.lapbot.ui.main.MainScreen
import com.example.lapbot.ui.main.AnnouncementScreen
import com.example.lapbot.ui.main.DriverScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onDriverClick = { driverId -> backStack.add(Driver(driverId)) },
            onAnnouncementsClick = { backStack.add(Announcer) },
            modifier = Modifier.safeDrawingPadding().padding(12.dp),
          )
        }
        entry<Driver> { key ->
          DriverScreen(
            driverId = key.driverId,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding().padding(12.dp),
          )
        }
        entry<Announcer> {
          AnnouncementScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding().padding(12.dp),
          )
        }
      },
  )
}
