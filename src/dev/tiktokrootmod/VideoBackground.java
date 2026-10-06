package dev.tiktokrootmod;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileInputStream;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;

/** Muted looping video placed behind the existing controls of dark containers. */
final class VideoBackground {
    static final String VIDEO_PATH = "/data/user/0/com.ss.android.ugc.trill/files/tiktokrootmod_theme_video.mp4";
    private static final WeakHashMap<ViewGroup, State> ATTACHED = new WeakHashMap<>();
    private VideoBackground() {}

    static void attach(ViewGroup parent) { attach(parent, false); }

    static void attach(ViewGroup parent, boolean layoutNeutral) {
        File file = new File(VIDEO_PATH);
        if (!file.isFile()) {
            State old = ATTACHED.remove(parent);
            if (old != null) old.detach(parent);
            return;
        }
        long modified = file.lastModified();
        long length = file.length();
        State current = ATTACHED.get(parent);
        if (current != null && current.modified == modified && current.length == length) return;
        if (current != null) {
            ATTACHED.remove(parent);
            current.detach(parent);
        }

        State state = new State(modified, length);
        ATTACHED.put(parent, state);
        TextureView video = new TextureView(parent.getContext());
        state.video = video;
        video.setClickable(false);
        video.setFocusable(false);
        video.setOpaque(false);
        video.setAlpha(Config.THEME_VIDEO_OPACITY / 100f);
        video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            private MediaPlayer player;
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                try {
                    XposedBridge.log("TikTokRootMod: theme video surface ready " + width + "x" + height);
                    player = new MediaPlayer();
                    state.player = player;
                    try (FileInputStream input = new FileInputStream(VIDEO_PATH)) {
                        player.setDataSource(input.getFD());
                    }
                    Surface surface = new Surface(texture);
                    player.setSurface(surface);
                    surface.release();
                    player.setLooping(true);
                    player.setVolume(0f, 0f);
                    player.setOnPreparedListener(media -> { XposedBridge.log("TikTokRootMod: theme video prepared"); media.start(); });
                    player.setOnErrorListener((media, what, extra) -> {
                        XposedBridge.log("TikTokRootMod: theme video playback error " + what + "/" + extra);
                        return true;
                    });
                    player.prepareAsync();
                } catch (Throwable error) {
                    XposedBridge.log("TikTokRootMod: theme video setup: " + error);
                    release();
                }
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { release(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
            private void release() {
                if (player != null) {
                    try { player.release(); } catch (Throwable ignored) { }
                    if (state.player == player) state.player = null;
                    player = null;
                }
            }
        });
        if (parent instanceof FrameLayout && !layoutNeutral) {
            parent.addView(video, 0, new FrameLayout.LayoutParams(-1, -1));
        } else {
            if (parent instanceof FrameLayout) parent.addView(video, 0, new FrameLayout.LayoutParams(0, 0));
            else parent.addView(video, 0, new ViewGroup.LayoutParams(-1, -1));
            View.OnLayoutChangeListener fill = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> fillVideo(video, right - left, bottom - top);
            parent.addOnLayoutChangeListener(fill);
            fillVideo(video, parent.getWidth(), parent.getHeight());
            if (layoutNeutral) {
                ViewTreeObserver.OnPreDrawListener repair = () -> {
                    if (video.getWidth() != parent.getWidth() || video.getHeight() != parent.getHeight()) fillVideo(video, parent.getWidth(), parent.getHeight());
                    return true;
                };
                parent.getViewTreeObserver().addOnPreDrawListener(repair);
                parent.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) { }
                    @Override public void onViewDetachedFromWindow(View view) {
                        if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(repair);
                        view.removeOnAttachStateChangeListener(this);
                    }
                });
            }
        }
        XposedBridge.log("TikTokRootMod: looping background video attached");
    }

    private static final class State {
        final long modified;
        final long length;
        TextureView video;
        MediaPlayer player;
        State(long modified, long length) { this.modified = modified; this.length = length; }
        void detach(ViewGroup parent) {
            if (player != null) { try { player.stop(); } catch (Throwable ignored) {} try { player.release(); } catch (Throwable ignored) {} player = null; }
            if (video != null) { try { video.setSurfaceTextureListener(null); } catch (Throwable ignored) {} try { parent.removeView(video); } catch (Throwable ignored) {} video = null; }
        }
    }

    private static void fillVideo(TextureView video, int width, int height) {
        if (width <= 0 || height <= 0) return;
        video.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        video.layout(0, 0, width, height);
    }
}
