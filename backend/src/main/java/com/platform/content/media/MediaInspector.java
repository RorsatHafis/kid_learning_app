package com.platform.content.media;

import com.platform.content.entity.AssetType;

import java.nio.charset.StandardCharsets;

/**
 * What an uploaded file really is, from its bytes (never the name or the type the browser
 * claims): a picture (PNG, JPEG, WebP) or a recording (MP3, M4A, Ogg, WAV) - the formats every
 * browser a family uses can show or play. Anything else is refused, and so is a picture larger
 * than {@link #MAX_DIMENSION} on a side, which a child's tablet would have to decode in full.
 * SVG is not accepted: it is a document that can carry script, not a picture.
 */
public final class MediaInspector {

    public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    public static final long MAX_AUDIO_BYTES = 5L * 1024 * 1024;
    public static final int MAX_DIMENSION = 4096;

    private MediaInspector() {
    }

    public record Inspected(AssetType type, String mimeType) {
    }

    public static Inspected inspect(byte[] b) {
        if (b == null || b.length < 12) {
            throw new MediaRejectedException("That file is empty or too small to be a picture or a recording.");
        }
        Inspected inspected = recognise(b);
        long limit = inspected.type() == AssetType.IMAGE ? MAX_IMAGE_BYTES : MAX_AUDIO_BYTES;
        if (b.length > limit) {
            throw new MediaRejectedException("%s can be at most %d MB.".formatted(
                    inspected.type() == AssetType.IMAGE ? "A picture" : "A recording", limit / (1024 * 1024)));
        }
        return inspected;
    }

    private static Inspected recognise(byte[] b) {
        if (u8(b, 0) == 0x89 && ascii(b, 1, "PNG") && u8(b, 4) == 0x0D && u8(b, 5) == 0x0A && b.length >= 24) {
            requireSize(be32(b, 16), be32(b, 20));
            return new Inspected(AssetType.IMAGE, "image/png");
        }
        if (u8(b, 0) == 0xFF && u8(b, 1) == 0xD8 && u8(b, 2) == 0xFF) {
            int[] size = jpegSize(b);
            requireSize(size[0], size[1]);
            return new Inspected(AssetType.IMAGE, "image/jpeg");
        }
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP") && b.length >= 30) {
            int[] size = webpSize(b);
            requireSize(size[0], size[1]);
            return new Inspected(AssetType.IMAGE, "image/webp");
        }
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WAVE")) {
            return new Inspected(AssetType.AUDIO, "audio/wav");
        }
        if (ascii(b, 0, "OggS")) {
            return new Inspected(AssetType.AUDIO, "audio/ogg");
        }
        if (ascii(b, 4, "ftyp") && (ascii(b, 8, "M4A ") || ascii(b, 8, "mp42") || ascii(b, 8, "isom") || ascii(b, 8, "M4B "))) {
            return new Inspected(AssetType.AUDIO, "audio/mp4");
        }
        if (ascii(b, 0, "ID3") || (u8(b, 0) == 0xFF && (u8(b, 1) & 0xE6) == 0xE2)) {
            return new Inspected(AssetType.AUDIO, "audio/mpeg");
        }
        throw new MediaRejectedException("Upload a picture (PNG, JPEG or WebP) or a recording (MP3, M4A, Ogg or WAV).");
    }

    private static void requireSize(long width, long height) {
        if (width <= 0 || height <= 0) {
            throw new MediaRejectedException("That picture's file is damaged.");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new MediaRejectedException("A picture can be at most %d pixels on a side (this one is %d × %d).".formatted(
                    MAX_DIMENSION, width, height));
        }
    }

    /** Walks the JPEG markers to the first frame header (SOFn), which holds the size. */
    private static int[] jpegSize(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if (u8(b, i) != 0xFF) {
                break;
            }
            int marker = u8(b, i + 1);
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            int length = (u8(b, i + 2) << 8) | u8(b, i + 3);
            boolean frame = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (frame) {
                int height = (u8(b, i + 5) << 8) | u8(b, i + 6);
                int width = (u8(b, i + 7) << 8) | u8(b, i + 8);
                return new int[] {width, height};
            }
            if (length < 2) {
                break;
            }
            i += 2 + length;
        }
        throw new MediaRejectedException("That picture's file is damaged.");
    }

    private static int[] webpSize(byte[] b) {
        if (ascii(b, 12, "VP8X")) {
            return new int[] {le24(b, 24) + 1, le24(b, 27) + 1};
        }
        if (ascii(b, 12, "VP8L") && u8(b, 20) == 0x2F) {
            long bits = u8(b, 21) | (u8(b, 22) << 8) | (u8(b, 23) << 16) | ((long) u8(b, 24) << 24);
            return new int[] {(int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1};
        }
        if (ascii(b, 12, "VP8 ")) {
            return new int[] {((u8(b, 27) << 8) | u8(b, 26)) & 0x3FFF, ((u8(b, 29) << 8) | u8(b, 28)) & 0x3FFF};
        }
        throw new MediaRejectedException("That picture's file is damaged.");
    }

    private static int u8(byte[] b, int i) {
        return i < b.length ? b[i] & 0xFF : -1;
    }

    private static long be32(byte[] b, int i) {
        return ((long) u8(b, i) << 24) | (u8(b, i + 1) << 16) | (u8(b, i + 2) << 8) | u8(b, i + 3);
    }

    private static int le24(byte[] b, int i) {
        return u8(b, i) | (u8(b, i + 1) << 8) | (u8(b, i + 2) << 16);
    }

    private static boolean ascii(byte[] b, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (offset + expected.length > b.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (b[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

}
