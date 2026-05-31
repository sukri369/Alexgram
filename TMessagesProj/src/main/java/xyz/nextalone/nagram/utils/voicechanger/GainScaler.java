package xyz.nextalone.nagram.utils.voicechanger;

public final class GainScaler {
    private final float gainFactor;

    public GainScaler(int gainDb) {
        this.gainFactor = (float) Math.pow(10.0f, gainDb / 20.0f);
    }

    public void a(short[] sArr) {
        if (this.gainFactor == 1.0f) {
            return;
        }
        for (int i = 0; i < sArr.length; i++) {
            float scaledVal = sArr[i] * this.gainFactor;
            if (scaledVal > 32767.0f) {
                sArr[i] = Short.MAX_VALUE;
            } else if (scaledVal < -32768.0f) {
                sArr[i] = Short.MIN_VALUE;
            } else {
                sArr[i] = (short) scaledVal;
            }
        }
    }
}
