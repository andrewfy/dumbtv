package tv.dumbtv.home;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Checks provider streams with no size metadata, including failures at a chunk boundary. */
public final class BoundedCopyTest {
    private BoundedCopyTest() {}

    public static void main(String[] args) throws IOException {
        byte[] image = new byte[8193];
        Arrays.fill(image, (byte) 17);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BoundedCopy.copy(new ByteArrayInputStream(image), out, image.length);
        if (!Arrays.equals(image, out.toByteArray())) throw new AssertionError("copy changed bytes");
        out.reset();
        try {
            BoundedCopy.copy(new ByteArrayInputStream(image), out, 8192);
            throw new AssertionError("oversized stream accepted");
        } catch (IOException expected) {
            if (out.size() > 8192) throw new AssertionError("wrote beyond limit");
        }
        try {
            BoundedCopy.copy(new ByteArrayInputStream(new byte[0]), out, 8192);
            throw new AssertionError("empty stream accepted");
        } catch (IOException expected) {
            // Empty input must not create a wallpaper.
        }
        System.out.println("BoundedCopyTest passed");
    }
}
