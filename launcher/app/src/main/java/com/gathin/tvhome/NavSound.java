package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * The launcher's own navigation sounds (config.xml nav_sound), in place of Android's, whose files sit on the read-only
 * system partition. Theme.TvHome.Quiet switches the click on OK off, and moveFocus takes the arrows before ViewRootImpl
 * clicks for them; this class then plays res/raw/nav_tick when a D-pad press moves focus and res/raw/nav_select when
 * OK clicks something. Android's own
 * "Sounds" setting (sound_effects_enabled) still mutes both.
 */
final class NavSound {

    /** A focus change this long after an arrow press counts as that press moving focus (scrolls settle first). */
    private static final long NAV_WINDOW_MS = 700;

    private static SoundPool pool;
    private static int tick;
    private static int select;
    private static final boolean[] loaded = new boolean[2];
    private static long armedAt;
    /** Two sounds closer than this belong to one press; key repeat on a held D-pad is about 50 ms apart. */
    private static final long SAME_PRESS_MS = 40;
    private static long lastPlayAt;

    private NavSound() {
    }

    static boolean enabled(Context context) {
        return context.getResources().getBoolean(R.bool.nav_sound);
    }

    /** Loads the sounds once and plays them for focus changes in root's window. Call with the activity's decor view. */
    static void watch(View root) {
        if (pool == null) load(root.getContext().getApplicationContext());
        ViewTreeObserver.OnGlobalFocusChangeListener listener = (oldFocus, newFocus) -> {
            if (newFocus == null || armedAt == 0) return;
            boolean fromKey = SystemClock.uptimeMillis() - armedAt < NAV_WINDOW_MS;
            armedAt = 0;
            if (fromKey) play(root.getContext(), tick, 0);
        };
        // A view not yet attached has a stand-in observer that Android merges on attach, so this covers both cases.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(listener);
    }

    /**
     * Every key the window sees, before it is handled: an arrow arms the focus listener, a released OK on a clickable
     * view plays the select sound.
     */
    static void onKey(KeyEvent event, View focused) {
        if (pool == null) return;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getAction() == KeyEvent.ACTION_DOWN) armedAt = SystemClock.uptimeMillis();
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && focused != null
                        && focused.isClickable() && focused.isEnabled()) {
                    play(focused.getContext(), select, 1);
                }
                break;
            default:
                break;
        }
    }

    /**
     * For a move the launcher makes itself (Up/Down in the grid), where focus may only land after a scroll: the tick
     * plays now, and the focus change that follows plays nothing. Without nav_sound, the system click as before.
     */
    static void navigate(View view, int soundEffect) {
        if (pool == null) {
            view.playSoundEffect(soundEffect);
            return;
        }
        armedAt = 0;
        play(view.getContext(), tick, 0);
    }

    /**
     * An arrow nothing else handled: move focus here, as ViewRootImpl would next. ViewRootImpl clicks for that move
     * without asking the view (soundEffectsEnabled only covers clicks), so the theme alone cannot silence it.
     * Returns false, leaving it to ViewRootImpl, without nav_sound or when there is nowhere to go.
     */
    static boolean moveFocus(KeyEvent event, View focused) {
        if (pool == null || focused == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int direction;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: direction = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = View.FOCUS_DOWN; break;
            default: return false;
        }
        View next = focused.focusSearch(direction);
        if (next == null || next == focused) return false;
        // The same hint ViewRootImpl passes: where focus was, so a list picks the child in line with it.
        Rect rect = new Rect();
        focused.getFocusedRect(rect);
        View root = focused.getRootView();
        if (root instanceof ViewGroup) {
            ((ViewGroup) root).offsetDescendantRectToMyCoords(focused, rect);
            ((ViewGroup) root).offsetRectIntoDescendantCoords(next, rect);
        }
        // Disarmed first: requestFocus calls the focus listener right away, which would tick a second time.
        long armed = armedAt;
        armedAt = 0;
        if (!next.requestFocus(direction, rect)) {
            armedAt = armed;
            return false;
        }
        play(focused.getContext(), tick, 0);
        return true;
    }

    /** Popups and dialogs get keys in their own window: put their content in a frame that reports them. */
    static View wrap(View panel) {
        if (pool == null) return panel;
        FrameLayout frame = new FrameLayout(panel.getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                onKey(event, findFocus());
                return super.dispatchKeyEvent(event) || moveFocus(event, findFocus());
            }
        };
        frame.addView(panel);
        watch(frame);
        return frame;
    }

    private static void load(Context app) {
        pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (status != 0) return;
            if (id == tick) loaded[0] = true;
            if (id == select) loaded[1] = true;
        });
        tick = pool.load(app, R.raw.nav_tick, 1);
        select = pool.load(app, R.raw.nav_select, 1);
    }

    private static void play(Context context, int sound, int index) {
        if (!loaded[index]) return;
        // One press, one sound: a move can reach both the focus listener and navigate() (focusTile focuses first).
        long now = SystemClock.uptimeMillis();
        if (now - lastPlayAt < SAME_PRESS_MS) return;
        lastPlayAt = now;
        if (Settings.System.getInt(context.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return;
        pool.play(sound, 1f, 1f, 1, 0, 1f);
    }
}
