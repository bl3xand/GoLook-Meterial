"""Center and group the native empty/saved-device states inside a scroll viewport."""
import re
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'


def refine_home(source, package):
    for path in (source / 'res').glob('layout*/device_manage*.xml'):
        if path.stem not in ('device_manage', 'device_manage_pad', 'device_manage_empty',
                             'device_manage_not_empty', 'device_manage_not_empty_pad'):
            continue
        tree = ET.parse(path)
        root = tree.getroot()
        if root.get(A + 'id') in ('@id/layout_dev_empty', '@id/layout_dev_not_empty'):
            root.set(A + 'layout_height', 'wrap_content')
            root.set(A + 'layout_marginLeft', '24dp')
            root.set(A + 'layout_marginRight', '24dp')
            root.set(A + 'padding', '16dp')
            for key in ('paddingLeft', 'paddingRight', 'paddingStart', 'paddingEnd', 'paddingTop'):
                root.attrib.pop(A + key, None)
            root.set(A + 'background', '@drawable/material_ui_home_card')
            if root.get(A + 'id') == '@id/layout_dev_empty':
                root.attrib.pop(A + 'minHeight', None)
                image = root.find('ImageView')
                action = next((node for node in root if node.get(A + 'id') == '@id/addDevBig'), None)
                if image is None or action is None:
                    raise ValueError('Native empty home card content missing.')
                root.remove(image)
                image.attrib.pop(A + 'layout_marginBottom', None)
                image.set(A + 'layout_gravity', 'center')
                media = ET.Element('FrameLayout', {
                    A + 'layout_width': 'match_parent', A + 'layout_height': '208dp'})
                media.append(image)
                root.insert(0, media)
                action.set(A + 'minHeight', '56dp')
                action.set(A + 'layout_marginTop', '16dp')
            else:
                action = next((node for node in root if node.get(A + 'id') == '@id/btnConnect'), None)
                if action is None:
                    raise ValueError('Native saved home card action missing.')
                action.tag = 'local.materialui.ConnectionButton'
                action.set(A + 'layout_marginBottom', '0dp')
            action.set(A + 'insetTop', '0dp')
            action.set(A + 'insetBottom', '0dp')
        else:
            host = next((node for node in root.iter() if node.tag == 'FrameLayout'
                         and any(child.tag == 'include' and child.get('layout') == '@layout/device_manage_empty'
                                 for child in node)), None)
            if host is None:
                raise ValueError('Native home state container missing: ' + path.name)
            children = list(host)
            for child in children:
                host.remove(child)
            scroll = ET.SubElement(host, 'ScrollView', {
                A + 'layout_width': 'match_parent', A + 'layout_height': 'match_parent',
                A + 'fillViewport': 'true', A + 'scrollbarStyle': 'outsideOverlay',
                A + 'clipToPadding': 'false'})
            stack = ET.SubElement(scroll, 'LinearLayout', {
                A + 'orientation': 'vertical', A + 'gravity': 'center_vertical',
                A + 'layout_width': 'match_parent', A + 'layout_height': 'wrap_content',
                A + 'paddingTop': '16dp', A + 'paddingBottom': '16dp'})
            for child in children:
                stack.append(child)
        tree.write(path, encoding='utf-8', xml_declaration=True)

    # Preserve the full card in landscape while reducing its decorative area.
    for name in ('device_manage_empty', 'device_manage_not_empty', 'device_manage_not_empty_pad'):
        path = source / 'res/layout' / (name + '.xml')
        if not path.is_file():
            continue
        tree = ET.parse(path)
        media = tree.getroot().find('FrameLayout')
        if media is None:
            raise ValueError('Native home card media container missing.')
        if name == 'device_manage_empty':
            media.set(A + 'layout_height', '64dp')
            image = media.find('ImageView')
            image.set(A + 'layout_width', '48dp')
            image.set(A + 'layout_height', '48dp')
        else:
            include = media.find('include')
            if include is None:
                raise ValueError('Native recorder card include missing.')
            include.set(A + 'layout_height', '64dp')
        destination = source / 'res/layout-land' / path.name
        destination.parent.mkdir(exist_ok=True)
        tree.write(destination, encoding='utf-8', xml_declaration=True)

    for name in ('device_item', 'device_item_pad', 'device_item_0821'):
        path = source / 'res/layout' / (name + '.xml')
        if not path.is_file():
            continue
        tree = ET.parse(path)
        root = tree.getroot()
        root.set(A + 'layout_height', '64dp')
        for node in root.iter():
            if node.get(A + 'id') == '@id/material_ui_device_media':
                node.set(A + 'visibility', 'gone')
            if node.get(A + 'id') == '@id/material_ui_device_footer':
                node.attrib.pop(A + 'layout_alignParentBottom', None)
                node.set(A + 'layout_centerVertical', 'true')
                node.set(A + 'paddingBottom', '0dp')
            if node.get(A + 'id') == '@id/openWifiTips':
                node.set(A + 'layout_height', '0dp')
                node.set(A + 'minHeight', '0dp')
                node.set(A + 'layout_marginBottom', '0dp')
        tree.write(source / 'res/layout-land' / path.name, encoding='utf-8', xml_declaration=True)

    for name in ('device_manage', 'device_manage_pad'):
        path = source / 'res/layout' / (name + '.xml')
        if not path.is_file():
            continue
        tree = ET.parse(path)
        stack = tree.find('.//ScrollView/LinearLayout')
        if stack is None:
            raise ValueError('Native landscape home scroll column missing.')
        stack.set(A + 'paddingTop', '8dp')
        stack.set(A + 'paddingBottom', '8dp')
        tree.write(source / 'res/layout-land' / path.name, encoding='utf-8', xml_declaration=True)

    classes = source / 'smali_classes2' / package.replace('.', '/') / 'activitys/devmanager'
    path = classes / 'DeviceManagerActivity.smali'
    text = path.read_text()
    for callback in ('onResume()V', 'changeEmptyView()V'):
        match = re.search(r'(?ms)^\.method [^\n]* ' + re.escape(callback) + r'\n.*?^\.end method', text)
        if not match:
            raise ValueError('Native home refresh callback missing.')
        body = match.group().replace('    return-void',
                '    invoke-static {p0}, Llocal/materialui/HomeCards;->refresh(Landroid/app/Activity;)V\n'
                '    return-void')
        text = text[:match.start()] + body + text[match.end():]
    path.write_text(text)
