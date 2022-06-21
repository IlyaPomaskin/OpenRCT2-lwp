package org.libsdl.app;

import android.util.Log;

public class LwdSDLMain implements Runnable {
    @Override
    public void run() {
        LwpService.nativeInit(LwpService.mSingleton.getArguments());

        Log.v("SDL", "SDL thread terminated");
    }
}
