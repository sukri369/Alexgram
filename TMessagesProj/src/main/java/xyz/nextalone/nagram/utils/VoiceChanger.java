package xyz.nextalone.nagram.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import xyz.nextalone.nagram.NaConfig;
import org.telegram.messenger.voip.VLog;

public class VoiceChanger {

    private static final int SAMPLE_RATE = 48000;
    private static int currentEffect = 0; // 0 is None

    // Effect IDs matching strings_na.xml order (approx)
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

    private static short[] echoBuffer = new short[SAMPLE_RATE / 2]; // 0.5s buffer
    private static int echoPtr = 0;
    private static double phase = 0;
    private static Random random = new Random();

    public static void setEffect(int effect) {
        currentEffect = effect;
    }

    public static int getCurrentEffect() {
        return currentEffect;
    }

    public static void process(ByteBuffer buffer) {
        int effect = NaConfig.getVoiceChangerEffectValue();
        if (effect == EFFECT_NONE) return;

        int position = buffer.position();
        int limit = buffer.limit();
        int count = position > 0 ? position : limit; // Use position if data was just read

        if (count == 0) return;

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();

        short[] pcm = new short[count / 2];
        buffer.asShortBuffer().get(pcm);

        if (currentEffect != effect) {
            VLog.d("VoiceChanger: changing effect from " + currentEffect + " to " + effect);
            currentEffect = effect;
        }

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
                applyPitchShift(pcm, 1.5f);
                break;
            case EFFECT_MOUSE:
                applyPitchShift(pcm, 2.0f);
                break;
            case EFFECT_MAN:
                applyPitchShift(pcm, 0.7f);
                break;
            case EFFECT_WOMAN:
                applyPitchShift(pcm, 1.2f);
                break;
            case EFFECT_MONSTER:
                applyPitchShift(pcm, 0.5f);
                applyDistortion(pcm);
                break;
            case EFFECT_ECHO:
                applyEcho(pcm, 0.5f, 0.3f);
                break;
            case EFFECT_NOISE:
                applyNoise(pcm, 0.1f);
                break;
            case EFFECT_HELIUM:
                applyPitchShift(pcm, 1.8f);
                break;
            case EFFECT_HEXAFLUORIDE:
                applyPitchShift(pcm, 0.6f);
                break;
            case EFFECT_CAVE:
                applyEcho(pcm, 0.7f, 0.6f);
                break;
        }

        buffer.rewind();
        buffer.asShortBuffer().put(pcm);
        buffer.position(count); // Restore position
    }

    private static void applyRobotic(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            double sine = Math.sin(2 * Math.PI * 50 * i / SAMPLE_RATE);
            pcm[i] = (short) (pcm[i] * sine);
            if (i % 100 < 50) pcm[i] = (short) (pcm[i] * 0.5);
        }
    }

    private static void applyAlien(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            phase += 2 * Math.PI * 100 / SAMPLE_RATE;
            double mod = Math.sin(phase) * Math.cos(phase * 0.5);
            pcm[i] = (short) (pcm[i] * mod);
        }
    }

    private static void applyHoarseness(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            if (random.nextFloat() > 0.8) {
                pcm[i] = (short) (pcm[i] + (random.nextInt(2000) - 1000));
            }
        }
    }

    private static void applyModulation(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (pcm[i] * Math.sin(2 * Math.PI * 440 * i / SAMPLE_RATE));
        }
    }

    private static void applyPitchShift(short[] pcm, float factor) {
        // Linear interpolation resampling for better quality
        short[] original = pcm.clone();
        int len = pcm.length;
        for (int i = 0; i < len; i++) {
            float srcIdx = i * factor;
            int floor = (int) srcIdx;
            if (floor >= len) {
                pcm[i] = 0;
            } else {
                float frac = srcIdx - floor;
                if (floor + 1 < len) {
                    pcm[i] = (short) (original[floor] * (1.0f - frac) + original[floor + 1] * frac);
                } else {
                    pcm[i] = original[floor];
                }
            }
        }
    }

    private static void applyDistortion(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            if (pcm[i] > 10000) pcm[i] = 10000;
            if (pcm[i] < -10000) pcm[i] = -10000;
            pcm[i] *= 2;
        }
    }

    private static void applyEcho(short[] pcm, float delayPercent, float decay) {
        int delaySamples = (int) (echoBuffer.length * delayPercent);
        for (int i = 0; i < pcm.length; i++) {
            short delayedSample = echoBuffer[(echoPtr + i + delaySamples) % echoBuffer.length];
            pcm[i] = (short) (pcm[i] + delayedSample * decay);
            echoBuffer[(echoPtr + i) % echoBuffer.length] = pcm[i];
        }
        echoPtr = (echoPtr + pcm.length) % echoBuffer.length;
    }

    private static void applyNoise(short[] pcm, float level) {
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (pcm[i] + (random.nextFloat() - 0.5f) * 65535 * level);
        }
    }
}
