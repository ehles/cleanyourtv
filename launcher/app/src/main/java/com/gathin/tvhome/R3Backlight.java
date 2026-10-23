package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;

/**
 * ham_tutor R3 look: the LCD backlight (lib/theme/r3_theme.dart LcdBacklight), one of four palettes the layouts read
 * as ?attr/r3Ink, r3Lcd, r3InvBg and r3InvFg. The choice is kept on the TV and cycles with the lamp pill, in the app's
 * order: dark, gray, amber, green.
 */
final class R3Backlight {

    private static final String PREFS = "r3";
    private static final String KEY = "backlight";

    private static final int[] STYLES = {
            R.style.R3Backlight_Dark, R.style.R3Backlight_Gray, R.style.R3Backlight_Amber, R.style.R3Backlight_Green,
    };
    private static final int[] NAMES = {
            R.string.r3_backlight_dark, R.string.r3_backlight_gray, R.string.r3_backlight_amber,
            R.string.r3_backlight_green,
    };
    /** Gray, what the app shows on a light system theme, until the lamp is pressed. */
    private static final int DEFAULT = 1;

    private R3Backlight() {
    }

    private static int index(Context context) {
        int i = prefs(context).getInt(KEY, DEFAULT);
        return i >= 0 && i < STYLES.length ? i : DEFAULT;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Before setContentView: every layout and drawable of the activity takes its colours from this. */
    static void apply(Activity activity) {
        activity.getTheme().applyStyle(STYLES[index(activity)], true);
    }

    /** The next backlight, saved; the caller recreates the activity to show it. */
    static void cycle(Context context) {
        prefs(context).edit().putInt(KEY, (index(context) + 1) % STYLES.length).apply();
    }

    /** The backlight's name for the status line ("СЕРАЯ"). */
    static String name(Context context) {
        return context.getString(NAMES[index(context)]);
    }

    /** A palette colour from the activity's theme (R.attr.r3Ink and the like) with the given alpha, 0 to 255. */
    static int color(Context context, int attr, int alpha) {
        TypedArray a = context.obtainStyledAttributes(new int[]{attr});
        int argb = a.getColor(0, 0xFF1F2221);
        a.recycle();
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    static int ink(Context context, int alpha) {
        return color(context, R.attr.r3Ink, alpha);
    }
}
