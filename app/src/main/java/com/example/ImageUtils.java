package com.example;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.InputStream;

public class ImageUtils {

    public static Bitmap getBitmapFromUri(Context context, Uri uri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;
            
            // Decode bounds
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);

            int maxDimension = 1920;
            int sampleSize = 1;
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / sampleSize) >= maxDimension || (halfWidth / sampleSize) >= maxDimension) {
                    sampleSize *= 2;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;

            try (InputStream streamForDecode = context.getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(streamForDecode, null, options);
                return rotateImageIfRequired(context, bitmap, uri);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage) {
        if (img == null) return null;
        try (InputStream input = context.getContentResolver().openInputStream(selectedImage)) {
            if (input == null) return img;

            ExifInterface ei = new ExifInterface(input);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return rotateImage(img, 90);
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return rotateImage(img, 180);
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return rotateImage(img, 270);
                default:
                    return img;
            }
        } catch (Exception e) {
            return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        if (rotatedImg != img) {
            img.recycle();
        }
        return rotatedImg;
    }
}
