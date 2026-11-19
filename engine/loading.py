"""Give native loading surfaces consistent geometry without changing their callbacks."""
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'


def refine_loading(source):
    # Some OEM downloaders inflate this XML directly and bypass our dialog class.
    for path in (source / 'res').glob('layout*/dialog_progress.xml'):
        tree = ET.parse(path)
        tree.getroot().set(A + 'tag', 'material-ui-progress')
        for node in tree.iter():
            if node.get(A + 'id') == '@id/download_progressbar':
                node.set(A + 'layout_width', 'match_parent')
                node.set(A + 'layout_marginBottom', '0dp')
        tree.write(path, encoding='utf-8', xml_declaration=True)
    names = {'alertdialog_progress.xml', 'changhong_alertdialog_progress.xml',
             'dmc_control_loading_dialog.xml'}
    for path in (source / 'res').glob('layout*/*.xml'):
        if path.name not in names:
            continue
        tree = ET.parse(path)
        root = tree.getroot()
        if root.tag != 'LinearLayout':
            raise ValueError('Native loading surface contract changed: ' + path.name)
        root.tag = 'local.materialui.LoadingSurface'
        root.set(A + 'gravity', 'center')
        root.set(A + 'minHeight', '96dp')
        root.set(A + 'padding', '24dp')
        root.set(A + 'paddingTop', '24dp')
        root.set(A + 'paddingBottom', '24dp')
        root.set(A + 'tag', 'material-ui-loading')
        for node in root.iter():
            if node.tag.endswith('CircularProgressIndicator'):
                node.set(A + 'layout_width', '32dp')
                node.set(A + 'layout_height', '32dp')
                node.set(A + 'layout_gravity', 'center_horizontal')
            if node.get(A + 'id') in {'@id/txtDialog', '@id/tvDialogMessage01'}:
                node.set(A + 'layout_marginTop', '12dp')
                node.set(A + 'paddingStart', '0dp')
                node.set(A + 'paddingEnd', '0dp')
                node.set(A + 'paddingLeft', '0dp')
                node.set(A + 'paddingRight', '0dp')
                node.set(A + 'textSize', '14sp')
                node.set(A + 'lineHeight', '20sp')
                node.set('{http://schemas.android.com/apk/res-auto}lineHeight', '20sp')
                node.set(A + 'gravity', 'center')
        tree.write(path, encoding='utf-8', xml_declaration=True)
    path = source / 'res/layout/no_network_dialog.xml'
    tree = ET.parse(path)
    for node in tree.iter():
        if node.get(A + 'id') == '@id/tvDialogTitle':
            node.set(A + 'text', '@string/dialog_tips')
        if node.get(A + 'id') == '@id/btnClose':
            node.set(A + 'contentDescription', '@string/close')
    tree.write(path, encoding='utf-8', xml_declaration=True)
