"""Attach an on-demand, private MP4 worker without publisher startup in that process."""
import re
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'


def refine_share(source):
    manifest = source / 'AndroidManifest.xml'
    tree = ET.parse(manifest)
    root = tree.getroot()
    app = root.find('application')
    name = app.get(A + 'name')
    if name.startswith('.'):
        name = root.get('package') + name
    matches = list(source.glob('smali*/' + name.replace('.', '/') + '.smali'))
    if len(matches) != 1:
        raise ValueError('Application startup contract missing.')
    path = matches[0]
    text = path.read_text()
    method = re.search(r'(?ms)^\.method public onCreate\(\)V\n.*?^\.end method', text)
    if not method or ':material_share_main' in method.group():
        raise ValueError('Application startup hook missing or already applied.')
    body = method.group()
    count = re.search(r'(?m)^    \.(locals|registers) (\d+)$', body)
    if not count:
        raise ValueError('Application startup registers missing.')
    minimum = 1 if count[1] == 'locals' else 2
    declaration = '    .' + count[1] + ' ' + str(max(minimum, int(count[2])))
    guard = ('\n    invoke-static {p0}, Llocal/materialui/Mp4Share;->worker(Landroid/content/Context;)Z\n'
             '    move-result v0\n    if-eqz v0, :material_share_main\n'
             '    return-void\n    :material_share_main\n')
    body = body[:count.start()] + declaration + guard + body[count.end():]
    path.write_text(text[:method.start()] + body + text[method.end():])
    if any(node.get(A + 'name') == 'local.materialui.ShareRemuxService' for node in app.findall('service')):
        raise ValueError('Remux service already registered.')
    ET.SubElement(app, 'service', {A + 'name': 'local.materialui.ShareRemuxService',
                                 A + 'exported': 'false', A + 'process': ':material_share'})
    tree.write(manifest, encoding='utf-8', xml_declaration=True)
