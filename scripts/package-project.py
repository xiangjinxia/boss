#!/usr/bin/env python3
"""Package source and reviewed deliverables, excluding ignored caches and credentials."""
from pathlib import Path
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parent.parent
target = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root.parent/'拾光校园二手交易平台-课程项目.zip'
names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root).split(b'\0')
paths = sorted({name.decode('utf-8') for name in names if name})
assert paths, 'No project files found'
with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for name in paths:
        p = root/name
        if p.is_file() and p.resolve() != target:
            assert p.resolve().is_relative_to(root), f'File escapes project: {name}'
            assert p.name == '.env.example' or not p.name.startswith('.env'), 'Refusing to package environment credentials'
            z.write(p, 'campus-exchange/'+name)
with zipfile.ZipFile(target) as z:
    assert z.testzip() is None, 'Archive integrity check failed'
    assert 'campus-exchange/README.md' in z.namelist()
    assert 'campus-exchange/frontend/package-lock.json' in z.namelist()
print(f'{target}\n{target.stat().st_size:,} bytes; source archive integrity verified')
