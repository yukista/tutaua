package com.yukista.tutaua.box;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class HomeActivity extends Activity {
    private static final int BG = Color.rgb(7, 10, 18), SURFACE = Color.rgb(22, 28, 42), MUTED = Color.rgb(156, 166, 190);
    private final Handler handler = new Handler();
    private boolean backHeld;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); immersive();
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(72), dp(42), dp(72), dp(34)); page.setBackgroundColor(BG);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("TUTAUA", 27, Color.WHITE, true); brand.setLetterSpacing(.16f); header.addView(brand);
        header.addView(label("  BOX", 14, Color.rgb(150, 128, 255), true));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView ready = label("●  Sistema preparat", 13, Color.rgb(111, 218, 169), true);
        ready.setBackground(fill(Color.rgb(18, 50, 43), 30)); ready.setPadding(dp(16), dp(8), dp(16), dp(8)); header.addView(ready);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView title = label("Què vols veure?", 38, Color.WHITE, true); LinearLayout.LayoutParams tp = wrap(); tp.topMargin = dp(38); page.addView(title, tp);
        TextView intro = label("Tot el teu entreteniment, en un sol lloc.", 17, MUTED, false); LinearLayout.LayoutParams ip = wrap(); ip.topMargin = dp(8); page.addView(intro, ip);
        LinearLayout choices = new LinearLayout(this); choices.setGravity(Gravity.CENTER); choices.setClipChildren(false); choices.setClipToPadding(false); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1); cp.topMargin = dp(34); cp.bottomMargin = dp(28); page.addView(choices, cp);
        View library = choice("▶", "Sèries i pel·lícules", "La teva biblioteca, favorits i contingut pendent", "com.yukista.tutaua", Color.rgb(150, 118, 255));
        View tv = choice("◉", "Televisió en directe", "Canals en directe amb canvi ràpid i automàtic", "tv.tutaua.app", Color.rgb(76, 190, 221));
        choices.addView(library, card()); choices.addView(tv, card());
        TextView help = label("Manté premut ENRERE durant 4 segons per obrir Administració", 13, Color.rgb(112, 122, 146), false); help.setGravity(Gravity.CENTER); page.addView(help, new LinearLayout.LayoutParams(-1, dp(34)));
        setContentView(page); library.requestFocus();
    }

    private View choice(String icon, String title, String subtitle, String packageName, int accent) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(30), dp(26), dp(30), dp(24)); card.setFocusable(true); card.setClickable(true);
        card.setBackground(panel(SURFACE, Color.rgb(52, 62, 84), 2)); card.addView(label(icon, 34, accent, true));
        TextView heading = label(title, 24, Color.WHITE, true); LinearLayout.LayoutParams hp = wrap(); hp.topMargin = dp(18); card.addView(heading, hp);
        TextView detail = label(subtitle, 14, MUTED, false); detail.setMaxLines(2); detail.setLineSpacing(0, 1.12f); LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, -2); dpv.topMargin = dp(10); card.addView(detail, dpv);
        TextView open = label("OBRIR  →", 12, Color.rgb(180, 166, 255), true); open.setLetterSpacing(.10f); LinearLayout.LayoutParams op = wrap(); op.topMargin = dp(25); card.addView(open, op);
        card.setOnClickListener(v -> launch(packageName));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.012f : 1f).scaleY(focused ? 1.012f : 1f)
                    .translationZ(focused ? dp(12) : 0).setDuration(140).start();
            v.setBackground(panel(
                    focused ? Color.rgb(33, 40, 60) : SURFACE,
                    focused ? Color.rgb(150, 128, 255) : Color.rgb(52, 62, 84),
                    focused ? 5 : 2));
        });
        return card;
    }

    private void launch(String packageName) { Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName); if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(packageName); if (intent == null) { Toast.makeText(this, "Aplicació no instal·lada", Toast.LENGTH_SHORT).show(); return; } startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
    @Override protected void onResume() { super.onResume(); immersive(); int timeout = DeviceSettings.configuredScreenTimeoutMinutes(this); new Thread(() -> DeviceSettings.applyScreenTimeoutMinutes(timeout), "box-device-settings").start(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event); if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) { backHeld = true; handler.postDelayed(this::openAdmin, 4000); return true; } if (event.getAction() == KeyEvent.ACTION_UP) { backHeld = false; handler.removeCallbacksAndMessages(null); return true; } return true; }
    private void openAdmin() { if (backHeld) { backHeld = false; startActivity(new Intent(this, AdminActivity.class)); } }
    private GradientDrawable panel(int fill, int stroke, int width) { GradientDrawable d = fill(fill, 18); d.setStroke(dp(width), stroke); return d; }
    private GradientDrawable fill(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void immersive() { getWindow().getDecorView().setSystemUiVisibility(5894); }
    private TextView label(String text, int size, int color, boolean bold) { TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return view; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams card() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.setMargins(dp(12),0,dp(12),0); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
