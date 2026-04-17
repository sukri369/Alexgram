package org.telegram.ui.Components;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import xyz.nextalone.nagram.NaConfig;

public class V8DAudioProcessor implements AudioProcessor {

    private int sampleRateHz = -1;
    private int channelCount = -1;
    @C.PcmEncoding
    private int encoding = C.ENCODING_INVALID;

    private boolean isActive;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;

    private float angle = 0;
    private final float angleStep = (float) (2 * Math.PI / (10.0 * 48000.0)); // 10 seconds per cycle at 48kHz
    private float lastAngleStep = 0;

    // Simple Delay for Reverb
    private short[] reverbBuffer;
    private int reverbPos = 0;
    private final int REVERB_SIZE = 4800; // ~100ms at 48kHz

    public V8DAudioProcessor() {
        buffer = EMPTY_BUFFER;
        outputBuffer = EMPTY_BUFFER;
    }

    @Override
    public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        sampleRateHz = inputAudioFormat.sampleRate;
        channelCount = inputAudioFormat.channelCount;
        encoding = inputAudioFormat.encoding;

        isActive = NaConfig.INSTANCE.getV8dAudio().Bool() && channelCount == 2;
        
        if (isActive) {
            reverbBuffer = new short[REVERB_SIZE * channelCount];
            lastAngleStep = (float) (2 * Math.PI / (12.0 * sampleRateHz)); // Adjust speed for sample rate
        }
        
        return isActive ? inputAudioFormat : AudioFormat.NOT_CONFIGURED;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!isActive) {
            return;
        }

        int position = inputBuffer.position();
        int limit = inputBuffer.limit();
        int remaining = limit - position;

        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder());
        } else {
            buffer.clear();
        }

        while (inputBuffer.hasRemaining()) {
            // Process stereo PCM 16-bit
            short left = inputBuffer.getShort();
            short right = inputBuffer.getShort();

            // Apply Panning
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            // 8D Panning Logic: 
            // Left channel gain moves from 0 to 1 and back
            // Right channel gain moves from 0 to 1 and back out of phase
            float leftGain = (cos + 1.0f) * 0.5f; 
            float rightGain = (1.0f - cos) * 0.5f;
            
            // Apply slight volume modulation for "distance" effect
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
                angle -= 2 * Math.PI;
            }
        }

        inputBuffer.position(limit);
        buffer.flip();
        outputBuffer = buffer;
    }

    @Override
    public void queueEndOfStream() {
        inputEnded = true;
    }

    @Override
    public ByteBuffer getOutput() {
        ByteBuffer outputBuffer = this.outputBuffer;
        this.outputBuffer = EMPTY_BUFFER;
        return outputBuffer;
    }

    @Override
    public boolean isEnded() {
        return inputEnded && outputBuffer == EMPTY_BUFFER;
    }

    @Override
    public void flush() {
        outputBuffer = EMPTY_BUFFER;
        inputEnded = false;
        angle = 0;
        reverbPos = 0;
        if (reverbBuffer != null) {
            java.util.Arrays.fill(reverbBuffer, (short) 0);
        }
    }

    @Override
    public void reset() {
        flush();
        buffer = EMPTY_BUFFER;
        sampleRateHz = -1;
        channelCount = -1;
        encoding = C.ENCODING_INVALID;
        isActive = false;
        reverbBuffer = null;
    }
}
