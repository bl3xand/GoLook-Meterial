#!/usr/bin/env python3
"""Generate com/google/android/material/R smali classes with real resource IDs.

Sources:
- app resource table (aapt2 dump resources of built APK): final 0x7f IDs
- framework table (aapt2 dump resources of framework 1.apk): 0x01 IDs for
  android: attrs used inside styleable arrays
- declare-styleable definitions collected from the whole merged tree
  (app attrs.xml + AAR values.xml) so transitive-lib styleables (CardView,
  FontFamily, ...) are included

Field naming follows Gradle R rules: '.' and ':' and '-' -> '_'.
"""
import os, re, sys
import xml.etree.ElementTree as ET
from pathlib import Path

TABLE = sys.argv[1]
FW_TABLE = sys.argv[2]
OUT = sys.argv[3]

def parse_table(path, pkg_filter=None):
    res = {}
    name_re = re.compile(r'resource (0x[0-9a-f]+) (\w+)/(\S+)')
    with open(path) as f:
        for line in f:
            m = name_re.search(line)
            if m:
                rid, ty, nm = m.groups()
                res.setdefault(ty, {})[nm] = int(rid, 16)
    return res

res = parse_table(TABLE)
fw = parse_table(FW_TABLE)

# collect styleables from both AAR values and the app tree
styleables = {}
def collect_styleables(values_path):
    try:
        t = ET.parse(values_path)
    except Exception:
        return
    for el in t.getroot():
        if el.tag == 'declare-styleable':
            n = el.get('name')
            if n and n not in styleables:
                styleables[n] = [a.get('name') for a in el if a.tag == 'attr']

collect_styleables(str(Path(__file__).parent / 'material/material-values.xml'))
TREE = str(Path(__file__).resolve().parents[1] / 'apktool/res')
for root, dirs, files in os.walk(TREE):
    for fn in files:
        if fn.endswith('.xml') and os.path.basename(root).startswith('values'):
            collect_styleables(os.path.join(root, fn))

types_used = ['anim', 'animator', 'attr', 'bool', 'color', 'dimen', 'drawable',
              'id', 'integer', 'interpolator', 'layout', 'string', 'style',
              'macro', 'array', 'plurals', 'font', 'mipmap']

os.makedirs(OUT, exist_ok=True)

def smali_path(cls):
    return os.path.join(OUT, cls.replace('/', os.sep) + '.smali')

def jname(nm):
    return re.sub(r'[.:\-]', '_', nm)

count_fields = 0
for ty in types_used:
    entries = res.get(ty, {})
    fields = [(jname(nm), hex(rid)) for nm, rid in sorted(entries.items())]
    if fields:
        p = smali_path(f'com/google/android/material/R${ty}')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w') as f:
            f.write(f'.class public final Lcom/google/android/material/R${ty};\n')
            f.write('.super Ljava/lang/Object;\n')
            f.write('.source "R.java"\n\n')
            for name, val in fields:
                f.write(f'.field public static final {name}:I = {val}\n')
        count_fields += len(fields)

p = smali_path('com/google/android/material/R')
with open(p, 'w') as f:
    f.write('.class public final Lcom/google/android/material/R;\n')
    f.write('.super Ljava/lang/Object;\n')
    f.write('.source "R.java"\n')

# styleable: index fields + arrays built in <clinit>
fw_attr = fw.get('attr', {})
app_attr = res.get('attr', {})

index_fields = {}
clinit = ['.method static constructor <clinit>()V', '    .registers 5', '']
arrays = []
for sname, attrs in sorted(styleables.items()):
    resolved = []
    for an in attrs:
        if an.startswith('android:'):
            rid = fw_attr.get(an.split(':', 1)[1].split('/')[-1])
        else:
            rid = app_attr.get(an.split('/')[-1])
        if rid is None:
            raise ValueError(f'Unresolved attribute {sname}.{an}')
        resolved.append((rid, an))
    # Android's typed-array lookup requires resource IDs in ascending order.
    # Declaration order from an AAR is not the final application's ID order.
    resolved.sort()
    arr = [rid for rid, _ in resolved]
    for i, (_, an) in enumerate(resolved):
        index_fields[f'{sname}_{jname(an)}'] = i
    arrays.append((sname, arr))

for sname, arr in arrays:
    n = len(arr)
    clinit.append(f'    const v0, {hex(n)}')
    clinit.append('    new-array v0, v0, [I')
    for i, rid in enumerate(arr):
        if rid is None:
            continue  # leave 0
        clinit.append(f'    const v1, {hex(rid)}')
        clinit.append(f'    const v2, {hex(i)}')
        clinit.append('    aput v1, v0, v2')
    clinit.append(f'    sput-object v0, Lcom/google/android/material/R$styleable;->{sname}:[I')
clinit.append('    return-void')
clinit.append('.end method')

p = smali_path('com/google/android/material/R$styleable')
with open(p, 'w') as f:
    f.write('.class public final Lcom/google/android/material/R$styleable;\n')
    f.write('.super Ljava/lang/Object;\n')
    f.write('.source "R.java"\n\n')
    for name, i in sorted(index_fields.items()):
        f.write(f'.field public static final {name}:I = {hex(i)}\n')
    for sname, _ in arrays:
        f.write(f'.field public static final {sname}:[I\n')
    f.write('\n')
    f.write('\n'.join(clinit))
    f.write('\n')

print(f"{count_fields} scalar fields, {len(arrays)} styleables, {len(index_fields)} index fields")
