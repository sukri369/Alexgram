package xyz.nextalone.nagram.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import xyz.nextalone.nagram.NaConfig;
import org.telegram.messenger.FileLog;

public class VoiceChanger {

    private static final int SAMPLE_RATE = 48000;
    private static int currentEffect = 0;

    // Effect IDs matching NaConfig
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

    // Persistent state
    private static double modPhase = 0;
    private static double lfoPhase = 0;
    
    private static float pitchSrcIdx = 0;
    private static final int PITCH_BUFFER_SIZE = 32768;
    private static short[] pitchBuffer = new short[PITCH_BUFFER_SIZE];
    private static int pitchBufferPtr = 0;
    
    private static short[] echoBuffer = new short[SAMPLE_RATE]; // 1s buffer
    private static int echoPtr = 0;
    private static Random random = new Random();

    // Filters state
    private static float filter_lp = 0;
    private static float filter_hp = 0;

    public static void setEffect(int effect) {
        if (currentEffect != effect) {
            clearState();
            currentEffect = effect;
        }
    }

    private static void clearState() {
        modPhase = 0;
        lfoPhase = 0;
        pitchSrcIdx = 0;
        pitchBufferPtr = 0;
        for (int i = 0; i < PITCH_BUFFER_SIZE; i++) pitchBuffer[i] = 0;
        echoPtr = 0;
        for (int i = 0; i < echoBuffer.length; i++) echoBuffer[i] = 0;
        filter_lp = 0;
        filter_hp = 0;
    }

    public static void process(ByteBuffer buffer, int count) {
        if (buffer == null || count <= 0) return;

        int effect = NaConfig.INSTANCE.getVoiceChangerEffectValue();
        if (effect == EFFECT_NONE) {
            if (currentEffect != EFFECT_NONE) clearState();
            currentEffect = EFFECT_NONE;
            return;
        }

        if (currentEffect != effect) {
            setEffect(effect);
        }

        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            // On some devices (Samsung), the buffer limit might be set to a previous read length.
            // Ensure the limit is sufficient for the current count.
            if (buffer.limit() < count) {
                if (count <= buffer.capacity()) {
                    buffer.limit(count);
                } else {
                    count = buffer.capacity();
                    buffer.limit(count);
                }
            }
            
            buffer.rewind();

            int shortCount = count / 2;
            short[] pcm = new short[shortCount];
            java.nio.ShortBuffer shortBuffer = buffer.asShortBuffer();
            
            if (shortBuffer.remaining() < shortCount) {
                shortCount = shortBuffer.remaining();
                if (shortCount <= 0) return;
                pcm = new short[shortCount];
            }
            
            shortBuffer.get(pcm);

            switch (effect) {
                case EFFECT_ROBOTIC:
                    applyRobotic(pcm);
                    break;
                case EFFECT_ALIEN:
                    applyAlien(pcm);
                    break;
                case EFFECT_HOARSENESS:
                    applyHoarseness(pcm);
                    break;
                case EFFECT_MODULATION:
                    applyModulation(pcm);
                    break;
                case EFFECT_CHILD:
                    applyPerfectChild(pcm);
                    break;
                case EFFECT_MOUSE:
                    applyPerfectMouse(pcm);
                    break;
                case EFFECT_MAN:
                    applyPerfectMan(pcm);
                    break;
                case EFFECT_WOMAN:
                    applyPerfectWoman(pcm);
                    break;
                case EFFECT_MONSTER:
                    applyPerfectMonster(pcm);
                    break;
                case EFFECT_ECHO:
                    applyEcho(pcm, 0.35f, 0.45f);
                    break;
                case EFFECT_NOISE:
                    applyNoise(pcm, 0.08f);
                    break;
                case EFFECT_HELIUM:
                    applyPitchShift(pcm, 1.85f);
                    applyHighPass(pcm, 0.6f);
                    break;
                case EFFECT_HEXAFLUORIDE:
                    applyPitchShift(pcm, 0.55f);
                    applyLowPass(pcm, 0.5f);
                    break;
                case EFFECT_CAVE:
                    applyEcho(pcm, 0.65f, 0.55f);
                    applyLowPass(pcm, 0.8f);
                    break;
            }

            buffer.rewind();
            java.nio.ShortBuffer outShortBuffer = buffer.asShortBuffer();
            if (outShortBuffer.remaining() >= pcm.length) {
                outShortBuffer.put(pcm);
            }
            buffer.position(Math.min(count, buffer.capacity()));
        } catch (java.nio.BufferUnderflowException e) {
            FileLog.e("VoiceChanger: BufferUnderflowException! count=" + count + " pos=" + buffer.position() + " lim=" + buffer.limit() + " cap=" + buffer.capacity(), e);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // --- Complex Effects ---

    private static void applyPerfectChild(short[] pcm) {
        applyPitchShift(pcm, 1.45f);
        applyHighPass(pcm, 0.45f); // Simulate smaller throat
        applyDistortion(pcm, 1.1f); // Add a bit of brightness
    }

    private static void applyPerfectMouse(short[] pcm) {
        applyPitchShift(pcm, 2.1f);
        applyHighPass(pcm, 0.7f);
    }

    private static void applyPerfectMan(short[] pcm) {
        applyPitchShift(pcm, 0.78f);
        applyLowPass(pcm, 0.4f); // Simulates larger chest/throat
        // Add subtle sub-octave to make it deeper
        short[] copy = pcm.clone();
        applyPitchShift(copy, 0.5f);
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) clamp(pcm[i] * 0.8f + copy[i] * 0.4f);
        }
    }

    private static void applyPerfectWoman(short[] pcm) {
        applyPitchShift(pcm, 1.22f);
        applyHighPass(pcm, 0.3f);
    }

    private static void applyPerfectMonster(short[] pcm) {
        applyPitchShift(pcm, 0.55f);
        applyLowPass(pcm, 0.3f);
        applyDistortion(pcm, 2.5f);
        applyEcho(pcm, 0.15f, 0.3f);
    }

    private static void applyRobotic(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            // Ring modulation with 50Hz carrier
            double carrier = Math.sin(modPhase);
            pcm[i] = (short) clamp(pcm[i] * carrier);
            
            // LFO for pulse-width modulation vibe
            if (Math.sin(lfoPhase) > 0) pcm[i] = (short) clamp(pcm[i] * 0.5);
            
            modPhase += 2 * Math.PI * 65.0 / SAMPLE_RATE;
            lfoPhase += 2 * Math.PI * 4.0 / SAMPLE_RATE;
        }
        applyLowPass(pcm, 0.9f); // Cleaning up harsh harmonics
    }

    private static void applyAlien(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            // Frequency modulation based alienation
            double freq = 120.0 + 80.0 * Math.sin(lfoPhase);
            pcm[i] = (short) clamp(pcm[i] * Math.sin(modPhase));
            
            modPhase += 2 * Math.PI * freq / SAMPLE_RATE;
            lfoPhase += 2 * Math.PI * 2.0 / SAMPLE_RATE;
        }
    }

    private static void applyHoarseness(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            if (random.nextFloat() > 0.7) {
                pcm[i] = (short) clamp(pcm[i] + (random.nextInt(3000) - 1500));
            }
        }
    }

    private static void applyModulation(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) clamp(pcm[i] * Math.sin(modPhase));
            modPhase += 2 * Math.PI * 440.0 / SAMPLE_RATE;
        }
    }

    // --- Core Algorithms ---

    private static void applyPitchShift(short[] pcm, float factor) {
        for (short s : pcm) {
            pitchBuffer[pitchBufferPtr] = s;
            pitchBufferPtr = (pitchBufferPtr + 1) % PITCH_BUFFER_SIZE;
        }

        int pcmLen = pcm.length;
        for (int i = 0; i < pcmLen; i++) {
            int floor = (int) pitchSrcIdx;
            int next = (floor + 1) % PITCH_BUFFER_SIZE;
            float frac = pitchSrcIdx - floor;
            
            short s1 = pitchBuffer[floor];
            short s2 = pitchBuffer[next];
            pcm[i] = (short) clamp(s1 * (1.0f - frac) + s2 * frac);
            
            pitchSrcIdx = (pitchSrcIdx + factor) % PITCH_BUFFER_SIZE;
        }

        float distance = (pitchBufferPtr - pitchSrcIdx + PITCH_BUFFER_SIZE) % PITCH_BUFFER_SIZE;
        if (distance > PITCH_BUFFER_SIZE * 0.85f || distance < pcmLen) {
            pitchSrcIdx = (pitchBufferPtr - pcmLen * 2.5f + PITCH_BUFFER_SIZE) % PITCH_BUFFER_SIZE;
        }
    }

    private static void applyDistortion(short[] pcm, float gain) {
        for (int i = 0; i < pcm.length; i++) {
            float val = pcm[i] * gain;
            if (val > 28000) val = 28000;
            if (val < -28000) val = -28000;
            pcm[i] = (short) clamp(val);
        }
    }

    private static void applyLowPass(short[] pcm, float factor) {
        float alpha = 1.0f - factor;
        for (int i = 0; i < pcm.length; i++) {
            filter_lp = filter_lp + alpha * (pcm[i] - filter_lp);
            pcm[i] = (short) clamp(filter_lp);
        }
    }

    private static void applyHighPass(short[] pcm, float factor) {
        float alpha = factor;
        for (int i = 0; i < pcm.length; i++) {
            filter_hp = alpha * (filter_hp + pcm[i] - (i > 0 ? pcm[i-1] : pcm[i]));
            pcm[i] = (short) clamp(filter_hp);
        }
    }

    private static void applyEcho(short[] pcm, float delayPercent, float decay) {
        int delaySamples = (int) (echoBuffer.length * delayPercent);
        for (int i = 0; i < pcm.length; i++) {
            int delayedIdx = (echoPtr - delaySamples + echoBuffer.length) % echoBuffer.length;
            short delayedSample = echoBuffer[delayedIdx];
            
            int mixed = (int)(pcm[i] + delayedSample * decay);
            pcm[i] = (short) clamp(mixed);
            
            echoBuffer[echoPtr] = pcm[i];
            echoPtr = (echoPtr + 1) % echoBuffer.length;
        }
    }

    private static void applyNoise(short[] pcm, float level) {
        for (int i = 0; i < pcm.length; i++) {
            int noise = (int)((random.nextFloat() - 0.5f) * 65535 * level);
            pcm[i] = (short) clamp(pcm[i] + noise);
        }
    }

    private static int clamp(double val) {
        if (val > 32767) return 32767;
        if (val < -32768) return -32768;
        return (int) val;
    }
}
