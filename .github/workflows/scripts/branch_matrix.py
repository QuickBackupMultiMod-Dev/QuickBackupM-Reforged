"""
Build GitHub Actions matrices for multi-branch releases.

Outputs:
  branch_matrix  — one entry per release branch (for build jobs)
  publish_matrix — branch × loader (for publish jobs)
"""
__author__ = 'SkyDy'

import json
import os
from pathlib import Path


SUBPROJECTS = ['neoforge', 'fabric']
RELEASE_BRANCHES_PATH = Path('.github/release-branches.json')


def main():
    with RELEASE_BRANCHES_PATH.open(encoding='utf-8') as f:
        branches = json.load(f)['include']

    branch_matrix = {'include': branches}

    publish_entries = []
    for entry in branches:
        for subproject in SUBPROJECTS:
            publish_entries.append({
                'branch': entry['branch'],
                'java': entry['java'],
                'build_task': entry['build_task'],
                'subproject': subproject,
                'artifact_name': f"build-artifacts-{entry['branch']}",
            })
    publish_matrix = {'include': publish_entries}

    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as out:
        out.write('branch_matrix={}\n'.format(json.dumps(branch_matrix)))
        out.write('publish_matrix={}\n'.format(json.dumps(publish_matrix)))

    print('branch_matrix:')
    print(json.dumps(branch_matrix, indent=2))
    print('publish_matrix:')
    print(json.dumps(publish_matrix, indent=2))


if __name__ == '__main__':
    main()
