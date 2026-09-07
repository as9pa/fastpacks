package io.github.as9pa.fastpacks.filter;

import java.io.IOException;
import java.io.InputStream;

/** Reads a PNG's pixel width from its IHDR chunk without decoding the image. */
public final class PngHeader {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    static final int HEADER_BYTES = 24;

    private PngHeader() {}

    /** Width in pixels, or -1 if the stream is too short or not a PNG. Does not close the stream. */
    public static int width(InputStream in) {
        byte[] head = new byte[HEADER_BYTES];
        int read = 0;
        try {
            while (read < HEADER_BYTES) {
                int n = in.read(head, read, HEADER_BYTES - read);
                if (n < 0) {
                    return -1;
                }
                read += n;
            }
        } catch (IOException e) {
            return -1;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (head[i] != SIGNATURE[i]) {
                return -1;
            }
        }
        if (head[12] != 'I' || head[13] != 'H' || head[14] != 'D' || head[15] != 'R') {
            return -1;
        }
        int w = ((head[16] & 0xff) << 24) | ((head[17] & 0xff) << 16) | ((head[18] & 0xff) << 8) | (head[19] & 0xff);
        return w > 0 ? w : -1;
    }
}
