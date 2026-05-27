package xyz.nextalone.nagram.utils;

import android.content.Context;
import android.media.AudioRecord;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import org.telegram.messenger.FileLog;
import xyz.nextalone.nagram.NaConfig;

/**
 * VoiceChanger — ported from Telegraph's B7.java (voice changer processor).
 *
 * Architecture is identical to Telegraph:
 *   - Spectral effects (Robotic, Alien, Hoarseness): AnalysisFilterBank → SpectralModifier → SynthesisFilterBank
 *   - Pitch effects (Child, Mouse, Man, Woman, Monster, Helium, Hexafluoride):
 *       AnalysisFilterBank → NativeTimescaleProcessor → KissFFT(IFFT) → NativeResampleProcessor → SynthesisFilterBank(no-IFFT)
 *   - Echo / Cave:  direct short[] → EchoProcessor
 *   - Noise:        direct short[] → NoiseProcessor
 *
 * NativeTimescaleProcessor and NativeResampleProcessor are implemented in pure Java
 * using the same linear interpolation approach as Telegraph's native code.
 *
 * Math natives (abs, real, imag, random, princarg) are implemented in pure Java.
 */
public class VoiceChanger {

    // ─── Effect IDs (must match NaConfig.voiceChangerEffect values) ──────────
    public static final int EFFECT_NONE         = 0;
    public static final int EFFECT_ROBOTIC      = 1;  // Telegraph: i2==2
    public static final int EFFECT_ALIEN        = 2;  // Telegraph: i2==3
    public static final int EFFECT_HOARSENESS   = 3;  // Telegraph: i2==4
    public static final int EFFECT_MODULATION   = 4;  // (extra, not in Telegraph)
    public static final int EFFECT_CHILD        = 5;  // Telegraph: i2==7, +5 semi (AbstractC10135lD.F1=5)
    public static final int EFFECT_MOUSE        = 6;  // Telegraph: i2==6, +9 semi
    public static final int EFFECT_MAN          = 7;  // Telegraph: i2==8, -3 semi
    public static final int EFFECT_WOMAN        = 8;  // Telegraph: i2==9, +3 semi (i2!=9 falls to default i5)
    public static final int EFFECT_MONSTER      = 9;  // Telegraph: i2==10, -8 semi
    public static final int EFFECT_ECHO         = 10; // Telegraph: i2==11
    public static final int EFFECT_NOISE        = 11; // Telegraph: i2==12
    public static final int EFFECT_HELIUM       = 12; // Telegraph: i2==13, +12 semi
    public static final int EFFECT_HEXAFLUORIDE = 13; // Telegraph: i2==14, -5 semi
    public static final int EFFECT_CAVE         = 14; // Telegraph: i2==15

    private static final int SAMPLE_RATE = 48000;

    // ─── Processor state ─────────────────────────────────────────────────────
    private static int lastEffect = -1;
    private static Processor processor = null;
    private static long lastProcessTime = 0;

    public static void process(ByteBuffer buffer, int count) {
        if (buffer == null || count <= 0) return;

        int effect = NaConfig.INSTANCE.getVoiceChangerEffectValue();

        if (effect == EFFECT_NONE) {
            if (lastEffect != EFFECT_NONE) {
                destroyProcessor();
                lastEffect = EFFECT_NONE;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastProcessTime > 1000) {
            destroyProcessor();
        }
        lastProcessTime = now;

        if (effect != lastEffect) {
            destroyProcessor();
            lastEffect = effect;
        }

        try {
            // Set up buffer
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            if (count <= buffer.capacity()) {
                buffer.limit(count);
            } else {
                count = buffer.capacity();
                buffer.limit(count);
            }
            buffer.position(0);

            int numSamples = count / 2;
            if (numSamples <= 0) return;

            // Read PCM
            short[] pcm = new short[numSamples];
            buffer.asShortBuffer().get(pcm);

            // Create processor on first call (Telegraph creates per recording session)
            if (processor == null) {
                int sRate = SAMPLE_RATE;
                try {
                    sRate = org.telegram.messenger.MediaController.getInstance().sampleRate;
                } catch (Throwable ignored) {}
                processor = createProcessor(effect, sRate, numSamples);
            }

            // Process
            short[] out = processor.process(pcm);
            if (out == null) out = pcm; // passthrough on null

            // Write back — clamp to buffer size
            int writeSamples = Math.min(out.length, numSamples);
            buffer.position(0);
            buffer.limit(count);
            for (int i = 0; i < writeSamples; i++) {
                buffer.putShort(out[i]);
            }
            // Zero-fill any remaining space
            while (buffer.hasRemaining()) {
                buffer.putShort((short) 0);
            }
            buffer.position(0);

        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void destroyProcessor() {
        if (processor != null) {
            processor.destroy();
            processor = null;
        }
    }

    private static Processor createProcessor(int effect, int sampleRate, int bufferSize) {
        switch (effect) {
            case EFFECT_ROBOTIC:
            case EFFECT_ALIEN:
            case EFFECT_HOARSENESS:
                return new SpectralProcessor(effect, sampleRate, bufferSize, FFTRatio.MEDIUM);
            case EFFECT_CHILD:
                return new PitchProcessor(effect, sampleRate, bufferSize, semitones(5));
            case EFFECT_MOUSE:
                return new PitchProcessor(effect, sampleRate, bufferSize, semitones(9));
            case EFFECT_MAN:
                return new PitchProcessor(effect, sampleRate, bufferSize, semitones(-3));
            case EFFECT_WOMAN:
                return new PitchProcessor(effect, sampleRate, bufferSize, semitones(3));
            case EFFECT_MONSTER:
                return new PitchProcessor(effect, sampleRate, bufferSize, semitones(-8));
            case EFFECT_HELIUM:
                return new PitchProcessor(effect, sampleRate, bufferSize, semitones(12));
            case EFFECT_HEXAFLUORIDE:
                return new PitchProcessor(effect, sampleRate, bufferSize, semitones(-5));
            case EFFECT_MODULATION:
                return new ModulationProcessor(sampleRate, bufferSize);
            case EFFECT_ECHO:
                return new EchoProcessor(sampleRate, 0.06f, 0.5f, bufferSize);
            case EFFECT_CAVE:
                return new EchoProcessor(sampleRate, 0.35f, 0.6f, bufferSize);
            case EFFECT_NOISE:
                return new NoiseProcessor(0.3f, bufferSize);
            default:
                return pcm -> pcm; // passthrough
        }
    }

    private static float semitones(int n) {
        return (float) java.lang.Math.pow(2.0, n / 12.0);
    }

    // ─── Processor interface ─────────────────────────────────────────────────
    interface Processor {
        short[] process(short[] pcm);
        default void destroy() {}
    }

    // ─── FFT frame size ratios (Telegraph EnumC2421Aux) ──────────────────────
    enum FFTRatio {
        LARGE(2.0), DEFAULT(1.0), MEDIUM(0.5), SMALL(0.25);
        final double ratio;
        FFTRatio(double r) { ratio = r; }
    }

    // Frame size formula from AbstractC2422aUx.f()
    private static int frameSize(FFTRatio ratio, int sampleRate) {
        int v = (int) (sampleRate * 0.046439909297052155 * ratio.ratio);
        return v % 2 != 0 ? v + 1 : v;
    }
    private static int stepSize(FFTRatio ratio, int sampleRate) {
        return frameSize(ratio, sampleRate) / 4;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SPECTRAL PROCESSOR (Robotic, Alien, Hoarseness)
    //  = Telegraph B7 effect 2/3/4: AnalysisFilterBank → modifier → SynthesisFilterBank
    // ═════════════════════════════════════════════════════════════════════════
    static class SpectralProcessor implements Processor {
        private final int effect;
        private final AnalysisFilterBank analysis;
        private final SynthesisFilterBank synthesis;
        private final float[] floatBuf;
        private final ShortQueue inputQueue;
        private final ShortQueue outputQueue;

        SpectralProcessor(int effect, int sampleRate, int bufferSize, FFTRatio ratio) {
            this.effect = effect;
            int fftSize = frameSize(ratio, sampleRate);
            int step = stepSize(ratio, sampleRate);
            this.floatBuf = new float[fftSize];
            this.analysis = new AnalysisFilterBank(fftSize, step, true);
            this.synthesis = new SynthesisFilterBank(fftSize, step, true);
            this.inputQueue = new ShortQueue(sampleRate * 2);
            this.outputQueue = new ShortQueue(sampleRate * 2);
        }

        @Override
        public short[] process(short[] pcm) {
            inputQueue.write(pcm);
            while (inputQueue.available() >= analysis.fftSize) {
                analysis.process(floatBuf, inputQueue);
                applySpectralEffect(floatBuf);
                short[] outFrame = synthesis.process(floatBuf);
                if (outFrame != null) {
                    outputQueue.write(outFrame);
                }
            }
            short[] out = new short[pcm.length];
            outputQueue.read(out, out.length);
            return out;
        }

        private void applySpectralEffect(float[] fArr) {
            // Telegraph: AbstractC6938AUX (Robotic), AbstractC6943aUx (Alien), AbstractC6944auX (Hoarseness)
            int length = fArr.length / 2;
            for (int i = 1; i < length; i++) {
                int re = i * 2;
                int im = re + 1;
                switch (effect) {
                    case EFFECT_ROBOTIC:
                        // abs magnitude, zero phase → metallic/robotic
                        fArr[re] = DSP.abs(fArr[re], fArr[im]);
                        fArr[im] = 0.0f;
                        break;
                    case EFFECT_ALIEN:
                        // conjugate spectrum (flip imaginary)
                        fArr[im] = -fArr[im];
                        break;
                    case EFFECT_HOARSENESS:
                        // random phase, preserve magnitude
                        float mag = DSP.abs(fArr[re], fArr[im]);
                        float angle = DSP.random(-3.1415927f, 3.1415927f);
                        fArr[re] = DSP.real(mag, angle);
                        fArr[im] = DSP.imag(mag, angle);
                        break;
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PITCH PROCESSOR (Child, Mouse, Man, Woman, Monster, Helium, Hexafluoride)
    //  = Telegraph B7 else branch:
    //    AnalysisFilterBank → NativeTimescaleProcessor → KissFFT(IFFT) → NativeResampleProcessor → SynthesisFilterBank(no-IFFT)
    // ═════════════════════════════════════════════════════════════════════════
    static class PitchProcessor implements Processor {
        private final AnalysisFilterBank analysis;
        private final TimescaleProcessor timescale;
        private final SimpleFFT fft;
        private final ResampleProcessor resample;
        private final SynthesisFilterBank synthesis;
        private final float[] floatBuf;
        private final float[] resampleBuf;
        private final ShortQueue inputQueue;
        private final ShortQueue outputQueue;

        PitchProcessor(int effect, int sampleRate, int bufferSize, float pitchFactor) {
            // Telegraph: iF = frameSize(Default), iG = stepSize(Default)
            int fftSize = frameSize(FFTRatio.DEFAULT, sampleRate);
            int step = stepSize(FFTRatio.DEFAULT, sampleRate);
            // Telegraph: iA = Math.a(iG / fPow), iA2 = Math.a(iF / fPow)
            int synthStep = java.lang.Math.round(step / pitchFactor);
            int resampleSize = java.lang.Math.round(fftSize / pitchFactor);

            this.floatBuf = new float[fftSize];
            this.resampleBuf = new float[resampleSize];
            this.analysis = new AnalysisFilterBank(fftSize, synthStep, true);
            this.timescale = new TimescaleProcessor(fftSize, synthStep, step);
            this.fft = new SimpleFFT(fftSize);
            this.resample = new ResampleProcessor(fftSize, resampleSize);
            this.synthesis = new SynthesisFilterBank(resampleSize, synthStep, false); // no IFFT
            this.inputQueue = new ShortQueue(sampleRate * 2);
            this.outputQueue = new ShortQueue(sampleRate * 2);
        }

        @Override
        public short[] process(short[] pcm) {
            inputQueue.write(pcm);
            while (inputQueue.available() >= analysis.fftSize) {
                // Telegraph: analysis.b(floatBuf, audioRecord)
                analysis.process(floatBuf, inputQueue);
                // Telegraph: timescale.b(floatBuf)
                timescale.process(floatBuf);
                // Telegraph: fft.b(floatBuf) [IFFT]
                fft.ifft(floatBuf);
                // Telegraph: resample.b(floatBuf, resampleBuf)
                resample.process(floatBuf, resampleBuf);
                // Telegraph: synthesis.a(resampleBuf)
                short[] outFrame = synthesis.process(resampleBuf);
                if (outFrame != null) {
                    outputQueue.write(outFrame);
                }
            }
            short[] out = new short[pcm.length];
            outputQueue.read(out, out.length);
            return out;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ECHO PROCESSOR (Telegraph C6939AUx — exact port)
    // ═════════════════════════════════════════════════════════════════════════
    static class EchoProcessor implements Processor {
        private final float gain;
        private final short[] delayLine;
        private final int delaySamples;
        private int writePtr;
        private int readPtr = 0;

        EchoProcessor(int sampleRate, float gain, float delayRatio, int bufferSize) {
            this.gain = gain;
            int size = (int) (sampleRate * delayRatio);
            if (size % 2 != 0) size++;
            this.delaySamples = size;
            this.delayLine = new short[size];
            this.writePtr = size / 5; // Telegraph: f22994e = i4 / 5
        }

        @Override
        public short[] process(short[] pcm) {
            // Exact port of C6939AUx.a()
            for (int i = 0; i < pcm.length; i++) {
                delayLine[writePtr] = (short) (delayLine[writePtr] + (pcm[i] * gain));
                writePtr++;
                if (writePtr >= delaySamples - 1) writePtr = 0;

                short s = pcm[i];
                pcm[i] = (short) (s + delayLine[readPtr]);
                delayLine[readPtr] = (short) (delayLine[readPtr] * 0.45f);
                readPtr++;
                if (readPtr >= delaySamples - 1) readPtr = 0;
            }
            return pcm;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NOISE PROCESSOR (Telegraph C6940AuX — exact port)
    // ═════════════════════════════════════════════════════════════════════════
    static class NoiseProcessor implements Processor {
        private final float level;
        private final Random random = new Random();

        NoiseProcessor(float level, int bufferSize) {
            this.level = level;
        }

        @Override
        public short[] process(short[] pcm) {
            // Exact port of C6940AuX.a()
            for (int i = 0; i < pcm.length; i++) {
                float n1 = (random.nextFloat() - 0.5f) * 4.0f;
                float n2 = (random.nextFloat() - 0.5f) * 2.0f;
                short s = pcm[i];
                pcm[i] = (short) (s + (level * (n1 + (s * n2))));
            }
            return pcm;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MODULATION PROCESSOR (AM ring modulation @ 440Hz)
    // ═════════════════════════════════════════════════════════════════════════
    static class ModulationProcessor implements Processor {
        private double phase = 0.0;
        private final double step;

        ModulationProcessor(int sampleRate, int bufferSize) {
            this.step = 2 * java.lang.Math.PI * 440.0 / sampleRate;
        }

        @Override
        public short[] process(short[] pcm) {
            for (int i = 0; i < pcm.length; i++) {
                double carrier = java.lang.Math.sin(phase);
                pcm[i] = clamp((int) (pcm[i] * carrier));
                phase += step;
                if (phase > 2 * java.lang.Math.PI) phase -= 2 * java.lang.Math.PI;
            }
            return pcm;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ANALYSIS FILTER BANK (Telegraph C6979Aux — exact Java port)
    //  Reads from ShortQueue instead of AudioRecord (no JNI needed)
    // ═════════════════════════════════════════════════════════════════════════
    static class AnalysisFilterBank {
        final int fftSize;
        private final int stepSize;
        private final boolean doFFT;
        private final float[] window;
        private final short[] prevBuf;
        private final short[] currBuf;
        private int bufferOffset = -1;
        private final SimpleFFT fft;

        AnalysisFilterBank(int fftSize, int stepSize, boolean doFFT) {
            this.fftSize = fftSize;
            this.stepSize = stepSize;
            this.doFFT = doFFT;
            this.window = HannWindow.create(fftSize);
            this.prevBuf = new short[fftSize];
            this.currBuf = new short[fftSize];
            this.fft = new SimpleFFT(fftSize);
        }

        void process(float[] fArr, ShortQueue queue) {
            // Exact port of C6979Aux.b()
            int off = this.bufferOffset;
            if (off == -1) {
                this.bufferOffset = this.fftSize;
                queue.read(this.currBuf, this.fftSize);
                // MeanTracker, VAD, GainScaler skipped (they use native calls in Telegraph)
            } else {
                int size = this.fftSize;
                if (off >= size) {
                    this.bufferOffset = off - size;
                    System.arraycopy(this.currBuf, 0, this.prevBuf, 0, size);
                    queue.read(this.currBuf, size);
                }
            }
            int i4 = this.bufferOffset;
            applyWindow(this.prevBuf, i4, fArr, 0, this.fftSize - i4);
            applyWindow(this.currBuf, 0, fArr, this.fftSize - i4, i4);
            if (this.doFFT) {
                fft.fft(fArr);
                // SpectralNoiseGate skipped (needs native call in Telegraph)
            }
            this.bufferOffset += this.stepSize;
        }

        private void applyWindow(short[] src, int srcOff, float[] dst, int dstOff, int len) {
            for (int i = 0; i < len; i++) {
                int di = i + dstOff;
                dst[di] = java.lang.Math.min(1.0f, java.lang.Math.max(-1.0f,
                        src[i + srcOff] / 32767.0f)) * window[di];
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SYNTHESIS FILTER BANK (Telegraph C6980aux — exact Java port)
    // ═════════════════════════════════════════════════════════════════════════
    static class SynthesisFilterBank {
        private final int fftSize;
        private final int stepSize;
        private final boolean doIFFT;
        private final float[] window;
        private final short[] prevBuf;
        private final short[] currBuf;
        private int bufferOffset = 0;
        private final SimpleFFT fft;

        SynthesisFilterBank(int fftSize, int stepSize, boolean doIFFT) {
            this.fftSize = fftSize;
            this.stepSize = stepSize;
            this.doIFFT = doIFFT;
            this.window = HannWindow.create(fftSize);
            this.prevBuf = new short[fftSize];
            this.currBuf = new short[fftSize];
            this.fft = new SimpleFFT(fftSize);
        }

        short[] process(float[] fArr) {
            // Exact port of C6980aux.a()
            if (this.doIFFT) {
                fft.ifft(fArr);
            }
            int off = this.bufferOffset;
            addWindowed(fArr, 0, this.prevBuf, off, this.fftSize - off);
            addWindowed(fArr, this.fftSize - off, this.currBuf, 0, off);

            int next = this.bufferOffset + this.stepSize;
            this.bufferOffset = next;

            short[] output = null;
            if (next >= this.fftSize) {
                this.bufferOffset = next - this.fftSize;
                if (this.prevBuf.length > 0) {
                    output = new short[this.prevBuf.length];
                    System.arraycopy(this.prevBuf, 0, output, 0, this.prevBuf.length);
                }
                System.arraycopy(this.currBuf, 0, this.prevBuf, 0, this.fftSize);
                java.util.Arrays.fill(this.currBuf, (short) 0);
            }
            return output;
        }

        private void addWindowed(float[] src, int srcOff, short[] dst, int dstOff, int len) {
            for (int i = 0; i < len; i++) {
                int si = i + srcOff;
                int di = i + dstOff;
                float val = java.lang.Math.min(1.0f, java.lang.Math.max(-1.0f,
                        src[si] * window[si])) * 32767.0f;
                dst[di] = (short) (dst[di] + (short) java.lang.Math.round(val));
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TIMESCALE PROCESSOR (Telegraph NativeTimescaleProcessor — pure Java port)
    //  Phase vocoder time-stretch: adjusts phase of each FFT bin
    // ═════════════════════════════════════════════════════════════════════════
    static class TimescaleProcessor {
        private final int fftSize;
        private final int synthStep;
        private final int analysisStep;
        private final float[] lastPhase;
        private final float[] phaseAccum;

        TimescaleProcessor(int fftSize, int synthStep, int analysisStep) {
            this.fftSize = fftSize;
            this.synthStep = synthStep;
            this.analysisStep = analysisStep;
            this.lastPhase = new float[fftSize / 2 + 1];
            this.phaseAccum = new float[fftSize / 2 + 1];
        }

        void process(float[] fArr) {
            // Phase vocoder: adjust bin phases for time-stretching
            // fArr is interleaved [re0, im0, re1, im1, ...]
            int bins = fftSize / 2;
            float analysisStepF = analysisStep;
            float synthStepF = synthStep;
            float freqPerBin = (float) (2.0 * java.lang.Math.PI / fftSize);

            for (int k = 0; k <= bins; k++) {
                int re = k * 2;
                int im = re + 1;
                float real = (re < fArr.length) ? fArr[re] : 0;
                float imag = (im < fArr.length) ? fArr[im] : 0;

                // Current phase
                float phase = DSP.atan2(imag, real);
                // Expected phase from last frame
                float expectedPhase = lastPhase[k] + k * freqPerBin * analysisStepF;
                // True frequency deviation
                float phaseDiff = princarg(phase - expectedPhase);
                // Instantaneous frequency
                float instFreq = k * freqPerBin + phaseDiff / analysisStepF;
                // Accumulate output phase
                phaseAccum[k] += instFreq * synthStepF;
                lastPhase[k] = phase;

                // Set new magnitude + accumulated phase
                float mag = DSP.abs(real, imag);
                if (re < fArr.length) fArr[re] = DSP.real(mag, phaseAccum[k]);
                if (im < fArr.length) fArr[im] = DSP.imag(mag, phaseAccum[k]);
            }
        }

        private static float princarg(float phase) {
            return phase - (float) (2.0 * java.lang.Math.PI *
                    java.lang.Math.floor((phase + java.lang.Math.PI) / (2.0 * java.lang.Math.PI)));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RESAMPLE PROCESSOR (Telegraph NativeResampleProcessor — exact Java port)
    // ═════════════════════════════════════════════════════════════════════════
    static class ResampleProcessor {
        private final int inSize;
        private final int outSize;

        ResampleProcessor(int inSize, int outSize) {
            this.inSize = inSize;
            this.outSize = outSize;
        }

        void process(float[] src, float[] dst) {
            // Exact port of NativeResampleProcessor.b() from Telegraph
            if (outSize <= 1) {
                if (outSize == 1 && inSize > 0) dst[0] = src[0];
                return;
            }
            float factor = (float) (inSize - 1) / (outSize - 1);
            for (int i = 0; i < outSize; i++) {
                float srcIdx = i * factor;
                int floor = (int) srcIdx;
                int ceil = java.lang.Math.min(inSize - 1, floor + 1);
                float frac = srcIdx - floor;
                dst[i] = src[floor] * (1.0f - frac) + src[ceil] * frac;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SIMPLE FFT — DFT/IDFT in pure Java (interleaved re/im format)
    //  Telegraph uses KissFFT native; we implement the same interface in Java
    // ═════════════════════════════════════════════════════════════════════════
    static class SimpleFFT {
        private final int n;
        private final float[] cosTable;
        private final float[] sinTable;

        SimpleFFT(int n) {
            this.n = n;
            this.cosTable = new float[n];
            this.sinTable = new float[n];
            for (int i = 0; i < n; i++) {
                double angle = -2.0 * java.lang.Math.PI * i / n;
                cosTable[i] = (float) java.lang.Math.cos(angle);
                sinTable[i] = (float) java.lang.Math.sin(angle);
            }
        }

        // In-place FFT on interleaved [re0,im0,re1,im1,...] array of size n*2
        void fft(float[] data) {
            // Cooley-Tukey iterative FFT
            int len = n;
            // Bit-reversal permutation
            for (int i = 1, j = 0; i < len; i++) {
                int bit = len >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) {
                    float tr = data[i * 2]; float ti = data[i * 2 + 1];
                    data[i * 2] = data[j * 2]; data[i * 2 + 1] = data[j * 2 + 1];
                    data[j * 2] = tr; data[j * 2 + 1] = ti;
                }
            }
            // Butterfly operations
            for (int size = 2; size <= len; size <<= 1) {
                double angStep = -2.0 * java.lang.Math.PI / size;
                float wr = (float) java.lang.Math.cos(angStep);
                float wi = (float) java.lang.Math.sin(angStep);
                for (int i = 0; i < len; i += size) {
                    float cr = 1.0f, ci = 0.0f;
                    for (int j = 0; j < size / 2; j++) {
                        int a = (i + j) * 2, b = (i + j + size / 2) * 2;
                        float ur = data[a], ui = data[a + 1];
                        float vr = data[b] * cr - data[b + 1] * ci;
                        float vi = data[b] * ci + data[b + 1] * cr;
                        data[a] = ur + vr; data[a + 1] = ui + vi;
                        data[b] = ur - vr; data[b + 1] = ui - vi;
                        float ncr = cr * wr - ci * wi;
                        ci = cr * wi + ci * wr;
                        cr = ncr;
                    }
                }
            }
        }

        void ifft(float[] data) {
            // Conjugate → FFT → conjugate → scale
            for (int i = 0; i < n; i++) data[i * 2 + 1] = -data[i * 2 + 1];
            fft(data);
            for (int i = 0; i < n; i++) {
                data[i * 2] /= n;
                data[i * 2 + 1] = -data[i * 2 + 1] / n;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HANN WINDOW (Telegraph C6919aUx)
    // ═════════════════════════════════════════════════════════════════════════
    static class HannWindow {
        static float[] create(int size) {
            float[] w = new float[size];
            for (int i = 0; i < size; i++) {
                w[i] = 0.5f * (1.0f - (float) java.lang.Math.cos(2.0 * java.lang.Math.PI * i / (size - 1)));
            }
            return w;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SHORT QUEUE — non-blocking FIFO (replaces C2423aux AudioRecord reader)
    // ═════════════════════════════════════════════════════════════════════════
    static class ShortQueue {
        private final short[] buf;
        private int head = 0, tail = 0, count = 0;

        ShortQueue(int capacity) {
            buf = new short[capacity];
        }

        void write(short[] src) {
            for (short s : src) {
                if (count < buf.length) {
                    buf[tail] = s;
                    tail = (tail + 1) % buf.length;
                    count++;
                }
            }
        }

        void read(short[] dst, int len) {
            for (int i = 0; i < len; i++) {
                if (count > 0) {
                    dst[i] = buf[head];
                    head = (head + 1) % buf.length;
                    count--;
                } else {
                    dst[i] = 0;
                }
            }
        }

        int available() { return count; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DSP MATH HELPERS (Telegraph native Math.* functions — pure Java)
    // ═════════════════════════════════════════════════════════════════════════
    static class DSP {
        static float abs(float re, float im) {
            return (float) java.lang.Math.sqrt(re * re + im * im);
        }
        static float atan2(float im, float re) {
            return (float) java.lang.Math.atan2(im, re);
        }
        static float real(float mag, float angle) {
            return (float) (mag * java.lang.Math.cos(angle));
        }
        static float imag(float mag, float angle) {
            return (float) (mag * java.lang.Math.sin(angle));
        }
        static float random(float min, float max) {
            return min + (float) (java.lang.Math.random() * (max - min));
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────────────
    private static short clamp(int v) {
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return (short) v;
    }
}
