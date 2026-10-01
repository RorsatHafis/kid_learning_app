package com.platform.content.media;

import com.platform.content.entity.AssetType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What an upload is comes from its bytes alone (X11). */
class MediaInspectorTests {

    @Test
    void recognisesThePicturesAndRecordingsBrowsersHandle() throws Exception {
        assertThat(MediaInspector.inspect(image("png", 30, 20))).isEqualTo(new MediaInspector.Inspected(AssetType.IMAGE, "image/png"));
        assertThat(MediaInspector.inspect(image("jpg", 30, 20))).isEqualTo(new MediaInspector.Inspected(AssetType.IMAGE, "image/jpeg"));
        assertThat(MediaInspector.inspect(webpLossless(64, 48)).mimeType()).isEqualTo("image/webp");
        assertThat(MediaInspector.inspect(bytes("ID3", 64)).mimeType()).isEqualTo("audio/mpeg");
        byte[] frame = new byte[64];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        assertThat(MediaInspector.inspect(frame).mimeType()).isEqualTo("audio/mpeg");
        assertThat(MediaInspector.inspect(bytes("OggS", 64)).mimeType()).isEqualTo("audio/ogg");
        byte[] m4a = bytes("\0\0\0 ftypM4A ", 64);
        assertThat(MediaInspector.inspect(m4a)).isEqualTo(new MediaInspector.Inspected(AssetType.AUDIO, "audio/mp4"));
        byte[] wav = bytes("RIFF\0\0\0\0WAVE", 64);
        assertThat(MediaInspector.inspect(wav).type()).isEqualTo(AssetType.AUDIO);
    }

    @Test
    void refusesWhatIsNotOneOfThemWhateverItIsCalled() {
        assertThatThrownBy(() -> MediaInspector.inspect(bytes("<svg xmlns='http://www.w3.org/2000/svg'>", 64)))
                .isInstanceOf(MediaRejectedException.class);
        assertThatThrownBy(() -> MediaInspector.inspect(bytes("GIF89a", 64))).isInstanceOf(MediaRejectedException.class);
        assertThatThrownBy(() -> MediaInspector.inspect(bytes("%PDF-1.7", 64))).isInstanceOf(MediaRejectedException.class);
        assertThatThrownBy(() -> MediaInspector.inspect(new byte[4])).isInstanceOf(MediaRejectedException.class);
        assertThatThrownBy(() -> MediaInspector.inspect(null)).isInstanceOf(MediaRejectedException.class);
    }

    @Test
    void refusesPicturesTooLargeToDecodeAndFilesTooHeavy() throws Exception {
        assertThatThrownBy(() -> MediaInspector.inspect(image("png", 4097, 2))).hasMessageContaining("4096");
        assertThatThrownBy(() -> MediaInspector.inspect(webpLossless(5000, 10))).hasMessageContaining("4096");
        byte[] damagedJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertThatThrownBy(() -> MediaInspector.inspect(damagedJpeg)).hasMessageContaining("damaged");
        byte[] heavyAudio = Arrays.copyOf(bytes("OggS", 64), (int) MediaInspector.MAX_AUDIO_BYTES + 1);
        assertThatThrownBy(() -> MediaInspector.inspect(heavyAudio)).hasMessageContaining("at most 5 MB");
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    /** A lossless WebP header: RIFF, WEBP, a VP8L chunk with 14-bit width-1 and height-1. */
    private static byte[] webpLossless(int width, int height) {
        byte[] b = bytes("RIFF\0\0\0\0WEBPVP8L\0\0\0\0", 64);
        b[20] = 0x2F;
        long bits = (width - 1L) | ((height - 1L) << 14);
        b[21] = (byte) bits;
        b[22] = (byte) (bits >> 8);
        b[23] = (byte) (bits >> 16);
        b[24] = (byte) (bits >> 24);
        return b;
    }

    private static byte[] bytes(String start, int length) {
        byte[] head = start.getBytes(StandardCharsets.ISO_8859_1);
        return Arrays.copyOf(head, Math.max(length, head.length));
    }

}
