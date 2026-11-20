"""Apply owned media presentation adapters after the verified input patches."""
import re
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'


def refine(source, package):
    ET.register_namespace('android', A[1:-1])
    ET.register_namespace('app', 'http://schemas.android.com/apk/res-auto')
    layouts = source / 'res/layout'
    adaptive = {'device_item.xml', 'device_item_0821.xml', 'device_item_pad.xml',
                'file_browser_gridview.xml', 'file_browser_gridview20240713.xml',
                'changhong_file_browser_gridview.xml'}
    for path in (source / 'res').glob('layout-*/*.xml'):
        if path.name in adaptive and (layouts / path.name).is_file():
            # Old pixel-size/language variants bypass the adaptive replacement.
            path.write_bytes((layouts / path.name).read_bytes())
    for path in (source / 'res').glob('layout*/*.xml'):
        tree = ET.parse(path)
        root = tree.getroot()
        parents = {child: parent for parent in root.iter() for child in parent}
        changed = False
        if path.name.startswith(('dmc_video_control_', 'gps_dmc_video_control_')):
            # One continuous scrim across the complete native controller, not
            # separate bands behind its timeline and action rows.
            for node in root.iter():
                if node.get(A + 'background') == '@drawable/material_ui_overlay_scrim':
                    node.attrib.pop(A + 'background')
            root.set(A + 'background', '@drawable/material_ui_media_scrim_bottom')
            changed = True
        if path.name.startswith('title_layout_video_control_'):
            root.set(A + 'background', '@drawable/material_ui_media_scrim_top')
            changed = True
        for node in root.iter():
            if node.tag == 'SeekBar' and node.get(A + 'id') == '@id/mediacontroller_progress':
                # Buffering must not leave a separate gray tail on the playback track.
                # Keep the native buffer value while using one continuous inactive color.
                node.set(A + 'progressBackgroundTint', '@color/material_ui_primary_container')
                node.set(A + 'secondaryProgressTint', '@color/material_ui_primary_container')
                changed = True
            if path.name in {'rxffmpeg_player_controller.xml', 'activity_vod.xml'} and node.get(A + 'id') in {'@id/bottomPanel', '@id/media_controller'}:
                node.set(A + 'background', '@drawable/material_ui_media_scrim_bottom')
                changed = True
            if node.get(A + 'id') == '@id/ibImgGridView':
                node.set(A + 'paddingTop', '12dp')
                node.set(A + 'clipToPadding', 'true')
                changed = True
            if node.get(A + 'id') == '@id/ivWifiSignal' and node.tag == 'ImageView':
                node.tag = 'local.materialui.WifiAssociationIcon'
                changed = True
            if node.tag == 'include' and node.get('layout') == '@layout/progressbar_layout':
                parent = parents[node]
                if parent.tag in {'FrameLayout', 'android.widget.FrameLayout',
                                  'local.materialui.AspectVideoFrame'}:
                    node.attrib.pop(A + 'layout_centerInParent', None)
                    node.set(A + 'layout_gravity', 'center')
                    changed = True
                elif parent.tag in {'RelativeLayout', 'android.widget.RelativeLayout'}:
                    node.attrib.pop(A + 'layout_gravity', None)
                    node.set(A + 'layout_centerInParent', 'true')
                    changed = True
        if changed:
            tree.write(path, encoding='utf-8', xml_declaration=True)

    styles = source / 'res/values/styles.xml'
    tree = ET.parse(styles)
    for node in tree.getroot().findall('style'):
        if node.get('name') == 'dialog_item':
            for key, value in [('android:gravity', 'center_vertical'),
                               ('android:baselineAligned', 'false')]:
                existing = node.find("item[@name='%s']" % key)
                if existing is None:
                    existing = ET.SubElement(node, 'item', {'name': key})
                existing.text = value
    tree.write(styles, encoding='utf-8', xml_declaration=True)

    # These activities retain all original file/recorder operations. Only add owned actions.
    classes = source / 'smali_classes2' / package.replace('.', '/') / 'activitys'
    photo = classes / 'backplay/PhotoActivity.smali'
    menu = source / 'smali_classes3/com/hisilicon/cameralib/ui/dialog/PopupWinforPreviewActivity.smali'
    for path, method in [(photo, 'photo'), (menu, 'menu')]:
        text = path.read_text()
        match = re.search(r'(?ms)^\.method [^\n]* onCreate\(Landroid/os/Bundle;\)V\n.*?^\.end method', text)
        if not match:
            raise ValueError('Media activity creation hook missing: ' + path.name)
        body = match.group()
        superclass = re.search(r'^\.super (\S+)', text, re.M).group(1)
        hook = '    invoke-static {p0}, Llocal/materialui/MediaFiles;->' + method + '(Landroid/app/Activity;)V\n\n'
        # Bind both the existing-file and download paths, before adding a new
        # invalid-state return that must never initialize a missing photo.
        body = body.replace('    return-void', hook + '    return-void')
        if method == 'photo':
            creation = re.compile(r'    invoke-super \{p0, p1\}, ' + re.escape(superclass)
                                  + r'->onCreate\(Landroid/os/Bundle;\)V\n')
            if len(creation.findall(body)) != 1:
                raise ValueError('Photo restoration callback missing.')
            locals_match = re.search(r'(?m)^    \.locals (\d+)', body)
            if not locals_match:
                raise ValueError('Photo local register declaration missing.')
            if int(locals_match.group(1)) == 0:
                body = body[:locals_match.start()] + '    .locals 1' + body[locals_match.end():]
            body = creation.sub(lambda m: m.group() +
                    '    invoke-static {p0, p1}, Llocal/materialui/PhotoState;->restore(Landroid/app/Activity;Landroid/os/Bundle;)Z\n'
                    '    move-result v0\n'
                    '    if-nez v0, :material_ui_photo_ready\n'
                    '    invoke-virtual {p0}, Landroid/app/Activity;->finish()V\n'
                    '    return-void\n'
                    '    :material_ui_photo_ready\n', body)
            if ' onSaveInstanceState(Landroid/os/Bundle;)V' in text:
                raise ValueError('Unexpected photo state callback.')
            text += ('\n.method protected onSaveInstanceState(Landroid/os/Bundle;)V\n'
                     '    .locals 0\n'
                     '    invoke-static {p0, p1}, Llocal/materialui/PhotoState;->save(Landroid/app/Activity;Landroid/os/Bundle;)V\n'
                     '    invoke-super {p0, p1}, ' + superclass + '->onSaveInstanceState(Landroid/os/Bundle;)V\n'
                     '    return-void\n.end method\n')
        text = text[:match.start()] + body + text[match.end():]
        # The original activities have no activity-result override.
        if ' onActivityResult(IILandroid/content/Intent;)V' in text:
            raise ValueError('Unexpected media activity-result override: ' + path.name)
        text += ('\n.method protected onActivityResult(IILandroid/content/Intent;)V\n'
                 '    .locals 0\n'
                 '    invoke-super {p0, p1, p2, p3}, ' + superclass + '->onActivityResult(IILandroid/content/Intent;)V\n'
                 '    invoke-static {p0, p1, p2, p3}, Llocal/materialui/MediaFiles;->result(Landroid/app/Activity;IILandroid/content/Intent;)V\n'
                 '    return-void\n.end method\n')
        path.write_text(text)
