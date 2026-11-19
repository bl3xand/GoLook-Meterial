.class public final Llocal/materialui/UiLog;
.super Ljava/lang/Object;
.source "UiLog.java"

.method public static eInt(Ljava/lang/String;Ljava/lang/String;I)V
    .locals 2
    invoke-static {p1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;
    move-result-object v0
    invoke-static {p2}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    invoke-static {p0, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    return-void
.end method
