#!/usr/bin/env python3
"""Build the optional dependency-free preview bundle from the actual Vue source."""
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
subprocess.run(['npm.cmd' if os.name == 'nt' else 'npm', 'run', 'build'], cwd=root/'frontend', check=True)
out = root/'ready-web'
out.mkdir(exist_ok=True)
# Remove only previous generated assets, leaving any unknown user files untouched.
for old in out.glob('assets/*'):
    if old.is_file(): old.unlink()
shutil.copytree(root/'frontend/dist', out, dirs_exist_ok=True)
notices = []
for license_file in sorted((root/'frontend/node_modules').rglob('*')):
    if license_file.is_file() and license_file.name.lower() in {'license', 'license.md', 'license.txt'}:
        notices.append(str(license_file.relative_to(root/'frontend/node_modules'))+'\n'+license_file.read_text(errors='replace'))
(out/'THIRD_PARTY_NOTICES.txt').write_text('\n\n'+'\n\n'.join(notices), encoding='utf-8')
target = root/'campus-frontend-ready.zip'
with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for p in sorted(out.rglob('*')):
        if p.is_file(): z.write(p, p.relative_to(root))
    for name in ['START-WEB.bat', 'READY-WEB.md', 'scripts/serve-web.cjs', '替换启动说明.md', 'docs/完整功能说明.md', 'docs/完整功能说明.docx']:
        z.write(root/name, name)
with zipfile.ZipFile(target) as z: assert z.testzip() is None
print(f'Created {target.name}: {target.stat().st_size:,} bytes')
