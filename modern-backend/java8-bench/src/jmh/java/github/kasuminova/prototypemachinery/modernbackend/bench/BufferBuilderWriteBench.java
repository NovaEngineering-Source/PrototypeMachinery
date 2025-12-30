package github.kasuminova.prototypemachinery.modernbackend.bench;

import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

/**
 * A "BufferBuilder-like" write-path benchmark (Java 8 friendly).
 *
 * Goal: measure the core costs commonly attributed to Minecraft 1.12 BufferBuilder hotspots:
 * - packing vertex attributes (pos/uv/color/normal)
 * - writing into a direct ByteBuffer with a fixed vertex stride
 * - committing vertices (endVertex-like bookkeeping)
 *
 * Stride model (bytes):
 * - position: 3 floats (12)
 * - uv:       2 floats (8)  => 20
 * - color:    4 u8 (4)      => 24
 * - normal:   4 i8 (4)      => 28  (XYZ + padding)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class BufferBuilderWriteBench {

    private static final int STRIDE = 28;
    private static final int OFF_POS = 0;
    private static final int OFF_UV = 12;
    private static final int OFF_COLOR = 20;
    private static final int OFF_NORMAL = 24;

    @Param({"256", "4096", "65536"})
    public int vertices;

    // Input streams (SoA)
    private float[] x;
    private float[] y;
    private float[] z;
    private float[] u;
    private float[] v;
    private float[] nx;
    private float[] ny;
    private float[] nz;
    private int[] rgba;

    // Output buffer
    private ByteBuffer bb;

    @Setup(Level.Trial)
    public void setup() {
        x = new float[vertices];
        y = new float[vertices];
        z = new float[vertices];
        u = new float[vertices];
        v = new float[vertices];
        nx = new float[vertices];
        ny = new float[vertices];
        nz = new float[vertices];
        rgba = new int[vertices];

        for (int i = 0; i < vertices; i++) {
            // Deterministic values; avoid denormals.
            x[i] = (float) (i * 0.001 + 1.0);
            y[i] = (float) (i * 0.002 + 2.0);
            z[i] = (float) (i * 0.003 + 3.0);
            u[i] = (float) ((i & 1023) / 1024.0);
            v[i] = (float) (((i * 7) & 1023) / 1024.0);

            // Normal-ish vectors.
            nx[i] = 0.0f;
            ny[i] = 1.0f;
            nz[i] = 0.0f;

            int r = (i * 13) & 255;
            int g = (i * 29) & 255;
            int b = (i * 47) & 255;
            int a = 255;
            rgba[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        bb = ByteBuffer.allocateDirect(vertices * STRIDE).order(ByteOrder.nativeOrder());
    }

    private static int packNormal8(float fx, float fy, float fz) {
        int x = (int) (Math.max(-1.0f, Math.min(1.0f, fx)) * 127.0f);
        int y = (int) (Math.max(-1.0f, Math.min(1.0f, fy)) * 127.0f);
        int z = (int) (Math.max(-1.0f, Math.min(1.0f, fz)) * 127.0f);
        return (x & 0xFF) | ((y & 0xFF) << 8) | ((z & 0xFF) << 16);
    }

    /**
     * Tight loop: absolute puts, prepacked int color, packed normal.
     */
    @Benchmark
    public int abs_put_packed() {
        final ByteBuffer out = bb;
        final float[] lx = x, ly = y, lz = z;
        final float[] lu = u, lv = v;
        final float[] lnx = nx, lny = ny, lnz = nz;
        final int[] lrgba = rgba;

        int checksum = 0;
        int base = 0;
        for (int i = 0; i < vertices; i++) {
            out.putFloat(base + OFF_POS, lx[i]);
            out.putFloat(base + OFF_POS + 4, ly[i]);
            out.putFloat(base + OFF_POS + 8, lz[i]);

            out.putFloat(base + OFF_UV, lu[i]);
            out.putFloat(base + OFF_UV + 4, lv[i]);

            out.putInt(base + OFF_COLOR, lrgba[i]);

            out.putInt(base + OFF_NORMAL, packNormal8(lnx[i], lny[i], lnz[i]));

            // Read back one int per vertex to prevent DCE.
            checksum ^= out.getInt(base + OFF_COLOR);

            base += STRIDE;
        }
        return checksum;
    }

    /**
     * Builder-style call overhead + float->byte color conversion per vertex.
     */
    @Benchmark
    public int builder_style_float_color() {
        final SimBuilder b = new SimBuilder(bb);
        final float[] lx = x, ly = y, lz = z;
        final float[] lu = u, lv = v;
        final float[] lnx = nx, lny = ny, lnz = nz;
        final int[] lrgba = rgba;

        int checksum = 0;
        for (int i = 0; i < vertices; i++) {
            int c = lrgba[i];
            float a = ((c >>> 24) & 255) * (1.0f / 255.0f);
            float r = ((c >>> 16) & 255) * (1.0f / 255.0f);
            float g = ((c >>> 8) & 255) * (1.0f / 255.0f);
            float bl = (c & 255) * (1.0f / 255.0f);

            b.pos(lx[i], ly[i], lz[i])
                    .tex(lu[i], lv[i])
                    .color(r, g, bl, a)
                    .normal(lnx[i], lny[i], lnz[i])
                    .endVertex();

            checksum ^= b.lastColor;
        }
        return checksum;
    }

    /**
     * Builder-style call overhead + already packed int color.
     */
    @Benchmark
    public int builder_style_int_color() {
        final SimBuilder b = new SimBuilder(bb);
        final float[] lx = x, ly = y, lz = z;
        final float[] lu = u, lv = v;
        final float[] lnx = nx, lny = ny, lnz = nz;
        final int[] lrgba = rgba;

        int checksum = 0;
        for (int i = 0; i < vertices; i++) {
            b.pos(lx[i], ly[i], lz[i])
                    .tex(lu[i], lv[i])
                    .colorInt(lrgba[i])
                    .normal(lnx[i], lny[i], lnz[i])
                    .endVertex();

            checksum ^= b.lastColor;
        }
        return checksum;
    }

    static final class SimBuilder {
        final ByteBuffer out;
        int base;
        int lastColor;

        SimBuilder(ByteBuffer out) {
            this.out = out;
            this.base = 0;
        }

        SimBuilder pos(float px, float py, float pz) {
            out.putFloat(base + OFF_POS, px);
            out.putFloat(base + OFF_POS + 4, py);
            out.putFloat(base + OFF_POS + 8, pz);
            return this;
        }

        SimBuilder tex(float tu, float tv) {
            out.putFloat(base + OFF_UV, tu);
            out.putFloat(base + OFF_UV + 4, tv);
            return this;
        }

        SimBuilder color(float r, float g, float b, float a) {
            int rr = (int) (Math.max(0.0f, Math.min(1.0f, r)) * 255.0f);
            int gg = (int) (Math.max(0.0f, Math.min(1.0f, g)) * 255.0f);
            int bb = (int) (Math.max(0.0f, Math.min(1.0f, b)) * 255.0f);
            int aa = (int) (Math.max(0.0f, Math.min(1.0f, a)) * 255.0f);
            return colorInt((aa << 24) | (rr << 16) | (gg << 8) | bb);
        }

        SimBuilder colorInt(int rgba) {
            out.putInt(base + OFF_COLOR, rgba);
            lastColor = rgba;
            return this;
        }

        SimBuilder normal(float nx, float ny, float nz) {
            out.putInt(base + OFF_NORMAL, packNormal8(nx, ny, nz));
            return this;
        }

        SimBuilder endVertex() {
            base += STRIDE;
            if (base >= out.capacity()) {
                base = 0;
            }
            return this;
        }
    }
}
