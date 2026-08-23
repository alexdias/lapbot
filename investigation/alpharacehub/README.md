# Alpha Race Hub timing investigation

This Python client exposes Buckmore's current drivers, retained lap/sector history,
and live timing updates. Alpha Race Hub uses authenticated Pusher Channels rather
than a WebSocket hosted on `alpharacehub.com`.

## Setup

```bash
cd investigation/alpharacehub
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

On Debian/Ubuntu, install the matching `python3-venv` package first if
`python3 -m venv` reports that `ensurepip` is unavailable.

Print every current driver and their retained laps (times are milliseconds):

```bash
.venv/bin/python alpharacehub.py snapshot
```

Stream newline-delimited JSON. The first record is a complete snapshot; later
`lap_update` records are emitted as sectors and completed laps arrive:

```bash
.venv/bin/python alpharacehub.py stream
```

Library usage:

```python
import asyncio
from alpharacehub import AlphaRaceHubClient

async def main():
    async for event in AlphaRaceHubClient("buckmore").stream():
        print(event)

asyncio.run(main())
```

## Protocol notes

- `GET /buckmore/live` establishes the required `buckmore-pst` cookie and returns
  a matching short-lived token in the root element.
- `GET /api/v1/buckmore/live/current` returns the current session, all drivers,
  and retained `Laps`, including `Split1Time`, `Split2Time`, `Split3Time`, and
  `LapTime` in integer milliseconds.
- Live data arrives on Pusher channel `private-buckmorelive` at
  `wss://ws-eu.pusher.com`. The private-channel signature comes from
  `POST /pusher/auth`; that request requires the exact live-page `Referer`.
- `update` events are sparse patches. They are merged by `CompetitorId` and
  `LapNumber`; a sequence gap causes a fresh HTTP snapshot.
- Once the live endpoint moves to a newer event, the public finished-session
  pages retain full lap times but apparently not every lap's sector splits.
