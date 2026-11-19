#!/usr/bin/env python3
"""Generate androidx R smali stubs (appcompat, cardview, core, recyclerview)
with real resource IDs from the built APK table. Only the fields referenced
by material 1.14 dex (plus full styleables from attrs.xml) are needed, but
generating full per-type field lists is simpler and harmless.
"""
import os, re, sys
import xml.etree.ElementTree as ET
from pathlib import Path

TABLE = sys.argv[2] if len(sys.argv) > 2 else '/tmp/m14_res_table.txt'
FW = sys.argv[3] if len(sys.argv) > 3 else '/tmp/fw_table.txt'
OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/m14_r_smali'

def parse_table(path):
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
fw = parse_table(FW)

# styleables per package: definitions from the matching androidx AAR values.xml
AAR_DIR = Path(__file__).parent / 'material/androidx'
styleables = {}
pkg_of = {}
def collect(path, wanted):
    t = ET.parse(path)
    for el in t.getroot():
        if el.tag == 'declare-styleable':
            n = el.get('name')
            if n in wanted and n not in styleables:
                styleables[n] = [a.get('name') for a in el if a.tag == 'attr']

collect(f'{AAR_DIR}/appcompat-values.xml', {'TextAppearance'})
collect(f'{AAR_DIR}/cardview-values.xml', {'CardView'})
collect(f'{AAR_DIR}/core-values.xml', {'FontFamily'})
collect(f'{AAR_DIR}/recyclerview-values.xml', {'RecyclerView'})

APPCOMPAT = {'TextAppearance'}
CARDVIEW = {'CardView'}
CORE = {'FontFamily'}
RECYCLER = {'RecyclerView'}

def jname(nm):
    return re.sub(r'[.:\-]', '_', nm)

fw_attr = fw.get('attr', {})
app_attr = res.get('attr', {})

def gen_pkg(pkg, styleable_names, types=('attr', 'style')):
    for ty in types:
        entries = res.get(ty, {})
        fields = [(jname(nm), hex(rid)) for nm, rid in sorted(entries.items())]
        p = os.path.join(OUT, pkg.replace('/', os.sep) + f'{os.sep}R${ty}.smali')
        # careful: path join double sep; construct manually
        p = os.path.join(OUT, *pkg.split('/')) + f'/R${ty}.smali'
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w') as f:
            f.write(f'.class public final L{pkg}/R${ty};\n')
            f.write('.super Ljava/lang/Object;\n')
            f.write('.source "R.java"\n\n')
            for name, val in fields:
                f.write(f'.field public static final {name}:I = {val}\n')
    # styleable
    clinit = ['.method static constructor <clinit>()V', '    .registers 5', '']
    idx = {}
    arrays = []
    for sname in sorted(styleable_names):
        attrs = styleables.get(sname)
        if attrs is None:
            continue
        resolved = []
        for an in attrs:
            if an.startswith('android:'):
                rid = fw_attr.get(an.split(':', 1)[1].split('/')[-1])
            else:
                rid = app_attr.get(an.split('/')[-1])
            if rid is None:
                raise ValueError(f'Unresolved attribute {sname}.{an}')
            resolved.append((rid, an))
        resolved.sort()
        arr = [rid for rid, _ in resolved]
        for i, (_, an) in enumerate(resolved):
            idx[f'{sname}_{jname(an)}'] = i
        arrays.append((sname, arr))
    for sname, arr in arrays:
        clinit.append(f'    const v0, {hex(len(arr))}')
        clinit.append('    new-array v0, v0, [I')
        for i, rid in enumerate(arr):
            if rid is None:
                continue
            clinit.append(f'    const v1, {hex(rid)}')
            clinit.append(f'    const v2, {hex(i)}')
            clinit.append('    aput v1, v0, v2')
        clinit.append(f'    sput-object v0, L{pkg}/R$styleable;->{sname}:[I')
    clinit.append('    return-void')
    clinit.append('.end method')
    p = os.path.join(OUT, *pkg.split('/')) + '/R$styleable.smali'
    with open(p, 'w') as f:
        f.write(f'.class public final L{pkg}/R$styleable;\n')
        f.write('.super Ljava/lang/Object;\n')
        f.write('.source "R.java"\n\n')
        for name, i in sorted(idx.items()):
            f.write(f'.field public static final {name}:I = {hex(i)}\n')
        for sname, _ in arrays:
            f.write(f'.field public static final {sname}:[I\n')
        f.write('\n')
        f.write('\n'.join(clinit))
        f.write('\n')
    # plain R holder
    p = os.path.join(OUT, *pkg.split('/')) + '/R.smali'
    with open(p, 'w') as f:
        f.write(f'.class public final L{pkg}/R;\n')
        f.write('.super Ljava/lang/Object;\n')
        f.write('.source "R.java"\n')
    print(f"{pkg}: {len(arrays)} styleables")

gen_pkg('androidx/appcompat', APPCOMPAT)
gen_pkg('androidx/cardview', CARDVIEW)
gen_pkg('androidx/core', CORE)
gen_pkg('androidx/recyclerview', RECYCLER)
