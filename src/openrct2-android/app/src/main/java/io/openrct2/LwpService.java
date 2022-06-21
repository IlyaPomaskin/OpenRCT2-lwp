package io.openrct2;

public class LwpService extends org.libsdl.app.LwpService {
    public float getDefaultScale() {
        return getResources().getDisplayMetrics().density;
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{
            "c++_shared",
            "speexdsp",
            "png16",
            "SDL2-2.0",
            "openrct2",
            "openrct2-ui",
        };
    }

    @Override
    public String[] getArguments() {
        return new String[0];
    }
}
