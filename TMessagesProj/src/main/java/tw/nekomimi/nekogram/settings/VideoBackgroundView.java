package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * VideoBackgroundView — live wallpaper for the dialogs header.
 *
 * Uses SurfaceView instead of TextureView so its Surface is composited in a
 * completely separate sub-window layer.  This guarantees zero conflict with the
 * TextureView / SurfaceTexture used by round video messages in ChatActivity,
 * which previously caused round videos to appear blank whenever this view was
 * alive and holding a hardware decoder instance.
 */
public class VideoBackgroundView extends FrameLayout implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private ImageView imageView;
    private MediaPlayer mediaPlayer;
    private String currentVideoPath;
    private boolean isImage;
    private boolean surfaceReady = false;

    public VideoBackgroundView(Context context) {
        super(context);

        currentVideoPath = NekoConfig.videoHeaderPath.String();
        isImage = isImageFile(currentVideoPath);

        if (isImage) {
            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                if (currentVideoPath != null) {
                    imageView.setImageURI(android.net.Uri.fromFile(new File(currentVideoPath)));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            surfaceView = new SurfaceView(context);
            // Place the surface BEHIND the window so round-video TextureViews
            // (which are inline in the view hierarchy) always render on top.
            surfaceView.setZOrderOnTop(false);
            surfaceView.setZOrderMediaOverlay(false);
            surfaceView.getHolder().setFormat(PixelFormat.RGB_888);
            surfaceView.getHolder().addCallback(this);
            addView(surfaceView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
    }

    private boolean isImageFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".gif");
    }

    // ── SurfaceHolder.Callback ──────────────────────────────────────────────

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        initMediaPlayer(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // nothing needed — MediaPlayer adapts to the surface dimensions
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseMediaPlayer();
    }

    // ── MediaPlayer lifecycle ────────────────────────────────────────────────

    private void initMediaPlayer(SurfaceHolder holder) {
        if (isImage || currentVideoPath == null || currentVideoPath.isEmpty()) return;
        File file = new File(currentVideoPath);
        if (!file.exists()) return;

        try {
            releaseMediaPlayer(); // safety — ensure no stale instance
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(currentVideoPath);
            mediaPlayer.setDisplay(holder);
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                FileLog.e("VideoBackgroundView MediaPlayer error: " + what + "/" + extra);
                releaseMediaPlayer();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            FileLog.e("VideoBackgroundView init error", e);
            releaseMediaPlayer();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignore) {}
            try { mediaPlayer.release(); } catch (Exception ignore) {}
            mediaPlayer = null;
        }
    }

    // ── View lifecycle ───────────────────────────────────────────────────────

    public void play() {
        if (isImage || mediaPlayer == null) return;
        if (!mediaPlayer.isPlaying()) {
            try { mediaPlayer.start(); } catch (Exception ignore) {}
        }
    }

    public void pause() {
        if (isImage || mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            try { mediaPlayer.pause(); } catch (Exception ignore) {}
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isImage) return;
        // SurfaceHolder callbacks will trigger initMediaPlayer when the surface
        // becomes available; here we just resume if we already have a player.
        if (surfaceReady) play();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isImage) return;
        // Fully release the decoder so ChatActivity round-videos can use it.
        releaseMediaPlayer();
    }
}
