package xyz.nextalone.nagram.utils.voicechanger;

public final class MeanTracker {
    private final boolean enabled;
    private final float[] coeffs;
    private float filterState0 = 0.0f;
    private float filterState1 = 0.0f;

    public MeanTracker(boolean enabled) {
        this.coeffs = new float[]{0.025f, 0.0f};
        this.enabled = enabled;
    }

    private float updateFilter(float input) {
        float bVal = filterState0 + filterState1;
        float diff = input - filterState0;
        float state0 = bVal + (coeffs[0] * diff);
        this.filterState0 = state0;
        this.filterState1 += coeffs[1] * diff;
        return state0;
    }

    public void a(short[] sArr) {
        if (this.enabled) {
            float mean = DSPMath.mean(sArr, 0, sArr.length);
            updateFilter(mean);
            short meanOffset = (short) (filterState0 + filterState1);
            for (int i = 0; i < sArr.length; i++) {
                sArr[i] = (short) (sArr[i] - meanOffset);
            }
        }
    }
}
