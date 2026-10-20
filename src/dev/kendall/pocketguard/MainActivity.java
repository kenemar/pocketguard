package dev.kendall.pocketguard;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final long STATUS_REFRESH_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Switch swEnabled;
    private TextView tvStatus;
    private Button btnAccessibility;
    private EditText etDelay;
    private EditText etEscape;

    private final Runnable statusTick = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        swEnabled = findViewById(R.id.sw_enabled);
        tvStatus = findViewById(R.id.tv_status);
        btnAccessibility = findViewById(R.id.btn_accessibility);
        etDelay = findViewById(R.id.et_delay);
        etEscape = findViewById(R.id.et_escape);

        // The switch mirrors Prefs only; never let saved view state (restored on activity
        // recreation, e.g. a keyboard config change) write back into Prefs.
        swEnabled.setSaveEnabled(false);
        etDelay.setSaveEnabled(false);
        etEscape.setSaveEnabled(false);
        btnAccessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        etDelay.setText(String.valueOf(Prefs.lockDelaySec(this)));
        etEscape.setText(Prefs.escapeSeq(this));
    }

    @Override protected void onResume() {
        super.onResume();
        swEnabled.setOnCheckedChangeListener(null);
        swEnabled.setChecked(Prefs.enabled(this));
        swEnabled.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setEnabled(this, checked);
            refreshStatus();
        });
        handler.post(statusTick);
    }

    @Override protected void onPause() {
        swEnabled.setOnCheckedChangeListener(null);
        handler.removeCallbacks(statusTick);
        super.onPause();
    }

    private void save() {
        int delay;
        try {
            delay = Integer.parseInt(etDelay.getText().toString().trim());
            if (delay < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.err_delay, Toast.LENGTH_SHORT).show();
            return;
        }
        String seq = etEscape.getText().toString().trim().toUpperCase();
        int[] codes = Prefs.parseEscape(seq);
        if (codes == null || codes.length < Prefs.MIN_ESCAPE_KEYS) {
            Toast.makeText(this, R.string.err_escape, Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.save(this, delay, seq);
        etEscape.setText(seq);
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        boolean running = GuardService.isRunning();
        Sensor prox = getSystemService(SensorManager.class).getDefaultSensor(Sensor.TYPE_PROXIMITY);
        StringBuilder sb = new StringBuilder();
        sb.append("Accessibility service: ").append(running ? "running" : "NOT enabled").append('\n');
        sb.append("Proximity sensor: ").append(prox != null ? prox.getName() : "MISSING").append('\n');
        sb.append("Guard: ").append(!Prefs.enabled(this) ? "off"
                : !running ? "waiting for service"
                : GuardService.isBlocking() ? "BLOCKING input" : "idle (far)");
        tvStatus.setText(sb);
        btnAccessibility.setVisibility(running ? android.view.View.GONE : android.view.View.VISIBLE);
    }
}
