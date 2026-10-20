package io.github.pigerzhu.onelab.system;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.pigerzhu.onelab.R;

/** Publishes the bundled donation image to the system photo library unchanged. */
public final class DonationImageSaver {
    public static final String DISPLAY_NAME = "OneLab-WeChat-Donation.jpg";
    public static final String DISPLAY_PATH = "Pictures/OneLab";

    private DonationImageSaver() {
    }

    public static Uri save(Context context) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, DISPLAY_NAME);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, DISPLAY_PATH);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("cannot create donation image");
        try (InputStream input = context.getResources().openRawResource(R.raw.wechat_donation);
             OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) throw new IOException("cannot write donation image");
            copy(input, output);
        } catch (Throwable error) {
            resolver.delete(uri, null, null);
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("cannot save donation image", error);
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        if (resolver.update(uri, values, null, null) != 1) {
            resolver.delete(uri, null, null);
            throw new IOException("cannot publish donation image");
        }
        return uri;
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
    }
}
