"""Attach owned render fitting without retaining native method implementations."""
import re


def refine_geometry(source, package):
    classes = source / 'smali_classes2' / package.replace('.', '/')
    helper = classes / 'activitys/preview/view/ijk/MeasureHelper.smali'
    text = helper.read_text()
    owner = re.search(r'^\.class .* (L[^;]+;)$', text, re.M).group(1)
    fields = ['mVideoWidth', 'mVideoHeight', 'mVideoSarNum', 'mVideoSarDen', 'mVideoRotationDegree']
    for name in fields + ['mMeasuredWidth', 'mMeasuredHeight']:
        if not re.search(r'^\.field [^\n]*\b' + name + ':I$', text, re.M):
            raise ValueError('Native render dimension missing: ' + name)
    match = re.search(r'(?ms)^\.method public doMeasure\(II\)V\n.*?^\.end method', text)
    if not match or 'Llocal/materialui/VideoFit;' in match.group():
        raise ValueError('Native measurement hook missing or already applied.')
    body = '.method public doMeasure(II)V\n    .locals 7\n    move v0, p1\n    move v1, p2\n'
    for register, name in enumerate(fields, 2):
        body += f'    iget v{register}, p0, {owner}->{name}:I\n'
    body += ('    invoke-static/range {v0 .. v6}, Llocal/materialui/VideoFit;->measure(IIIIIII)[I\n'
             '    move-result-object v0\n    const/4 v1, 0x0\n    aget v2, v0, v1\n'
             f'    iput v2, p0, {owner}->mMeasuredWidth:I\n'
             '    const/4 v1, 0x1\n    aget v2, v0, v1\n'
             f'    iput v2, p0, {owner}->mMeasuredHeight:I\n    return-void\n.end method')
    helper.write_text(text[:match.start()] + body + text[match.end():])
    for suffix in ['preview/PreviewActivity', 'backplay/BackPlayIjkActivity', 'backplay/BackPlayVlcActivity']:
        path = classes / 'activitys' / (suffix + '.smali')
        text = path.read_text()
        match = re.search(r'(?ms)^\.method [^\n]* onCreate\(Landroid/os/Bundle;\)V\n.*?^\.end method', text)
        if not match:
            raise ValueError('Native media activity creation missing: ' + suffix)
        hook = '    invoke-static {p0}, Llocal/materialui/MediaGeometry;->install(Landroid/app/Activity;)V\n'
        body = match.group().replace('    return-void', hook + '    return-void')
        path.write_text(text[:match.start()] + body + text[match.end():])
