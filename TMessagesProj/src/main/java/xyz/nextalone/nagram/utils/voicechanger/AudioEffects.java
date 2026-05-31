package xyz.nextalone.nagram.utils.voicechanger;

import java.util.Random;

public final class AudioEffects {

    // Robotic Spectrum Modifier (AbstractC6938AUX)
    public static class RoboticModifier {
        public static void a(float[] fArr) {
            int length = fArr.length / 2;
            for (int i = 1; i < length; i++) {
                int iReal = i * 2;
                int iImag = iReal + 1;
                fArr[iReal] = DSPMath.abs(fArr[iReal], fArr[iImag]);
                fArr[iImag] = 0.0f;
            }
        }
    }

    // Conjugate Spectrum Modifier (AbstractC6943aUx)
    public static class ConjugateModifier {
        public static void a(float[] fArr) {
            int length = fArr.length / 2;
            for (int i = 1; i < length; i++) {
                int iReal = i * 2;
                int iImag = iReal + 1;
                fArr[iImag] = -fArr[iImag];
            }
        }
    }

    // Random Phase Spectrum Modifier (AbstractC6944auX)
    public static class RandomPhaseModifier {
        public static void a(float[] fArr) {
            int length = fArr.length / 2;
            for (int i = 1; i < length; i++) {
                int iReal = i * 2;
                int iImag = iReal + 1;
                float mag = DSPMath.abs(fArr[iReal], fArr[iImag]);
                float randAngle = DSPMath.random(-3.1415927f, 3.1415927f);
                fArr[iReal] = DSPMath.real(mag, randAngle);
                fArr[iImag] = DSPMath.imag(mag, randAngle);
            }
        }
    }

    // Echo / Delay line Processor (C6939AUx)
    public static class EchoProcessor {
        private final float gain;
        private final float delayRatio;
        private int delaySamples;
        private final short[] delayLine;
        private int readPtr = 0;
        private int writePtr;

        public EchoProcessor(int sampleRate, float gain, float delayRatio) {
            this.gain = gain;
            this.delayRatio = delayRatio;
            int size = (int) (sampleRate * delayRatio);
            if (size % 2 != 0) {
                size++;
            }
            this.delaySamples = size;
            this.delayLine = new short[size];
            this.writePtr = size / 5;
        }

        public void a(short[] sArr) {
            for (int i = 0; i < sArr.length; i++) {
                int writeIdx = this.writePtr;
                delayLine[writeIdx] = (short) (delayLine[writeIdx] + (sArr[i] * gain));
                this.writePtr = writeIdx + 1;
                if (writePtr >= delaySamples - 1) {
                    writePtr = 0;
                }
                
                short s = sArr[i];
                int readIdx = this.readPtr;
                sArr[i] = (short) (s + delayLine[readIdx]);
                delayLine[readIdx] = (short) (delayLine[readIdx] * 0.45f);
                this.readPtr = readIdx + 1;
                if (readPtr >= delaySamples - 1) {
                    readPtr = 0;
                }
            }
        }
    }

    // Noise Generator (C6940AuX)
    public static class NoiseProcessor {
        private final float level;
        private final Random random = new Random();

        public NoiseProcessor(float level) {
            this.level = level;
        }

        public short[] a(short[] sArr) {
            for (int i = 0; i < sArr.length; i++) {
                float n1 = (random.nextFloat() - 0.5f) * 4.0f;
                float n2 = (random.nextFloat() - 0.5f) * 2.0f;
                short s = sArr[i];
                sArr[i] = (short) (s + (level * (n1 + (s * n2))));
            }
            short[] output = new short[sArr.length];
            System.arraycopy(sArr, 0, output, 0, sArr.length);
            return output;
        }
    }
}
