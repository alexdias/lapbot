package com.example.lapbot

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class Driver(val driverId: String) : NavKey

@Serializable data object Announcer : NavKey
