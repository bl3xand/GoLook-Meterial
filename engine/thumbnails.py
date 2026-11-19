"""Replace distorted frame extraction without redistributing the native implementation."""
import re


def refine_thumbnails(source):
    path = source / 'smali_classes3/com/hisilicon/cameralib/file/ImageLoader.smali'
    text = path.read_text()
    if 'Llocal/materialui/ThumbnailFrames;' in text:
        raise ValueError('Thumbnail adapters already applied.')
    signature = 'getDrivingRecorderVideoThumbnail(Ljava/lang/String;II)Landroid/graphics/Bitmap;'
    pattern = re.compile(r'(?ms)^\.method public static ' + re.escape(signature)
                         + r'\n.*?^\.end method')
    replacement = ('\n'.join([
        '.method public static ' + signature,
        '    .locals 1',
        '    invoke-static {p0, p1, p2}, Llocal/materialui/ThumbnailFrames;->extract(Ljava/lang/String;II)Landroid/graphics/Bitmap;',
        '    move-result-object v0',
        '    return-object v0',
        '.end method',
    ]))
    text, count = pattern.subn(lambda _: replacement, text)
    if count != 1:
        raise ValueError('Native thumbnail extraction contract changed.')
    # Derived cache entries use a new namespace; original media and old entries
    # remain untouched, while distorted cached frames are no longer reused.
    for call in [
        '    invoke-static {p2, p1}, Lcom/hisilicon/cameralib/utils/ImgUtils;->saveImageToGallery(Landroid/graphics/Bitmap;Ljava/lang/String;)V',
        '    invoke-static {p1}, Lcom/hisilicon/cameralib/utils/ImgUtils;->getBitmapToLocal(Ljava/lang/String;)Landroid/graphics/Bitmap;',
    ]:
        if text.count(call) != 1:
            raise ValueError('Native thumbnail cache contract changed.')
        text = text.replace(call,
            '    invoke-static {p1}, Llocal/materialui/ThumbnailFrames;->cacheKey(Ljava/lang/String;)Ljava/lang/String;\n'
            '    move-result-object p1\n' + call)
    path.write_text(text)
