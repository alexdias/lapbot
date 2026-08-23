#!/usr/bin/env python3
"""Programmatic access to Alpha Race Hub live timing."""

from __future__ import annotations

import argparse
import asyncio
import json
from dataclasses import dataclass, field
from html.parser import HTMLParser
from typing import Any, AsyncIterator
from urllib.parse import urlencode

import requests


BASE_URL = "https://www.alpharacehub.com"
PUSHER_KEY = "3aaffebc8193ea83cb2f"
PUSHER_CLUSTER = "eu"


class _RootParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.attributes: dict[str, str] = {}

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if values.get("id") == "root":
            self.attributes = {key: value for key, value in attrs if value is not None}


def _decode_data(value: Any) -> Any:
    while isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError:
            break
    return value


@dataclass
class TimingState:
    """Current snapshot, updated in place from Alpha's sparse patches."""

    snapshot: dict[str, Any] = field(default_factory=dict)

    @property
    def competitors(self) -> list[dict[str, Any]]:
        return self.snapshot.get("Competitors", [])

    def driver_history(self) -> list[dict[str, Any]]:
        return [
            {
                "id": driver.get("CompetitorId"),
                "number": driver.get("CompetitorNumber"),
                "name": driver.get("DriverName") or driver.get("CompetitorName"),
                "position": driver.get("Position"),
                "laps": [
                    {
                        "lap": lap.get("LapNumber"),
                        "lap_ms": lap.get("LapTime"),
                        "sector_1_ms": lap.get("Split1Time"),
                        "sector_2_ms": lap.get("Split2Time"),
                        "sector_3_ms": lap.get("Split3Time"),
                    }
                    for lap in driver.get("Laps", [])
                ],
            }
            for driver in self.competitors
        ]

    def apply(self, patch: dict[str, Any]) -> list[dict[str, Any]]:
        """Merge a Pusher patch and return lap records changed by it."""
        if patch.get("Clear"):
            self.snapshot = patch
            return []

        changed: list[dict[str, Any]] = []
        competitors = self.snapshot.setdefault("Competitors", [])
        by_id = {item.get("CompetitorId"): item for item in competitors}

        for driver_patch in patch.get("Competitors", []):
            competitor_id = driver_patch.get("CompetitorId")
            driver = by_id.get(competitor_id)
            if driver is None:
                driver = {"CompetitorId": competitor_id, "Laps": []}
                competitors.append(driver)
                by_id[competitor_id] = driver

            laps = driver.setdefault("Laps", [])
            by_lap = {lap.get("LapNumber"): lap for lap in laps}
            for lap_patch in driver_patch.get("Laps", []):
                lap_number = lap_patch.get("LapNumber")
                lap = by_lap.get(lap_number)
                if lap is None:
                    lap = {"LapNumber": lap_number}
                    laps.append(lap)
                    by_lap[lap_number] = lap
                before = dict(lap)
                lap.update(lap_patch)
                if lap != before:
                    changed.append({"driver": driver, "lap": lap})

            driver.update({key: value for key, value in driver_patch.items() if key != "Laps"})

        self.snapshot.update({key: value for key, value in patch.items() if key != "Competitors"})
        return [self._lap_event(item["driver"], item["lap"]) for item in changed]

    @staticmethod
    def _lap_event(driver: dict[str, Any], lap: dict[str, Any]) -> dict[str, Any]:
        return {
            "type": "lap_update",
            "driver_id": driver.get("CompetitorId"),
            "number": driver.get("CompetitorNumber"),
            "name": driver.get("DriverName") or driver.get("CompetitorName"),
            "lap": lap.get("LapNumber"),
            "lap_ms": lap.get("LapTime"),
            "sector_1_ms": lap.get("Split1Time"),
            "sector_2_ms": lap.get("Split2Time"),
            "sector_3_ms": lap.get("Split3Time"),
            "complete": bool(lap.get("LapTime")),
        }


class AlphaRaceHubClient:
    def __init__(self, site: str = "buckmore") -> None:
        self.site = site
        self.http = requests.Session()
        self.http.headers["User-Agent"] = (
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/139.0 Safari/537.36"
        )
        self.token = ""
        self.pusher_key = PUSHER_KEY
        self.pusher_cluster = PUSHER_CLUSTER
        self.channel_suffix = "live"
        self.state = TimingState()

    def bootstrap(self) -> None:
        response = self.http.get(f"{BASE_URL}/{self.site}/live", timeout=20)
        response.raise_for_status()
        parser = _RootParser()
        parser.feed(response.text)
        attrs = parser.attributes
        self.token = attrs["data-pushertoken"]
        self.pusher_key = attrs.get("data-pusherkey", PUSHER_KEY)
        self.pusher_cluster = attrs.get("data-pushercluster", PUSHER_CLUSTER)
        self.channel_suffix = attrs.get("data-pusherchannelsuffix", "live")

    def _headers(self) -> dict[str, str]:
        return {"at-pst": self.token, "at-site": self.site, "Accept": "application/json"}

    def get_current(self, event_id: str | None = None) -> TimingState:
        if not self.token:
            self.bootstrap()
        params = {"eventId": event_id} if event_id else None
        response = self.http.get(
            f"{BASE_URL}/api/v1/{self.site}/live/current",
            params=params,
            headers=self._headers(),
            timeout=20,
        )
        if response.status_code == 204:
            self.state = TimingState()
            return self.state
        response.raise_for_status()
        self.state = TimingState(response.json())
        return self.state

    def _authorize(self, socket_id: str, channel: str) -> str:
        headers = self._headers()
        headers.update(
            {
                "Accept": "*/*",
                "Origin": BASE_URL,
                "Referer": f"{BASE_URL}/{self.site}/live",
            }
        )
        response = self.http.post(
            f"{BASE_URL}/pusher/auth",
            data={"socket_id": socket_id, "channel_name": channel},
            headers=headers,
            timeout=20,
        )
        response.raise_for_status()
        return response.json()["auth"]

    async def stream(self) -> AsyncIterator[dict[str, Any]]:
        """Yield snapshots and partial/completed lap updates indefinitely."""
        import websockets

        state = await asyncio.to_thread(self.get_current)
        yield {"type": "snapshot", "session": state.snapshot, "drivers": state.driver_history()}

        channel = f"private-{self.site}{self.channel_suffix}"
        query = urlencode(
            {"protocol": 7, "client": "python", "version": "1.0", "flash": "false"}
        )
        ws_url = f"wss://ws-{self.pusher_cluster}.pusher.com/app/{self.pusher_key}?{query}"

        async with websockets.connect(ws_url, origin=BASE_URL, ping_interval=None) as websocket:
            established = json.loads(await websocket.recv())
            connection = _decode_data(established["data"])
            auth = await asyncio.to_thread(self._authorize, connection["socket_id"], channel)
            await websocket.send(
                json.dumps(
                    {"event": "pusher:subscribe", "data": {"auth": auth, "channel": channel}}
                )
            )

            async for raw_message in websocket:
                message = json.loads(raw_message)
                event = message.get("event")
                data = _decode_data(message.get("data"))

                if event == "pusher:ping":
                    await websocket.send(json.dumps({"event": "pusher:pong", "data": {}}))
                elif event == "token" and isinstance(data, dict):
                    self.token = data["token"]
                elif event in {"new_session", "refresh"}:
                    event_uuid = data.get("eventUuid") if isinstance(data, dict) else None
                    state = await asyncio.to_thread(self.get_current, event_uuid)
                    yield {"type": "snapshot", "session": state.snapshot, "drivers": state.driver_history()}
                elif event == "update" and isinstance(data, dict):
                    old_sequence = self.state.snapshot.get("Sequence")
                    new_sequence = data.get("Sequence")
                    if old_sequence is not None and new_sequence != old_sequence + 1:
                        state = await asyncio.to_thread(self.get_current)
                        yield {"type": "snapshot", "reason": "sequence_gap", "session": state.snapshot, "drivers": state.driver_history()}
                    else:
                        for lap_event in self.state.apply(data):
                            yield lap_event


async def _stream(client: AlphaRaceHubClient) -> None:
    async for event in client.stream():
        print(json.dumps(event, separators=(",", ":")), flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("snapshot", "stream"))
    parser.add_argument("--site", default="buckmore")
    args = parser.parse_args()
    client = AlphaRaceHubClient(args.site)
    if args.command == "snapshot":
        print(json.dumps(client.get_current().driver_history(), indent=2))
    else:
        asyncio.run(_stream(client))


if __name__ == "__main__":
    main()
