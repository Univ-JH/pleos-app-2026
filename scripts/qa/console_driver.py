#!/usr/bin/env python3
"""Drive ONLY the local Pleos emulator through its Virtual Console HTTP endpoints.

Usage: adb forward tcp:8088 tcp:8088
       python3 scripts/qa/console_driver.py /tmp/coach-controls.json
Control JSON: {"gear":"Park","speed":0,"belt":"Off"}
Keeps the console's browser session alive. This is SDK-path QA, not in-app replay.
"""
import json
import sys
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8088"

def request(path, method, params):
    with urlopen(Request(BASE + path + "?" + urlencode(params), method=method), timeout=2) as response:
        return json.load(response)

if __name__ == "__main__":
    controls = Path(sys.argv[1])
    previous = {}
    heartbeat = 0
    try:
        while True:
            start = time.monotonic()
            try:
                state = json.loads(controls.read_text())
                heartbeat_due = start - heartbeat > .8
                if heartbeat_due:
                    request("/browser/session", "POST", {"client": "coach-sdk-qa"})
                    heartbeat = start
                if state.get("gear") != previous.get("gear") or heartbeat_due:
                    request("/vehicle/gear", "PUT", {"gear": state["gear"]})
                request("/vehicle/controls", "PUT", {"speed": round(state.get("speed", 0)), "accelerator": 0, "brake": 0})
                if state.get("belt") != previous.get("belt") or heartbeat_due:
                    request("/vehicle/telltales/simulator", "PUT", {"type": "SeatBelt", "color": "Red", "state": state.get("belt", "Off")})
                previous = state
            except Exception as error:
                previous = {}
                print(type(error).__name__, str(error), flush=True)
            time.sleep(max(0, .1 - (time.monotonic() - start)))
    except KeyboardInterrupt:
        request("/vehicle/controls", "PUT", {"speed": 0, "accelerator": 0, "brake": 0})
        request("/vehicle/gear", "PUT", {"gear": "Park"})
        request("/browser/session", "POST", {"client": "coach-sdk-qa", "closing": 1})
