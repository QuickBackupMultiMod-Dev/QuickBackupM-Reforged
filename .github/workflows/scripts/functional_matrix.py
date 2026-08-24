"""
Turn the harness's resolved (mc, loader) matrix into a GitHub Actions include matrix for the functional
test job, applying the optional dispatch filters.

Reads build/mc-matrix.json (produced by the harness `printMcMatrix` task, which resolves the branch's
support range against the live Mojang manifest) and writes `matrix=` and `branch=` to GITHUB_OUTPUT.

The one transformation of note: the harness emits `java` (the JDK the *game* needs), which we rename to
`game_java` so the workflow can install it alongside the JDK 21 the harness itself runs on.
"""
__author__ = 'SkyDy'

import argparse
import json
import os
import sys
from pathlib import Path


def csv(value):
    """A comma-separated filter; empty means "no filter" (everything)."""
    return [v.strip().lower() for v in (value or '').split(',') if v.strip()]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--matrix', required=True, help='path to mc-matrix.json')
    parser.add_argument('--versions', default='', help='comma-separated MC version filter')
    parser.add_argument('--loaders', default='', help='comma-separated loader filter')
    args = parser.parse_args()

    path = Path(args.matrix)
    if not path.exists():
        # No matrix file means the resolver could not run (no harness, or a resolution error). Emit an
        # empty matrix so the test job skips cleanly rather than the workflow erroring out.
        print(f'{args.matrix} not found; emitting empty matrix', file=sys.stderr)
        write('', '')
        return

    data = json.loads(path.read_text(encoding='utf-8'))
    branch = data.get('branch', '')

    version_filter = csv(args.versions)
    loader_filter = csv(args.loaders)

    include = []
    for entry in data.get('include', []):
        mc = entry['mc']
        loader = entry['loader']
        if version_filter and mc.lower() not in version_filter:
            continue
        if loader_filter and loader.lower() not in loader_filter:
            continue
        include.append({
            'mc': mc,
            'loader': loader,
            'game_java': entry.get('java', 21),
        })

    matrix = json.dumps({'include': include})
    write(matrix, branch)

    print('Resolved functional matrix:')
    print(json.dumps({'include': include}, indent=2))
    if not include:
        print('(empty — the test job will be skipped)')


def write(matrix, branch):
    out_path = os.environ.get('GITHUB_OUTPUT')
    if not out_path:
        # Allow running the script by hand for debugging.
        print(f'matrix={matrix}')
        print(f'branch={branch}')
        return
    with open(out_path, 'a', encoding='utf-8') as out:
        out.write(f'matrix={matrix}\n')
        out.write(f'branch={branch}\n')


if __name__ == '__main__':
    main()
