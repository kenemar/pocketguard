package dev.kendall.pocketguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Blocks hardware keys and touches while the proximity sensor reports "near" and the
 * display is on. Sleeps the display after a configurable delay of continuous "near".
 * An escape key sequence entered while blocked disables the guard.
 */
public class GuardService extends AccessibilityService implements SensorEventListener {
    static final String TAG = "PocketGuard";

    /** Near must persist this long before blocking, so a finger passing the sensor
     *  during a swipe from the top edge does not kill the gesture. */
    private static final long NEAR_DELAY_MS = 500;
    private static final long FAR_DEBOUNCE_MS = 300;
    private static final long ESCAPE_STEP_TIMEOUT_MS = 2000;

    private static volatile GuardService sInstance;
    private static volatile boolean sBlocking;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensorManager;
    private Sensor proximity;
    private PowerManager powerManager;
    private WindowManager windowManager;
    private View overlay;

    private boolean enabled;
    private int lockDelayMs;
    private int[] escape;
    private String escapeLabel;   // compact form for the overlay, e.g. "*#*#"
    private int escapePos;
    private long lastEscapeMs;

    private boolean sensorRegistered;
    private boolean blocking;
    private boolean receiverRegistered;

    private final Runnable lockRunnable = () -> {
        if (!blocking) return;
        Log.i(TAG, "near for " + lockDelayMs + " ms -> locking screen");
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    };

    private final Runnable unblockRunnable = () -> setBlocking(false);

    private final Runnable blockRunnable = () -> {
        if (blocking) return;
        setBlocking(true);
        scheduleLock();
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "screen event " + intent.getAction());
            applyState();
        }
    };

    // ---- lifecycle -------------------------------------------------------------------

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        sensorManager = getSystemService(SensorManager.class);
        powerManager = getSystemService(PowerManager.class);
        windowManager = getSystemService(WindowManager.class);
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximity == null) Log.e(TAG, "no proximity sensor on this device");

        // We never need accessibility events, only key filtering.
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = 0;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, f);
        receiverRegistered = true;

        reloadPrefs();
        applyState();
        Log.i(TAG, "service connected, enabled=" + enabled + " sensor=" + (proximity != null));
    }

    @Override public boolean onUnbind(Intent intent) {
        teardown();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    private void teardown() {
        Log.i(TAG, "service stopping");
        sInstance = null;
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        stopSensor();
        handler.removeCallbacksAndMessages(null);
        setBlocking(false);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}

    // ---- public hooks for the activity / tile ----------------------------------------

    static boolean isRunning() { return sInstance != null; }

    static boolean isBlocking() { return sBlocking; }

    /** adb-only test hook, see DebugReceiver. */
    static void simulateProximity(boolean near) {
        GuardService s = sInstance;
        if (s == null) return;
        s.handler.post(() -> {
            Log.w(TAG, "SIMULATED proximity near=" + near);
            s.onProximity(near);
        });
    }

    static void notifyPrefsChanged() {
        GuardService s = sInstance;
        if (s == null) return;
        s.handler.post(() -> {
            s.reloadPrefs();
            s.applyState();
        });
    }

    // ---- state machine ---------------------------------------------------------------

    private void reloadPrefs() {
        enabled = Prefs.enabled(this);
        lockDelayMs = Prefs.lockDelaySec(this) * 1000;
        String seq = Prefs.escapeSeq(this);
        escape = Prefs.parseEscape(seq);
        if (escape == null || escape.length < Prefs.MIN_ESCAPE_KEYS) {
            seq = Prefs.DEFAULT_ESCAPE;
            escape = Prefs.parseEscape(seq);
        }
        escapeLabel = Prefs.compactLabel(seq);
        escapePos = 0;
    }

    /** Sensor on iff enabled && display interactive. Everything else follows from it. */
    private void applyState() {
        boolean want = enabled && proximity != null && powerManager.isInteractive();
        if (want && !sensorRegistered) {
            sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
            sensorRegistered = true;
            Log.i(TAG, "proximity listener registered");
        } else if (!want && sensorRegistered) {
            stopSensor();
        }
        if (!want) {
            handler.removeCallbacks(blockRunnable);
            handler.removeCallbacks(unblockRunnable);
            setBlocking(false);
        }
    }

    private void stopSensor() {
        if (sensorRegistered && sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.i(TAG, "proximity listener unregistered");
        }
        sensorRegistered = false;
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float v = e.values[0];
        boolean near = v < proximity.getMaximumRange() / 2f;
        Log.i(TAG, "proximity=" + v + " near=" + near);
        onProximity(near);
    }

    /** Shared by the real sensor and the adb debug hook. Ignored while the guard is off. */
    void onProximity(boolean near) {
        if (!sensorRegistered) return;
        if (near) {
            handler.removeCallbacks(unblockRunnable);
            if (!blocking) {
                handler.removeCallbacks(blockRunnable);
                handler.postDelayed(blockRunnable, NEAR_DELAY_MS);
            }
        } else {
            handler.removeCallbacks(blockRunnable);
            if (blocking) {
                handler.removeCallbacks(unblockRunnable);
                handler.postDelayed(unblockRunnable, FAR_DEBOUNCE_MS);
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void scheduleLock() {
        handler.removeCallbacks(lockRunnable);
        if (lockDelayMs > 0) handler.postDelayed(lockRunnable, lockDelayMs);
    }

    private void setBlocking(boolean b) {
        if (b == blocking) return;
        blocking = b;
        sBlocking = b;
        escapePos = 0;
        Log.i(TAG, b ? "BLOCKING input" : "input released");
        if (b) {
            showOverlay();
        } else {
            handler.removeCallbacks(lockRunnable);
            hideOverlay();
        }
    }

    // ---- key filtering + escape sequence ---------------------------------------------

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!blocking) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            Log.i(TAG, "consumed key " + KeyEvent.keyCodeToString(event.getKeyCode()));
            handleEscapeKey(event.getKeyCode());
        }
        return true;
    }

    private void handleEscapeKey(int code) {
        long now = SystemClock.uptimeMillis();
        if (escapePos > 0 && now - lastEscapeMs > ESCAPE_STEP_TIMEOUT_MS) escapePos = 0;
        lastEscapeMs = now;

        if (code == escape[escapePos]) {
            escapePos++;
        } else {
            escapePos = (code == escape[0]) ? 1 : 0;
        }

        if (escapePos >= escape.length) {
            escapePos = 0;
            onEscapeTriggered();
        } else if (escapePos >= 2) {
            // Genuine attempt in progress: keep the display awake until it finishes.
            scheduleLock();
        }
    }

    private void onEscapeTriggered() {
        Log.w(TAG, "escape sequence entered -> disabling guard");
        Toast.makeText(this, R.string.toast_escape, Toast.LENGTH_LONG).show();
        Prefs.setEnabled(this, false);   // posts reloadPrefs()+applyState()
    }

    // ---- overlay ---------------------------------------------------------------------

    private void showOverlay() {
        if (overlay != null) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("PocketGuard");
        View v = LayoutInflater.from(this).inflate(R.layout.overlay, null);
        ((TextView) v.findViewById(R.id.overlay_text))
                .setText(getString(R.string.overlay_text, escapeLabel));
        v.setOnTouchListener((view, ev) -> true);   // swallow every touch
        try {
            windowManager.addView(v, lp);
            overlay = v;
        } catch (RuntimeException ex) {
            Log.e(TAG, "addView failed", ex);
        }
    }

    private void hideOverlay() {
        if (overlay == null) return;
        try {
            windowManager.removeViewImmediate(overlay);
        } catch (RuntimeException ex) {
            Log.e(TAG, "removeView failed", ex);
        }
        overlay = null;
    }
}
