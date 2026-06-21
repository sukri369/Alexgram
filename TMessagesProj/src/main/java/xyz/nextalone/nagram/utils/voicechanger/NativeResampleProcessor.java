package xyz.nextalone.nagram.utils.voicechanger;

public class NativeResampleProcessor {
    private final int inSize;
    private final int outSize;

    public NativeResampleProcessor(int inSize, int outSize) {
        this.inSize = inSize;
        this.outSize = outSize;
    }

    public void a() {
        // Free resources
    }

    public void b(float[] fArr, float[] fArr2) {
        if (outSize <= 1) {
            if (outSize == 1 && inSize > 0) {
                fArr2[0] = fArr[0];
            }
            return;
        }
        float factor = (float) (inSize - 1) / (outSize - 1);
        for (int i = 0; i < outSize; i++) {
            float srcIdx = i * factor;
            int floor = (int) srcIdx;
            int ceil = Math.min(inSize - 1, floor + 1);
            float frac = srcIdx - floor;
            fArr2[i] = fArr[floor] * (1.0f - frac) + fArr[ceil] * frac;
        }
    }
}
