#!/usr/bin/env python3
"""Writes a timed signal profile for console_driver.py; no app data are injected."""
import json
import sys
import time
from pathlib import Path

path = Path(sys.argv[1])
mode = sys.argv[2] if len(sys.argv) > 2 else 'events'

def publish(gear, speed, belt='Off'):
    state = dict(gear=gear, speed=round(speed, 2), belt=belt)
    temp = path.with_suffix('.tmp')
    temp.write_text(json.dumps(state))
    temp.replace(path)

def hold(seconds, gear, speed, belt='Off'):
    publish(gear, speed, belt)
    print(json.dumps(dict(time=time.time(), duration=seconds, gear=gear, speed=speed, belt=belt)), flush=True)
    time.sleep(seconds)

def ramp(seconds, start, end):
    print(json.dumps(dict(time=time.time(), duration=seconds, gear='Drive', start=start, end=end)), flush=True)
    origin = time.monotonic()
    while True:
        fraction = min(1, (time.monotonic() - origin) / seconds)
        publish('Drive', start + (end-start)*fraction)
        if fraction == 1: return
        time.sleep(.1)

if mode == 'events':
    hold(3, 'Drive', 0)
    ramp(5, 0, 72)
    hold(2, 'Drive', 72)
    hold(5, 'Drive', 72, 'On')
    hold(2, 'Drive', 72)
    ramp(4, 72, 0)
    hold(5, 'Drive', 0)  # A stopped vehicle in D must not close the trip.
elif mode == 'normal':
    hold(3, 'Drive', 0)
    ramp(10, 0, 36)
    hold(3, 'Drive', 36)
    ramp(10, 36, 0)
else:
    raise SystemExit('mode must be events or normal')
hold(35, 'Park', 0)
print('Scenario complete', flush=True)
