package org.libsdl.app;

import android.util.Log;

public class SDLMain implements Runnable {
    public String TAG = "SDLMain";

    @Override
    public void run() {
        TAG = TAG + Math.round((Math.random() * 10));
        Log.v(TAG, "SDL thread start");

        SDLActivity.nativeInit(SDLActivity.mSingleton.getArguments());

        Log.v(TAG, "SDL thread terminated");
    }
}
