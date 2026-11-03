#!/usr/bin/env python3
"""Read a debug build's local Room snapshot for emulator QA. Never modifies app data.
Run while parked and after the report has been saved (no database writes pending).
"""
import argparse
import json
import sqlite3
import subprocess
import tempfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--user', default='10')
args = parser.parse_args()
with tempfile.TemporaryDirectory(prefix='coach-qa-') as temporary:
    directory = Path(temporary)
    for name in ['driving-coach.db', 'driving-coach.db-wal']:
        result = subprocess.run([args.adb, 'exec-out', 'run-as', 'io.github.univjh.pleosapp', '--user', args.user,
                                 'cat', 'databases/' + name], capture_output=True, check=False)
        if result.returncode == 0:
            (directory / name).write_bytes(result.stdout)
    db = sqlite3.connect(directory / 'driving-coach.db')
    db.row_factory = sqlite3.Row
    result = {table: [dict(row) for row in db.execute('SELECT * FROM ' + table)]
              for table in ['trips', 'events', 'feedback']}
    result['integrity'] = db.execute('PRAGMA integrity_check').fetchone()[0]
    result['sampleCount'] = db.execute('SELECT count(*) FROM samples').fetchone()[0]
    result['evidenceCount'] = db.execute('SELECT count(*) FROM evidence').fetchone()[0]
    print(json.dumps(result, ensure_ascii=False, indent=2))
    db.close()
