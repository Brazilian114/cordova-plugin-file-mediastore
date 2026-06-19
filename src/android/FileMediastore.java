package com.pttdigital.dhappy.mediastore;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileMediastore extends CordovaPlugin {
    private static final int SAVE_FILE_REQUEST = 43180;
    private static final String DEFAULT_MIME_TYPE = "application/pdf";

    private CallbackContext saveCallback;
    private byte[] saveData;
    private String saveMimeType;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        JSONObject options = args.optJSONObject(0);
        if (options == null) {
            callbackContext.error("Missing options");
            return true;
        }

        if ("writeFile".equals(action)) {
            saveFile(options, callbackContext);
            return true;
        }

        if ("saveFile".equals(action)) {
            saveFile(options, callbackContext);
            return true;
        }

        return false;
    }

    private void writeFile(final JSONObject options, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = decodeData(options.optString("data", ""));
                    String filename = requiredFilename(options);
                    String mimeType = options.optString("mimeType", DEFAULT_MIME_TYPE);
                    String folder = options.optString("folder", Environment.DIRECTORY_DOWNLOADS);
                    String uri = writeToDownloads(bytes, filename, mimeType, folder);
                    callbackContext.success(uri);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void saveFile(JSONObject options, CallbackContext callbackContext) {
        try {
            saveData = decodeData(options.optString("data", ""));
            saveMimeType = options.optString("mimeType", DEFAULT_MIME_TYPE);
            String filename = requiredFilename(options);
            saveCallback = callbackContext;

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(saveMimeType);
            intent.putExtra(Intent.EXTRA_TITLE, filename);

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            cordova.startActivityForResult(this, intent, SAVE_FILE_REQUEST);
        } catch (Exception e) {
            clearPendingSave();
            callbackContext.error(e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != SAVE_FILE_REQUEST) {
            return;
        }

        final CallbackContext callback = saveCallback;
        final byte[] bytes = saveData;
        final String mimeType = saveMimeType;
        final Uri uri = intent == null ? null : intent.getData();
        clearPendingSave();

        if (callback == null) {
            return;
        }

        if (resultCode != Activity.RESULT_OK || uri == null) {
            callback.error("Save file cancelled");
            return;
        }

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    writeBytes(uri, bytes, mimeType);
                    callback.success(uri.toString());
                } catch (Exception e) {
                    callback.error(e.getMessage());
                }
            }
        });
    }

    private String writeToDownloads(byte[] bytes, String filename, String mimeType, String folder) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = cordova.getActivity().getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDownloadPath(folder));
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Unable to create MediaStore entry");
            }

            try {
                writeBytes(uri, bytes, mimeType);
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return uri.toString();
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }
        }

        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create Downloads directory");
        }

        File output = uniqueFile(directory, filename);
        FileOutputStream stream = new FileOutputStream(output);
        try {
            stream.write(bytes);
        } finally {
            stream.close();
        }

        return Uri.fromFile(output).toString();
    }

    private void writeBytes(Uri uri, byte[] bytes, String mimeType) throws IOException {
        ContentResolver resolver = cordova.getActivity().getContentResolver();
        OutputStream stream = resolver.openOutputStream(uri, "w");
        if (stream == null) {
            throw new IOException("Unable to open output stream");
        }
        try {
            stream.write(bytes);
        } finally {
            stream.close();
        }
    }

    private byte[] decodeData(String data) {
        if (data == null || data.length() == 0) {
            throw new IllegalArgumentException("Missing data");
        }

        int comma = data.indexOf(',');
        String base64 = comma >= 0 ? data.substring(comma + 1) : data;
        return Base64.decode(base64, Base64.DEFAULT);
    }

    private String requiredFilename(JSONObject options) {
        String filename = options.optString("filename", "");
        if (filename.trim().length() == 0) {
            throw new IllegalArgumentException("Missing filename");
        }
        return filename;
    }

    private String relativeDownloadPath(String folder) {
        String downloads = Environment.DIRECTORY_DOWNLOADS;
        if (folder == null || folder.trim().length() == 0 || "Download".equalsIgnoreCase(folder) || "Downloads".equalsIgnoreCase(folder)) {
            return downloads;
        }

        String normalized = folder.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.equalsIgnoreCase(downloads) || normalized.toLowerCase().startsWith(downloads.toLowerCase() + "/")) {
            return normalized;
        }

        return downloads + "/" + normalized;
    }

    private File uniqueFile(File directory, String filename) {
        File candidate = new File(directory, filename);
        if (!candidate.exists()) {
            return candidate;
        }

        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        int index = 1;
        do {
            candidate = new File(directory, base + " (" + index + ")" + extension);
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private void clearPendingSave() {
        saveCallback = null;
        saveData = null;
        saveMimeType = null;
    }
}
