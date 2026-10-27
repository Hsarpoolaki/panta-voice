package com.panta.lift;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int RATE = 16000;
    static final long WAKE_MS = 5000;          // حداکثر زمان بیداری
    static final boolean ONE_COMMAND_PER_WAKE = true; // پس از هر فرمان به استندبای برگرد

    static final boolean AUTO_LISTEN = true;           // شروع خودکار گوش دادن
    static final String UART_DEV = "/dev/ttyS1";        // پورت سریال؛ با برد تنظیم شود
    static final int UART_BAUD = 9600;
    TextView tvStat;
    long lastCpu = 0, lastT = 0;
    TextView tvState, tvHeard, tvJson, tvLog;
    Button btn;
    ScrollView scroll;
    final Handler ui = new Handler(Looper.getMainLooper());
    volatile boolean running = false;
    volatile boolean forceWake = false;
    Model model;
    Thread worker;
    long wakeUntil = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        setState("در حال بارگذاری مدل فارسی…", Color.GRAY);
        StorageService.unpack(this, "model-fa", "model",
                m -> { model = m; setState("آماده. دکمه شروع را بزنید", Color.DKGRAY); btn.setEnabled(true);
                    if (AUTO_LISTEN && (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) start(); },
                e -> setState("خطا در مدل: " + e.getMessage(), Color.RED));
    }

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.WHITE);
        // رنگ‌های برند پنتا: آبی #3051A1 و زرشکی #9B2E53
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.panta_logo);
        logo.setAdjustViewBounds(true);
        logo.setMaxHeight(96);
        root.addView(logo, new LinearLayout.LayoutParams(-2, -2));
        TextView ver = tv(14, false);
        try { ver.setText("نسخه " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName); } catch (Exception e) {}
        root.addView(ver);
        tvStat = tv(14, false); root.addView(tvStat);
        ui.post(statTick);
        tvState = tv(30, true); tvHeard = tv(20, false); tvJson = tv(18, false);
        tvJson.setTypeface(Typeface.MONOSPACE);
        btn = new Button(this); btn.setText("شروع گوش دادن"); btn.setEnabled(false);
        btn.setBackgroundColor(Color.rgb(0x30, 0x51, 0xA1)); btn.setTextColor(Color.WHITE);
        btn.setOnClickListener(v -> toggle());
        Button wk = new Button(this); wk.setText("بیدار کردن دستی (تست)");
        wk.setOnClickListener(v -> forceWake = true);
        wk.setBackgroundColor(Color.rgb(0x9B, 0x2E, 0x53)); wk.setTextColor(Color.WHITE);
        LinearLayout row = new LinearLayout(this);
        row.addView(btn); row.addView(wk);
        tvLog = tv(14, false);
        scroll = new ScrollView(this); scroll.addView(tvLog);
        root.addView(tvState); root.addView(tvHeard); root.addView(tvJson); root.addView(row);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    final Runnable statTick = new Runnable() { public void run() {
        long cpu = android.os.Process.getElapsedCpuTime(), t = android.os.SystemClock.elapsedRealtime();
        double pct = lastT == 0 ? 0 : 100.0 * (cpu - lastCpu) / Math.max(1, t - lastT);
        lastCpu = cpu; lastT = t;
        android.os.Debug.MemoryInfo mi = new android.os.Debug.MemoryInfo();
        android.os.Debug.getMemoryInfo(mi);
        tvStat.setText(String.format(Locale.US, "RAM: %d MB   CPU: %.0f%%", mi.getTotalPss() / 1024, pct));
        ui.postDelayed(this, 2000);
    } };

    java.io.OutputStream uart; boolean uartTried = false;
    synchronized void uartSend(String js) {
        try {
            if (uart == null && !uartTried) {
                uartTried = true;
                try { Runtime.getRuntime().exec(new String[]{"sh", "-c", "stty -F " + UART_DEV + " " + UART_BAUD + " raw -echo"}).waitFor(); } catch (Exception ignored) {}
                uart = new java.io.FileOutputStream(UART_DEV);
            }
            if (uart != null) { uart.write((js + "\n").getBytes("UTF-8")); uart.flush(); }
        } catch (Exception e) { log("UART: " + e); uart = null; }
    }

    TextView tv(int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp); t.setGravity(Gravity.START);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    void setState(String s, int color) { ui.post(() -> { tvState.setText(s); tvState.setTextColor(color); }); }

    void toggle() {
        if (running) { stop(); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        start();
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) start();
        else setState("اجازه میکروفون داده نشد", Color.RED);
    }

    void start() {
        running = true; btn.setText("توقف");
        worker = new Thread(this::loop, "stt"); worker.start();
    }

    void stop() {
        running = false; btn.setText("شروع گوش دادن");
        setState("متوقف", Color.DKGRAY);
    }

    void loop() {
        Recognizer cmdRec = null;
        AudioRecord ar = null;
        ToneGenerator tone = null;
        try {
            cmdRec = new Recognizer(model, RATE, Commands.grammarJson(false));
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            int minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, 16000));
            ar.startRecording();
            short[] buf = new short[3200]; // 200ms
            boolean awake = false;
            setState("استندبای — بگویید «پنتا»", Color.DKGRAY);
            while (running) {
                int n = ar.read(buf, 0, buf.length);
                if (n <= 0) continue;
                long now = System.currentTimeMillis();
                if (now < mutedUntil) continue; // هنگام پخش صدا گوش نده
                if (pendTxt != null && now - pendAt > 1500) {
                    String p = pendTxt; pendTxt = null;
                    if (emit(Commands.parse(p))) {
                        awake = false; cmdRec.reset();
                        setState("استندبای — بگویید «پنتا»", Color.DKGRAY);
                        log("STANDBY");
                    }
                }
                if (!awake) {
                    boolean hit = forceWake;
                    forceWake = false;
                    boolean done = false;
                    if (cmdRec.acceptWaveForm(buf, n)) {
                        String t = text(cmdRec.getResult(), "text");
                        if (!t.isEmpty()) heard("[استندبای] " + t);
                        if (Commands.isWake(t)) {
                            hit = true;
                            // «پنتا طبقه سه» در یک جمله: فرمان همان لحظه اجرا شود
                            done = handle(Commands.stripWake(t));
                        }
                    } else if (Commands.isWake(text(cmdRec.getPartialResult(), "partial"))) {
                        hit = true; // بدون reset تا ادامهٔ همین جمله هم شنیده شود
                    }
                    if (hit) {
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
                        log("WAKE");
                        if (done) { cmdRec.reset(); }
                        else { awake = true; wakeUntil = now + WAKE_MS; }
                    }
                } else {
                    boolean done = false;
                    if (cmdRec.acceptWaveForm(buf, n)) {
                        done = handle(text(cmdRec.getResult(), "text"));
                    } else if (now >= wakeUntil) {
                        handle(text(cmdRec.getFinalResult(), "text"));
                        done = true;
                    }
                    if (!done && now < wakeUntil) {
                        long left = (wakeUntil - now + 999) / 1000;
                        setState("بیدار — فرمان بدهید (" + left + " ثانیه)", Color.rgb(0, 130, 0));
                    }
                    if (done || (now >= wakeUntil && pendTxt == null)) {
                        awake = false; cmdRec.reset();
                        setState("استندبای — بگویید «پنتا»", Color.DKGRAY);
                        log("STANDBY");
                    }
                }
            }
        } catch (Throwable e) {
            setState("خطا: " + e, Color.RED);
            running = false; ui.post(() -> btn.setText("شروع گوش دادن"));
        } finally {
            try { if (ar != null) { ar.stop(); ar.release(); } } catch (Exception ignored) {}
            if (cmdRec != null) cmdRec.close();
            if (tone != null) tone.release();
        }
    }

    String pendTxt = null; long pendAt = 0;

    /** true یعنی فرمان معتبر اجرا شد (و باید به استندبای برگردیم) */
    boolean handle(String t) {
        if (t.isEmpty()) return false;
        heard(t);
        JSONObject j = Commands.parse(t);
        if (pendTxt != null) {
            String p = pendTxt; pendTxt = null;
            String tg = j == null ? "" : j.optString("target", "");
            if (tg.matches("F[1-9]")) {
                // «سی» + «دو» → «سی و دو»
                return emit(Commands.parse(p + " و " + t));
            }
            emit(Commands.parse(p));
        }
        if (j == null) return false;
        String tg = j.optString("target", "");
        if (tg.equals("F20") || tg.equals("F30") || tg.equals("F40")) {
            pendTxt = t; pendAt = System.currentTimeMillis(); // صبر برای یکان
            return false;
        }
        return emit(j);
    }

    boolean emit(JSONObject j) {
        if (j == null) return false;
        try { j.put("ts", System.currentTimeMillis()); } catch (Exception ignored) {}
        final String js = j.toString();
        ui.post(() -> tvJson.setText(js));
        log(js);
        uartSend(js);
        speak(j);
        return ONE_COMMAND_PER_WAKE;
    }

    final java.util.Random rnd = new java.util.Random();
    final java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
    volatile long mutedUntil = 0;
    android.media.MediaPlayer mp;

    final java.util.HashMap<String, long[]> vidx = new java.util.HashMap<>();

    void loadIdx() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open("voice.txt")))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] a = l.trim().split(" ");
                if (a.length == 3) vidx.put(a[0], new long[]{Long.parseLong(a[1]), Long.parseLong(a[2])});
            }
        } catch (Exception e) { log("voice.txt: " + e); }
    }

    /** پاسخ صوتی متنوع: تأیید + نام طبقه + (گاهی) عبارت پایانی */
    void speak(JSONObject j) {
        String cmd = j.optString("cmd"), tg = j.optString("target");
        java.util.ArrayList<String> p = new java.util.ArrayList<>();
        if (cmd.equals("FLOOR")) {
            String f;
            if (tg.equals("G")) f = "sp_g"; else if (tg.equals("ROOF")) f = "sp_roof";
            else if (tg.equals("RESTAURANT")) f = "sp_rest"; else if (tg.equals("LOBBY")) f = "sp_lobby";
            else if (tg.equals("MEZZANINE")) f = "sp_mezz"; else if (tg.equals("P")) f = "sp_p";
            else f = tg.substring(0, 1).toLowerCase() + "_" + tg.substring(1);
            if (rnd.nextInt(3) > 0) p.add("ack_" + (1 + rnd.nextInt(7)));
            p.add(f);
            if (rnd.nextInt(3) == 0) p.add("post_" + (1 + rnd.nextInt(3)));
        } else {
            p.add("c_" + cmd.toLowerCase() + "_" + (1 + rnd.nextInt(3)));
        }
        synchronized (q) {
            for (String n : p) q.add(n);
        }
        mutedUntil = System.currentTimeMillis() + 4000;
        ui.post(this::playNext);
    }

    void playNext() {
        String n;
        synchronized (q) { if (mp != null) return; n = q.poll(); }
        if (n == null) { mutedUntil = System.currentTimeMillis() + 400; return; }
        if (vidx.isEmpty()) loadIdx();
        long[] o = vidx.get(n);
        if (o == null) { playNext(); return; }
        try {
            android.content.res.AssetFileDescriptor fd = getAssets().openFd("voice.bin");
            mp = new android.media.MediaPlayer();
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset() + o[0], o[1]);
            fd.close();
            mp.setOnCompletionListener(m -> { m.release(); mp = null; playNext(); });
            mp.setOnErrorListener((m, w, x) -> { m.release(); mp = null; playNext(); return true; });
            mp.prepare();
            mp.start();
            mutedUntil = System.currentTimeMillis() + 60000;
        } catch (Exception e) {
            log("play: " + e);
            if (mp != null) { mp.release(); mp = null; }
            playNext();
        }
    }

    void heard(String s) { ui.post(() -> tvHeard.setText("شنیده شد: " + s)); log("heard: " + s); }

    final java.util.ArrayDeque<String> logBuf = new java.util.ArrayDeque<>();

    void log(String s) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + s;
        ui.post(() -> {
            logBuf.addFirst(line);
            while (logBuf.size() > 200) logBuf.removeLast();
            tvLog.setText(android.text.TextUtils.join("\n", logBuf));
        });
    }

    static String text(String json, String key) {
        try { return Commands.norm(new JSONObject(json).optString(key, "")); } catch (Exception e) { return ""; }
    }

    @Override protected void onDestroy() { running = false; ui.removeCallbacks(statTick); super.onDestroy(); }
}
