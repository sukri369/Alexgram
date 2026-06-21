package xyz.nextalone.nagram.utils.voicechanger;

import android.content.Context;

public final class AnalysisFilterBank {
    private final int fftSize;
    private final int stepSize;
    private final boolean doSpectralFFT;
    
    private final MeanTracker meanTracker;
    private final VoiceActivityDetector vad;
    private final GainScaler gainScaler;
    private final SpectralNoiseGate noiseGate;
    private final KissFFT fft;
    
    private final float[] window;
    private final short[] prevBuf;
    private final short[] currBuf;
    private int bufferOffset = -1;

    public AnalysisFilterBank(Context context, int sampleRate, int fftSize, int stepSize, boolean doSpectralFFT) {
        this.fftSize = fftSize;
        this.stepSize = stepSize;
        this.doSpectralFFT = doSpectralFFT;
        
        this.meanTracker = new MeanTracker(true); // default true
        this.vad = new VoiceActivityDetector(sampleRate);
        this.gainScaler = new GainScaler(0); // i = 0 db gain
        this.noiseGate = new SpectralNoiseGate(fftSize);
        this.fft = new KissFFT(fftSize);
        this.window = new HannWindow(fftSize, true).a();
        
        this.prevBuf = new short[fftSize];
        this.currBuf = new short[fftSize];
    }

    private static void applyWindow(short[] src, int srcOffset, float[] dest, int destOffset, int length, float[] win) {
        if (length == 0) return;
        for (int i = 0; i < length; i++) {
            int dIdx = i + destOffset;
            dest[dIdx] = Math.min(1.0f, Math.max(-1.0f, src[i + srcOffset] / 32767.0f)) * win[dIdx];
        }
    }

    public void b(float[] fArr, ShortBufferQueue queue) {
        int offset = this.bufferOffset;
        if (offset == -1) {
            this.bufferOffset = this.fftSize;
            queue.read(this.currBuf, 0, this.fftSize);
            this.meanTracker.a(this.currBuf);
            this.vad.a(this.currBuf);
            this.gainScaler.a(this.currBuf);
        } else {
            if (offset >= this.fftSize) {
                this.bufferOffset = offset - this.fftSize;
                System.arraycopy(this.currBuf, 0, this.prevBuf, 0, this.fftSize);
                queue.read(this.currBuf, 0, this.fftSize);
                this.meanTracker.a(this.currBuf);
                this.vad.a(this.currBuf);
                this.gainScaler.a(this.currBuf);
            }
        }
        
        int off = this.bufferOffset;
        applyWindow(this.prevBuf, off, fArr, 0, this.fftSize - off, this.window);
        applyWindow(this.currBuf, 0, fArr, this.fftSize - off, off, this.window);
        
        if (this.doSpectralFFT) {
            this.fft.a(fArr);
            this.noiseGate.b(fArr);
        }
        
        this.bufferOffset += this.stepSize;
    }
}
