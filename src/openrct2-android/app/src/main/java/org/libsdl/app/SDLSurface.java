package org.libsdl.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;


/**
 * SDLSurface. This is what we draw on, so we need to know when it's created
 * in order to do anything useful.
 * <p>
 * Because of this, that's where we set up the SDL thread
 */

public class SDLSurface extends SurfaceView implements SurfaceHolder.Callback {
    private String TAG = "SDLSurface";

    // Sensors
//    protected static SensorManager mSensorManager;
    protected static Display mDisplay;
    // Keep track of the surface size to normalize touch events
    protected static float mWidth;
    protected static float mHeight;
    private SurfaceHolder mSurfaceHolder;

    // Startup
    public SDLSurface(Context context) {
        super(context);
        TAG = TAG + Math.round((Math.random() * 10));
        Log.v(TAG, "SDLSurface");
        getHolder().addCallback(this);

        setFocusable(false);
        setFocusableInTouchMode(false);

        mDisplay = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mWidth = 1.0f;
        mHeight = 1.0f;
    }

    public Surface getNativeSurface() {
        Log.v(TAG, "getNativeSurface");
        return mSurfaceHolder.getSurface();
    }

    public SurfaceHolder getSurfaceHolder() {
        Log.v(TAG, "getSurfaceHolder");
        return mSurfaceHolder;
    }

    // Called when we have a valid drawing surface
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        mSurfaceHolder = holder;
    }

    // Called when we lose the surface
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed()");
        // Call this *before* setting mIsSurfaceReady to 'false'
        SDLActivity.handlePause();
        SDLActivity.mIsSurfaceReady = false;
        SDLActivity.onNativeSurfaceDestroyed();
    }

    // Called when the surface is resized
    @Override
    public void surfaceChanged(SurfaceHolder holder,
                               int format, int width, int height) {
        Log.v("SDL", "surfaceChanged()");

        int sdlFormat = 0x15151002; // SDL_PIXELFORMAT_RGB565 by default
        switch (format) {
            case PixelFormat.A_8:
                Log.v("SDL", "pixel format A_8");
                break;
            case PixelFormat.LA_88:
                Log.v("SDL", "pixel format LA_88");
                break;
            case PixelFormat.L_8:
                Log.v("SDL", "pixel format L_8");
                break;
            case PixelFormat.RGBA_4444:
                Log.v("SDL", "pixel format RGBA_4444");
                sdlFormat = 0x15421002; // SDL_PIXELFORMAT_RGBA4444
                break;
            case PixelFormat.RGBA_5551:
                Log.v("SDL", "pixel format RGBA_5551");
                sdlFormat = 0x15441002; // SDL_PIXELFORMAT_RGBA5551
                break;
            case PixelFormat.RGBA_8888:
                Log.v("SDL", "pixel format RGBA_8888");
                sdlFormat = 0x16462004; // SDL_PIXELFORMAT_RGBA8888
                break;
            case PixelFormat.RGBX_8888:
                Log.v("SDL", "pixel format RGBX_8888");
                sdlFormat = 0x16261804; // SDL_PIXELFORMAT_RGBX8888
                break;
            case PixelFormat.RGB_332:
                Log.v("SDL", "pixel format RGB_332");
                sdlFormat = 0x14110801; // SDL_PIXELFORMAT_RGB332
                break;
            case PixelFormat.RGB_565:
                Log.v("SDL", "pixel format RGB_565");
                sdlFormat = 0x15151002; // SDL_PIXELFORMAT_RGB565
                break;
            case PixelFormat.RGB_888:
                Log.v("SDL", "pixel format RGB_888");
                // Not sure this is right, maybe SDL_PIXELFORMAT_RGB24 instead?
                sdlFormat = 0x16161804; // SDL_PIXELFORMAT_RGB888
                break;
            default:
                Log.v("SDL", "pixel format unknown " + format);
                break;
        }

        mWidth = width;
        mHeight = height;
        SDLActivity.onNativeResize(width, height, sdlFormat, mDisplay.getRefreshRate());
        Log.v("SDL", "Window size: " + width + "x" + height);


        // Set mIsSurfaceReady to 'true' *before* making a call to handleResume
        SDLActivity.mIsSurfaceReady = true;
        SDLActivity.onNativeSurfaceChanged();


        if (SDLActivity.mSDLThread == null) {
            // This is the entry point to the C app.
            // Start up the C app thread and enable sensor input for the first time

            final Thread sdlThread = new Thread(new SDLMain(), "SDLThread");
//            enableSensor(Sensor.TYPE_ACCELEROMETER, true);
            sdlThread.start();

            // Set up a listener thread to catch when the native thread ends
            SDLActivity.mSDLThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        sdlThread.join();
                    } catch (Exception e) {
                        Log.v("SDL", "Thread join failed");
                    } finally {
                        // Native thread has finished
                        if (!SDLActivity.mExitCalledFromJava) {
                            SDLActivity.handleNativeExit();
                        }
                    }
                }
            }, "SDLThreadListener");
            SDLActivity.mSDLThread.start();
        }

        if (SDLActivity.mHasFocus) {
            SDLActivity.handleResume();
        }
    }

}
