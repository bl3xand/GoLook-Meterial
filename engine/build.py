"""Assemble and sign the patched, locally decoded application and UI adapters."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET


def publish_new_file(source, output):
    """Publish without replacing a file created while the build was running."""
    with tempfile.NamedTemporaryFile(prefix='.material-ui-ready-', dir=output.parent, delete=False) as stream:
        staged = Path(stream.name)
        try:
            with source.open('rb') as original:
                shutil.copyfileobj(original, stream)
            stream.flush()
            os.fsync(stream.fileno())
            os.link(staged, output)
        finally:
            staged.unlink(missing_ok=True)


def build_apk(project, work, framework, args, env, native_payloads, alignment_files=()):
    source = project / 'apktool'
    tools = project / 'tools'
    sdk = Path(env['ANDROID_SDK_ROOT'])
    bt = sdk / 'build-tools/37.0.0'
    java = Path(env['JAVA_HOME']) / 'bin/java'
    apktool = Path(env['APKTOOL_JAR'])
    manifest = ET.parse(source / 'AndroidManifest.xml').getroot()
    # Compressed libraries are extracted by Android's package installer.
    manifest.find('application').set(
        '{http://schemas.android.com/apk/res/android}extractNativeLibs', 'true')
    ET.ElementTree(manifest).write(source / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    android_name = '{http://schemas.android.com/apk/res/android}name'
    activities = {node.get(android_name, '') for node in manifest.findall('application/activity')}
    for suffix in ('devmanager.DeviceManagerActivity', 'preview.PreviewActivity',
                   'album.AlbumActvity', 'videoclip.VideoClipsActivity', 'appsettings.AppPreferActivity'):
        if env['TARGET_PACKAGE'] + '.activitys.' + suffix not in activities:
            raise ValueError('Required production screen missing: ' + suffix)
    for path in (bt / 'aapt2', bt / 'zipalign', bt / 'apksigner', sdk / 'platforms/android-36/android.jar'):
        if not path.is_file():
            raise ValueError('Required Android SDK component missing: ' + str(path))
    (project / 'dist').mkdir()

    def run(*command, stdout=None):
        subprocess.run([str(item) for item in command], env=env, check=True, stdout=stdout)

    from engine.media import refine
    refine(source, env['TARGET_PACKAGE'])
    from engine.geometry import refine_geometry
    refine_geometry(source, env['TARGET_PACKAGE'])
    from engine.share import refine_share
    refine_share(source)
    from engine.thumbnails import refine_thumbnails
    refine_thumbnails(source)
    from engine.chrome import refine_chrome
    refine_chrome(source, env['TARGET_PACKAGE'])
    from engine.forms import refine_feedback
    refine_feedback(source)
    from engine.loading import refine_loading
    refine_loading(source)
    from engine.lifecycle import refine_navigation
    refine_navigation(source, env['TARGET_PACKAGE'])
    from engine.home import refine_home
    refine_home(source, env['TARGET_PACKAGE'])
    run(sys.executable, tools / 'ui/compile.py')
    raw = work / 'raw.apk'
    run(java, '-Xmx2g', '-jar', apktool, 'b', '-p', framework, '-j', '4', source, '-o', raw)
    generated = work / 'generated-r'
    table, fw_table = work / 'resources.txt', work / 'framework.txt'
    for apk, destination in ((raw, table), (framework / '1.apk', fw_table)):
        with destination.open('w') as stream:
            run(bt / 'aapt2', 'dump', 'resources', apk, stdout=stream)
    run(sys.executable, tools / 'gen_r_smali.py', table, fw_table, generated)
    run(sys.executable, tools / 'gen_androidx_r.py', generated, table, fw_table)
    material = work / 'material'
    material.mkdir()
    shutil.copy2(tools / 'material-apktool.yml', material / 'apktool.yml')
    shutil.copytree(tools / 'material-smali', material / 'smali')
    for path in generated.rglob('*.smali'):
        target = material / 'smali' / path.relative_to(generated)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
    material_apk = work / 'material.apk'
    run(java, '-Xmx1g', '-jar', apktool, 'b', '-p', framework, '-j', '4', material, '-o', material_apk)
    with zipfile.ZipFile(material_apk) as archive:
        material_dex = archive.read('classes.dex')
    complete = work / 'complete.apk'
    with zipfile.ZipFile(raw) as original, zipfile.ZipFile(complete, 'w') as archive:
        if 'classes4.dex' in original.namelist():
            raise ValueError('Unexpected input dex layout; refusing to overwrite a class archive.')
        for entry in original.infolist():
            native = entry.filename in native_payloads
            archive.writestr(entry.filename, original.read(entry),
                             compress_type=zipfile.ZIP_DEFLATED if native else entry.compress_type,
                             compresslevel=9 if native else None)
        archive.writestr('classes4.dex', material_dex, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(complete) as archive:
        for name, data in native_payloads.items():
            if archive.read(name) != data:
                raise ValueError('Native library changed: ' + name)
        libraries = {name for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
        if libraries != set(native_payloads):
            raise ValueError('Unexpected native-library set; no ABI substitutions are supported.')
        for name in archive.namelist():
            if name.endswith('.dex') and (name == 'classes5.dex' or any(token in archive.read(name)
                    for token in (b'10.0.2.2', b'golookConnectMock', (env['TARGET_PACKAGE'].replace('.', '/') + '/qa/LayoutPreviewActivity').encode(),
                                  b'FirmwareUpgradeEeasytechActivity', (env['TARGET_PACKAGE'].replace('.', '/') + '/activitys/DevOtaHttp').encode(),
                                  *(('L' + env['TARGET_PACKAGE'].replace('.', '/') + '/activitys/' + item + ';').encode()
                                    for item in ('test/TestActivity', 'debug/DbgPreferActivity',
                                                 'debug/DebugPreferActivityOld', 'debug/DevInfoCodeActivity'))))):
                raise ValueError('Review-only or removed recorder-update code leaked into output: ' + name)
    aligned = work / 'aligned.apk'
    run(bt / 'zipalign', '-f', '-P', '16', '4', complete, aligned)
    result = aligned
    if not args.unsigned:
        result = work / 'signed.apk'
        command = [bt / 'apksigner', 'sign', '--ks', args.keystore.resolve(),
                   '--ks-key-alias', args.alias, '--ks-pass', 'env:' + args.ks_pass_env,
                   '--v4-signing-enabled', 'false', '--out', result]
        if args.key_pass_env:
            command.extend(['--key-pass', 'env:' + args.key_pass_env])
        command.append(aligned)
        run(*command)
        run(bt / 'apksigner', 'verify', result)
    run(bt / 'zipalign', '-c', '-P', '16', '4', result)
    # Move within the output filesystem only after all integrity/signature checks pass.
    publish_new_file(result, args.output)
    digest = hashlib.sha256()
    with args.output.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return {'output': str(args.output), 'sha256': digest.hexdigest(), 'signed': not args.unsigned,
            'output_size_bytes': args.output.stat().st_size,
            'apk_zip_alignment': '16 KiB', 'native_libraries': len(native_payloads),
            'native_packaging': {'compressed': True, 'extract_on_install': True},
            'native_16k_page_support': False,
            'native_machine_code_preserved': True,
            'elf_header_alignment_normalized': list(alignment_files),
            'abis': sorted({name.split('/')[1] for name in native_payloads}),
            'mock_or_qa_code': False, 'apktool': '2.12.1', 'build_tools': '37.0.0'}
