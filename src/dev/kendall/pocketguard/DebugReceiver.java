package dev.kendall.pocketguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Test hook reachable only from adb shell / system (manifest requires android.permission.DUMP):
 *   adb shell am broadcast -n dev.kendall.pocketguard/.DebugReceiver --ez near true
 *   adb shell am broadcast -n dev.kendall.pocketguard/.DebugReceiver --ez enabled false
 *   adb shell am broadcast -n dev.kendall.pocketguard/.DebugReceiver --es escape "1 3 7 9" --ei delay 10
 */
public class DebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra("enabled")) {
            Prefs.setEnabled(context, intent.getBooleanExtra("enabled", true));
        }
        if (intent.hasExtra("escape") || intent.hasExtra("delay")) {
            String seq = intent.getStringExtra("escape");
            int[] codes = Prefs.parseEscape(seq);
            if (codes == null || codes.length < Prefs.MIN_ESCAPE_KEYS) seq = Prefs.escapeSeq(context);
            Prefs.save(context, intent.getIntExtra("delay", Prefs.lockDelaySec(context)), seq.trim().toUpperCase());
        }
        if (intent.hasExtra("near")) {
            GuardService.simulateProximity(intent.getBooleanExtra("near", false));
        }
    }
}
