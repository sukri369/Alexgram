package xyz.nextalone.nagram.utils.voicechanger;

import org.telegram.messenger.FileLog;
import java.util.Arrays;

public final class VoiceActivityDetector {
    private final int sampleRate;
    private final float lfoCoeff = 0.02f;
    private final float[] coeffs = {0.3f, 0.02f};
    private final int windowSize;
    private final MeanTracker thresholdFilter;
    private final SchmittTrigger schmittTrigger;
    private final float hangoverTime;
    private float hangoverCounter = 0.0f;
    private final boolean enabled;
    private boolean voiceDetectedState = false;

    private static class SchmittTrigger {
        private boolean state;
        private float lastVal;
        private final float lowerBound;
        private final float upperBound;

        public SchmittTrigger(boolean initialState, float initialVal, float lowerBound, float upperBound) {
            this.state = initialState;
            this.lastVal = initialVal;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public boolean check(float input) {
            if (input > this.lastVal && input > this.upperBound) {
                this.state = true;
            } else if (input < this.lastVal && input < this.lowerBound) {
                this.state = false;
            }
            this.lastVal = input;
            return this.state;
        }
    }

    private static class MeanTracker {
        private float filterState0;
        private float filterState1;
        private final float[] coeffs;

        public MeanTracker(float initialVal, float initialDeriv, float[] coeffs) {
            this.filterState0 = initialVal;
            this.filterState1 = initialDeriv;
            this.coeffs = coeffs;
        }

        public float update(float input) {
            float bVal = filterState0 + filterState1;
            float diff = input - filterState0;
            float state0 = bVal + (coeffs[0] * diff);
            this.filterState0 = state0;
            this.filterState1 += coeffs[1] * diff;
            return state0;
        }
    }

    public VoiceActivityDetector(int sampleRate) {
        this(sampleRate, -20, -25, 5, false); // defaults from AbstractC2422aUx (b=-20, c=-25, a=5, j=false)
    }

    public VoiceActivityDetector(int sampleRate, int dbThresholdMax, int dbThresholdMin, int hangoverFrames, boolean enabled) {
        this.sampleRate = sampleRate;
        int size = Math.round(sampleRate * lfoCoeff);
        this.windowSize = size;
        
        float initialDb = (dbThresholdMax + dbThresholdMin) / 2.0f;
        this.thresholdFilter = new MeanTracker(initialDb, 0.0f, coeffs);
        this.schmittTrigger = new SchmittTrigger(false, initialDb, dbThresholdMin, dbThresholdMax);
        this.hangoverTime = hangoverFrames;
        this.enabled = enabled;
    }

    private void processSubFrame(short[] sArr, int offset, int length, float stepTime) {
        float rmsVal = DSPMath.rms(sArr, offset, length);
        float dbVal = DSPMath.rms2dbfs(rmsVal, 1.0E-10f, 1.0f);
        float filteredDb = thresholdFilter.update(dbVal);
        boolean currentDetect = schmittTrigger.check(filteredDb);
        
        if (hangoverTime > 0.0f) {
            if (currentDetect) {
                hangoverCounter = 0.0f;
            } else {
                float newCounter = Math.min(hangoverTime, hangoverCounter + stepTime);
                hangoverCounter = newCounter;
                currentDetect = newCounter < hangoverTime;
            }
        }
        
        if (!currentDetect) {
            Arrays.fill(sArr, offset, offset + length, (short) 0);
        }
        
        if (voiceDetectedState != currentDetect) {
            voiceDetectedState = currentDetect;
            FileLog.d("VoiceActivityDetector: Voice activity detected = " + currentDetect);
        }
    }

    public void a(short[] sArr) {
        if (this.enabled) {
            int numSubFrames = sArr.length / this.windowSize;
            int stepSize = numSubFrames > 0 ? (int) Math.ceil((double) sArr.length / numSubFrames) : sArr.length;
            float stepTime = (float) stepSize / this.sampleRate;
            for (int i = 0; i < numSubFrames; i++) {
                int startIdx = i * stepSize;
                int currentLen = Math.min(stepSize, sArr.length - startIdx);
                if (currentLen > 0) {
                    processSubFrame(sArr, startIdx, currentLen, stepTime);
                }
            }
        }
    }
}
