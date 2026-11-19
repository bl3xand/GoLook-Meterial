"""Give native video controls reversible, animated tap visibility."""
import re


def refine_chrome(source, package):
    classes = source / 'smali_classes2' / package.replace('.', '/') / 'activitys'
    controller = classes / 'backplay/view/MediaController.smali'
    owner = 'L' + package.replace('.', '/') + '/activitys/backplay/view/MediaController;'
    text = controller.read_text()
    match = re.search(r'(?ms)^\.method public hide\(\)V\n.*?^\.end method', text)
    guard = '    if-ne v0, v1, :cond_2'
    if not match or match.group().count(guard) != 1:
        raise ValueError('Native video visibility orientation guard changed.')
    # The native landscape-only guard prevented a tap from hiding portrait controls.
    body = match.group().replace(guard, '    nop')
    text = text[:match.start()] + body + text[match.end():]
    match = re.search(r'(?ms)^\.method public show\(I\)V\n.*?^\.end method', text)
    branch = '    if-eqz p1, :cond_5'
    if not match or match.group().count(branch) != 1:
        raise ValueError('Native video visibility timeout guard changed.')
    # Viewer chrome is controlled by taps, including after pause, seek and keys.
    # Native input routes all converge here; none should restart auto-hide.
    body = match.group().replace(branch,
            '    iget-object v0, p0, ' + owner + '->mFadeOut:Ljava/lang/Runnable;\n'
            '    invoke-virtual {p0, v0}, Landroid/view/View;->removeCallbacks(Ljava/lang/Runnable;)Z\n'
            '    const/4 p1, 0x0\n\n' + branch)
    text = text[:match.start()] + body + text[match.end():]
    controller.write_text(text)

    listener = classes / 'backplay/BackPlayIjkActivity$7.smali'
    text = listener.read_text()
    visibility = 'invoke-virtual {v0, v1}, Landroid/widget/RelativeLayout;->setVisibility(I)V'
    if text.count(visibility) != 3:
        raise ValueError('Native video overlay visibility callbacks changed.')
    listener.write_text(text.replace(visibility,
            'invoke-static {v0, v1}, Llocal/materialui/MediaChrome;->visibility(Landroid/view/View;I)V'))
