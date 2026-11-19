"""Fetch pinned upstream inputs and prepare them only in the temporary build tree."""
import copy
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def resource_key(node):
    return '|'.join((node.tag, node.get('type', ''), node.get('name', '')))


def safe_path(root, relative):
    path = root / relative
    if Path(relative).is_absolute() or not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Unsafe dependency path: ' + relative)
    return path


def prepare(bundle, tools, work, env):
    lock = json.loads((bundle / 'engine/dependencies.json').read_text())
    downloaded = {}
    directory = work / 'dependencies'
    directory.mkdir()
    for name, item in lock['downloads'].items():
        print('Fetching build dependency:', name, flush=True)
        request = urllib.request.Request(item['url'], headers={'User-Agent': 'local-ui-builder/1'})
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read(64 * 1024 * 1024 + 1)
        if len(data) > 64 * 1024 * 1024 or digest(data) != item['sha256']:
            raise ValueError('Dependency hash mismatch: ' + name)
        downloaded[name] = data
        safe_path(directory, item['filename']).write_bytes(data)
    external = directory / 'payload'
    from fontTools.ttLib import TTFont
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    font = TTFont(io.BytesIO(downloaded['symbols']))
    glyphs = font.getGlyphSet(location={'wght': 400, 'FILL': 1, 'GRAD': 0, 'opsz': 24})
    cmap = font.getBestCmap()
    glyph_cache = {}
    for item in lock['files']:
        data = downloaded[item['archive']]
        if item['entry']:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                data = archive.read(item['entry'])
        if 'vector_template' in item:
            template = item['vector_template']
            for glyph in item['glyphs']:
                code = glyph['codepoint']
                if code not in glyph_cache:
                    pen = SVGPathPen(glyphs)
                    glyphs[cmap[code]].draw(TransformPen(pen, (1, 0, 0, -1, 0, font['head'].unitsPerEm)))
                    glyph_cache[code] = pen.getCommands()
                template = template.replace(glyph['marker'], glyph_cache[code])
            data = template.encode()
        if 'xml_edits' in item:
            root = ET.fromstring(data)
            nodes = list(root.iter())
            for edit in item['xml_edits']:
                node = nodes[edit['index']]
                if node.tag != edit['tag_before']:
                    raise ValueError('Dependency layout contract changed.')
                node.tag = edit['tag']
                node.attrib.update(edit['set'])
                for name in edit['remove']:
                    del node.attrib[name]
            ET.indent(root, space='  ')
            data = ET.tostring(root, encoding='utf-8', xml_declaration=True)
        if 'keep' in item:
            original = ET.fromstring(data)
            entries = {resource_key(node): node for node in original}
            result = ET.Element(original.tag, original.attrib)
            for key in item['keep']:
                node = copy.deepcopy(entries[key])
                if key in item['styleable_attrs']:
                    allowed = item['styleable_attrs'][key]
                    node[:] = [child for child in node if child.get('name') in allowed]
                result.append(node)
            ET.indent(result, space='  ')
            data = ET.tostring(result, encoding='utf-8', xml_declaration=True)
        if digest(data) != item['sha256']:
            raise ValueError('Prepared dependency mismatch: ' + item['destination'])
        destination = item['destination']
        if destination.startswith('payload/'):
            path = safe_path(external, destination.removeprefix('payload/'))
        elif destination.startswith('engine/'):
            path = safe_path(tools, destination.removeprefix('engine/'))
        else:
            raise ValueError('Unknown dependency destination: ' + destination)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    # Only the required compatibility subset is compiled alongside Material.
    with zipfile.ZipFile(io.BytesIO(downloaded['insets'])) as archive:
        core = zipfile.ZipFile(io.BytesIO(archive.read('classes.jar')))
        subset = directory / 'insets.jar'
        with zipfile.ZipFile(subset, 'w') as output:
            for name in lock['insets_classes']:
                output.writestr(name, core.read(name))
    with zipfile.ZipFile(io.BytesIO(downloaded['material'])) as archive:
        material = directory / 'material.jar'
        material.write_bytes(archive.read('classes.jar'))
    sdk = Path(env['ANDROID_SDK_ROOT'])
    java = Path(env['JAVA_HOME']) / 'bin/java'
    dex = directory / 'classes.zip'
    subprocess.run([str(java), '-cp', str(sdk / 'build-tools/37.0.0/lib/d8.jar'),
                    'com.android.tools.r8.D8', '--min-api', '24', '--lib',
                    str(sdk / 'platforms/android-36/android.jar'), '--output', str(dex),
                    str(material), str(subset)], env=env, check=True)
    decoded = directory / 'decoded'
    subprocess.run([str(java), '-jar', env['APKTOOL_JAR'], 'd', '--no-res', '--no-assets',
                    '-o', str(decoded), str(dex)], env=env, check=True)
    target = tools / 'material-smali'
    shutil.copytree(decoded / 'smali', target)
    return external
