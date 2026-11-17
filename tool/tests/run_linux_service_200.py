#!/usr/bin/env python3
"""Run exactly 200 distinct service cases and save a machine-readable result."""
import argparse
import contextlib
import datetime
import json
from pathlib import Path
import platform
import sys
import time
import unittest

MODULES = ('test_linux_service_security', 'test_linux_service_followup',
           'test_linux_systemd', 'test_linux_keyring_unlock', 'test_linux_service_matrix')


class RecordedResult(unittest.TextTestResult):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.records = []

    def startTest(self, test):
        self.started = time.monotonic()
        super().startTest(test)

    def record(self, test, status, detail=None):
        self.records.append(dict(id=test.id(), status=status,
                                 seconds=round(time.monotonic() - self.started, 6), detail=detail))

    def addSuccess(self, test):
        super().addSuccess(test)
        self.record(test, 'passed')

    def addFailure(self, test, error):
        super().addFailure(test, error)
        self.record(test, 'failed', self._exc_info_to_string(error, test))

    def addError(self, test, error):
        super().addError(test, error)
        self.record(test, 'error', self._exc_info_to_string(error, test))

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        self.record(test, 'skipped', reason)


def flatten(suite):
    for test in suite:
        if isinstance(test, unittest.TestSuite):
            yield from flatten(test)
        else:
            yield test


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('temp/service-200-tests'))
    options = parser.parse_args()
    suite = unittest.defaultTestLoader.loadTestsFromNames(MODULES)
    ids = [test.id() for test in flatten(suite)]
    if len(ids) != 200 or len(set(ids)) != 200:
        parser.error('Expected 200 distinct tests, got %d (%d unique)' % (len(ids), len(set(ids))))
    options.output.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    with (options.output / 'tests.log').open('w') as log, contextlib.redirect_stdout(log), contextlib.redirect_stderr(log):
        result = unittest.TextTestRunner(stream=log, verbosity=2, resultclass=RecordedResult).run(suite)
    summary = dict(started_utc=stamp, platform=platform.platform(), python=sys.version,
                   total=result.testsRun, unique_tests=len(set(ids)),
                   passed=sum(row['status'] == 'passed' for row in result.records),
                   failures=len(result.failures), errors=len(result.errors), skipped=len(result.skipped),
                   seconds=round(time.monotonic() - started, 3), cases=result.records)
    (options.output / 'results.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n')
    print(json.dumps({key: value for key, value in summary.items() if key != 'cases'}, ensure_ascii=False, indent=2))
    return 0 if result.wasSuccessful() and not result.skipped else 1


if __name__ == '__main__':
    sys.exit(main())
