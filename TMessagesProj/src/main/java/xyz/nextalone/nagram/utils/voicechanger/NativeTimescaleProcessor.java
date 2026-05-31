package xyz.nextalone.nagram.utils.voicechanger;

public final class NativeTimescaleProcessor {
    private final int fftSize;
    private final int synthHop;
    private final int analHop;
    
    private final float[] prevAnalPhase;
    private final float[] synthPhase;

    public NativeTimescaleProcessor(int fftSize, int synthHop, int analHop) {
        this.fftSize = fftSize;
        this.synthHop = synthHop;
        this.analHop = analHop;
        
        int numBins = fftSize / 2 + 1;
        this.prevAnalPhase = new float[numBins];
        this.synthPhase = new float[numBins];
    }

    public void a() {
        // Free resources
    }

    public void b(float[] fArr) {
        int numBins = fftSize / 2;
        for (int k = 0; k < numBins; k++) {
            float real = fArr[2 * k];
            float imag = fArr[2 * k + 1];
            
            float mag = (float) Math.sqrt(real * real + imag * imag);
            float phase = (float) Math.atan2(imag, real);
            
            float phaseDiff = phase - prevAnalPhase[k];
            prevAnalPhase[k] = phase;
            
            float expectedDiff = (float) (2 * Math.PI * k * analHop / fftSize);
            float diff = phaseDiff - expectedDiff;
            
            diff = (float) (diff - 2 * Math.PI * Math.round(diff / (2 * Math.PI)));
            
            float trueFreq = (float) (k * 2 * Math.PI / fftSize + diff / analHop);
            
            synthPhase[k] += trueFreq * synthHop;
            
            fArr[2 * k] = (float) (mag * Math.cos(synthPhase[k]));
            fArr[2 * k + 1] = (float) (mag * Math.sin(synthPhase[k]));
        }
    }
}
