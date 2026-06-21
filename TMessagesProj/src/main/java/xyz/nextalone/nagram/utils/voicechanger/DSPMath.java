package xyz.nextalone.nagram.utils.voicechanger;

import java.util.Random;

public final class DSPMath {
    private static final Random random = new Random();

    public static int a(float f2) {
        return Math.round(f2);
    }

    public static float abs(float f2, float f3) {
        return (float) Math.sqrt(f2 * f2 + f3 * f3);
    }

    public static float arg(float f2, float f3) {
        return (float) Math.atan2(f3, f2);
    }

    public static float atan2(float f2, float f3) {
        return (float) Math.atan2(f2, f3);
    }

    public static float ceil(float f2) {
        return (float) Math.ceil(f2);
    }

    public static float cos(float f2) {
        return (float) Math.cos(f2);
    }

    public static float floor(float f2) {
        return (float) Math.floor(f2);
    }

    public static float imag(float f2, float f3) {
        return (float) (f2 * Math.sin(f3));
    }

    public static float log10(float f2) {
        return (float) Math.log10(f2);
    }

    public static float max(float f2, float f3) {
        return Math.max(f2, f3);
    }

    public static short mean(short[] sArr, int offset, int length) {
        if (length <= 0) return 0;
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += sArr[offset + i];
        }
        return (short) (sum / length);
    }

    public static float min(float f2, float f3) {
        return Math.min(f2, f3);
    }

    public static float pow(float f2, float f3) {
        return (float) Math.pow(f2, f3);
    }

    public static float princarg(float f2) {
        double twoPi = 2 * Math.PI;
        double val = f2 % twoPi;
        if (val > Math.PI) {
            val -= twoPi;
        } else if (val < -Math.PI) {
            val += twoPi;
        }
        return (float) val;
    }

    public static float random(float f2, float f3) {
        return f2 + random.nextFloat() * (f3 - f2);
    }

    public static float real(float f2, float f3) {
        return (float) (f2 * Math.cos(f3));
    }

    public static float rms(short[] sArr, int offset, int length) {
        if (length <= 0) return 0;
        double sum = 0;
        for (int i = 0; i < length; i++) {
            double val = sArr[offset + i];
            sum += val * val;
        }
        return (float) Math.sqrt(sum / length);
    }

    public static float rms(short[] sArr, int offset, int length, short s2) {
        if (length <= 0) return 0;
        double sum = 0;
        for (int i = 0; i < length; i++) {
            double val = sArr[offset + i] - s2;
            sum += val * val;
        }
        return (float) Math.sqrt(sum / length);
    }

    public static float rms2dbfs(float f2, float f3, float f4) {
        if (f2 <= 0) return -120f;
        float db = (float) (20.0 * Math.log10(f2 / f3));
        return Math.max(f4, db);
    }

    public static float sin(float f2) {
        return (float) Math.sin(f2);
    }

    public static float sqrt(float f2) {
        return (float) Math.sqrt(f2);
    }
}
