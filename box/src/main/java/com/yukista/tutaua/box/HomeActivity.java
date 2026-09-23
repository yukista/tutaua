package com.yukista.tutaua.box;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class HomeActivity extends Activity {
    private static final String REMOTE_TAG = "TUTAUA_BOX_REMOTE";
    private static final int BG = Color.rgb(7, 10, 18), SURFACE = Color.rgb(22, 28, 42), MUTED = Color.rgb(156, 166, 190);
    private boolean resumedOnce;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); immersive();
        startService(new Intent(this, WatchdogService.class));
        Credentials.push(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false); page.setClipToPadding(false);
        page.setPadding(dp(72), dp(42), dp(72), dp(28));
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(12, 17, 31), BG, Color.rgb(10, 12, 23)}));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label(getString(R.string.brand_name), 27, Color.WHITE, true); brand.setLetterSpacing(.16f); header.addView(brand);
        header.addView(label(getString(R.string.brand_box), 14, Color.rgb(150, 128, 255), true));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView ready = label(getString(R.string.system_ready), 13, Color.rgb(111, 218, 169), true);
        ready.setBackground(fill(Color.rgb(18, 50, 43), 30)); ready.setPadding(dp(16), dp(8), dp(16), dp(8)); header.addView(ready);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView title = label(getString(R.string.home_title), 38, Color.WHITE, true); LinearLayout.LayoutParams tp = wrap(); tp.topMargin = dp(38); page.addView(title, tp);
        TextView intro = label(getString(R.string.home_intro), 17, MUTED, false); LinearLayout.LayoutParams ip = wrap(); ip.topMargin = dp(8); page.addView(intro, ip);
        LinearLayout choices = new LinearLayout(this); choices.setGravity(Gravity.CENTER); choices.setClipChildren(false); choices.setClipToPadding(false); choices.setPadding(0, dp(16), 0, dp(16)); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1); cp.topMargin = dp(18); cp.bottomMargin = dp(12); page.addView(choices, cp);
        View library = choice(R.drawable.ic_library, getString(R.string.library_title), getString(R.string.library_subtitle), "com.yukista.tutaua", Color.rgb(150, 118, 255));
        View tv = choice(R.drawable.ic_football, getString(R.string.live_tv_title), getString(R.string.live_tv_subtitle), "tv.tutaua.app", Color.rgb(76, 190, 221));
        choices.addView(library, card()); choices.addView(tv, card());
        View tdt = choice(R.drawable.ic_tdt, getString(R.string.tdt_title), getString(R.string.tdt_subtitle), "com.yukista.tutaua.tdt", Color.rgb(244, 197, 66));
        choices.addView(tdt, card());
        if (BoxConfig.preferences(this).getBoolean(BoxConfig.GAMES_ENABLED, BoxConfig.DEFAULT_GAMES_ENABLED)) {
            View games = choice(R.drawable.ic_games, getString(R.string.games_title), getString(R.string.games_subtitle), "com.yukista.tutaua.games", Color.rgb(241, 164, 76));
            choices.addView(games, card());
        }
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView boxId = label(getString(R.string.box_identifier, deviceIdentifier()), 12, Color.rgb(126, 138, 166), true);
        boxId.setLetterSpacing(.05f); boxId.setBackground(fill(Color.rgb(17, 23, 38), 18)); boxId.setPadding(dp(14), dp(7), dp(14), dp(7));
        footer.addView(boxId);
        LinearLayout updates = new LinearLayout(this); updates.setGravity(Gravity.CENTER_VERTICAL);
        updates.setFocusable(true); updates.setClickable(true); updates.setPadding(dp(14), dp(7), dp(16), dp(7));
        updates.setBackground(fill(Color.rgb(17, 23, 38), 18));
        ImageView updatesIcon = new ImageView(this); updatesIcon.setImageResource(R.drawable.ic_updates); updatesIcon.setColorFilter(Color.rgb(126, 138, 166));
        updates.addView(updatesIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView updatesLabel = label("Actualitzacions", 13, Color.rgb(126, 138, 166), false); LinearLayout.LayoutParams ulp = wrap(); ulp.leftMargin = dp(8); updates.addView(updatesLabel, ulp);
        updates.setOnClickListener(v -> startActivity(new Intent(this, UpdatesActivity.class)));
        updates.setOnFocusChangeListener((v, focused) -> v.setBackground(focused ? panel(Color.rgb(33, 40, 60), Color.rgb(150, 128, 255), 2) : fill(Color.rgb(17, 23, 38), 18)));
        LinearLayout.LayoutParams up = wrap(); up.leftMargin = dp(14); footer.addView(updates, up);
        footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        footer.addView(adminButton());
        page.addView(footer, new LinearLayout.LayoutParams(-1, dp(38)));
        setContentView(page); library.requestFocus();
    }

    private View choice(int iconRes, String title, String subtitle, String packageName, int accent) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(30), dp(24), dp(30), dp(22)); card.setFocusable(true); card.setClickable(true);
        card.setBackground(panel(SURFACE, Color.rgb(52, 62, 84), 2));
        ImageView icon = new ImageView(this); icon.setImageResource(iconRes); icon.setColorFilter(accent);
        card.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView heading = label(title, 22, Color.WHITE, true); LinearLayout.LayoutParams hp = wrap(); hp.topMargin = dp(11); card.addView(heading, hp);
        TextView detail = label(subtitle, 13, MUTED, false); detail.setMaxLines(2); detail.setLineSpacing(0, 1.06f); LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, -2); dpv.topMargin = dp(6); card.addView(detail, dpv);
        card.setOnClickListener(v -> launch(packageName));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f)
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
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(event);
    }

    private View adminButton() {
        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true); button.setClickable(true);
        button.setContentDescription(getString(R.string.admin_button));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackground(fill(Color.rgb(17, 23, 38), 18));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_admin);
        icon.setColorFilter(Color.rgb(96, 106, 130));
        button.addView(icon, new LinearLayout.LayoutParams(dp(17), dp(17)));
        button.setOnClickListener(v -> openAdmin());
        button.setOnFocusChangeListener((v, focused) -> {
            icon.setColorFilter(focused ? Color.WHITE : Color.rgb(96, 106, 130));
            v.setBackground(focused ? panel(Color.rgb(33, 40, 60), Color.rgb(150, 128, 255), 2) : fill(Color.rgb(17, 23, 38), 18));
        });
        return button;
    }

    private void openAdmin() {
        String pin = BoxConfig.preferences(this).getString(BoxConfig.ADMIN_PIN, BoxConfig.DEFAULT_ADMIN_PIN);
        if (pin == null || pin.isEmpty()) { startActivity(new Intent(this, AdminActivity.class)); return; }
        showPinDialog(pin);
    }

    private void showPinDialog(String expected) {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(R.string.admin_pin_hint);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(112, 122, 146));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.admin_pin_required)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String entered = input.getText() == null ? "" : input.getText().toString().trim();
                    if (expected.equals(entered)) startActivity(new Intent(this, AdminActivity.class));
                    else Toast.makeText(this, R.string.admin_pin_wrong, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            input.requestFocus();
        });
        dialog.show();
    }
    private GradientDrawable panel(int fill, int stroke, int width) { GradientDrawable d = fill(fill, 18); d.setStroke(dp(width), stroke); return d; }
    private String deviceIdentifier() {
        String value = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (value == null || value.trim().isEmpty()) value = android.os.Build.DEVICE;
        value = value.toUpperCase(java.util.Locale.ROOT);
        return value.length() > 8 ? value.substring(value.length() - 8) : value;
    }
    private GradientDrawable fill(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void immersive() { getWindow().getDecorView().setSystemUiVisibility(5894); }
    private TextView label(String text, int size, int color, boolean bold) { TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return view; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams card() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.setMargins(dp(12),0,dp(12),0); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
