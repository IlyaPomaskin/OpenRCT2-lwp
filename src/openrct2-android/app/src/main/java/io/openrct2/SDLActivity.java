package io.openrct2;

import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SDLActivity extends org.libsdl.app.SDLActivity {
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


    // TODO Don't copy/enumerate assets on every startup
    // When building, ensure OpenRCT2 assets are inside their own directory within the APK assets,
    // so that we do not attempt to copy files out of the standard Android asset folders - webkit, etc.
    private void copyAssets() {
        File dataDir = new File(Environment.getExternalStorageDirectory().toString()
            + File.separator + "openrct2" + File.separator);

        try {
            copyAsset(getAssets(), "openrct2", dataDir, "");
        } catch (IOException e) {
            Log.e("io.openrct2", "Error extracting files", e);
            return;
        }

    }

    // srcPath cannot be the empty string
    private void copyAsset(AssetManager assets, String srcPath, File dataDir, String destPath) throws IOException {
        String[] list = assets.list(srcPath);

        if (list.length == 0) {
            InputStream input = assets.open(srcPath);
            File extractedFile = new File(dataDir, destPath);
            File parentFile = extractedFile.getParentFile();
            if (!parentFile.exists()) {
                boolean success = parentFile.mkdirs();
                if (!success) {
                    Log.d("io.openrct2", String.format("Error creating folder '%s'", parentFile));
                }
            }
            FileOutputStream output = new FileOutputStream(extractedFile);
            IOUtils.copyLarge(input, output);
            output.close();
            input.close();
            return;
        }

        for (String fileName : list) {
            // This ternary expression makes sure that this string does not begin with a slash
            String destination = destPath + (destPath.equals("") ? "" : File.separator) + fileName;
            copyAsset(assets, srcPath + File.separator + fileName, dataDir, destination);
        }
    }
}
