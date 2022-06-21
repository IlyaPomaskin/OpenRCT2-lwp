package org.libsdl.app;

import android.util.Log;

public class SDLMain implements Runnable {
    @Override
    public void run() {
        SDLActivity.nativeInit(SDLActivity.mSingleton.getArguments());

        Log.v("SDL", "SDL thread terminated");
    }
}
