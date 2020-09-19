package com.avantica.videochat;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

/***********************************
 * Created by Babar on 9/19/2020.  *
 ***********************************/
public class App extends Application {
    private static Context context;
    private static Intent captureIntent;

    public static Intent getCaptureIntent() {
        return captureIntent;
    }

    public static void setCaptureIntent(Intent captureIntentt) {
        captureIntent = captureIntentt;
    }

    public static Context getContext() {
        return context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }
}
