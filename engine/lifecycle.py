"""Keep explicit recorder navigation consistent across native child screens."""
import re
import xml.etree.ElementTree as ET


def _navigation_animations(source):
    """Pair forward/back motion instead of inheriting unrelated platform defaults."""
    android = '{http://schemas.android.com/apk/res/android}'
    motions = {
        'open_enter': ('100%p', '0%p', '0', '1'),
        'open_exit': ('0%p', '-12%p', '1', '0'),
        'close_enter': ('-12%p', '0%p', '0', '1'),
        'close_exit': ('0%p', '100%p', '1', '0'),
    }
    for qualifier, mirror in (('anim', False), ('anim-ldrtl', True)):
        folder = source / 'res' / qualifier
        folder.mkdir(exist_ok=True)
        for name, (start, end, alpha_start, alpha_end) in motions.items():
            if mirror:
                start = start[1:] if start.startswith('-') else '-' + start
                end = end[1:] if end.startswith('-') else '-' + end
            root = ET.Element('set', {
                android + 'duration': '260',
                android + 'interpolator': '@android:interpolator/fast_out_slow_in',
                android + 'shareInterpolator': 'true',
            })
            ET.SubElement(root, 'translate', {
                android + 'fromXDelta': start, android + 'toXDelta': end})
            ET.SubElement(root, 'alpha', {
                android + 'fromAlpha': alpha_start, android + 'toAlpha': alpha_end})
            ET.ElementTree(root).write(folder / ('material_ui_nav_' + name + '.xml'),
                                       encoding='utf-8', xml_declaration=True)
    resources = ET.Element('resources')
    style = ET.SubElement(resources, 'style', {
        'name': 'Animation.MaterialUi.Navigation', 'parent': '@android:style/Animation.Activity'})
    for action in ('open', 'close'):
        for phase in ('enter', 'exit'):
            item = ET.SubElement(style, 'item', {
                'name': 'android:activity' + action.title() + phase.title() + 'Animation'})
            item.text = '@anim/material_ui_nav_' + action + '_' + phase
    ET.ElementTree(resources).write(source / 'res/values/material_ui_navigation.xml',
                                    encoding='utf-8', xml_declaration=True)
    found = False
    for path in (source / 'res').glob('values*/*.xml'):
        tree = ET.parse(path)
        changed = False
        for theme in tree.getroot().findall('style'):
            if theme.get('name') != 'Theme.MaterialUi':
                continue
            item = next((node for node in theme.findall('item')
                         if node.get('name') == 'android:windowAnimationStyle'), None)
            if item is None:
                item = ET.SubElement(theme, 'item', {'name': 'android:windowAnimationStyle'})
            item.text = '@style/Animation.MaterialUi.Navigation'
            found = changed = True
        if changed:
            tree.write(path, encoding='utf-8', xml_declaration=True)
    if not found:
        raise ValueError('Application navigation theme missing.')


def refine_navigation(source, package):
    _navigation_animations(source)
    classes = source / 'smali_classes2' / package.replace('.', '/') / 'activitys'
    manager = classes / 'devmanager/DeviceManagerActivity.smali'
    text = manager.read_text()
    owner = 'L' + package.replace('.', '/') + '/activitys/devmanager/DeviceManagerActivity;'
    match = re.search(r'(?ms)^\.method [^\n]* onResume\(\)V\n.*?^\.end method', text)
    anchor = f'    iput-boolean v0, p0, {owner}->isStateSaved:Z'
    if not match or match.group().count(anchor) != 1:
        raise ValueError('Home resume navigation guard missing.')
    # Resume means the child no longer occupies the foreground. Its result may
    # be absent if the original child was removed by the native activity manager.
    body = match.group().replace(anchor, anchor + f'\n    iput-boolean v0, p0, {owner}->isGoPreview:Z')
    text = text[:match.start()] + body + text[match.end():]
    manager.write_text(text)

    album = classes / 'album/AlbumActvity.smali'
    text = album.read_text()
    match = re.search(r'(?ms)^\.method private exitAcitivty\(\)V\n.*?^\.end method', text)
    launch = ('    invoke-direct {p0, v0}, L' + package.replace('.', '/')
              + '/activitys/album/AlbumActvity;->goDeviceManagerActivity(I)V')
    if not match or match.group().count(launch) != 1:
        raise ValueError('Local gallery home return boundary missing.')
    # A result-based caller is already below this gallery in the activity stack.
    # Launching it again turns Back into a forward transition and duplicates home.
    reuse = ('    invoke-virtual {p0}, Landroid/app/Activity;->getCallingActivity()Landroid/content/ComponentName;\n'
             '    move-result-object v1\n'
             '    if-eqz v1, :material_ui_launch_home\n'
             '    invoke-virtual {v1}, Landroid/content/ComponentName;->getClassName()Ljava/lang/String;\n'
             '    move-result-object v1\n'
             f'    const-string v2, "{package}.activitys.devmanager.DeviceManagerActivity"\n'
             '    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n'
             '    move-result v1\n'
             '    if-nez v1, :material_ui_reuse_home\n'
             '    :material_ui_launch_home\n' + launch + '\n'
             '    :material_ui_reuse_home')
    body = match.group().replace(launch, reuse)
    album.write_text(text[:match.start()] + body + text[match.end():])

    # The native fallback probe reports zero after successful protocol discovery;
    # direct known-subnet probes report a positive protocol number. Both succeed.
    callback = classes / 'devmanager/DeviceManagerActivity$15$1.smali'
    text = callback.read_text()
    branch = re.compile(r'->val\$protocolCode:I'
                        r'(?:\n(?:[ \t]*|    \.line \d+|    const[^\n]*))*'
                        r'\n    (?P<test>if-lez v1,)')
    matches = list(branch.finditer(text))
    if len(matches) != 1:
        raise ValueError('Native recorder handshake result boundary missing.')
    match = matches[0]
    text = text[:match.start('test')] + 'if-ltz v1,' + text[match.end('test'):]
    callback.write_text(text)

    preview = classes / 'preview/PreviewActivity.smali'
    text = preview.read_text()
    match = re.search(r'(?ms)^\.method [^\n]* onCreate\(Landroid/os/Bundle;\)V\n.*?^\.end method', text)
    if not match:
        raise ValueError('Recorder creation callback missing.')
    body = match.group().replace('    return-void',
            '    invoke-static {p0}, Llocal/materialui/RecorderSession;->begin(Landroid/app/Activity;)V\n'
            '    return-void')
    text = text[:match.start()] + body + text[match.end():]
    preview.write_text(text)

    native = 'Lcom/hisilicon/cameralib/utils/Utility;->isCameraWifi(Ljava/lang/String;)Z'
    replacement = 'Llocal/materialui/RecorderSession;->sameWifi(Ljava/lang/String;)Z'
    for name in ('DevicePreferActivity$1.smali', 'DevicePreferActivity$SettingsFragment$1$1.smali'):
        path = classes / 'devsettings' / name
        text = path.read_text()
        if text.count(native) != 1:
            raise ValueError('Recorder settings return guard missing: ' + name)
        path.write_text(text.replace(native, replacement))
