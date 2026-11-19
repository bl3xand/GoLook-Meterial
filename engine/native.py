"""Remove offline ELF debug information while proving runtime bytes unchanged."""
import hashlib
import struct
import subprocess


def runtime_image(data):
    """Fingerprint every mapped segment, allowing only section-table metadata changes."""
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected a little-endian ELF64 native library.')
    if struct.unpack_from('<H', data, 18)[0] != 183:
        raise ValueError('Expected an ARM64 native library.')
    offset = struct.unpack_from('<Q', data, 32)[0]
    stride, count = struct.unpack_from('<HH', data, 54)
    if stride < 56 or offset + stride * count > len(data):
        raise ValueError('Invalid native program-header table.')
    header = bytearray(data[:64])
    # ELF section headers describe offline tooling, not the runtime image.
    header[40:48] = b'\0' * 8
    header[58:64] = b'\0' * 6
    table = data[offset:offset + stride * count]
    segments = []
    for index in range(count):
        kind, _, start, _, _, size, _, _ = struct.unpack_from('<IIQQQQQQ', table, index * stride)
        if kind != 1:
            continue
        if start + size > len(data):
            raise ValueError('Native LOAD segment extends beyond the file.')
        payload = bytearray(data[start:start + size])
        for first, last in ((40, 48), (58, 64)):
            left, right = max(first, start), min(last, start + size)
            if left < right:
                payload[left - start:right - start] = b'\0' * (right - left)
        segments.append(hashlib.sha256(payload).hexdigest())
    if not segments:
        raise ValueError('Native library has no LOAD segments.')
    return bytes(header), table, segments


def strip_debug(source, payloads, tool, work):
    """Retain all libraries, symbols used by the loader and complete runtime segments."""
    version = subprocess.check_output([str(tool), '--version'], text=True).strip()
    directory = work / 'native-stripped'
    directory.mkdir()
    changes = []
    for name, original in list(payloads.items()):
        destination = directory / name.rsplit('/', 1)[-1]
        subprocess.run([str(tool), '--strip-debug', '-o', str(destination), str(source / name)],
                       check=True)
        result = destination.read_bytes()
        if runtime_image(original) != runtime_image(result):
            raise ValueError('Native debug removal changed runtime data: ' + name)
        if len(result) >= len(original):
            continue
        (source / name).write_bytes(result)
        payloads[name] = result
        changes.append({'path': name, 'before_bytes': len(original), 'after_bytes': len(result),
                        'saved_bytes': len(original) - len(result)})
    return {'tool': version, 'libraries': changes,
            'saved_bytes': sum(item['saved_bytes'] for item in changes),
            'mapped_segments_preserved': True}
