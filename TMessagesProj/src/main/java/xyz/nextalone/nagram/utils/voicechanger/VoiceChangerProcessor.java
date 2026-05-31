package xyz.nextalone.nagram.utils.voicechanger;

import android.content.Context;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.telegram.messenger.FileLog;

public class VoiceChangerProcessor {
    private final int effectId;
    private final boolean isVoip;
    private final ShortBufferQueue inputQueue;
    
    private AnalysisFilterBank analysisFilterBank;
    private SynthesisFilterBank synthesisFilterBank;
    private NativeTimescaleProcessor timescaleProcessor;
    private NativeResampleProcessor resampleProcessor;
    private AudioEffects.EchoProcessor echoProcessor;
    private AudioEffects.NoiseProcessor noiseProcessor;
    
    private short[] shortBuf;
    private float[] floatBufIn;
    private float[] floatBufOut;
    private KissFFT fft;
    
    private ByteBuffer excessBuffer;
    private boolean hasExcessSamples = false;
    private boolean isError = false;
    
    private double modPhase = 0.0;
    private final int sampleRate;

    // Effect constants matching xyz.nextalone.nagram.utils.VoiceChanger
    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_ROBOTIC = 1;
    public static final int EFFECT_ALIEN = 2;
    public static final int EFFECT_HOARSENESS = 3;
    public static final int EFFECT_MODULATION = 4;
    public static final int EFFECT_CHILD = 5;
    public static final int EFFECT_MOUSE = 6;
    public static final int EFFECT_MAN = 7;
    public static final int EFFECT_WOMAN = 8;
    public static final int EFFECT_MONSTER = 9;
    public static final int EFFECT_ECHO = 10;
    public static final int EFFECT_NOISE = 11;
    public static final int EFFECT_HELIUM = 12;
    public static final int EFFECT_HEXAFLUORIDE = 13;
    public static final int EFFECT_CAVE = 14;

    public VoiceChangerProcessor(Context context, boolean isVoip, int effectId, ShortBufferQueue inputQueue, int sampleRate, int bufferCapacity) {
        this.effectId = effectId;
        this.isVoip = isVoip;
        this.inputQueue = inputQueue;
        this.sampleRate = sampleRate;
        
        // Step size/Ratio calculations matching AbstractC2422aUx / B7
        double ratio;
        if (effectId == EFFECT_ROBOTIC || effectId == EFFECT_ALIEN) {
            ratio = 0.5d; // Medium
        } else if (effectId == EFFECT_HOARSENESS) {
            ratio = 0.25d; // Small
        } else {
            ratio = 1.0d; // Default
        }
        
        // Determine DSP path flags
        boolean isSpectral = false;
        boolean isEcho = false;
        boolean isNoise = false;
        boolean isCave = false;
        boolean isPitchShift = false;
        boolean isModulation = false;
        int semitones = 0;

        switch (effectId) {
            case EFFECT_ROBOTIC:
            case EFFECT_ALIEN:
            case EFFECT_HOARSENESS:
                isSpectral = true;
                break;
            case EFFECT_MODULATION:
                isModulation = true;
                break;
            case EFFECT_CHILD:
                isPitchShift = true;
                semitones = 5; // Custom pitch shift default
                break;
            case EFFECT_MOUSE:
                isPitchShift = true;
                semitones = 9;
                break;
            case EFFECT_MAN:
                isPitchShift = true;
                semitones = -3;
                break;
            case EFFECT_WOMAN:
                isPitchShift = true;
                semitones = 3;
                break;
            case EFFECT_MONSTER:
                isPitchShift = true;
                semitones = -8;
                break;
            case EFFECT_ECHO:
                isEcho = true;
                break;
            case EFFECT_NOISE:
                isNoise = true;
                break;
            case EFFECT_HELIUM:
                isPitchShift = true;
                semitones = 12;
                break;
            case EFFECT_HEXAFLUORIDE:
                isPitchShift = true;
                semitones = -5;
                break;
            case EFFECT_CAVE:
                isCave = true;
                break;
        }

        int frameSize;
        if (isEcho || isNoise || isCave || isModulation) {
            frameSize = bufferCapacity / 2;
        } else {
            int calculated = (int) (sampleRate * 0.046439909297052155d * ratio);
            frameSize = calculated % 2 != 0 ? calculated + 1 : calculated;
        }
        
        int stepSize = frameSize / 4;
        
        int lfoMaxLimit = isVoip ? 100 : 40;
        int lfoMinLimit = isVoip ? 16 : 8;
        int maxDelayLimit = isVoip ? 160 : 64;
        int minDelayLimit = isVoip ? 40 : 16;
        
        try {
            if (isSpectral) {
                this.floatBufIn = new float[frameSize];
                this.analysisFilterBank = new AnalysisFilterBank(context, sampleRate, frameSize, stepSize, true);
                this.synthesisFilterBank = new SynthesisFilterBank(frameSize, stepSize, true);
            } else if (isModulation) {
                this.shortBuf = new short[frameSize];
            } else if (isEcho) {
                this.shortBuf = new short[frameSize];
                this.echoProcessor = new AudioEffects.EchoProcessor(sampleRate, lfoMaxLimit * 0.1f, lfoMinLimit * 0.1f);
            } else if (isNoise) {
                this.shortBuf = new short[frameSize];
                this.noiseProcessor = new AudioEffects.NoiseProcessor(maxDelayLimit * 0.1f);
            } else if (isCave) {
                this.shortBuf = new short[frameSize];
                this.echoProcessor = new AudioEffects.EchoProcessor(sampleRate, 0.7f, 0.1f);
            } else if (isPitchShift) {
                float factor = (float) Math.pow(2.0f, semitones / 12.0f);
                int synthStep = Math.round(stepSize / factor);
                int resampleSize = Math.round(frameSize / factor);
                
                this.floatBufIn = new float[frameSize];
                this.floatBufOut = new float[resampleSize];
                
                this.analysisFilterBank = new AnalysisFilterBank(context, sampleRate, frameSize, synthStep, true);
                this.timescaleProcessor = new NativeTimescaleProcessor(frameSize, synthStep, stepSize);
                this.fft = new KissFFT(frameSize);
                this.resampleProcessor = new NativeResampleProcessor(frameSize, resampleSize);
                this.synthesisFilterBank = new SynthesisFilterBank(resampleSize, synthStep, false);
                frameSize = resampleSize;
            }
            
            this.excessBuffer = ByteBuffer.allocateDirect(Math.max(bufferCapacity, frameSize * 2));
            this.excessBuffer.order(ByteOrder.nativeOrder());
            this.excessBuffer.rewind();
        } catch (Exception e) {
            this.isError = true;
            FileLog.e(e);
        }
    }

    public int getEffect() {
        return this.effectId;
    }

    public void a() {
        try {
            if (this.resampleProcessor != null) {
                this.resampleProcessor.a();
                this.resampleProcessor = null;
            }
            if (this.timescaleProcessor != null) {
                this.timescaleProcessor.a();
                this.timescaleProcessor = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public int b(ByteBuffer byteBuffer) {
        if (this.isError) {
            return -1;
        }
        try {
            int remaining = byteBuffer.remaining();
            if (this.hasExcessSamples) {
                if (remaining >= this.excessBuffer.remaining()) {
                    byteBuffer.put(this.excessBuffer);
                } else {
                    for (int i = 0; i < remaining; i++) {
                        byteBuffer.put(this.excessBuffer.get());
                    }
                }
                if (this.excessBuffer.remaining() == 0) {
                    this.hasExcessSamples = false;
                    this.excessBuffer.position(0);
                    this.excessBuffer.limit(this.excessBuffer.capacity());
                }
                remaining = byteBuffer.remaining();
                if (remaining == 0) {
                    return byteBuffer.capacity();
                }
            }
            
            short[] processedSamples = null;
            while (true) {
                if (effectId == EFFECT_ROBOTIC) {
                    this.analysisFilterBank.b(this.floatBufIn, inputQueue);
                    AudioEffects.RoboticModifier.a(this.floatBufIn);
                    processedSamples = this.synthesisFilterBank.a(this.floatBufIn);
                } else if (effectId == EFFECT_ALIEN) {
                    this.analysisFilterBank.b(this.floatBufIn, inputQueue);
                    AudioEffects.ConjugateModifier.a(this.floatBufIn);
                    processedSamples = this.synthesisFilterBank.a(this.floatBufIn);
                } else if (effectId == EFFECT_HOARSENESS) {
                    this.analysisFilterBank.b(this.floatBufIn, inputQueue);
                    AudioEffects.RandomPhaseModifier.a(this.floatBufIn);
                    processedSamples = this.synthesisFilterBank.a(this.floatBufIn);
                } else if (effectId == EFFECT_MODULATION) {
                    inputQueue.read(this.shortBuf, 0, this.shortBuf.length);
                    for (int i = 0; i < this.shortBuf.length; i++) {
                        double carrier = Math.sin(modPhase);
                        this.shortBuf[i] = (short) Math.min(32767, Math.max(-32768, (int) (this.shortBuf[i] * carrier)));
                        modPhase += 2 * Math.PI * 440.0 / sampleRate;
                    }
                    processedSamples = this.shortBuf;
                } else if (effectId == EFFECT_ECHO || effectId == EFFECT_CAVE) {
                    inputQueue.read(this.shortBuf, 0, this.shortBuf.length);
                    this.echoProcessor.a(this.shortBuf);
                    processedSamples = this.shortBuf;
                } else if (effectId == EFFECT_NOISE) {
                    inputQueue.read(this.shortBuf, 0, this.shortBuf.length);
                    this.noiseProcessor.a(this.shortBuf);
                    processedSamples = this.shortBuf;
                } else {
                    // Pitch shifting using phase vocoder (timescale + FFT + resample + synthesis)
                    this.analysisFilterBank.b(this.floatBufIn, inputQueue);
                    this.timescaleProcessor.b(this.floatBufIn);
                    this.fft.b(this.floatBufIn);
                    this.resampleProcessor.b(this.floatBufIn, this.floatBufOut);
                    processedSamples = this.synthesisFilterBank.a(this.floatBufOut);
                }
                
                if (processedSamples == null && inputQueue.size() <= 0) {
                    processedSamples = new short[0];
                    break;
                }
                if (processedSamples != null) {
                    break;
                }
            }
            
            if (processedSamples.length > 0) {
                if (remaining / 2 > processedSamples.length) {
                    for (short s : processedSamples) {
                        byteBuffer.putShort(s);
                    }
                    return b(byteBuffer);
                }
                
                for (int i = 0; i < processedSamples.length; i++) {
                    short s = processedSamples[i];
                    if (i < remaining / 2) {
                        byteBuffer.putShort(s);
                    } else {
                        this.excessBuffer.putShort(s);
                    }
                }
                if (this.excessBuffer.position() > 0) {
                    this.excessBuffer.limit(this.excessBuffer.position());
                    this.excessBuffer.position(0);
                    this.hasExcessSamples = true;
                }
            }
            return byteBuffer.capacity();
        } catch (Exception e) {
            FileLog.e(e);
            return -1;
        }
    }
}
