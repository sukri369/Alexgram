package org.telegram.ui.Components;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;
import xyz.nextalone.nagram.NaConfig;

public class V8DAudioProcessor extends BaseAudioProcessor {

    private float angle = 0;
    private float lastAngleStep = 0;

    // Simple Delay for Reverb
    private short[] reverbBuffer;
    private int reverbPos = 0;
    private final int REVERB_SIZE = 4800; // ~100ms at 48kHz

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        
        boolean active = NaConfig.INSTANCE.getV8dAudio().Bool() && inputAudioFormat.channelCount == 2;
        
        if (active) {
            reverbBuffer = new short[REVERB_SIZE * inputAudioFormat.channelCount];
            lastAngleStep = (float) (2 * Math.PI / (12.0 * inputAudioFormat.sampleRate));
            return inputAudioFormat;
        }
        
        return AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!isActive()) {
            return;
        }

        int remaining = inputBuffer.remaining();
        ByteBuffer buffer = replaceOutputBuffer(remaining);

        while (inputBuffer.hasRemaining()) {
            // Process stereo PCM 16-bit
            short left = inputBuffer.getShort();
            short right = inputBuffer.getShort();

            // Apply Panning
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            // 8D Panning Logic
            float leftGain = (cos + 1.0f) * 0.5f; 
            float rightGain = (1.0f - cos) * 0.5f;
            
            float distanceMod = 0.8f + 0.2f * (float) Math.abs(sin);
            leftGain *= distanceMod;
            rightGain *= distanceMod;

            float outLeftF = left * leftGain;
            float outRightF = right * rightGain;

            // Simple Reverb (Feedback Delay)
            int revIdx = reverbPos * 2;
            float revL = reverbBuffer[revIdx] * 0.3f;
            float revR = reverbBuffer[revIdx + 1] * 0.3f;

            outLeftF += revL;
            outRightF += revR;

            // Update Reverb Buffer
            reverbBuffer[revIdx] = (short) outLeftF;
            reverbBuffer[revIdx + 1] = (short) outRightF;
            reverbPos = (reverbPos + 1) % REVERB_SIZE;

            // Clamp and convert back to short
            short outLeft = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, outLeftF));
            short outRight = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, outRightF));

            buffer.putShort(outLeft);
            buffer.putShort(outRight);

            // Update Angle
            angle += lastAngleStep;
            if (angle > 2 * Math.PI) {
                angle -= (float) (2 * Math.PI);
            }
        }
        buffer.flip();
    }

    @Override
    protected void onFlush() {
        angle = 0;
        reverbPos = 0;
        if (reverbBuffer != null) {
            java.util.Arrays.fill(reverbBuffer, (short) 0);
        }
    }

    @Override
    protected void onReset() {
        reverbBuffer = null;
    }
}
