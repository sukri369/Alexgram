package xyz.nextalone.nagram.utils.voicechanger;

public final class SpectralNoiseGate {
    private final int fftSize;
    private final boolean enabled = true; // matches AbstractC2422aUx.n() which is true
    private final boolean limitBands = false; // matches AbstractC2422aUx.k() which is false
    private final float noiseFloorDb = 3.0f; // matches AbstractC2422aUx.h() which is 3
    private final float noiseFloorLimit = (float) Math.pow(10.0f, -noiseFloorDb);
    private final float lowFreqLimit;
    private final float highFreqLimit;

    public SpectralNoiseGate(int fftSize) {
        this.fftSize = fftSize;
        // matches constructors in C6941Aux
        this.lowFreqLimit = (100.0f * 2.0f) / fftSize;
        this.highFreqLimit = (8000.0f * 2.0f) / fftSize;
    }

    private static float wienerGain(float signalRms, float noiseFloor) {
        return signalRms / (noiseFloor + signalRms);
    }

    public void b(float[] fArr) {
        if (this.enabled || this.limitBands) {
            int length = fArr.length / 2;
            float fLength = length;
            int lowBinIdx = (int) (this.lowFreqLimit * fLength);
            int highBinIdx = (int) (this.highFreqLimit * fLength);
            
            for (int i = 1; i < length; i++) {
                int iReal = i * 2;
                float r = fArr[iReal];
                int iImag = iReal + 1;
                float img = fArr[iImag];
                float mag = DSPMath.abs(r, img);
                
                float attenuation = 1.0f;
                boolean inRange = true;
                if (this.limitBands) {
                    inRange = i >= lowBinIdx && i <= highBinIdx;
                    if (!inRange) {
                        attenuation = 0.0f;
                    }
                }
                if (this.enabled && inRange) {
                    attenuation = wienerGain(mag / fLength, noiseFloorLimit);
                }
                fArr[iReal] = r * attenuation;
                fArr[iImag] = img * attenuation;
            }
        }
    }
}
