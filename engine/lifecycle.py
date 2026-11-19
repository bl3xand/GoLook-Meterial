"""Keep explicit recorder navigation consistent across native child screens."""
import re


def refine_navigation(source, package):
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
