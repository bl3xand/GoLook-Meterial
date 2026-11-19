"""Derive regional language fallbacks from the user's patched local resources."""
from pathlib import Path
import xml.etree.ElementTree as ET


FALLBACKS = (('vi', 'vi-rVN'), ('zh', 'zh-rCN'), ('zh-rHK', 'zh-rTW'))
TEXT_TYPES = {'string', 'plurals', 'string-array'}


def resources(directory):
    for path in sorted(directory.glob('*.xml')):
        for element in ET.parse(path).getroot():
            yield element


def identity(element):
    return element.get('type', element.tag), element.get('name')


def materialize_fallbacks(source):
    """Fill missing generic text without overriding existing locale definitions."""
    counts = {}
    for generic, regional in FALLBACKS:
        regional_dir = Path(source) / 'res' / ('values-' + regional)
        if not regional_dir.is_dir():
            continue
        generic_dir = Path(source) / 'res' / ('values-' + generic)
        target = generic_dir / 'material_ui_app_language.xml'
        if target.exists():
            raise ValueError('Language fallback output already exists: ' + str(target))
        existing = {identity(element) for element in resources(generic_dir)}
        root = ET.Element('resources')
        copied = set()
        for element in resources(regional_dir):
            key = identity(element)
            if key[0] not in TEXT_TYPES or key in existing:
                continue
            if key in copied:
                raise ValueError('Duplicate regional text resource: ' + str(key))
            copied.add(key)
            root.append(ET.fromstring(ET.tostring(element, encoding='unicode')))
        if len(root):
            generic_dir.mkdir(parents=True, exist_ok=True)
            ET.indent(root, space='  ')
            ET.ElementTree(root).write(target, encoding='utf-8', xml_declaration=True)
            counts[generic] = len(root)
    return counts
