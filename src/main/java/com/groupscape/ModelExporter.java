package com.groupscape;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import net.runelite.api.Model;
import net.runelite.api.TextureProvider;

/**
 * Exports a live {@link Model} (e.g. {@code player.getModel()}) as a real binary-little-endian
 * PLY (Stanford Polygon File Format) mesh, so the webapp can load it with three.js's stock
 * {@code PLYLoader} without a custom parser.
 *
 * <p>Each triangle's 3 corners are written as their own vertices (no shared-vertex indexing) so
 * each corner can carry its own per-face-corner color straight from {@link Model#getFaceColors1()}
 * /2/3 — {@code Model}'s per-face colors don't agree at shared vertices, so deduplicating would
 * need averaging/smoothing this export doesn't attempt. Cheap trade for a player-model-sized mesh
 * (a few hundred to low-thousands of faces).
 */
public final class ModelExporter {
    private ModelExporter() {
    }

    /**
     * @param textureProvider used to resolve a representative color for textured faces (no
     *                        per-vertex color exists for those, per {@link Model#getFaceColors1()}'s
     *                        contract) — pass {@code null} to fall back to a flat gray for them.
     */
    public static byte[] toBytes(Model model, TextureProvider textureProvider) {
        int rawFaceCount = model.getFaceCount();
        float[] x = model.getVerticesX();
        float[] y = model.getVerticesY();
        float[] z = model.getVerticesZ();
        int[] indices1 = model.getFaceIndices1();
        int[] indices2 = model.getFaceIndices2();
        int[] indices3 = model.getFaceIndices3();
        int[] colors1 = model.getFaceColors1();
        int[] colors2 = model.getFaceColors2();
        int[] colors3 = model.getFaceColors3();
        short[] textures = model.getFaceTextures();

        // colors3[face] doubles as a sentinel: HIDDEN_FACE marks a face the game never draws
        // (not a color at all — decoding it as one paints a stray near-white triangle over the
        // model), so it's filtered out before sizing the PLY buffers rather than skipped in place.
        int faceCount = 0;
        for (int face = 0; face < rawFaceCount; face++) {
            if (colors3[face] != HIDDEN_FACE) {
                faceCount++;
            }
        }

        int vertexCount = faceCount * 3;
        ByteBuffer body = ByteBuffer.allocate(vertexCount * VERTEX_RECORD_BYTES + faceCount * FACE_RECORD_BYTES);
        body.order(ByteOrder.LITTLE_ENDIAN);

        for (int face = 0; face < rawFaceCount; face++) {
            if (colors3[face] == HIDDEN_FACE) {
                continue;
            }

            boolean textured = textures != null && face < textures.length && textures[face] != -1;
            // FLAT_SHADED means this face has one color for all three corners, carried in
            // colors1 — colors3 itself holds the sentinel, not a corner color, for that case.
            boolean flat = colors3[face] == FLAT_SHADED;
            writeVertex(body, x[indices1[face]], y[indices1[face]], z[indices1[face]],
                    resolveColor(textured, colors1[face], textures, face, textureProvider));
            writeVertex(body, x[indices2[face]], y[indices2[face]], z[indices2[face]],
                    resolveColor(textured, flat ? colors1[face] : colors2[face], textures, face, textureProvider));
            writeVertex(body, x[indices3[face]], y[indices3[face]], z[indices3[face]],
                    resolveColor(textured, flat ? colors1[face] : colors3[face], textures, face, textureProvider));
        }

        int base = 0;
        for (int face = 0; face < rawFaceCount; face++) {
            if (colors3[face] == HIDDEN_FACE) {
                continue;
            }
            body.put((byte) 3);
            body.putInt(base);
            body.putInt(base + 1);
            body.putInt(base + 2);
            base += 3;
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(header(vertexCount, faceCount).getBytes(StandardCharsets.US_ASCII));
            out.write(body.array());
            return out.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream never actually throws — satisfy the checked signature.
            throw new UncheckedIOException(e);
        }
    }

    private static final int VERTEX_RECORD_BYTES = 3 * Float.BYTES + 3; // x,y,z floats + r,g,b bytes
    private static final int FACE_RECORD_BYTES = 1 + 3 * Integer.BYTES; // list-count byte + 3 vertex indices

    private static String header(int vertexCount, int faceCount) {
        return "ply\n"
                + "format binary_little_endian 1.0\n"
                + "comment GroupScape character portrait export\n"
                + "element vertex " + vertexCount + "\n"
                + "property float x\n"
                + "property float y\n"
                + "property float z\n"
                + "property uchar red\n"
                + "property uchar green\n"
                + "property uchar blue\n"
                + "element face " + faceCount + "\n"
                + "property list uchar int vertex_indices\n"
                + "end_header\n";
    }

    private static void writeVertex(ByteBuffer body, float vx, float vy, float vz, int[] rgb) {
        body.putFloat(vx);
        body.putFloat(vy);
        body.putFloat(vz);
        body.put((byte) rgb[0]);
        body.put((byte) rgb[1]);
        body.put((byte) rgb[2]);
    }

    private static int[] resolveColor(boolean textured, int packedHsl, short[] textures, int face, TextureProvider textureProvider) {
        if (!textured) {
            return jagexHslToRgb(packedHsl);
        }
        if (textureProvider != null) {
            return jagexHslToRgb(textureProvider.getDefaultColor(textures[face]));
        }
        return new int[]{128, 128, 128};
    }

    /** colors3[face] sentinel: this face has one flat color for all 3 corners, carried in colors1. */
    private static final int FLAT_SHADED = -1;
    /** colors3[face] sentinel: the game never draws this face at all — not a color to decode. */
    private static final int HIDDEN_FACE = -2;

    /**
     * Gamma the game's own HSL-to-RGB conversion applies after the standard math, confirmed
     * against {@code JagexColor.adjustForBrightness}. Without it, colors decode noticeably darker
     * than the client actually renders them — many mid-range luminance indices (skin tones,
     * cloth) are authored assuming this brightening pass runs, and skipping it makes captured
     * models look close to solid black.
     */
    private static final double BRIGHTNESS_GAMMA = 0.9;
    private static final double HUE_BIN_OFFSET = 0.5 / 64.0;
    private static final double SATURATION_BIN_OFFSET = 0.5 / 8.0;

    /**
     * Jagex's packed HSL: bits 10-15 hue (0-63), bits 7-9 saturation (0-7), bits 0-6 luminance
     * (0-127) — bit layout confirmed against {@code net.runelite.api.JagexColor}'s pack/unpack
     * methods. The hue/saturation bin-center offsets, the luminance-by-128 (not 127) denominator,
     * and the post-conversion gamma all mirror the client's own conversion rather than a plain
     * textbook HSL-to-RGB — those details are what previously produced overly dark colors.
     */
    static int[] jagexHslToRgb(int packed) {
        int hue = (packed >> 10) & 0x3F;
        int saturation = (packed >> 7) & 0x7;
        int luminance = packed & 0x7F;

        double h = hue / 64.0 + HUE_BIN_OFFSET;
        double s = saturation / 8.0 + SATURATION_BIN_OFFSET;
        double l = luminance / 128.0;

        double chroma = (1 - Math.abs(2 * l - 1)) * s;
        double x = chroma * (1 - Math.abs((h * 6) % 2 - 1));
        double m = l - chroma / 2;

        double r = m;
        double g = m;
        double b = m;
        switch ((int) (h * 6)) {
            case 0:
                r += chroma;
                g += x;
                break;
            case 1:
                g += chroma;
                r += x;
                break;
            case 2:
                g += chroma;
                b += x;
                break;
            case 3:
                b += chroma;
                g += x;
                break;
            case 4:
                b += chroma;
                r += x;
                break;
            default:
                r += chroma;
                b += x;
                break;
        }

        return new int[]{adjustForBrightness(r), adjustForBrightness(g), adjustForBrightness(b)};
    }

    private static int adjustForBrightness(double component) {
        double clamped = Math.max(0, Math.min(1, component));
        return (int) Math.round(Math.pow(clamped, BRIGHTNESS_GAMMA) * 255.0);
    }
}
