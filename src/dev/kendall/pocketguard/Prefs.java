package dev.kendall.pocketguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistent settings plus the key-name parser used for the escape sequence. */
final class Prefs {
    private static final String NAME = "pocketguard";
    private static final String K_ENABLED = "enabled";
    private static final String K_LOCK_DELAY = "lock_delay_sec";
    private static final String K_ESCAPE = "escape_seq";

    static final String DEFAULT_ESCAPE = "* # * #";
    static final int DEFAULT_LOCK_DELAY = 10;
    static final int MIN_ESCAPE_KEYS = 4;

    private static final Map<String, Integer> KEY_NAMES = new HashMap<>();
    static {
        for (int i = 0; i <= 9; i++) KEY_NAMES.put(String.valueOf(i), KeyEvent.KEYCODE_0 + i);
        KEY_NAMES.put("*", KeyEvent.KEYCODE_STAR);
        KEY_NAMES.put("#", KeyEvent.KEYCODE_POUND);
        KEY_NAMES.put("CALL", KeyEvent.KEYCODE_CALL);
        KEY_NAMES.put("BACK", KeyEvent.KEYCODE_BACK);
        KEY_NAMES.put("MENU", KeyEvent.KEYCODE_MENU);
        KEY_NAMES.put("UP", KeyEvent.KEYCODE_DPAD_UP);
        KEY_NAMES.put("DOWN", KeyEvent.KEYCODE_DPAD_DOWN);
        KEY_NAMES.put("LEFT", KeyEvent.KEYCODE_DPAD_LEFT);
        KEY_NAMES.put("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT);
        KEY_NAMES.put("OK", KeyEvent.KEYCODE_DPAD_CENTER);
        KEY_NAMES.put("VOLUP", KeyEvent.KEYCODE_VOLUME_UP);
        KEY_NAMES.put("VOLDOWN", KeyEvent.KEYCODE_VOLUME_DOWN);
    }

    private Prefs() {}

    private static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return get(c).getBoolean(K_ENABLED, true);
    }

    static void setEnabled(Context c, boolean v) {
        get(c).edit().putBoolean(K_ENABLED, v).apply();
        GuardService.notifyPrefsChanged();
    }

    static int lockDelaySec(Context c) {
        return get(c).getInt(K_LOCK_DELAY, DEFAULT_LOCK_DELAY);
    }

    static String escapeSeq(Context c) {
        return get(c).getString(K_ESCAPE, DEFAULT_ESCAPE);
    }

    static void save(Context c, int lockDelaySec, String escapeSeq) {
        get(c).edit().putInt(K_LOCK_DELAY, lockDelaySec).putString(K_ESCAPE, escapeSeq).apply();
        GuardService.notifyPrefsChanged();
    }

    /** "* # * #" -> "*#*#"; multi-letter names keep a separating space ("*# CALL 1"). */
    static String compactLabel(String seq) {
        StringBuilder sb = new StringBuilder();
        for (String tok : seq.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            boolean word = tok.length() > 1;
            if (sb.length() > 0 && (word || sb.charAt(sb.length() - 1) == ' ')) sb.append(' ');
            sb.append(tok);
            if (word) sb.append(' ');
        }
        return sb.toString().trim();
    }

    /** Parses "* # 1 9" into key codes; returns null if any token is unknown. */
    static int[] parseEscape(String seq) {
        if (seq == null) return null;
        List<Integer> codes = new ArrayList<>();
        for (String tok : seq.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            Integer code = KEY_NAMES.get(tok.toUpperCase());
            if (code == null) return null;
            codes.add(code);
        }
        int[] out = new int[codes.size()];
        for (int i = 0; i < out.length; i++) out[i] = codes.get(i);
        return out;
    }
}
