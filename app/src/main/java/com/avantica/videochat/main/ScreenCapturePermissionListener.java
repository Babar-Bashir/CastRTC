package com.avantica.videochat.main;

import android.content.Intent;

import androidx.annotation.Nullable;


interface ScreenCapturePermissionListener {

    public void onScreenCapturePermissionResult(boolean hasPermission, @Nullable Intent data);
}
