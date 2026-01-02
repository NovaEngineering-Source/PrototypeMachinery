package github.kasuminova.prototypemachinery.modernbackend.accel.vector;

import github.kasuminova.prototypemachinery.modernbackend.accel.PositionTransformBackend;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vector API implementation.
 *
 * <p>Packaged in the main ModernBackend jar, but loaded reflectively.
 * Requires the runtime to resolve the incubator module (e.g. via --add-modules=jdk.incubator.vector).
 */
public final class VectorPositionTransformBackend implements PositionTransformBackend {

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    @Override
    public String name() {
        return "vector(" + SPECIES.length() + ")";
    }

    @Override
    public boolean isVectorized() {
        return true;
    }

    @Override
    public void transformAffine3x4SoA(
            float[] xs,
            float[] ys,
            float[] zs,
            int offset,
            int count,
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23
    ) {
        final VectorSpecies<Float> sp = SPECIES;
        final int step = sp.length();
        final int bound = (count / step) * step;

        final FloatVector vm00 = FloatVector.broadcast(sp, m00);
        final FloatVector vm01 = FloatVector.broadcast(sp, m01);
        final FloatVector vm02 = FloatVector.broadcast(sp, m02);
        final FloatVector vm03 = FloatVector.broadcast(sp, m03);

        final FloatVector vm10 = FloatVector.broadcast(sp, m10);
        final FloatVector vm11 = FloatVector.broadcast(sp, m11);
        final FloatVector vm12 = FloatVector.broadcast(sp, m12);
        final FloatVector vm13 = FloatVector.broadcast(sp, m13);

        final FloatVector vm20 = FloatVector.broadcast(sp, m20);
        final FloatVector vm21 = FloatVector.broadcast(sp, m21);
        final FloatVector vm22 = FloatVector.broadcast(sp, m22);
        final FloatVector vm23 = FloatVector.broadcast(sp, m23);

        int i = 0;
        for (; i < bound; i += step) {
            final int idx = offset + i;

            final FloatVector vx = FloatVector.fromArray(sp, xs, idx);
            final FloatVector vy = FloatVector.fromArray(sp, ys, idx);
            final FloatVector vz = FloatVector.fromArray(sp, zs, idx);

            final FloatVector rx = vx.mul(vm00).add(vy.mul(vm01)).add(vz.mul(vm02)).add(vm03);
            final FloatVector ry = vx.mul(vm10).add(vy.mul(vm11)).add(vz.mul(vm12)).add(vm13);
            final FloatVector rz = vx.mul(vm20).add(vy.mul(vm21)).add(vz.mul(vm22)).add(vm23);

            rx.intoArray(xs, idx);
            ry.intoArray(ys, idx);
            rz.intoArray(zs, idx);
        }

        // Tail
        for (; i < count; i++) {
            final int idx = offset + i;
            final float x = xs[idx];
            final float y = ys[idx];
            final float z = zs[idx];

            xs[idx] = (m00 * x) + (m01 * y) + (m02 * z) + m03;
            ys[idx] = (m10 * x) + (m11 * y) + (m12 * z) + m13;
            zs[idx] = (m20 * x) + (m21 * y) + (m22 * z) + m23;
        }
    }
}
