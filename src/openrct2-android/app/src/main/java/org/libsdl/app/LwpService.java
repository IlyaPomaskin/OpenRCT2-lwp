package org.libsdl.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

public class LwpService extends WallpaperService {
    private static final String TAG = "SDL";
    public static boolean mIsPaused, mIsSurfaceReady, mHasFocus;
    public static boolean mExitCalledFromJava;
    /**
     * If shared libraries (e.g. SDL or the native application) could not be loaded.
     */
    public static boolean mBrokenLibraries;
    public static boolean mSeparateMouseAndTouch;
    public static LwpService mSingleton;
    protected static SDLSurface mSurface;
    protected static View mTextEdit;
    protected static ViewGroup mLayout;
    public static Thread mSDLThread;
    protected static AudioTrack mAudioTrack;
    protected static AudioRecord mAudioRecord;

    /**
     * This method is called by SDL before loading the native shared libraries.
     * It can be overridden to provide names of shared libraries to be loaded.
     * The default implementation returns the defaults. It never returns null.
     * An array returned by a new implementation must at least contain "SDL2".
     * Also keep in mind that the order the libraries are loaded may matter.
     *
     * @return names of shared libraries to be loaded (e.g. "SDL2", "main").
     */
    protected String[] getLibraries() {
        return new String[]{
            "c++_shared",
            "speexdsp",
            "png16",
            "SDL2-2.0",
            "openrct2",
            "openrct2-ui"
        };
    }

    public void loadLibraries() {
        for (String lib : getLibraries()) {
            System.loadLibrary(lib);
        }
    }


    /**
     * This method is called by SDL before starting the native application thread.
     * It can be overridden to provide the arguments after the application name.
     * The default implementation returns an empty array. It never returns null.
     *
     * @return arguments for the native application.
     */
    public String[] getArguments() {
        return new String[0];
    }

    public static void initialize() {
        mSingleton = null;
        mSurface = null;
        mTextEdit = null;
        mLayout = null;
        mSDLThread = null;
        mAudioTrack = null;
        mAudioRecord = null;
        mExitCalledFromJava = false;
        mBrokenLibraries = false;
        mIsPaused = false;
        mIsSurfaceReady = false;
        mHasFocus = true;
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "Device: " + android.os.Build.DEVICE);
        Log.v(TAG, "Model: " + android.os.Build.MODEL);
        Log.v(TAG, "onCreate(): " + mSingleton);
        super.onCreate();

        LwpService.initialize();
        mSingleton = this;

        String errorMsgBrokenLib = "";
        try {
            loadLibraries();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            mBrokenLibraries = true;
            errorMsgBrokenLib = e.getMessage();
        }

        if (mBrokenLibraries) {
            AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
            dlgAlert.setMessage("An error occurred while trying to start the application. Please try again and/or reinstall."
                + System.getProperty("line.separator")
                + System.getProperty("line.separator")
                + "Error: " + errorMsgBrokenLib);
            dlgAlert.setTitle("SDL Error");
            dlgAlert.setPositiveButton("Exit",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, close current activity
//                        LwpService.mSingleton.finish();
                        LwpService.mSingleton.stopSelf();
                    }
                });
            dlgAlert.setCancelable(false);
            dlgAlert.create().show();

            return;
        }

        mSurface = new SDLSurface(getApplication());
    }

    @Override
    public void onLowMemory() {
        Log.v(TAG, "onLowMemory()");
        super.onLowMemory();

        if (LwpService.mBrokenLibraries) {
            return;
        }

        LwpService.nativeLowMemory();
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy()");

        if (LwpService.mBrokenLibraries) {
            super.onDestroy();
            // Reset everything in case the user re opens the app
            LwpService.initialize();
            return;
        }

        // Send a quit message to the application
        LwpService.mExitCalledFromJava = true;
        LwpService.nativeQuit();

        // Now wait for the SDL thread to quit
        if (LwpService.mSDLThread != null) {
            try {
                LwpService.mSDLThread.join();
            } catch (Exception e) {
                Log.v(TAG, "Problem stopping thread: " + e);
            }
            LwpService.mSDLThread = null;

            //Log.v(TAG, "Finished waiting for SDL thread");
        }

        super.onDestroy();
        // Reset everything in case the user re opens the app
        LwpService.initialize();
    }

    public static void handlePause() {
        if (!SDLActivity.mIsPaused && SDLActivity.mIsSurfaceReady) {
            SDLActivity.mIsPaused = true;
            SDLActivity.nativePause();
            mSurface.handlePause();
        }
    }

    /**
     * Called by onResume or surfaceCreated. An actual resume should be done only when the surface is ready.
     * Note: Some Android variants may send multiple surfaceChanged events, so we don't need to resume
     * every time we get one of those events, only if it comes after surfaceDestroyed
     */
    public static void handleResume() {
        if (SDLActivity.mIsPaused && SDLActivity.mIsSurfaceReady && SDLActivity.mHasFocus) {
            SDLActivity.mIsPaused = false;
            SDLActivity.nativeResume();
            mSurface.handleResume();
        }
    }

    /* The native thread has finished */
    public static void handleNativeExit() {
        LwpService.mSDLThread = null;
//        mSingleton.finish();
        LwpService.mSingleton.stopSelf();
    }


    // Messages from the SDLMain thread
    static final int COMMAND_CHANGE_TITLE = 1;
    static final int COMMAND_UNUSED = 2;
    static final int COMMAND_TEXTEDIT_HIDE = 3;
    static final int COMMAND_SET_KEEP_SCREEN_ON = 5;

    protected static final int COMMAND_USER = 0x8000;

    /**
     * This method is called by SDL if SDL did not handle a message itself.
     * This happens if a received message contains an unsupported command.
     * Method can be overwritten to handle Messages in a different class.
     *
     * @param command the command of the message.
     * @param param   the parameter of the message. May be null.
     * @return if the message was handled in overridden method.
     */
    protected boolean onUnhandledMessage(int command, Object param) {
        return false;
    }

    /**
     * A Handler class for Messages from native SDL applications.
     * It uses current Activities as target (e.g. for the title).
     * static to prevent implicit references to enclosing object.
     */
    protected static class SDLCommandHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Context context = getContext();
            if (context == null) {
                Log.e(TAG, "error handling message, getContext() returned null");
                return;
            }
            switch (msg.arg1) {
                case COMMAND_CHANGE_TITLE:
                case COMMAND_TEXTEDIT_HIDE:
                    break;
                case COMMAND_SET_KEEP_SCREEN_ON: {
//                    Window window = ((Activity) context).getWindow();
//                    if (window != null) {
//                        if ((msg.obj instanceof Integer) && (((Integer) msg.obj).intValue() != 0)) {
//                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//                        } else {
//                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//                        }
//                    }
                    break;
                }
                default:
                    if ((context instanceof LwpService) && !((LwpService) context).onUnhandledMessage(msg.arg1, msg.obj)) {
                        Log.e(TAG, "error handling message, command is " + msg.arg1);
                    }
            }
        }
    }

    Handler commandHandler = new LwpService.SDLCommandHandler();

    // Send a message from the SDLMain thread
    boolean sendCommand(int command, Object data) {
        Message msg = commandHandler.obtainMessage();
        msg.arg1 = command;
        msg.obj = data;
        return commandHandler.sendMessage(msg);
    }

    public static native int nativeInit(Object arguments);

    public static native void nativeLowMemory();

    public static native void nativeQuit();

    public static native void nativePause();

    public static native void nativeResume();

    public static native void onNativeDropFile(String filename);

    public static native void onNativeResize(int x, int y, int format, float rate);

    public static native int onNativePadDown(int device_id, int keycode);

    public static native int onNativePadUp(int device_id, int keycode);

    public static native void onNativeJoy(int device_id, int axis,
                                          float value);

    public static native void onNativeHat(int device_id, int hat_id,
                                          int x, int y);

    public static native void onNativeKeyDown(int keycode);

    public static native void onNativeKeyUp(int keycode);

    public static native void onNativeKeyboardFocusLost();

    public static native void onNativeMouse(int button, int action, float x, float y);

    public static native void onNativeTouch(int touchDevId, int pointerFingerId,
                                            int action, float x,
                                            float y, float p);

    public static native void onNativeAccel(float x, float y, float z);

    public static native void onNativeSurfaceChanged();

    public static native void onNativeSurfaceDestroyed();

    public static native int nativeAddJoystick(int device_id, String name,
                                               int is_accelerometer, int nbuttons,
                                               int naxes, int nhats, int nballs);

    public static native int nativeRemoveJoystick(int device_id);

    public static native String nativeGetHint(String name);

    /**
     * This method is called by SDL using JNI.
     */
    public static boolean setActivityTitle(String title) {
        // Called from SDLMain() thread and can't directly affect the view
        return mSingleton.sendCommand(COMMAND_CHANGE_TITLE, title);
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static boolean sendMessage(int command, int param) {
        return mSingleton.sendCommand(command, Integer.valueOf(param));
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static Context getContext() {
        return mSingleton;
    }

    /**
     * This method is called by SDL using JNI.
     *
     * @return result of getSystemService(name) but executed on UI thread.
     */
    public Object getSystemServiceFromUiThread(final String name) {
        final Object lock = new Object();
        final Object[] results = new Object[2]; // array for writable variables
        synchronized (lock) {
            (new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        results[0] = getSystemService(name);
                        results[1] = Boolean.TRUE;
                        lock.notify();
                    }
                }
            }).run();
            if (results[1] == null) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return results[0];
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static boolean showTextInput(int x, int y, int w, int h) {
        return false;
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static Surface getNativeSurface() {
        return LwpService.mSurface.getNativeSurface();
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static int audioOpen(int sampleRate, boolean is16Bit, boolean isStereo, int desiredFrames) {
        return -1;
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static void audioWriteShortBuffer(short[] buffer) {
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static void audioWriteByteBuffer(byte[] buffer) {
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static int captureOpen(int sampleRate, boolean is16Bit, boolean isStereo, int desiredFrames) {
        return 0;
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static int captureReadShortBuffer(short[] buffer, boolean blocking) {
        return 0;
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static int captureReadByteBuffer(byte[] buffer, boolean blocking) {
        return 0;
    }


    /**
     * This method is called by SDL using JNI.
     */
    public static void audioClose() {
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static void captureClose() {
    }


    /**
     * This method is called by SDL using JNI.
     *
     * @return an array which may be empty but is never null.
     */
    public static int[] inputGetInputDeviceIds(int sources) {
        return new int[0];
    }

    public static boolean handleJoystickMotionEvent(MotionEvent event) {
        return false;
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static void pollInputDevices() {
    }

    // Check if a given device is considered a possible SDL joystick
    public static boolean isDeviceSDLJoystick(int deviceId) {
        return false;
    }

    // APK expansion files support

    /**
     * com.android.vending.expansion.zipfile.ZipResourceFile object or null.
     */
    private Object expansionFile;

    /**
     * com.android.vending.expansion.zipfile.ZipResourceFile's getInputStream() or null.
     */
    private Method expansionFileMethod;

    /**
     * This method is called by SDL using JNI.
     *
     * @return an InputStream on success or null if no expansion file was used.
     * @throws IOException on errors. Message is set for the SDL error message.
     */
    public InputStream openAPKExpansionInputStream(String fileName) throws IOException {
        // Get a ZipResourceFile representing a merger of both the main and patch files
        if (expansionFile == null) {
            String mainHint = nativeGetHint("SDL_ANDROID_APK_EXPANSION_MAIN_FILE_VERSION");
            if (mainHint == null) {
                return null; // no expansion use if no main version was set
            }
            String patchHint = nativeGetHint("SDL_ANDROID_APK_EXPANSION_PATCH_FILE_VERSION");
            if (patchHint == null) {
                return null; // no expansion use if no patch version was set
            }

            Integer mainVersion;
            Integer patchVersion;
            try {
                mainVersion = Integer.valueOf(mainHint);
                patchVersion = Integer.valueOf(patchHint);
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
                throw new IOException("No valid file versions set for APK expansion files", ex);
            }

            try {
                // To avoid direct dependency on Google APK expansion library that is
                // not a part of Android SDK we access it using reflection
                expansionFile = Class.forName("com.android.vending.expansion.zipfile.APKExpansionSupport")
                    .getMethod("getAPKExpansionZipFile", Context.class, int.class, int.class)
                    .invoke(null, this, mainVersion, patchVersion);

                expansionFileMethod = expansionFile.getClass()
                    .getMethod("getInputStream", String.class);
            } catch (Exception ex) {
                ex.printStackTrace();
                expansionFile = null;
                expansionFileMethod = null;
                throw new IOException("Could not access APK expansion support library", ex);
            }
        }

        // Get an input stream for a known file inside the expansion file ZIPs
        InputStream fileStream;
        try {
            fileStream = (InputStream) expansionFileMethod.invoke(expansionFile, fileName);
        } catch (Exception ex) {
            // calling "getInputStream" failed
            ex.printStackTrace();
            throw new IOException("Could not open stream from APK expansion file", ex);
        }

        if (fileStream == null) {
            // calling "getInputStream" was successful but null was returned
            throw new IOException("Could not find path in APK expansion file");
        }

        return fileStream;
    }

    /**
     * Result of current messagebox. Also used for blocking the calling thread.
     */
    protected final int[] messageboxSelection = new int[1];

    /**
     * Id of current dialog.
     */
    protected int dialogs = 0;

    /**
     * This method is called by SDL using JNI.
     * Shows the messagebox from UI thread and block calling thread.
     * buttonFlags, buttonIds and buttonTexts must have same length.
     *
     * @param buttonFlags array containing flags for every button.
     * @param buttonIds   array containing id for every button.
     * @param buttonTexts array containing text for every button.
     * @param colors      null for default or array of length 5 containing colors.
     * @return button id or -1.
     */
    public int messageboxShowMessageBox(
        final int flags,
        final String title,
        final String message,
        final int[] buttonFlags,
        final int[] buttonIds,
        final String[] buttonTexts,
        final int[] colors) {

        messageboxSelection[0] = -1;

        // sanity checks

        if ((buttonFlags.length != buttonIds.length) && (buttonIds.length != buttonTexts.length)) {
            return -1; // implementation broken
        }

        // collect arguments for Dialog
//
//        final Bundle args = new Bundle();
//        args.putInt("flags", flags);
//        args.putString("title", title);
//        args.putString("message", message);
//        args.putIntArray("buttonFlags", buttonFlags);
//        args.putIntArray("buttonIds", buttonIds);
//        args.putStringArray("buttonTexts", buttonTexts);
//        args.putIntArray("colors", colors);

        System.out.printf("title: %s message: %s\n", title, message);

        // trigger Dialog creation on UI thread
//
//        (new Runnable() {
//            @Override
//            public void run() {
////                showDialog(dialogs++, args);
//            }
//        }).run();

        // block the calling thread

//        synchronized (messageboxSelection) {
//            try {
//                messageboxSelection.wait();
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//                return -1;
//            }
//        }

        // return selected value

        return messageboxSelection[0];
    }


    @Override
    public Engine onCreateEngine() {
        return new LwpEngine();
    }

    class LwpEngine extends Engine {
        private final SDLSurface mSurf = new SDLSurface(getContext());

        LwpEngine() {
            super();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                mSurf.handleResume();
            } else {
                mSurf.handlePause();
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
        }

        @Override
        public SurfaceHolder getSurfaceHolder() {
            return mSurf.getSurfaceHolder();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            mSurf.surfaceCreated(holder);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mSurf.surfaceChanged(holder, format, width, height);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            mSurf.surfaceDestroyed(holder);
        }
    }

}

