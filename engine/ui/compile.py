#!/usr/bin/env python3
"""Compile the small UI adapter into the decompiled application's smali tree."""
from pathlib import Path
import argparse
import shutil
import subprocess
import tempfile
import os

root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ['ANDROID_SDK_ROOT'])
android = sdk / 'platforms/android-36/android.jar'
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac')
apktool = Path(os.environ['APKTOOL_JAR'])
target_package = os.environ['TARGET_PACKAGE']
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, default=root / 'apktool/smali_classes2/local/materialui/PreviewLayout.smali')
parser.add_argument('--progress-output', type=Path, default=root / 'apktool/smali_classes2/local/materialui/ExpressiveProgressDialog.smali')
parser.add_argument('--aspect-output', type=Path, default=root / 'apktool/smali_classes2/local/materialui/AspectVideoFrame.smali')
parser.add_argument('--actions-output', type=Path, default=root / 'apktool/smali_classes2/local/materialui/DialogActions.smali')
parser.add_argument('--menu-output', type=Path, default=root / 'apktool/smali_classes2/local/materialui/AnchoredMenu.smali')
parser.add_argument('--clip-output', type=Path, default=root / 'apktool/smali_classes2/local/materialui/ClipLayout.smali')
args = parser.parse_args()
with tempfile.TemporaryDirectory(prefix='ui-adapter-', dir=root / 'dist') as temporary:
    work = Path(temporary)
    stub = work / 'UiBridge.java'
    stub.write_text('package local.materialui; public final class UiBridge {'
                    ' public static native void updateAdasBounds(android.app.Activity a,int w,int h);'
                    ' public static native void apply(android.app.Dialog d);'
                    ' public static native androidx.appcompat.app.AlertDialog show(androidx.appcompat.app.AlertDialog.Builder b); }')
    sheet_stub = work / 'AppCompatDialog.java'
    sheet_stub.write_text('package androidx.appcompat.app;'
                          ' public class AppCompatDialog extends android.app.Dialog {'
                          ' public AppCompatDialog(android.content.Context c) { super(c); } }')
    button_stub = work / 'MaterialButton.java'
    button_stub.write_text('package com.google.android.material.button;'
        ' public class MaterialButton extends android.widget.Button {'
        ' public MaterialButton(android.content.Context c,android.util.AttributeSet a) { super(c,a); } }')
    alert_stub = work / 'AlertDialog.java'
    alert_stub.write_text('package androidx.appcompat.app; public class AlertDialog extends android.app.Dialog {'
        ' public AlertDialog(android.content.Context c) { super(c); }'
        ' public static class Builder { public Builder(android.content.Context c) {}'
        ' public native Builder setTitle(CharSequence title);'
        ' public native Builder setNegativeButton(CharSequence text,android.content.DialogInterface.OnClickListener listener);'
        ' public native Builder setPositiveButton(CharSequence text,android.content.DialogInterface.OnClickListener listener);'
        ' public native Builder setMessage(CharSequence text);'
        ' public native Builder setItems(CharSequence[] labels,android.content.DialogInterface.OnClickListener listener);'
        ' public native Builder setSingleChoiceItems(CharSequence[] labels,int checked,android.content.DialogInterface.OnClickListener listener); } }')
    clip_stub = work / 'VideoClipsActivity.java'
    clip_stub.write_text('package ' + target_package + '.activitys.videoclip;'
        ' public class VideoClipsActivity extends android.app.Activity {'
        ' public native com.zoulequan.base.spx.videoclipeditviewtest.ClipContainer getClipContainer();'
        ' public native com.spx.library.player.VideoPlayer getVideoPlayer();'
        ' public native void updatePlayPosition(); public native void materialUiResumeTimer(); public native void materialUiUpdateExport();'
        ' public native long getStartMillSec(); public native long getEndMillSec();'
        ' public native void setStartMillSec(long value); public native void setEndMillSec(long value);'
        ' public native void onSelectionChang(int count,long first,long last,boolean finished); }')
    range_stub = work / 'ClipContainer.java'
    range_stub.write_text('package com.zoulequan.base.spx.videoclipeditviewtest;'
        ' public class ClipContainer {'
        ' public native float getStartMillSec(); public native float getEndMillSec();'
        ' public native int getMediaDutaion(); public native int getFrameWidth();'
        ' public native long getMillSecInFrame();'
        ' public native int getItemCount();'
        ' public native void setStartMillSec(float value); public native void setEndMillSec(float value);'
        ' public native void materialUiSetRange(float left,float right,float scroll,boolean finished);'
        ' public native void materialUiRestoreRange(float left,float right,float scroll); }')
    player_stub = work / 'VideoPlayer.java'
    player_stub.write_text('package com.spx.library.player; public interface VideoPlayer {'
        ' int getDuration(); int getPlayerCurrentPosition(); boolean isPlaying();'
        ' void pausePlayer(); void startPlayer(); void seekToPosition(long position); }')
    clip_adapter = work / 'ClipLayout.java'
    clip_adapter.write_text((root / 'tools/ui/ClipLayout.java').read_text().replace('__TARGET_PACKAGE__', target_package))
    classes = work / 'classes'
    classes.mkdir()
    sources = sorted((root / 'tools/ui').glob('*.java'))
    sources = [source for source in sources if source.name != 'ClipLayout.java'] + [clip_adapter]
    subprocess.run([javac, '-source', '8', '-target', '8',
                    '-bootclasspath', str(android), '-d', str(classes),
                    *map(str, [stub, sheet_stub, alert_stub, button_stub, clip_stub, range_stub, player_stub]),
                    *map(str, sources)], check=True)
    archive = work / 'adapter.zip'
    adapters = sorted((classes / 'local/materialui').glob('*.class'))
    adapters = [adapter for adapter in adapters if adapter.name != 'UiBridge.class']
    subprocess.run([java, '-cp', str(sdk / 'build-tools/37.0.0/lib/d8.jar'),
                    'com.android.tools.r8.D8', '--min-api', '21', '--lib', str(android),
                    '--classpath', str(classes), '--output', str(archive),
                    *map(str, adapters)], check=True)
    decoded = work / 'decoded'
    subprocess.run([java, '-jar', str(apktool), 'd',
                    '--no-res', '--no-assets', '-o', str(decoded), str(archive)], check=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    destinations = {'PreviewLayout.smali': args.output,
                    'ExpressiveProgressDialog.smali': args.progress_output,
                    'AspectVideoFrame.smali': args.aspect_output,
                    'DialogActions.smali': args.actions_output,
                    'AnchoredMenu.smali': args.menu_output,
                    'ClipLayout.smali': args.clip_output}
    for source in (decoded / 'smali/local/materialui').glob('*.smali'):
        destination = destinations.get(source.name, args.output.parent / source.name)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
