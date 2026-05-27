package xyz.nextalone.nagram.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.utils.voicechanger.ShortBufferQueue;
import xyz.nextalone.nagram.utils.voicechanger.VoiceChangerProcessor;

public class VoiceChanger {

    private static final int SAMPLE_RATE = 48000;
    private static int currentEffect = 0;
    private static VoiceChangerProcessor processor = null;
    private static final ShortBufferQueue inputQueue = new ShortBufferQueue(SAMPLE_RATE * 2);

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

    public static void setEffect(int effect) {
        if (currentEffect != effect) {
            clearState();
            currentEffect = effect;
        }
    }

    private static void clearState() {
        inputQueue.clear();
        if (processor != null) {
            processor.a();
            processor = null;
        }
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
            if (processor == null) {
                processor = new VoiceChangerProcessor(
                    ApplicationLoader.applicationContext,
                    false, // isVoip
                    effect,
                    inputQueue,
                    SAMPLE_RATE,
                    count
                );
            }

            buffer.order(ByteOrder.LITTLE_ENDIAN);
            
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
            inputQueue.write(pcm, shortCount);

            buffer.rewind();
            buffer.limit(count);
            int res = processor.b(buffer);
            if (res < 0) {
                FileLog.e("VoiceChanger: processor returned error");
            }
            
            // Set position to 0 so callers (like amplitude calculation and encoders) read the processed data from start
            buffer.position(0);

        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
