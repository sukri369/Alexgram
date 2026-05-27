package xyz.nextalone.nagram.utils.voicechanger;

import java.util.Arrays;

public final class SynthesisFilterBank {
    private final int fftSize;
    private final int stepSize;
    private final boolean doSpectralIFFT;
    
    private final KissFFT fft;
    private final float[] window;
    
    private final short[] prevBuf;
    private final short[] currBuf;
    private int bufferOffset = 0;

    public SynthesisFilterBank(int fftSize, int stepSize, boolean doSpectralIFFT) {
        this.fftSize = fftSize;
        this.stepSize = stepSize;
        this.doSpectralIFFT = doSpectralIFFT;
        
        this.fft = new KissFFT(fftSize);
        this.window = new HannWindow(fftSize, true).a();
        
        this.prevBuf = new short[fftSize];
        this.currBuf = new short[fftSize];
    }

    private static void applySynthesisAndAdd(float[] src, int srcOffset, short[] dest, int destOffset, int length, float[] win) {
        if (length == 0) return;
        for (int i = 0; i < length; i++) {
            int sIdx = i + srcOffset;
            int dIdx = i + destOffset;
            float val = Math.min(1.0f, Math.max(-1.0f, src[sIdx] * win[sIdx])) * 32767.0f;
            dest[dIdx] = (short) (dest[dIdx] + (short) DSPMath.a(val));
        }
    }

    public short[] a(float[] fArr) {
        if (this.doSpectralIFFT) {
            this.fft.b(fArr);
        }
        
        int offset = this.bufferOffset;
        applySynthesisAndAdd(fArr, 0, this.prevBuf, offset, this.fftSize - offset, this.window);
        applySynthesisAndAdd(fArr, this.fftSize - offset, this.currBuf, 0, offset, this.window);
        
        this.bufferOffset += this.stepSize;
        int off = this.bufferOffset;
        short[] output = null;
        if (off >= this.fftSize) {
            this.bufferOffset = off - this.fftSize;
            output = new short[this.fftSize];
            System.arraycopy(this.prevBuf, 0, output, 0, this.fftSize);
            System.arraycopy(this.currBuf, 0, this.prevBuf, 0, this.fftSize);
            Arrays.fill(this.currBuf, (short) 0);
        }
        return output;
    }
}
