package xyz.nextalone.nagram.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import org.telegram.messenger.FileLog;
import xyz.nextalone.nagram.NaConfig;

/**
 * VoiceChanger — real-time voice effect processor.
 *
 * All effects operate in-place on the PCM buffer (16-bit mono, 48 kHz).
 *
 * Pitch shift is implemented via linear resampling:
 *   - speed < 1  → lower pitch (e.g. Man, Monster)
 *   - speed > 1  → higher pitch (e.g. Child, Mouse, Helium)
 *
 * The resampled output is re-stretched back to the original length so the
 * duration of the recorded voice note doesn't change.
 */
public class VoiceChanger {

    // Effect IDs — must match NaConfig.voiceChangerEffect values
    public static final int EFFECT_NONE        = 0;
    public static final int EFFECT_ROBOTIC     = 1;
    public static final int EFFECT_ALIEN       = 2;
    public static final int EFFECT_HOARSENESS  = 3;
    public static final int EFFECT_MODULATION  = 4;
    public static final int EFFECT_CHILD       = 5;
    public static final int EFFECT_MOUSE       = 6;
    public static final int EFFECT_MAN         = 7;
    public static final int EFFECT_WOMAN       = 8;
    public static final int EFFECT_MONSTER     = 9;
    public static final int EFFECT_ECHO        = 10;
    public static final int EFFECT_NOISE       = 11;
    public static final int EFFECT_HELIUM      = 12;
    public static final int EFFECT_HEXAFLUORIDE = 13;
    public static final int EFFECT_CAVE        = 14;

    private static final int SAMPLE_RATE = 48000;

    // Semitone factors: factor = 2^(semitones/12)
    // speed > 1 = higher pitch, speed < 1 = lower pitch
    private static float semitonesFactor(int semitones) {
        return (float) Math.pow(2.0, semitones / 12.0);
    }

    // Echo / delay state
    private static short[] echoDelayLine = null;
    private static int echoReadPtr = 0;
    private static int echoWritePtr = 0;
    private static int echoDelaySamples = 0;

    // Modulation phase
    private static double modPhase = 0.0;

    // Pitch-shift ring buffer for speed resampling
    private static float[] pitchResampleBuf = null;
    private static float pitchReadPos = 0.0f;

    private static int lastEffect = -1;

    private static void resetState() {
        echoDelayLine = null;
        echoReadPtr = 0;
        echoWritePtr = 0;
        echoDelaySamples = 0;
        modPhase = 0.0;
        pitchResampleBuf = null;
        pitchReadPos = 0.0f;
    }

    public static void process(ByteBuffer buffer, int count) {
        if (buffer == null || count <= 0) return;

        int effect = NaConfig.INSTANCE.getVoiceChangerEffectValue();

        if (effect == EFFECT_NONE) {
            if (lastEffect != EFFECT_NONE) {
                resetState();
                lastEffect = EFFECT_NONE;
            }
            return;
        }

        if (effect != lastEffect) {
            resetState();
            lastEffect = effect;
        }

        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            // Ensure limit is set correctly
            if (count <= buffer.capacity()) {
                buffer.limit(count);
            } else {
                count = buffer.capacity();
                buffer.limit(count);
            }
            buffer.position(0);

            int numSamples = count / 2;
            if (numSamples <= 0) return;

            // Read PCM into short array
            short[] pcm = new short[numSamples];
            buffer.asShortBuffer().get(pcm);

            // Apply effect
            switch (effect) {
                case EFFECT_CHILD:
                    pitchShift(pcm, semitonesFactor(5));   // +5 semitones
                    break;
                case EFFECT_MOUSE:
                    pitchShift(pcm, semitonesFactor(9));   // +9 semitones
                    break;
                case EFFECT_MAN:
                    pitchShift(pcm, semitonesFactor(-3));  // -3 semitones
                    break;
                case EFFECT_WOMAN:
                    pitchShift(pcm, semitonesFactor(3));   // +3 semitones
                    break;
                case EFFECT_MONSTER:
                    pitchShift(pcm, semitonesFactor(-8));  // -8 semitones
                    break;
                case EFFECT_HELIUM:
                    pitchShift(pcm, semitonesFactor(12));  // +12 semitones
                    break;
                case EFFECT_HEXAFLUORIDE:
                    pitchShift(pcm, semitonesFactor(-5));  // -5 semitones
                    break;
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
                case EFFECT_ECHO:
                    applyEcho(pcm, SAMPLE_RATE, 0.06f, 0.5f);
                    break;
                case EFFECT_CAVE:
                    applyEcho(pcm, SAMPLE_RATE, 0.35f, 0.6f);
                    break;
                case EFFECT_NOISE:
                    applyNoise(pcm, 0.3f);
                    break;
            }

            // Write processed PCM back to buffer
            buffer.position(0);
            buffer.limit(count);
            for (short s : pcm) {
                buffer.putShort(s);
            }
            buffer.position(0);

        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  PITCH SHIFT via linear resampling
    //  factor > 1 = higher pitch, factor < 1 = lower pitch
    // ─────────────────────────────────────────────────────────────────
    private static void pitchShift(short[] pcm, float factor) {
        int n = pcm.length;

        // Number of source samples we need to consume to produce n output samples
        // at speed `factor`: outSample[i] = src[i * factor]
        int srcLen = Math.round(n * factor);

        // Ensure ring buffer is large enough
        int needed = srcLen + 4;
        if (pitchResampleBuf == null || pitchResampleBuf.length < needed) {
            pitchResampleBuf = new float[needed * 2];
            pitchReadPos = 0;
        }

        // Fill ring buffer with new input, converting to float [-1, 1]
        // We treat pitchResampleBuf as a simple array appended each call.
        // For simplicity: just resample directly from pcm using linear interp.
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float srcPos = i * factor;
            int idx0 = (int) srcPos;
            float frac = srcPos - idx0;
            int idx1 = idx0 + 1;
            // Clamp to valid range
            if (idx0 >= n) idx0 = n - 1;
            if (idx1 >= n) idx1 = n - 1;
            float val = pcm[idx0] * (1.0f - frac) + pcm[idx1] * frac;
            out[i] = clampShort(Math.round(val));
        }
        System.arraycopy(out, 0, pcm, 0, n);
    }

    // ─────────────────────────────────────────────────────────────────
    //  ROBOTIC: quantize amplitude to steps (replicate phone dialer effect)
    // ─────────────────────────────────────────────────────────────────
    private static void applyRobotic(short[] pcm) {
        // Simple ring modulation at a carrier frequency to give robotic sound
        double carrierFreq = 100.0; // Hz
        double phase = 0.0;
        double step = 2 * Math.PI * carrierFreq / SAMPLE_RATE;
        for (int i = 0; i < pcm.length; i++) {
            double carrier = Math.abs(Math.sin(phase));
            pcm[i] = clampShort((int) (pcm[i] * carrier));
            phase += step;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  ALIEN: reverse polarity of every other sample + pitch up
    // ─────────────────────────────────────────────────────────────────
    private static void applyAlien(short[] pcm) {
        // Combine pitch shift + ring mod
        pitchShift(pcm, semitonesFactor(7));
        double phase = 0.0;
        double step = 2 * Math.PI * 300.0 / SAMPLE_RATE;
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = clampShort((int) (pcm[i] * Math.sin(phase)));
            phase += step;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  HOARSENESS: add slight distortion + random noise
    // ─────────────────────────────────────────────────────────────────
    private static final Random hoarsenessRandom = new Random();
    private static void applyHoarseness(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            float s = pcm[i] / 32767.0f;
            // Soft clipping / tanh-like distortion
            s = (float) Math.tanh(s * 2.5f) * 0.8f;
            // Add slight noise
            s += (hoarsenessRandom.nextFloat() - 0.5f) * 0.05f;
            pcm[i] = clampShort((int) (s * 32767));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  MODULATION: AM modulation at 440 Hz carrier
    // ─────────────────────────────────────────────────────────────────
    private static void applyModulation(short[] pcm) {
        double step = 2 * Math.PI * 440.0 / SAMPLE_RATE;
        for (int i = 0; i < pcm.length; i++) {
            double carrier = Math.sin(modPhase);
            pcm[i] = clampShort((int) (pcm[i] * carrier));
            modPhase += step;
            if (modPhase > 2 * Math.PI) modPhase -= 2 * Math.PI;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  ECHO: feedback delay line
    //  delaySeconds = delay time, feedback = echo level [0..1]
    // ─────────────────────────────────────────────────────────────────
    private static void applyEcho(short[] pcm, int sampleRate, float delaySeconds, float feedback) {
        int newDelaySamples = Math.max(1, (int) (sampleRate * delaySeconds));
        if (echoDelayLine == null || echoDelaySamples != newDelaySamples) {
            echoDelayLine = new short[newDelaySamples];
            echoDelaySamples = newDelaySamples;
            echoReadPtr = 0;
            echoWritePtr = newDelaySamples / 4; // start offset
        }
        for (int i = 0; i < pcm.length; i++) {
            short delayed = echoDelayLine[echoReadPtr];
            short input = pcm[i];
            pcm[i] = clampShort(input + delayed);
            echoDelayLine[echoWritePtr] = clampShort((int) (input + delayed * feedback));
            echoReadPtr = (echoReadPtr + 1) % echoDelaySamples;
            echoWritePtr = (echoWritePtr + 1) % echoDelaySamples;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  NOISE: add colored noise to signal
    // ─────────────────────────────────────────────────────────────────
    private static final Random noiseRandom = new Random();
    private static void applyNoise(short[] pcm, float level) {
        for (int i = 0; i < pcm.length; i++) {
            float noise = (noiseRandom.nextFloat() - 0.5f) * 2.0f * level * 32767;
            pcm[i] = clampShort((int) (pcm[i] + noise));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Utility
    // ─────────────────────────────────────────────────────────────────
    private static short clampShort(int v) {
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return (short) v;
    }
}
