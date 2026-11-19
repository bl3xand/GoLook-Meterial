"""Remove obsolete diagnostic layouts only after checking their remaining references."""
import re
import xml.etree.ElementTree as ET


OBSOLETE = {
    ('layout', 'activity_debug'),
    ('layout', 'activity_debug_layout'),
    ('layout', 'activity_dev_info_code'),
    ('layout', 'activity_socket_test'),
}


def remove_diagnostics(source):
    public_path = source / 'res/values/public.xml'
    public = ET.parse(public_path)
    ids = {node.get('id') for node in public.getroot()
           if (node.get('type'), node.get('name')) in OBSOLETE}
    names = {name for _, name in OBSOLETE}
    obsolete_paths = {path for kind, name in OBSOLETE
                      for path in (source / 'res').glob(kind + '*/' + name + '.xml')}
    for folder in source.glob('smali*'):
        for path in folder.rglob('*.smali'):
            if path.stem == 'R' or path.stem.startswith('R$'):
                continue
            text = path.read_text()
            if any(re.search(r'(?<![\w])' + re.escape(token) + r'(?![\w])', text)
                   for token in names | ids):
                raise ValueError('Obsolete diagnostic resource still referenced: ' + str(path.relative_to(source)))
    for path in (source / 'res').rglob('*.xml'):
        if path in obsolete_paths or path == public_path:
            continue
        text = path.read_text()
        if any('@' + kind + '/' + name in text for kind, name in OBSOLETE):
            raise ValueError('Obsolete diagnostic layout still included: ' + str(path.relative_to(source)))
    removed = []
    for path in sorted(obsolete_paths):
        removed.append(str(path.relative_to(source)))
        path.unlink()
    for node in list(public.getroot()):
        if (node.get('type'), node.get('name')) in OBSOLETE:
            public.getroot().remove(node)
    public.write(public_path, encoding='utf-8', xml_declaration=True)
    return removed
