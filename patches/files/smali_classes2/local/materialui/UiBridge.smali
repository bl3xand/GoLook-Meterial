.class public final Llocal/materialui/UiBridge;
.super Ljava/lang/Object;

# Shared centered presentation for application modal dialogs.
.method public static applyWindow(Landroid/view/Window;)V
    .locals 6
    if-eqz p0, :done
    invoke-static {p0}, Llocal/materialui/UiBridge;->configureDialogBars(Landroid/view/Window;)V
    const/4 v0, 0x2
    invoke-virtual {p0, v0}, Landroid/view/Window;->addFlags(I)V
    const/high16 v0, 0x3f000000
    invoke-virtual {p0, v0}, Landroid/view/Window;->setDimAmount(F)V
    const/16 v0, 0x11
    invoke-virtual {p0, v0}, Landroid/view/Window;->setGravity(I)V
    invoke-virtual {p0}, Landroid/view/Window;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
    move-result-object v0
    iget v1, v0, Landroid/util/DisplayMetrics;->widthPixels:I
    iget v2, v0, Landroid/util/DisplayMetrics;->density:F
    const/high16 v3, 0x42400000
    mul-float/2addr v3, v2
    float-to-int v3, v3
    sub-int/2addr v1, v3
    const/high16 v3, 0x440c0000
    mul-float/2addr v3, v2
    float-to-int v3, v3
    invoke-static {v1, v3}, Ljava/lang/Math;->min(II)I
    move-result v0
    const/4 v1, -0x2
    invoke-virtual {p0, v0, v1}, Landroid/view/Window;->setLayout(II)V
    invoke-virtual {p0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v0
    const/4 v1, 0x0
    invoke-virtual {v0, v1, v1, v1, v1}, Landroid/view/View;->setPadding(IIII)V
    invoke-virtual {p0}, Landroid/view/Window;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v1
    const-string v2, "material_ui_dialog_bg"
    const-string v3, "drawable"
    invoke-virtual {v0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
    move-result-object v4
    invoke-virtual {v1, v2, v3, v4}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
    move-result v1
    if-eqz v1, :done
    invoke-virtual {p0, v1}, Landroid/view/Window;->setBackgroundDrawableResource(I)V
    :done
    invoke-static {p0}, Llocal/materialui/ModalLayout;->apply(Landroid/view/Window;)V
    return-void
.end method

.method public static apply(Landroid/app/Dialog;)V
    .locals 2
    if-eqz p0, :done
    invoke-static {p0}, Llocal/materialui/MediaLoading;->modal(Landroid/app/Dialog;)V
    instance-of v0, p0, Lcom/google/android/material/bottomsheet/BottomSheetDialog;
    if-eqz v0, :native_dialog
    check-cast p0, Lcom/google/android/material/bottomsheet/BottomSheetDialog;
    invoke-virtual {p0}, Lcom/google/android/material/bottomsheet/BottomSheetDialog;->getBehavior()Lcom/google/android/material/bottomsheet/BottomSheetBehavior;
    move-result-object v0
    const/4 v1, 0x1
    invoke-virtual {v0, v1}, Lcom/google/android/material/bottomsheet/BottomSheetBehavior;->setSkipCollapsed(Z)V
    const/4 v1, 0x3
    invoke-virtual {v0, v1}, Lcom/google/android/material/bottomsheet/BottomSheetBehavior;->setState(I)V
    return-void
    :native_dialog
    invoke-static {p0}, Llocal/materialui/UiMetrics;->dialogChoices(Landroid/app/Dialog;)V
    invoke-virtual {p0}, Landroid/app/Dialog;->getWindow()Landroid/view/Window;
    move-result-object v0
    invoke-static {v0}, Llocal/materialui/UiBridge;->applyWindow(Landroid/view/Window;)V
    :done
    return-void
.end method

.method public static show(Landroid/app/Dialog;)V
    .locals 0
    invoke-virtual {p0}, Landroid/app/Dialog;->show()V
    invoke-static {p0}, Llocal/materialui/UiBridge;->apply(Landroid/app/Dialog;)V
    return-void
.end method

.method public static show(Landroidx/appcompat/app/AlertDialog$Builder;)Landroidx/appcompat/app/AlertDialog;
    .locals 1
    invoke-virtual {p0}, Landroidx/appcompat/app/AlertDialog$Builder;->create()Landroidx/appcompat/app/AlertDialog;
    move-result-object v0
    invoke-static {v0}, Llocal/materialui/UiBridge;->show(Landroid/app/Dialog;)V
    return-object v0
.end method


# Legacy controllers hide the input; its Material outline must follow it.
.method public static setInputVisibility(Landroid/view/View;I)V
    .locals 2
    invoke-virtual {p0, p1}, Landroid/view/View;->setVisibility(I)V
    invoke-virtual {p0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
    move-result-object v0
    instance-of v1, v0, Lcom/google/android/material/textfield/TextInputLayout;
    if-nez v1, :container
    instance-of v1, v0, Landroid/view/View;
    if-eqz v1, :done
    check-cast v0, Landroid/view/View;
    invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
    move-result-object v0
    instance-of v1, v0, Lcom/google/android/material/textfield/TextInputLayout;
    if-eqz v1, :done
    :container
    check-cast v0, Landroid/view/View;
    invoke-virtual {v0, p1}, Landroid/view/View;->setVisibility(I)V
    :done
    return-void
.end method

.method public static updateAdasBounds(Landroid/app/Activity;II)V
    .locals 3
    const v0, 0x7f0a0384
    invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
    move-result-object v0
    instance-of v1, v0, Lcom/hisilicon/cameralib/ui/SVDrawRectangleEeasytech;
    if-eqz v1, :done
    check-cast v0, Lcom/hisilicon/cameralib/ui/SVDrawRectangleEeasytech;
    int-to-float v1, p1
    invoke-virtual {v0, v1}, Lcom/hisilicon/cameralib/ui/SVDrawRectangleEeasytech;->setAdasCanvasWidth(F)V
    int-to-float v1, p2
    invoke-virtual {v0, v1}, Lcom/hisilicon/cameralib/ui/SVDrawRectangleEeasytech;->setAdasCanvasHight(F)V
    invoke-static {p1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v0
    invoke-static {p0, v0}, Lcom/hisilicon/cameralib/utils/SharedPreferencesUtil;->setAdasCanvasWidth(Landroid/content/Context;Ljava/lang/Integer;)V
    invoke-static {p2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v0
    invoke-static {p0, v0}, Lcom/hisilicon/cameralib/utils/SharedPreferencesUtil;->setAdasCanvasHeight(Landroid/content/Context;Ljava/lang/Integer;)V
    :done
    return-void
.end method

.method public static drawableBitmap(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;
    .locals 6
    invoke-virtual {p0, p1}, Landroid/content/res/Resources;->getDrawable(I)Landroid/graphics/drawable/Drawable;
    move-result-object v0
    const/4 v1, 0x1
    invoke-virtual {v0}, Landroid/graphics/drawable/Drawable;->getIntrinsicWidth()I
    move-result v2
    invoke-static {v1, v2}, Ljava/lang/Math;->max(II)I
    move-result v2
    invoke-virtual {v0}, Landroid/graphics/drawable/Drawable;->getIntrinsicHeight()I
    move-result v3
    invoke-static {v1, v3}, Ljava/lang/Math;->max(II)I
    move-result v3
    sget-object v4, Landroid/graphics/Bitmap$Config;->ARGB_8888:Landroid/graphics/Bitmap$Config;
    invoke-static {v2, v3, v4}, Landroid/graphics/Bitmap;->createBitmap(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;
    move-result-object v4
    new-instance v5, Landroid/graphics/Canvas;
    invoke-direct {v5, v4}, Landroid/graphics/Canvas;-><init>(Landroid/graphics/Bitmap;)V
    const/4 v1, 0x0
    invoke-virtual {v0, v1, v1, v2, v3}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V
    invoke-virtual {v0, v5}, Landroid/graphics/drawable/Drawable;->draw(Landroid/graphics/Canvas;)V
    return-object v4
.end method

.method public static googleMarker(I)Lcom/google/android/gms/maps/model/BitmapDescriptor;
    .locals 2
    invoke-static {}, Lcom/damoa/dv/app/DashCamApp;->get()Landroid/content/Context;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v0
    invoke-static {v0, p0}, Llocal/materialui/UiBridge;->drawableBitmap(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;
    move-result-object v0
    invoke-static {v0}, Lcom/google/android/gms/maps/model/BitmapDescriptorFactory;->fromBitmap(Landroid/graphics/Bitmap;)Lcom/google/android/gms/maps/model/BitmapDescriptor;
    move-result-object v0
    return-object v0
.end method

.method public static mapMarker(I)Lcom/amap/api/maps/model/BitmapDescriptor;
    .locals 2
    invoke-static {}, Lcom/damoa/dv/app/DashCamApp;->get()Landroid/content/Context;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v0
    invoke-static {v0, p0}, Llocal/materialui/UiBridge;->drawableBitmap(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;
    move-result-object v0
    invoke-static {v0}, Lcom/amap/api/maps/model/BitmapDescriptorFactory;->fromBitmap(Landroid/graphics/Bitmap;)Lcom/amap/api/maps/model/BitmapDescriptor;
    move-result-object v0
    return-object v0
.end method

.method public static configureDialogBars(Landroid/view/Window;)V
    .locals 5
    invoke-virtual {p0}, Landroid/view/Window;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
    move-result-object v1
    iget v1, v1, Landroid/content/res/Configuration;->uiMode:I
    and-int/lit8 v1, v1, 0x30
    invoke-virtual {p0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v2
    invoke-virtual {v2}, Landroid/view/View;->getSystemUiVisibility()I
    move-result v3
    const/16 v4, 0x20
    if-eq v1, v4, :night
    const/16 v4, 0x2010
    or-int/2addr v3, v4
    goto :bars
    :night
    const v4, -0x2011
    and-int/2addr v3, v4
    :bars
    invoke-virtual {v2, v3}, Landroid/view/View;->setSystemUiVisibility(I)V
    const/4 v1, 0x0
    invoke-virtual {p0, v1}, Landroid/view/Window;->setNavigationBarColor(I)V
    invoke-virtual {p0, v1}, Landroid/view/Window;->setStatusBarColor(I)V
    return-void
.end method

.method public static text(Ljava/lang/String;)Ljava/lang/String;
    .locals 4
    invoke-virtual {p0}, Ljava/lang/String;->isEmpty()Z
    move-result v0
    if-eqz v0, :nonempty_text
    return-object p0
    :nonempty_text
    const-string v0, "|"
    invoke-virtual {p0, v0}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I
    move-result v0
    if-ltz v0, :single_text
    const/4 v1, 0x0
    invoke-virtual {p0, v1, v0}, Ljava/lang/String;->substring(II)Ljava/lang/String;
    move-result-object v1
    invoke-static {v1}, Llocal/materialui/UiBridge;->text(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    add-int/lit8 v0, v0, 0x1
    invoke-virtual {p0, v0}, Ljava/lang/String;->substring(I)Ljava/lang/String;
    move-result-object p0
    invoke-static {p0}, Llocal/materialui/UiBridge;->text(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p0
    new-instance v0, Ljava/lang/StringBuilder;
    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    const-string v1, ": "
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object p0
    return-object p0
    :single_text
    invoke-static {}, Lcom/damoa/dv/app/DashCamApp;->get()Landroid/content/Context;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v1
    const-string v2, "string"
    invoke-virtual {v0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
    move-result-object v3
    invoke-virtual {v1, p0, v2, v3}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
    move-result v2
    invoke-virtual {v0, v2}, Landroid/content/Context;->getString(I)Ljava/lang/String;
    move-result-object p0
    return-object p0
.end method

.method public static number(Ljava/lang/String;I)Ljava/lang/String;
    .locals 3
    invoke-static {p0}, Llocal/materialui/UiBridge;->text(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p0
    const/4 v0, 0x1
    new-array v0, v0, [Ljava/lang/Object;
    const/4 v1, 0x0
    invoke-static {p1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v2
    aput-object v2, v0, v1
    invoke-static {p0, v0}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
    move-result-object p0
    return-object p0
.end method

.method public static normalizeSpaces(Landroid/preference/PreferenceGroup;)V
    .locals 5
    const/4 v0, 0x0
    const/4 v1, 0x1
    :next_space
    invoke-virtual {p0}, Landroid/preference/PreferenceGroup;->getPreferenceCount()I
    move-result v2
    if-ge v0, v2, :spaces_done
    invoke-virtual {p0, v0}, Landroid/preference/PreferenceGroup;->getPreference(I)Landroid/preference/Preference;
    move-result-object v2
    instance-of v3, v2, Lcom/hisilicon/cameralib/ui/HiPreferenceSpace;
    if-eqz v3, :keep_space
    if-eqz v1, :keep_space
    invoke-virtual {p0, v2}, Landroid/preference/PreferenceGroup;->removePreference(Landroid/preference/Preference;)Z
    goto :next_space
    :keep_space
    move v1, v3
    add-int/lit8 v0, v0, 0x1
    goto :next_space
    :spaces_done
    invoke-virtual {p0}, Landroid/preference/PreferenceGroup;->getPreferenceCount()I
    move-result v0
    if-lez v0, :spaces_finished
    add-int/lit8 v0, v0, -0x1
    invoke-virtual {p0, v0}, Landroid/preference/PreferenceGroup;->getPreference(I)Landroid/preference/Preference;
    move-result-object v1
    instance-of v2, v1, Lcom/hisilicon/cameralib/ui/HiPreferenceSpace;
    if-eqz v2, :spaces_finished
    invoke-virtual {p0, v1}, Landroid/preference/PreferenceGroup;->removePreference(Landroid/preference/Preference;)Z
    goto :spaces_done
    :spaces_finished
    return-void
.end method

.method public static fitVlcVideo(Lorg/videolan/libvlc/MediaPlayer;Ljava/lang/String;)V
    .locals 1
    const/4 v0, 0x0
    invoke-virtual {p0, v0}, Lorg/videolan/libvlc/MediaPlayer;->setAspectRatio(Ljava/lang/String;)V
    return-void
.end method

.method public static quietPlayer()V
    .locals 1
    const/16 v0, 0x8
    invoke-static {v0}, Ltv/danmaku/ijk/media/player/IjkMediaPlayer;->native_setLogLevel(I)V
    return-void
.end method
