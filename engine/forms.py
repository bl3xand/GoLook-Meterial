"""Keep the feedback action visible while the editable form scrolls."""
import copy
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'


def refine_feedback(source):
    layout = source / 'res/layout/activity_feedback.xml'
    tree = ET.parse(layout)
    root = tree.getroot()
    button = next((node for node in root.iter() if node.get(A + 'id') == '@id/btn_submit'), None)
    field = next((node for node in root.iter() if node.get(A + 'id') == '@id/feed_field_cotent'), None)
    if root.tag != 'LinearLayout' or button is None or field is None:
        raise ValueError('Feedback form contract changed.')
    for parent in root.iter():
        if button in list(parent):
            parent.remove(button)
            break
    button.set(A + 'layout_marginTop', '8dp')
    button.set(A + 'layout_marginBottom', '16dp')
    button.set(A + 'layout_marginStart', '24dp')
    button.set(A + 'layout_marginEnd', '24dp')
    root.append(button)
    tree.write(layout, encoding='utf-8', xml_declaration=True)
    landscape = copy.deepcopy(root)
    for node in landscape.iter():
        if node.get(A + 'id') == '@id/feed_field_cotent':
            node.set(A + 'minLines', '2')
    destination = source / 'res/layout-land/activity_feedback.xml'
    destination.parent.mkdir(exist_ok=True)
    ET.ElementTree(landscape).write(destination, encoding='utf-8', xml_declaration=True)
