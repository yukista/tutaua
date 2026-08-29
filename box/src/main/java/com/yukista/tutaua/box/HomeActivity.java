package com.yukista.tutaua.box;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class HomeActivity extends Activity {
    private static final String REMOTE_TAG = "TUTAUA_BOX_REMOTE";
    private static final int BG = Color.rgb(7, 10, 18), SURFACE = Color.rgb(22, 28, 42), MUTED = Color.rgb(156, 166, 190);
    private final Handler handler = new Handler();
    private boolean backHeld;
    private boolean resumedOnce;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); immersive();
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(72), dp(42), dp(72), dp(34)); page.setBackgroundColor(BG);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label(getString(R.string.brand_name), 27, Color.WHITE, true); brand.setLetterSpacing(.16f); header.addView(brand);
        header.addView(label(getString(R.string.brand_box), 14, Color.rgb(150, 128, 255), true));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView ready = label(getString(R.string.system_ready), 13, Color.rgb(111, 218, 169), true);
        ready.setBackground(fill(Color.rgb(18, 50, 43), 30)); ready.setPadding(dp(16), dp(8), dp(16), dp(8)); header.addView(ready);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView title = label(getString(R.string.home_title), 38, Color.WHITE, true); LinearLayout.LayoutParams tp = wrap(); tp.topMargin = dp(38); page.addView(title, tp);
        TextView intro = label(getString(R.string.home_intro), 17, MUTED, false); LinearLayout.LayoutParams ip = wrap(); ip.topMargin = dp(8); page.addView(intro, ip);
        LinearLayout choices = new LinearLayout(this); choices.setGravity(Gravity.CENTER); choices.setClipChildren(false); choices.setClipToPadding(false); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1); cp.topMargin = dp(34); cp.bottomMargin = dp(28); page.addView(choices, cp);
        View library = choice("▶", getString(R.string.library_title), getString(R.string.library_subtitle), "com.yukista.tutaua", Color.rgb(150, 118, 255));
        View tv = choice("◉", getString(R.string.live_tv_title), getString(R.string.live_tv_subtitle), "tv.tutaua.app", Color.rgb(76, 190, 221));
        choices.addView(library, card()); choices.addView(tv, card());
        if (BoxConfig.preferences(this).getBoolean(BoxConfig.GAMES_ENABLED, BoxConfig.DEFAULT_GAMES_ENABLED)) {
            View games = choice("+", getString(R.string.games_title), getString(R.string.games_subtitle), "com.yukista.tutaua.games", Color.rgb(241, 164, 76));
            choices.addView(games, card());
        }
        TextView help = label(getString(R.string.admin_hint), 13, Color.rgb(112, 122, 146), false); help.setGravity(Gravity.CENTER); page.addView(help, new LinearLayout.LayoutParams(-1, dp(34)));
        setContentView(page); library.requestFocus();
    }

    private View choice(String icon, String title, String subtitle, String packageName, int accent) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(30), dp(18), dp(30), dp(16)); card.setFocusable(true); card.setClickable(true);
        card.setBackground(panel(SURFACE, Color.rgb(52, 62, 84), 2)); card.addView(label(icon, 34, accent, true));
        TextView heading = label(title, 22, Color.WHITE, true); LinearLayout.LayoutParams hp = wrap(); hp.topMargin = dp(11); card.addView(heading, hp);
        TextView detail = label(subtitle, 13, MUTED, false); detail.setMaxLines(2); detail.setLineSpacing(0, 1.06f); LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, -2); dpv.topMargin = dp(6); card.addView(detail, dpv);
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

    private void launch(String packageName) { Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName); if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(packageName); if (intent == null) { Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show(); return; } startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
    @Override protected void onResume() { super.onResume(); immersive(); if (resumedOnce) { recreate(); return; } resumedOnce = true; int timeout = DeviceSettings.configuredScreenTimeoutMinutes(this); new Thread(() -> DeviceSettings.applyScreenTimeoutMinutes(timeout), "box-device-settings").start(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int libraryKey = BoxConfig.keyCodeOrDefault(BoxConfig.preferences(this).getString(BoxConfig.LIBRARY_KEYCODE, String.valueOf(BoxConfig.DEFAULT_LIBRARY_KEYCODE)), BoxConfig.DEFAULT_LIBRARY_KEYCODE);
        int liveTvKey = BoxConfig.keyCodeOrDefault(BoxConfig.preferences(this).getString(BoxConfig.LIVE_TV_KEYCODE, String.valueOf(BoxConfig.DEFAULT_LIVE_TV_KEYCODE)), BoxConfig.DEFAULT_LIVE_TV_KEYCODE);
        int gamesKey = BoxConfig.keyCodeOrDefault(BoxConfig.preferences(this).getString(BoxConfig.GAMES_KEYCODE, String.valueOf(BoxConfig.DEFAULT_GAMES_KEYCODE)), BoxConfig.DEFAULT_GAMES_KEYCODE);
        boolean gamesEnabled = BoxConfig.preferences(this).getBoolean(BoxConfig.GAMES_ENABLED, BoxConfig.DEFAULT_GAMES_ENABLED);
        boolean libraryButton = libraryKey != 0 && event.getKeyCode() == libraryKey;
        boolean liveTvButton = liveTvKey != 0 && event.getKeyCode() == liveTvKey;
        boolean gamesButton = gamesEnabled && gamesKey != 0 && event.getKeyCode() == gamesKey;
        if (libraryButton || liveTvButton || gamesButton) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                String destination = libraryButton ? "library" : liveTvButton ? "live_tv" : "games";
                Log.i(REMOTE_TAG, "key=" + event.getKeyCode() + " destination=" + destination);
                launch(libraryButton ? "com.yukista.tutaua" : liveTvButton ? "tv.tutaua.app" : "com.yukista.tutaua.games");
            }
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) { backHeld = true; handler.postDelayed(this::openAdmin, 4000); return true; }
        if (event.getAction() == KeyEvent.ACTION_UP) { backHeld = false; handler.removeCallbacksAndMessages(null); return true; }
        return true;
    }
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
