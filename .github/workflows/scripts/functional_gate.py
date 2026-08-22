"""
Aggregate the per-(mc, loader) compatibility reports the functional tests produce, write one combined
summary to the job step summary, and fail the workflow if any real incompatibility was found.

"Real" means a FAILED, ERROR, or UNEXPECTED_PASS (a stale marking) verdict. A KNOWN_FAILURE is expected —
it is recorded and shown, but does not fail the gate. This is what lets a release be blocked by an
unmarked regression while a version that is known-broken (and tracked) does not block it.

Usage: functional_gate.py <reports-dir>
"""
__author__ = 'SkyDy'

import json
import os
import sys
from pathlib import Path

# The report uses emoji; force UTF-8 stdout so it prints on a non-UTF-8 console (e.g. Windows GBK) too.
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

# Verdicts that must fail the gate.
BLOCKING = {'FAILED', 'ERROR', 'UNEXPECTED_PASS'}

ICON = {
    'PASSED': '✅',
    'FAILED': '❌',
    'KNOWN_FAILURE': '⚠️',
    'UNEXPECTED_PASS': '🧹',
    'SKIPPED': '⏭️',
    'ERROR': '💥',
}


def load_reports(root):
    """Every compatibility.json under the reports directory, in a stable order."""
    reports = []
    for path in sorted(Path(root).rglob('compatibility.json')):
        try:
            reports.append(json.loads(path.read_text(encoding='utf-8')))
        except (json.JSONDecodeError, OSError) as e:
            print(f'Skipping unreadable report {path}: {e}', file=sys.stderr)
    return reports


def main():
    if len(sys.argv) < 2:
        print('usage: functional_gate.py <reports-dir>', file=sys.stderr)
        sys.exit(2)

    reports = load_reports(sys.argv[1])

    if not reports:
        # No reports at all usually means every shard was skipped (no harness on this branch, or the
        # matrix was empty). That is not a failure — there was simply nothing to test.
        summary('## Functional tests\n\nNo compatibility reports were produced (nothing to test).\n')
        print('No reports found; passing.')
        return

    rows = []
    counts = {k: 0 for k in ICON}
    blocking_rows = []
    branch = reports[0].get('branch', '?')

    for report in reports:
        for r in report.get('results', []):
            status = r['status']
            counts[status] = counts.get(status, 0) + 1
            note = r.get('detail') or r.get('issue') or ''
            if note:
                note = str(note).replace('|', r'\|').replace('\n', ' ')[:200]
            rows.append((r['mc'], r['loader'], r['side'], r['scenario'], status, note))
            if status in BLOCKING:
                blocking_rows.append(r)

    # Sort by loader/side/scenario within a version; versions keep the order the reports came in, which
    # the harness already sorted numerically per shard.
    rows.sort(key=lambda t: (t[1], t[2], t[3]))

    lines = [f'## Functional tests — branch `{branch}`', '']
    lines.append('| Minecraft | Loader | Side | Scenario | Result | Notes |')
    lines.append('|---|---|---|---|---|---|')
    for mc, loader, side, scenario, status, note in rows:
        lines.append(f'| {mc} | {loader} | {side} | {scenario} | {ICON.get(status, status)} {status} | {note} |')
    lines.append('')
    for status in ('PASSED', 'FAILED', 'KNOWN_FAILURE', 'UNEXPECTED_PASS', 'SKIPPED', 'ERROR'):
        if counts.get(status):
            lines.append(f'- {ICON[status]} {status}: {counts[status]}')

    if blocking_rows:
        lines.append('')
        lines.append('### Blocking problems')
        lines.append('')
        for r in blocking_rows:
            detail = (r.get('detail') or '').replace('\n', ' ')[:300]
            if r['status'] == 'UNEXPECTED_PASS':
                lines.append(f"- 🧹 `{r['mc']} / {r['loader']} / {r['side']} / {r['scenario']}` "
                             f"passed but is marked as a known issue — remove it from "
                             f"`.github/known-issues.json`.")
            else:
                lines.append(f"- {ICON.get(r['status'], '')} `{r['mc']} / {r['loader']} / "
                             f"{r['side']} / {r['scenario']}`: {detail}")

    text = '\n'.join(lines) + '\n'
    summary(text)
    print(text)

    if blocking_rows:
        print(f'\n{len(blocking_rows)} blocking problem(s) found; failing the gate.', file=sys.stderr)
        sys.exit(1)
    print('\nNo blocking problems; gate passes.')


def summary(text):
    path = os.environ.get('GITHUB_STEP_SUMMARY')
    if path:
        with open(path, 'a', encoding='utf-8') as out:
            out.write(text)


if __name__ == '__main__':
    main()
