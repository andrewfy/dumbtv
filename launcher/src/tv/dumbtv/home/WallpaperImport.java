package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Copies only validated images into the app-owned wallpaper collection. No network access. */
final class WallpaperImport {
    static final long MAX_BYTES = 20L * 1024 * 1024;

    private WallpaperImport() {}

    static File save(InputStream source, File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create the wallpaper folder");
        }
        File tmp = File.createTempFile("import-", ".part", directory);
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                BoundedCopy.copy(source, out, MAX_BYTES);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getPath(), options);
            String mime = options.outMimeType;
            if (options.outWidth <= 0 || options.outHeight <= 0
                    || options.outWidth > 8192 || options.outHeight > 8192
                    || (long) options.outWidth * options.outHeight > 20_000_000L) {
                throw new IOException("Choose an image up to 8192 pixels per side and 20 megapixels");
            }
            String extension;
            if ("image/jpeg".equals(mime)) extension = ".jpg";
            else if ("image/png".equals(mime)) extension = ".png";
            else if ("image/webp".equals(mime)) extension = ".webp";
            else throw new IOException("Choose a JPG, PNG or WebP image");
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) {
                options.inSampleSize *= 2;
            }
            Bitmap check = BitmapFactory.decodeFile(tmp.getPath(), options);
            if (check == null) throw new IOException("The image could not be read");
            check.recycle();
            File image = new File(directory, "import-" + UUID.randomUUID() + extension);
            if (!tmp.renameTo(image)) throw new IOException("Could not save the wallpaper");
            return image;
        } finally {
            // The original source and existing wallpapers are never changed.
            if (tmp.exists()) tmp.delete();
        }
    }
}
