package tv.dumbtv.home;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Stream limit applies even when a provider does not supply a trustworthy file size. */
final class BoundedCopy {
    private BoundedCopy() {}

    static void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int n; (n = in.read(buffer)) != -1; ) {
            total += n;
            if (total > limit) throw new IOException("Choose an image smaller than 20 MB");
            out.write(buffer, 0, n);
        }
        if (total == 0) throw new IOException("The image file is empty");
    }
}
