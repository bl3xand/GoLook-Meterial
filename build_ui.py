#!/usr/bin/env python3
"""Build the UI update from a supported local input."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import struct
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
ET.register_namespace('app', 'http://schemas.android.com/apk/res-auto')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def normalize_elf_alignment(data, alignment=16384):
    """Lower oversized ELF64 LOAD alignment without modifying code or offsets."""
    if data[:6] != b'\x7fELF\x02\x01' or len(data) < 64:
        raise ValueError('Expected a little-endian ELF64 library.')
    result = bytearray(data)
    offset = struct.unpack_from('<Q', data, 32)[0]
    stride, count = struct.unpack_from('<HH', data, 54)
    if stride < 56 or offset + stride * count > len(data):
        raise ValueError('Invalid ELF program-header table.')
    for index in range(count):
        start = offset + index * stride
        kind = struct.unpack_from('<I', data, start)[0]
        old_alignment = struct.unpack_from('<Q', data, start + 48)[0]
        if kind == 1 and old_alignment > alignment:
            file_offset, address = struct.unpack_from('<QQ', data, start + 8)
            if (file_offset - address) % alignment:
                raise ValueError('ELF LOAD segment cannot use the requested alignment.')
            struct.pack_into('<Q', result, start + 48, alignment)
    return bytes(result)


def signature(element):
    return [element.tag, sorted(element.attrib.items()),
            element.text if element.text and element.text.strip() else '',
            [signature(child) for child in element]]


def element_sha(element):
    return sha(json.dumps(signature(element), ensure_ascii=False).encode())


def resource_key(element):
    return '|'.join((element.tag, element.get('type', ''), element.get('name', '')))


def inside(root, relative):
    path = root / relative
    if path.resolve().is_relative_to(root.resolve()) and not Path(relative).is_absolute():
        return path
    raise ValueError('Unsafe patch path: ' + relative)


def apply_changes(source, profile, external_files=None):
    for change in profile['changes']:
        path = inside(source, change['path'])
        kind = change['kind']
        if kind == 'resources':
            tree = ET.parse(path)
            container = tree.getroot()
            entries = {resource_key(item): item for item in container}
            for item in change['entries']:
                previous = entries.get(item['key'])
                actual = element_sha(previous) if previous is not None else None
                if actual != item['before']:
                    raise ValueError('Resource precondition failed: ' + change['path'] + ':' + item['key'])
                if previous is not None:
                    container.remove(previous)
                if item['xml'] is not None:
                    container.append(ET.fromstring(item['xml']))
            ET.indent(tree, space='  ')
            tree.write(path, encoding='utf-8', xml_declaration=True)
            actual = sorted((resource_key(item), element_sha(item)) for item in container)
            if sha(json.dumps(actual).encode()) != change['after']:
                raise ValueError('Resource result failed: ' + change['path'])
            continue
        original = path.read_bytes() if path.is_file() else None
        if (sha(original) if original is not None else None) != change['before']:
            raise ValueError('File precondition failed: ' + change['path'])
        if kind == 'delete':
            path.unlink()
            continue
        if kind == 'elf-alignment':
            result = normalize_elf_alignment(original, change['alignment'])
        elif kind == 'file':
            payload = inside(ROOT / 'patches/files', change['path'])
            if not payload.is_file() and external_files is not None:
                payload = inside(external_files, change['path'])
            result = payload.read_bytes()
        elif kind == 'text':
            text = original.decode()
            if change.get('normalize_newlines'):
                text = text.replace('\r\n', '\n').replace('\r', '\n')
            lines = text.splitlines(keepends=True)
            for edit in reversed(change['edits']):
                lines[edit['start']:edit['end']] = edit['insert']
            result = ''.join(lines).encode()
        else:
            raise ValueError('Unknown patch operation: ' + kind)
        if sha(result) != change['after']:
            raise ValueError('File result failed: ' + change['path'])
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(result)


def resolve_input(path, profile):
    """Identify APK containers without a checksum or version allowlist."""
    result = {'sha256': file_sha(path)}
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate archive entries.')
        if 'AndroidManifest.xml' in names and 'classes.dex' in names:
            return dict(result, kind='apk')
        bases, native = [], []
        for name in names:
            if not name.lower().endswith('.apk'):
                continue
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as inner:
                entries = inner.namelist()
                if 'classes.dex' in entries and 'resources.arsc' in entries:
                    bases.append(name)
                elif any(item.startswith('lib/arm64-v8a/') and item.endswith('.so') for item in entries):
                    native.append(name)
        if len(bases) != 1 or not native:
            raise ValueError('Provide an XAPK with one base APK and ARM64 native libraries, or a standalone ARM64 APK.')
        return dict(result, kind='xapk', base=bases[0], native_splits=native)


def read_native_libraries(path):
    libraries = {}
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            name = item.filename
            if not name.startswith('lib/') or not name.endswith('.so'):
                continue
            parts = Path(name).parts
            if len(parts) != 3 or parts[1] != 'arm64-v8a' or name in libraries:
                raise ValueError('Unexpected or duplicate native library: ' + name)
            libraries[name] = archive.read(item)
    return libraries


def unpack_input(path, match, work):
    """Copy selected APKs without extracting untrusted archive paths."""
    base = work / 'input.apk'
    if match['kind'] == 'apk':
        shutil.copyfile(path, base)
        if file_sha(base) != match['sha256']:
            raise ValueError('Input APK changed while preparing the build.')
        libraries = read_native_libraries(base)
    else:
        with zipfile.ZipFile(path) as archive:
            base.write_bytes(archive.read(match['base']))
            libraries = read_native_libraries(base)
            for index, name in enumerate(match['native_splits']):
                split = work / ('native-' + str(index) + '.apk')
                split.write_bytes(archive.read(name))
                for entry, data in read_native_libraries(split).items():
                    if entry in libraries:
                        raise ValueError('Native library occurs in multiple splits: ' + entry)
                    libraries[entry] = data
        if file_sha(path) != match['sha256']:
            raise ValueError('Input XAPK changed while preparing the build.')
    if not libraries:
        raise ValueError('ARM64 native libraries are missing; provide the complete XAPK.')
    return base, libraries


def normalize_split_metadata(source):
    """Remove split-delivery markers while retaining other manifest content."""
    android = '{http://schemas.android.com/apk/res/android}'
    manifest = source / 'AndroidManifest.xml'
    text = manifest.read_text()
    root = ET.fromstring(text)
    application = root.find('application')
    if application is None:
        raise ValueError('The input manifest has no application element.')
    remove = {'com.android.vending.splits.required', 'com.android.vending.splits',
              'com.android.vending.derived.apk.id', 'com.android.stamp.source', 'com.android.stamp.type'}
    split_resources = []
    for item in application.findall('meta-data'):
        name = item.get(android + 'name')
        if name not in remove:
            continue
        resource = item.get(android + 'resource', '')
        match = re.fullmatch(r'@xml/([a-z0-9_]+)', resource)
        if match:
            split_resources.append(match[1])
        pattern = r'(?m)^[ \t]*<meta-data\b[^>]*android:name="' + re.escape(name) + r'"[^>]*/>[ \t]*\r?\n?'
        text = re.sub(pattern, '', text)
    text = re.sub(r' android:(?:requiredSplitTypes|splitTypes|isSplitRequired)="[^"]*"', '', text)
    manifest.write_text(text)
    public = source / 'res/values/public.xml'
    for name in split_resources:
        inside(source, 'res/xml/' + name + '.xml').unlink(missing_ok=True)
        if public.is_file():
            data = public.read_text()
            data = re.sub(r'(?m)^[ \t]*<public\b(?=[^>]*type="xml")(?=[^>]*name="' + re.escape(name) + r'")[^>]*/>[ \t]*\r?\n?', '', data)
            public.write_text(data)
    config = source / 'apktool.yml'
    data = config.read_text()
    if not re.search(r'(?m)^- so$', data):
        if 'doNotCompress: []' in data:
            data = data.replace('doNotCompress: []', 'doNotCompress:\n- so')
        elif 'doNotCompress:\n' in data:
            data = data.replace('doNotCompress:\n', 'doNotCompress:\n- so\n', 1)
        else:
            data += '\ndoNotCompress:\n- so\n'
        config.write_text(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('inspect', 'build'))
    parser.add_argument('input', type=Path)
    parser.add_argument('-o', '--output', type=Path)
    parser.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
    parser.add_argument('--apktool', type=Path, default=os.environ.get('APKTOOL_JAR'))
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--llvm-strip', type=Path,
                        default=os.environ.get('LLVM_STRIP') or shutil.which('llvm-strip'),
                        help='LLVM strip executable used to remove offline native debug information')
    parser.add_argument('--keystore', type=Path)
    parser.add_argument('--alias')
    parser.add_argument('--ks-pass-env', default='UI_STORE_PASSWORD')
    parser.add_argument('--key-pass-env')
    parser.add_argument('--unsigned', action='store_true')
    parser.add_argument('--work-dir', type=Path)
    args = parser.parse_args()
    profile = json.loads((ROOT / 'patches/profile.json').read_text())
    match = resolve_input(args.input, profile)
    if args.command == 'inspect':
        print(json.dumps({'supported_version': profile['version'], 'input': match,
                          'changes': len(profile['changes'])}, indent=2))
        return
    if not args.output or not args.sdk or not args.apktool:
        parser.error('build requires --output, --sdk and --apktool (or their documented environment variables).')
    if not args.llvm_strip or not args.llvm_strip.is_file():
        parser.error('build requires llvm-strip on PATH, LLVM_STRIP, or --llvm-strip /path/to/llvm-strip.')
    if args.output.exists() or args.output.resolve() == args.input.resolve():
        parser.error('Output must be a new file; input and existing files are never overwritten.')
    if args.unsigned and args.keystore:
        parser.error('--unsigned and --keystore are mutually exclusive.')
    if not args.unsigned and (not args.keystore or not args.alias or not os.environ.get(args.ks_pass_env)):
        parser.error('Signing requires --keystore, --alias and the password environment variable; or use --unsigned.')
    args.output = args.output.resolve()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    java_bin = args.java_home / 'bin' if args.java_home else Path(shutil.which('java') or '').resolve().parent
    java = java_bin / 'java'
    version = subprocess.check_output([str(java), '-jar', str(args.apktool.resolve()), '--version'], text=True).strip()
    if version != profile['apktool_version']:
        raise ValueError('This patch requires apktool ' + profile['apktool_version'] + ', found ' + version)
    from engine.build import build_apk
    with tempfile.TemporaryDirectory(prefix='material-ui-local-', dir=args.work_dir or args.output.parent) as temporary:
        work = Path(temporary)
        project = work / 'project'
        source = project / 'apktool'
        framework = work / 'framework'
        local_input, native_payloads = unpack_input(args.input, match, work)
        subprocess.run([str(java), '-Xmx2g', '-jar', str(args.apktool.resolve()), 'd', '-p', str(framework),
                        '-o', str(source), str(local_input)], check=True)
        normalize_split_metadata(source)
        target_package = ET.parse(source / 'AndroidManifest.xml').getroot().get('package', '')
        if not re.fullmatch(r'[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+', target_package):
            raise ValueError('Input manifest has an invalid application package.')
        for name, data in native_payloads.items():
            path = inside(source, name)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        env = dict(os.environ, TARGET_PACKAGE=target_package, ANDROID_SDK_ROOT=str(args.sdk.resolve()),
                   APKTOOL_JAR=str(args.apktool.resolve()), JAVA_HOME=str(java_bin.parent),
                   PATH=str(java_bin) + os.pathsep + os.environ.get('PATH', ''))
        shutil.copytree(ROOT / 'engine', project / 'tools',
                        ignore=shutil.ignore_patterns('material', 'material-smali', '__pycache__', 'supported.json'))
        from engine.dependencies import prepare
        external_files = prepare(ROOT, project / 'tools', work, env)
        apply_changes(source, profile, external_files)
        from engine.cleanup import remove_diagnostics
        removed_diagnostics = remove_diagnostics(source)
        from engine.locales import materialize_fallbacks
        language_fallbacks = materialize_fallbacks(source)
        alignment_files = []
        for change in profile['changes']:
            if change['kind'] == 'elf-alignment':
                name = change['path']
                native_payloads[name] = normalize_elf_alignment(native_payloads[name], change['alignment'])
                alignment_files.append(name)
        from engine.native import strip_debug
        # llvm-strip can be an argv[0]-sensitive symlink to llvm-objcopy.
        native_debug = strip_debug(source, native_payloads, args.llvm_strip.absolute(), work)
        report = build_apk(project, work, framework, args, env, native_payloads, alignment_files)
        report['native_debug_removal'] = native_debug
        report['removed_diagnostic_resources'] = removed_diagnostics
        report.update(input_format=match['kind'], input_sha256=match['sha256'],
                      supported_version=profile['version'], language_fallbacks=language_fallbacks)
        args.output.with_suffix(args.output.suffix + '.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        sys.exit(1)
