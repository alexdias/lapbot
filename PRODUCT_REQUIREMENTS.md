# Lapbot Product Requirements

This document records the intended behavior of the Lapbot Android app. It is the source of truth for the requirements implemented during the initial Alpha Race Hub integration.

## Live Timing

- Connect to the Buckmore Alpha Race Hub live timing feed using its authenticated Pusher WebSocket protocol.
- Fetch the current timing snapshot before applying live updates.
- Merge sparse competitor, lap, and sector patches without discarding fields omitted by an update.
- Detect stream sequence gaps and fetch a replacement snapshot.
- Preserve existing timing data when a connect, reconnect, refresh, or sequence-gap request unexpectedly returns an empty snapshot.
- Honor an explicit `Clear` message from the live stream.
- Keep a configurable tail of the latest decoded JSON updates for diagnostics. The initial snapshot is not included in this tail.

## Background Operation

- Streaming and text-to-speech announcements must continue while the app is in the background.
- A foreground `dataSync` service owns the repository, WebSocket, and text-to-speech engine.
- Display an ongoing notification while streaming, including a Disconnect action.
- Handle Android's foreground data-sync timeout by stopping the stream and notifying the user that the app must be reopened.

## Connection Recovery

- Auto-reconnect is user-configurable and disabled by default.
- Reconnect with exponential backoff.
- Default reconnect policy:
  - Initial delay: 500 ms.
  - Maximum delay: 60 seconds.
  - Give-up period: 10 minutes.
- Allow the initial delay, maximum delay, and give-up period to be configured.
- Reset the reconnect backoff after the connection has remained healthy for 30 seconds.
- Stop retrying when the configured give-up period expires and allow the user to reconnect manually.

## Navigation

- The race overview, driver details, and announcer are separate Navigation3 destinations.
- Tapping a competitor row in the race table opens that competitor's driver details.
- Driver and Announcer pages provide a direct route back to the race overview.

## Race Overview

- Show a dense, scrollable timing table containing position, kart number, driver name, lap number, total lap time, and three sector times.
- Total lap time must appear before sector times because it has higher priority.
- Keep table rows compact enough to show as much of the field as practical.
- While the current lap is incomplete, fill each missing total or sector cell from the immediately previous lap.
- Render previous-lap fallback values in a muted grey while rendering fields received for the current lap normally.
- Replace fallback values independently as each current-lap field arrives.

## Valid Laps

- A lap is valid for calculated metrics and performance tones only when it has all of the following:
  - Total lap time.
  - Sector 1 time.
  - Sector 2 time.
  - Sector 3 time.
- Exclude incomplete laps from every best/recent/theoretical metric, even if they contain a total lap time, while retaining them for display.
- This exclusion prevents partial updates with implausibly low total times from distorting comparisons.
- The race overview may still show the current in-progress raw lap fields as they arrive.

## Driver Details

- Show the selected kart/driver name and timing comparison table.
- Show a dense lap-history table beneath the comparison table.
- Lap history includes partial laps and updates as individual total and sector fields arrive.
- Order lap history by lap number descending, with the most recent lap first.
- Lap-history columns are lap number, total lap time, Sector 1, Sector 2, and Sector 3, in that order.

## Metric Window

- Driver metrics have an optional `Since lap` number for endurance races where multiple drivers share a kart.
- The setting is shared between the Driver and Announcer views.
- A blank value includes the full valid lap history.
- A value includes valid laps whose lap number is greater than or equal to the configured number.
- A value greater than the current lap number is valid and supports configuring the app in anticipation of a driver swap.
- Until a valid lap reaches the configured number, use only the most recent valid lap as the metric window rather than showing no data.
- The lap-history table remains the full history; the setting filters calculated driver metrics.

## Comparison Metrics

- The selected driver's most recent valid lap is the baseline for displayed deltas.
- Show these rows:
  - Most recent valid lap in the metric window.
  - Previous valid lap in the metric window.
  - Driver best valid lap in the metric window.
  - Theoretical best assembled from the best valid individual sectors in the metric window.
  - Best valid lap across the race.
  - Best of each competitor's most recent valid lap.
- Show total lap time and delta before sector times and deltas.
- Delta is `selected most recent - metric`.
- Positive deltas mean the selected lap is slower and are red.
- Negative deltas mean the selected lap is faster and are green.
- Include the source driver and lap number for each comparison where applicable.

## Announcements

- Announcer is a dedicated page, not a general configuration option.
- Target announcements by normalized kart number rather than competitor ID or driver-name matching.
- Allow a kart already present in live timing to be selected from a list sorted numerically by kart number.
- Allow a kart number to be entered manually before that kart appears in live timing.
- Show a waiting state for a configured kart with no current timing row and resolve it automatically when the kart appears.
- Keep the kart target through driver swaps or competitor-ID changes.
- Announce the first live completed lap when a previously absent selected kart appears, while still suppressing historical laps loaded in an initial snapshot.
- Show the same timing comparison, metric-window control, and lap history available on the Driver page.
- Provide a `Speak laps` switch on the Announcer page.
- Announce a newly received total lap time for the selected competitor without waiting for all sector data.
- Speak lap times to one decimal place by truncating rather than rounding. For example, `61.499` seconds is spoken as "61 point 4".

## Performance Tones

- After each spoken lap time completes, play a five-tone performance sequence.
- TTS announcements do not depend on tone metric availability. If that same lap lacks complete metric data, speak the lap time and omit its tones.
- Play one 440 Hz reference tone followed by total lap, Sector 1, Sector 2, and Sector 3 comparison tones.
- Raise pitch when the latest value is faster than the configured metric and lower pitch when it is slower.
- Map each 100 ms of delta to one semitone, rounded to the nearest semitone and clamped to one octave above or below 440 Hz.
- Separate tones with a 75 ms gap and apply a short fade at each edge to prevent clicks.
- Allow the comparison metric to be selected from previous lap, driver best, theoretical best, race best, and best recent.
- Compare a newly completed lap against the benchmark that existed before that lap. A new best must therefore be compared with the old best, not itself.
- Treat previous lap as the exception: lap N compares with lap N-1 from the updated history rather than the pre-lap `Previous lap` metric, which would incorrectly be N-2.
- Wait until both TTS has completed and the same lap has complete sector data before playing its tones.
- Apply the `Since lap` metric window to previous lap, driver best, and theoretical comparisons.
- If the selected metric is unavailable, do not play a potentially misleading tone sequence.
- Default reference, lap, and sector tone durations to 200 ms, 300 ms, and 150 ms respectively, and allow each category to be configured from 50 to 1,000 ms.
- Keep duration controls and a `Test latest lap` action in a dedicated Tone configuration dialog.
- Testing plays tones for the selected competitor's latest valid lap without requiring a spoken announcement.

## Diagnostics

- Log stream lifecycle, snapshot application, ignored empty snapshots, sequence gaps, explicit clears, and failures under the `LapbotStream` tag.
- Log foreground service creation, commands, timeout, and destruction under the `LapbotService` tag.
- Diagnostic logging must identify why timing data was replaced or preserved without dumping private authentication values.

## Verification

- Unit tests cover sparse patch merging, valid-lap filtering, lap metrics, newest-first history, metric-window fallback, reconnect behavior, and announcement truncation.
- The debug build and Android test sources must compile before installation.
- Verify important navigation and table ordering on a connected Android device when one is available.
