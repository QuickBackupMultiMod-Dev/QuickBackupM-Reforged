"""
A script to scan through the versions directory and collect all folder names as the subproject list,
then output a json as the github action include matrix
"""
__author__ = 'Fallen_Breath'

import json
import os
import sys


def main():
    matrix_entries = []
    subprojects = ["neoforge", "fabric"]
    for subproject in subprojects:
        matrix_entries.append({
            'subproject': subproject,
        })
    matrix = {'include': matrix_entries}
    with open(os.environ['GITHUB_OUTPUT'], 'w') as f:
        f.write('matrix={}\n'.format(json.dumps(matrix)))

    print('matrix:')
    print(json.dumps(matrix, indent=2))


if __name__ == '__main__':
    main()