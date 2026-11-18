package com.videoflow.local;

import android.app.Application;

public class VideoFlowApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Store.get(this);
    }
}
