package com.yukista.tutaua;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.LruCache;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 10, 18);
    private static final int SURFACE = Color.rgb(20, 25, 38);
    private static final int SURFACE_LIGHT = Color.rgb(35, 42, 60);
    private static final int PRIMARY = Color.rgb(124, 92, 255);
    private static final int ACCENT = Color.rgb(45, 212, 191);
    private static final int MUTED = Color.rgb(178, 186, 207);

    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final AdminEscape adminEscape = new AdminEscape(this);
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(16 * 1024) { @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount() / 1024; } };
    private final Object imageCacheLock = new Object();
    private SharedPreferences prefs;
    private SecureTokenStore secureTokenStore;
    private String server = "";
    private String token = "";
    private String userId = "";
    private boolean loginInProgress;
    private final NavigationState navigation = new NavigationState();
    private LinearLayout root;
    private ExoPlayer player;
    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int searchRequestId;
    private List<JSONObject> lastSearchResults = new ArrayList<>();
    private String searchFilter = "All";
    private String catalogSort = "recent";
    private String catalogFilter = "all";
    private String catalogGenre = "all";
    private int catalogVisibleCount = 48;
    private Button activeNavigationButton;
    private Button browsePrimaryAction;
    private Button browseSecondaryAction;
    private ScrollView activeBrowseScroll;
    private String homeFeaturedId;
    private String playingItemId;
    private String playingSourceId;
    private String playSessionId;
    private String currentPlayMethod;
    private String playbackFallbackUrl;
    private boolean fallbackAttempted;
    private boolean playbackReported;
    private final List<ServerAudioTrack> serverAudioTracks = new ArrayList<>();
    private int selectedAudioStreamIndex = -1;
    private final Runnable progressReporter = new Runnable() { @Override public void run() { if (player != null && playbackReported) { reportPlayback("/Sessions/Playing/Progress", player.getCurrentPosition(), player.isPlaying()); playbackHandler.postDelayed(this, 10000); } } };
    private final List<SkipSegment> skipSegments = new ArrayList<>();
    private Button skipButton;
    private PlayerView activePlayerView;
    private SkipSegment activeSkipSegment;
    private LinearLayout playerControls;
    private TextView playerTime;
    private TextView playerTitle;
    private Button playerPlayButton;
    private Button playerAudioButton;
    private Button playerSubtitleButton;
    private Button playerQualityButton;
    private Button playerInfoButton;
    private TextView playbackDiagnostics;
    private ProgressBar playerSeekProgress;
    private TextView seekFeedback;
    private final Runnable hideSeekFeedback = () -> { if (seekFeedback != null) seekFeedback.setVisibility(View.GONE); };
    private final Runnable hidePlayerControls = () -> { if (playerControls != null && playerControls.getVisibility() == View.VISIBLE) { playerControls.setVisibility(View.GONE); if (!contextualPlayerActionFocused() && activePlayerView != null) activePlayerView.requestFocus(); } };
    private JSONObject nextEpisode;
    private LinearLayout nextEpisodeCard;
    private ProgressBar nextEpisodeProgress;
    private TextView nextEpisodeCountdownText;
    private long nextEpisodeDeadline;
    private Runnable currentPlayerReturnAction;
    private final Runnable nextEpisodeCountdown = new Runnable() { @Override public void run() {
        if (player == null || nextEpisodeCard == null || nextEpisodeCard.getVisibility() != View.VISIBLE) return;
        long remaining = nextEpisodeDeadline - android.os.SystemClock.uptimeMillis();
        if (remaining <= 0) { playNextEpisode(); return; }
        if (nextEpisodeProgress != null) nextEpisodeProgress.setProgress((int) Math.max(0, Math.min(1000, remaining * 1000 / 10000))); if (nextEpisodeCountdownText != null) nextEpisodeCountdownText.setText(((remaining + 999) / 1000) + " s");
        playbackHandler.postDelayed(this, 100);
    } };
    private final Runnable controlsUpdater = new Runnable() { @Override public void run() { if (player != null) { long duration = player.getDuration(), position = player.getCurrentPosition(); if (playerTime != null) playerTime.setText(formatPosition(position) + "  /  " + formatPosition(duration)); if (playerSeekProgress != null) { playerSeekProgress.setMax(1000); playerSeekProgress.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0); } if (playerPlayButton != null) playerPlayButton.setText(player.isPlaying() ? "Ⅱ" : "▶"); if (playerAudioButton != null) playerAudioButton.setText(s(R.string.audio) + " · " + selectedTrackCode(C.TRACK_TYPE_AUDIO)); if (playerSubtitleButton != null) playerSubtitleButton.setText("CC · " + selectedTrackCode(C.TRACK_TYPE_TEXT)); playbackHandler.postDelayed(this, 500); } } };
    private final Runnable skipSegmentWatcher = new Runnable() { @Override public void run() {
        if (player == null || skipButton == null) return;
        long position = player.getCurrentPosition(); SkipSegment found = null;
        for (SkipSegment segment : skipSegments) if (position >= segment.startMs && position < segment.endMs) { found = segment; break; }
        if (found != activeSkipSegment) {
            activeSkipSegment = found;
            if (found == null) { skipButton.setVisibility(View.GONE); playbackHandler.removeCallbacks(nextEpisodeCountdown); if (nextEpisodeCard != null) nextEpisodeCard.setVisibility(View.GONE); if (skipButton.hasFocus() && activePlayerView != null) activePlayerView.requestFocus(); }
            else if ("Outro".equalsIgnoreCase(found.type) && nextEpisode != null) showNextEpisodeCard();
            else { skipButton.setText(found.label); skipButton.setVisibility(View.VISIBLE); skipButton.requestFocus(); }
        }
        playbackHandler.postDelayed(this, 500);
    } };
    private boolean playing;
    private boolean returnHomeOnResume;
    private int generation;
    private Runnable backAction;

    private static final class SkipSegment {
        final long startMs, endMs; final String label, type;
        SkipSegment(long startMs, long endMs, String label, String type) { this.startMs = startMs; this.endMs = endMs; this.label = label; this.type = type; }
    }

    private final class TvEditText extends EditText {
        private View keyboardExitTarget;

        TvEditText() {
            super(MainActivity.this);
        }

        void setKeyboardExitTarget(View target) {
            keyboardExitTarget = target;
        }

        @Override public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    dismissKeyboard(this);
                    if (keyboardExitTarget != null) keyboardExitTarget.requestFocus();
                    immersive();
                }
                return true;
            }
            return super.onKeyPreIme(keyCode, event);
        }
    }

    private final class AdaptiveBannerImageView extends ImageView {
        AdaptiveBannerImageView() { super(MainActivity.this); setBackgroundColor(SURFACE); }

        @Override public void setImageDrawable(android.graphics.drawable.Drawable drawable) {
            super.setImageDrawable(drawable);
            configureImage(drawable);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            configureImage(getDrawable());
        }

        private void configureImage(android.graphics.drawable.Drawable drawable) {
            if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
            float aspectRatio = drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
            if (aspectRatio >= 3f) { setScaleType(ScaleType.FIT_CENTER); return; }
            setScaleType(ScaleType.MATRIX);
            float scale = Math.max(getWidth() / (float) drawable.getIntrinsicWidth(), getHeight() / (float) drawable.getIntrinsicHeight());
            float horizontalOffset = (getWidth() - drawable.getIntrinsicWidth() * scale) / 2f;
            android.graphics.Matrix matrix = new android.graphics.Matrix(); matrix.setScale(scale, scale); matrix.postTranslate(horizontalOffset, 0);
            setImageMatrix(matrix);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        immersive();
        prefs = getSharedPreferences("finity", MODE_PRIVATE);
        applyPreferredLanguage();
        secureTokenStore = new SecureTokenStore(prefs);
        server = prefs.getString("server", "");
        token = secureTokenStore.read();
        userId = prefs.getString("userId", "");
        String managedServer = BoxConfigClient.get(this, "jellyfin.base_url").trim().replaceAll("/+$", "");
        if (!managedServer.isEmpty() && !managedServer.equals(server)) {
            server = managedServer;
            token = "";
            userId = "";
            secureTokenStore.clear();
            prefs.edit().putString("server", server).remove("userId").remove("username").apply();
        }
        if (token.isEmpty()) showLogin(); else { showBrowse("home"); if (prefs.getString("username", "").isEmpty()) refreshUserName(); }
    }

    private String s(int id) { return getString(id); }
    private String s(int id, Object... args) { return getString(id, args); }
    private void applyPreferredLanguage() {
        String code = prefs.getString("language", "system");
        Locale locale = "system".equals(code) ? Locale.getDefault() : Locale.forLanguageTag(code);
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(locale);
        getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
    }
    private String languageLabel() {
        String code = prefs.getString("language", "system");
        return "en".equals(code) ? s(R.string.language_english) : "es".equals(code) ? s(R.string.language_spanish) : "ca".equals(code) ? s(R.string.language_catalan) : s(R.string.language_system);
    }
    private void selectNextLanguage() {
        String current = prefs.getString("language", "system");
        String next = "system".equals(current) ? "en" : "en".equals(current) ? "es" : "es".equals(current) ? "ca" : "system";
        prefs.edit().putString("language", next).apply(); applyPreferredLanguage(); showProfile();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void newRoot() {
        releasePlayer();
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        pendingSearch = null;
        searchRequestId++;
        generation++;
        playing = false;
        backAction = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(color); v.setTextSize(size);
        v.setFontFeatureSettings("kern");
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void showLogin() {
        newRoot();
        loginInProgress = false;
        FrameLayout page = new FrameLayout(this); root.addView(page, match());
        ImageView art = new ImageView(this); art.setScaleType(ImageView.ScaleType.CENTER_CROP); art.setImageResource(R.drawable.login_art);
        page.addView(art, match());
        View shade = new View(this); shade.setBackground(horizontalGradient(Color.argb(245, 7, 10, 18), Color.argb(155, 7, 10, 18), Color.argb(245, 7, 10, 18)));
        page.addView(shade, match());

        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(dp(58), dp(48), 0, dp(48));
        LinearLayout brandLine = new LinearLayout(this); brandLine.setGravity(Gravity.CENTER_VERTICAL); brandLine.addView(brandMark(), new LinearLayout.LayoutParams(dp(40), dp(40))); TextView logo = text(AppIdentity.WORDMARK, 27, Color.WHITE, true); LinearLayout.LayoutParams brandNameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); brandNameParams.leftMargin = dp(10); brandLine.addView(logo, brandNameParams); brand.addView(brandLine);
        View brandSpace = new View(this); brand.addView(brandSpace, new LinearLayout.LayoutParams(1, 0, 1));
        TextView eyebrow = text(s(R.string.login_eyebrow), 11, Color.rgb(171, 156, 255), true); eyebrow.setLetterSpacing(.12f); brand.addView(eyebrow);
        TextView slogan = text(s(R.string.login_slogan), 31, Color.WHITE, true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT); sp.topMargin = dp(9); brand.addView(slogan, sp);
        TextView intro = text(s(R.string.login_intro), 15, MUTED, false);
        intro.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT); ip.topMargin = dp(13); brand.addView(intro, ip);
        page.addView(brand, new FrameLayout.LayoutParams(dp(500), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(27), dp(23), dp(27), dp(21));
        panel.setBackground(stroked(Color.argb(110, 255, 255, 255), Color.argb(247, 14, 18, 30), 16, 1));
        panel.setElevation(dp(14));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(430), dp(500), Gravity.RIGHT | Gravity.CENTER_VERTICAL); pp.rightMargin = dp(44); page.addView(panel, pp);
        TextView accessLabel = text(s(R.string.secure_access), 10, Color.rgb(171, 156, 255), true); accessLabel.setLetterSpacing(.14f); panel.addView(accessLabel);
        TextView title = text(s(R.string.welcome_home), 27, Color.WHITE, true); LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); titleParams.topMargin = dp(4); panel.addView(title, titleParams);
        TextView helper = text(s(R.string.connect_helper), 14, MUTED, false); helper.setPadding(0, dp(3), 0, dp(13)); panel.addView(helper);
        EditText url = field(s(R.string.server_url), EditorInfo.IME_ACTION_NEXT); url.setText(server); panel.addView(labeled(s(R.string.server), url));
        EditText username = field(s(R.string.username_hint), EditorInfo.IME_ACTION_NEXT); username.setText(prefs.getString("username", "")); panel.addView(labeled(s(R.string.username), username));
        EditText password = field(s(R.string.password_hint), EditorInfo.IME_ACTION_DONE); password.setInputType(0x00000081); panel.addView(labeled(s(R.string.password), password));
        Button connect = actionButton(s(R.string.sign_in), true); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)); cp.topMargin = dp(7); panel.addView(connect, cp);
        ((TvEditText) url).setKeyboardExitTarget(connect);
        ((TvEditText) username).setKeyboardExitTarget(connect);
        ((TvEditText) password).setKeyboardExitTarget(connect);
        TextView status = text("", 12, Color.rgb(255, 155, 155), false); status.setPadding(0, dp(8), 0, 0); panel.addView(status);
        TextView privacy = text(s(R.string.login_privacy), 11, Color.rgb(126, 135, 158), false); privacy.setPadding(0, dp(8), 0, 0); panel.addView(privacy);
        connect.setOnClickListener(v -> login(url.getText().toString(), username.getText().toString(), password.getText().toString(), connect, status));
        password.setOnEditorActionListener((v, id, event) -> {
            boolean done = id == EditorInfo.IME_ACTION_DONE;
            boolean enterReleased = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (!done && !enterReleased) return false;
            dismissKeyboard(password);
            connect.requestFocus();
            login(url.getText().toString(), username.getText().toString(), password.getText().toString(), connect, status);
            return true;
        });
        (url.getText().length() == 0 ? url : username).requestFocus();
    }

    private LinearLayout labeled(String label, EditText field) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(label, 12, MUTED, true); l.setPadding(dp(2), 0, 0, dp(7)); box.addView(l);
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)); p.bottomMargin = dp(2); box.setLayoutParams(p); return box;
    }

    private EditText field(String hint, int action) {
        EditText e = new TvEditText(); e.setHint(hint); e.setHintTextColor(Color.rgb(118, 127, 151)); e.setTextColor(Color.WHITE);
        e.setSingleLine(); e.setTextSize(15); e.setPadding(dp(14), 0, dp(14), 0); e.setImeOptions(action); e.setBackground(rounded(Color.rgb(28, 34, 49), 9));
        e.setOnFocusChangeListener((v, focused) -> e.setBackground(stroked(focused ? Color.WHITE : Color.rgb(28, 34, 49), Color.rgb(28, 34, 49), 9, focused ? 2 : 0)));
        return e;
    }

    private void login(String base, String username, String password, Button connect, TextView status) {
        if (loginInProgress) return;
        base = base.trim().replaceAll("/+$", "");
        if (!base.matches("https?://.+") || username.trim().isEmpty()) { status.setText(s(R.string.login_incomplete)); return; }
        if (!ServerAddressPolicy.isSecureOrLocal(base)) { status.setText(s(R.string.https_required)); return; }
        loginInProgress = true;
        final String finalBase = base; connect.setEnabled(false); connect.setText(s(R.string.connecting)); status.setText("");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("Username", username).put("Pw", password);
                JSONObject response = request(finalBase + "/Users/AuthenticateByName", "POST", body.toString(), null);
                server = finalBase; token = response.getString("AccessToken"); userId = response.getJSONObject("User").getString("Id");
                if (!secureTokenStore.write(token)) throw new Exception(s(R.string.session_protection_error));
                prefs.edit().putString("server", server).putString("userId", userId).putString("username", username).apply();
                runOnUiThread(() -> showBrowse("home"));
            } catch (Exception e) { runOnUiThread(() -> { loginInProgress = false; connect.setEnabled(true); connect.setText(s(R.string.sign_in)); status.setText(s(R.string.connection_error, friendly(e))); }); }
        });
    }

    private void refreshUserName() { io.execute(() -> { try { String name = request(server + "/Users/" + userId, "GET", null, token).optString("Name"); if (!name.isEmpty()) { prefs.edit().putString("username", name).apply(); runOnUiThread(() -> { if (!playing) showBrowse(navigation.section()); }); } } catch (Exception ignored) {} }); }

    private void showBrowse(String section) {
        navigation.section(section);
        activeBrowseScroll = null;
        newRoot();
        root.setClipChildren(false);
        root.addView(topNavigation(section), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        ScrollView scroll = new ScrollView(this); activeBrowseScroll = scroll; scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> keepBrowseBlockVisible(content, scroll, newFocus));
        ProgressBar progress = new ProgressBar(this); LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(dp(56), dp(56)); pr.gravity = Gravity.CENTER; pr.topMargin = dp(300); content.addView(progress, pr);
        int expectedGeneration = generation;
        io.execute(() -> {
            try {
                String types = "movies".equals(section) ? "Movie" : "series".equals(section) ? "Series" : "Movie,Series";
                int limit = "favorites".equals(section) ? 500 : 180;
                String path = "/Users/" + userId + "/Items?Recursive=true&IncludeItemTypes=" + types
                        + "&SortBy=DateCreated&SortOrder=Descending&Limit=" + limit + "&Fields=PrimaryImageAspectRatio,Overview,Genres,BackdropImageTags,MediaSources,DateCreated";
                JSONArray raw = request(server + path, "GET", null, token).getJSONArray("Items");
                List<JSONObject> items = jsonList(raw);
                List<JSONObject> resume = new ArrayList<>();
                List<JSONObject> nextUp = new ArrayList<>();
                try {
                    String resumePath = "/Users/" + userId + "/Items/Resume?Limit=20&MediaTypes=Video&Fields=PrimaryImageAspectRatio,Overview,Genres,BackdropImageTags,MediaSources";
                    resume = jsonList(request(server + resumePath, "GET", null, token).getJSONArray("Items"));
                    if ("movies".equals(section)) resume = filterType(resume, "Movie");
                    else if ("series".equals(section)) resume = filterType(resume, "Episode");
                } catch (Exception ignored) {}
                if ("home".equals(section) || "series".equals(section) || "favorites".equals(section)) try { nextUp = jsonList(request(server + "/Shows/NextUp?UserId=" + enc(userId) + "&Limit=20&Fields=PrimaryImageAspectRatio,Overview,Genres,BackdropImageTags,MediaSources", "GET", null, token).getJSONArray("Items")); } catch (Exception ignored) {}
                List<JSONObject> finalResume = resume, finalNextUp = nextUp;
                runOnUiThread(() -> { if (generation == expectedGeneration) renderBrowse(content, items, finalResume, finalNextUp, section); });
            } catch (Exception e) { runOnUiThread(() -> { if (generation == expectedGeneration) { content.removeAllViews(); content.addView(errorView(s(R.string.catalog_error), e)); } }); }
        });
    }

    private void showCatalog(String section) {
        navigation.section(section);
        newRoot();
        root.setClipChildren(false);
        root.addView(topNavigation(section), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(46), dp(18), dp(34), dp(20)); content.setClipChildren(false); content.setClipToPadding(false);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        ProgressBar progress = new ProgressBar(this); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(48), dp(48)); pp.gravity = Gravity.CENTER; pp.topMargin = dp(250); content.addView(progress, pp);
        int expectedGeneration = generation;
        io.execute(() -> {
            try {
                String type = "movies".equals(section) ? "Movie" : "Series";
                String path = "/Users/" + userId + "/Items?Recursive=true&IncludeItemTypes=" + type
                        + "&SortBy=DateCreated&SortOrder=Descending&Limit=500&Fields=PrimaryImageAspectRatio,Overview,Genres,BackdropImageTags,MediaSources,DateCreated";
                List<JSONObject> items = jsonList(request(server + path, "GET", null, token).getJSONArray("Items"));
                runOnUiThread(() -> { if (generation == expectedGeneration) renderCatalog(content, items, section); });
            } catch (Exception e) { runOnUiThread(() -> { if (generation == expectedGeneration) { content.removeAllViews(); content.addView(errorView(s(R.string.catalog_error), e)); } }); }
        });
    }

    private void renderCatalog(LinearLayout content, List<JSONObject> allItems, String section) {
        content.removeAllViews();
        LinearLayout titleLine = new LinearLayout(this); titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("movies".equals(section) ? s(R.string.movies) : s(R.string.series), 25, Color.WHITE, true); titleLine.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView count = text(s(R.string.catalog_count, allItems.size()), 12, MUTED, false); count.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); titleLine.addView(count, new LinearLayout.LayoutParams(dp(220), dp(42))); content.addView(titleLine);

        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL); controls.setClipChildren(false);
        TextView activeFilters = text(catalogSummary(), 12, MUTED, false); activeFilters.setGravity(Gravity.CENTER_VERTICAL); controls.addView(activeFilters, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button filterButton = catalogControl(s(R.string.filter_and_sort)); filterButton.setId(View.generateViewId()); filterButton.setGravity(Gravity.CENTER); controls.addView(filterButton, catalogControlParams(170)); content.addView(controls);

        RecyclerView grid = new RecyclerView(this); grid.setId(View.generateViewId()); grid.setClipChildren(true); grid.setClipToPadding(true); grid.setPadding(0, dp(18), 0, dp(32)); grid.setItemAnimator(null); grid.setHasFixedSize(true);
        GridLayoutManager layout = new GridLayoutManager(this, 6); grid.setLayoutManager(layout); grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS); grid.setFocusable(false);
        content.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Runnable redraw = () -> {
            List<JSONObject> filtered = applyCatalogFilters(allItems);
            grid.setAdapter(new CatalogAdapter(filtered, section, grid, filterButton.getId()));
            grid.post(() -> { RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0); if (holder instanceof CatalogAdapter.Holder && ((CatalogAdapter.Holder) holder).boundCard != null) ((CatalogAdapter.Holder) holder).boundCard.setNextFocusUpId(filterButton.getId()); });
        };
        filterButton.setOnKeyListener((v, keyCode, event) -> { if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || event.getAction() != KeyEvent.ACTION_DOWN) return false; focusCatalogPosition(grid, 0); return true; });
        List<String> genres = catalogGenres(allItems);
        filterButton.setOnClickListener(v -> showCatalogFilters(genres, () -> { catalogVisibleCount = 48; activeFilters.setText(catalogSummary()); redraw.run(); }, filterButton));
        redraw.run();
        int saved = navigation.scrollPosition(section); if (saved > 0) grid.post(() -> layout.scrollToPositionWithOffset(saved, 0));
    }

    private final class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.Holder> {
        private final List<JSONObject> items; private final String section; private final RecyclerView recycler; private final int filterId;
        CatalogAdapter(List<JSONObject> items, String section, RecyclerView recycler, int filterId) { this.items = items; this.section = section; this.recycler = recycler; this.filterId = filterId; setHasStableIds(true); }
        @Override public long getItemId(int position) { return items.get(position).optString("Id").hashCode(); }
        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) { return new Holder(new FrameLayout(MainActivity.this)); }
        @Override public void onBindViewHolder(Holder holder, int position) {
            JSONObject item = items.get(position); clearCatalogImages(holder.container); holder.container.removeAllViews();
            View card = catalogCard(item, section, null);
            holder.container.addView(card, new FrameLayout.LayoutParams(dp(144), dp(228), Gravity.CENTER));
            holder.boundCard = card;
            if (position < 6) card.setNextFocusUpId(filterId);
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int current = holder.getBindingAdapterPosition(); if (current == RecyclerView.NO_POSITION) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { int target = current + 6; if (target < items.size()) { focusCatalogPosition(recycler, target); return true; } }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { if (current < 6) { View filter = findViewById(filterId); if (filter != null) filter.requestFocus(); return true; } focusCatalogPosition(recycler, current - 6); return true; }
                return false;
            });
        }
        @Override public int getItemCount() { return items.size(); }
        @Override public void onViewRecycled(Holder holder) { clearCatalogImages(holder.container); holder.container.removeAllViews(); holder.boundCard = null; }
        final class Holder extends RecyclerView.ViewHolder { final FrameLayout container; View boundCard; Holder(FrameLayout view) { super(view); container = view; RecyclerView.LayoutParams p = new RecyclerView.LayoutParams(dp(146), dp(244)); view.setLayoutParams(p); view.setClipChildren(false); view.setClipToPadding(false); } }
    }

    private void focusCatalogPosition(RecyclerView recycler, int position) {
        RecyclerView.ViewHolder visible = recycler.findViewHolderForAdapterPosition(position);
        if (visible instanceof CatalogAdapter.Holder) { View card = ((CatalogAdapter.Holder) visible).boundCard; if (card != null) card.requestFocus(); return; }
        RecyclerView.LayoutManager manager = recycler.getLayoutManager();
        if (manager instanceof GridLayoutManager) ((GridLayoutManager) manager).scrollToPositionWithOffset(position, dp(12)); else recycler.scrollToPosition(position);
        recycler.post(() -> { RecyclerView.ViewHolder target = recycler.findViewHolderForAdapterPosition(position); if (target instanceof CatalogAdapter.Holder) { View card = ((CatalogAdapter.Holder) target).boundCard; if (card != null) card.requestFocus(); } });
    }

    private void ensureCatalogHolderVisible(RecyclerView recycler, View holder) {
        recycler.post(() -> {
            int safeTop = recycler.getPaddingTop() + dp(8), safeBottom = recycler.getHeight() - recycler.getPaddingBottom() - dp(8);
            int delta = holder.getBottom() > safeBottom ? holder.getBottom() - safeBottom : holder.getTop() < safeTop ? holder.getTop() - safeTop : 0;
            if (delta != 0) recycler.scrollBy(0, delta);
        });
    }

    private LinearLayout.LayoutParams catalogControlParams(int width) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(width), dp(43)); p.rightMargin = dp(10); return p; }

    private Button catalogControl(String label) {
        Button button = actionButton(label, false); button.setTextSize(12); button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); button.setPadding(dp(16), 0, dp(12), 0); return button;
    }

    private void renderCatalogGrid(LinearLayout grid, List<JSONObject> items, String section, ScrollView scroll) {
        grid.removeAllViews();
        if (items.isEmpty()) { TextView empty = text(s(R.string.no_filter_results), 17, MUTED, false); empty.setPadding(0, dp(55), 0, dp(55)); grid.addView(empty); return; }
        int shown = Math.min(items.size(), catalogVisibleCount), columns = 6;
        for (int start = 0; start < shown; start += columns) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setClipChildren(false); row.setClipToPadding(false); row.setPadding(0, dp(3), 0, dp(3));
            for (int i = start; i < Math.min(shown, start + columns); i++) row.addView(catalogCard(items.get(i), section, scroll));
            grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(236)));
        }
        if (shown < items.size()) {
            Button more = actionButton(s(R.string.show_more, items.size() - shown), false); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(260), dp(46)); mp.topMargin = dp(10); mp.gravity = Gravity.CENTER_HORIZONTAL; grid.addView(more, mp);
            more.setOnClickListener(v -> { catalogVisibleCount += 48; renderCatalogGrid(grid, items, section, scroll); });
        }
        boolean restoring = navigation.consumeRestore() && navigation.focusedMediaId() != null;
        grid.post(() -> { View target = restoring ? grid.findViewWithTag("media:" + navigation.focusedMediaId()) : grid.findViewWithTag("media:" + items.get(0).optString("Id")); if (target != null) target.requestFocus(); });
    }

    private View catalogCard(JSONObject item, String section, ScrollView scroll) {
        int width = dp(132), imageHeight = dp(172);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setFocusable(true); card.setClickable(true); card.setClipChildren(false); card.setClipToPadding(false); card.setPadding(dp(6), dp(5), dp(6), dp(3)); card.setBackground(rounded(Color.TRANSPARENT, 10));
        FrameLayout artwork = new FrameLayout(this); artwork.setClipChildren(false); artwork.setClipToPadding(false); artwork.setPadding(dp(3), dp(3), dp(3), dp(3)); artwork.setBackground(stroked(Color.TRANSPARENT, SURFACE, 9, 0));
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setClipToOutline(true); image.setBackground(rounded(SURFACE, 7)); artwork.addView(image, match()); card.addView(artwork, new LinearLayout.LayoutParams(width, imageHeight)); loadImage(image, item.optString("Id"), "Primary", 280);
        JSONObject user = item.optJSONObject("UserData"); if (user != null && user.optBoolean("Played")) { TextView watched = text("✓", 12, Color.WHITE, true); watched.setGravity(Gravity.CENTER); watched.setBackground(rounded(Color.argb(220, 20, 25, 38), 12)); FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.TOP | Gravity.RIGHT); wp.setMargins(0, dp(7), dp(7), 0); artwork.addView(watched, wp); }
        TextView name = text(item.optString("Name"), 11, Color.rgb(232, 235, 243), true); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); name.setLineSpacing(0, .94f); name.setPadding(dp(1), dp(4), dp(1), 0); card.addView(name, new LinearLayout.LayoutParams(width, dp(32)));
        TextView meta = text(cardMetadata(item), 9, MUTED, false); meta.setMaxLines(1); meta.setEllipsize(TextUtils.TruncateAt.END); meta.setPadding(dp(1), 0, dp(1), 0); card.addView(meta, new LinearLayout.LayoutParams(width, dp(16)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(144), dp(228)); cp.rightMargin = dp(2); card.setLayoutParams(cp); card.setTag("media:" + item.optString("Id"));
        card.setOnFocusChangeListener((v, focused) -> { artwork.animate().translationZ(focused ? dp(8) : 0).scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(140).start(); artwork.setBackground(stroked(focused ? Color.WHITE : Color.TRANSPARENT, focused ? Color.rgb(29, 35, 51) : SURFACE, 9, focused ? 3 : 0)); name.setTextColor(focused ? Color.WHITE : Color.rgb(232, 235, 243)); meta.setTextColor(focused ? Color.rgb(210, 216, 230) : MUTED); if (focused) { navigation.rememberMedia(item.optString("Id")); if (scroll != null) { navigation.rememberScroll(section, scroll.getScrollY()); ensureFullyVisible(card, 10); } else rememberRecyclerPosition(card, section); } });
        card.setOnClickListener(v -> { if (scroll != null) navigation.rememberScroll(section, scroll.getScrollY()); else rememberRecyclerPosition(card, section); showDetails(item); }); return card;
    }

    private void rememberRecyclerPosition(View view, String section) {
        View child = view; ViewGroup parent = view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
        while (parent != null && !(parent instanceof RecyclerView)) { child = parent; parent = parent.getParent() instanceof ViewGroup ? (ViewGroup) parent.getParent() : null; }
        if (parent instanceof RecyclerView) { RecyclerView recycler = (RecyclerView) parent; int position = recycler.getChildAdapterPosition(child); if (position != RecyclerView.NO_POSITION) navigation.rememberScroll(section, position); }
    }

    private void clearCatalogImages(View view) {
        if (view instanceof ImageView) Glide.with(this).clear(view);
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) clearCatalogImages(group.getChildAt(i)); }
    }

    private void wireCatalogFocus(LinearLayout grid, Button filterButton) {
        if (grid.getChildCount() == 0 || !(grid.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout firstRow = (LinearLayout) grid.getChildAt(0); if (firstRow.getChildCount() == 0) return;
        View firstCard = firstRow.getChildAt(0); firstCard.setId(View.generateViewId()); filterButton.setNextFocusDownId(firstCard.getId());
        for (int i = 0; i < firstRow.getChildCount(); i++) firstRow.getChildAt(i).setNextFocusUpId(filterButton.getId());
    }

    private List<JSONObject> applyCatalogFilters(List<JSONObject> source) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject item : source) {
            JSONObject u = item.optJSONObject("UserData"); boolean played = u != null && u.optBoolean("Played"), started = u != null && u.optLong("PlaybackPositionTicks") > 0 && !played, favorite = u != null && u.optBoolean("IsFavorite");
            if ("unwatched".equals(catalogFilter) && (played || started)) continue; if ("started".equals(catalogFilter) && !started) continue; if ("favorites".equals(catalogFilter) && !favorite) continue;
            if (!"all".equals(catalogGenre)) { JSONArray genres = item.optJSONArray("Genres"); boolean match = false; if (genres != null) for (int i = 0; i < genres.length(); i++) if (catalogGenre.equals(genres.optString(i))) { match = true; break; } if (!match) continue; }
            result.add(item);
        }
        Comparator<JSONObject> comparator;
        if ("name".equals(catalogSort)) comparator = Comparator.comparing(o -> o.optString("SortName", o.optString("Name")).toLowerCase(Locale.ROOT));
        else if ("rating".equals(catalogSort)) comparator = (a, b) -> Double.compare(b.optDouble("CommunityRating", 0), a.optDouble("CommunityRating", 0));
        else if ("year".equals(catalogSort)) comparator = (a, b) -> Integer.compare(b.optInt("ProductionYear", 0), a.optInt("ProductionYear", 0));
        else comparator = (a, b) -> b.optString("DateCreated").compareTo(a.optString("DateCreated"));
        Collections.sort(result, comparator); return result;
    }

    private List<String> catalogGenres(List<JSONObject> items) { Set<String> values = new LinkedHashSet<>(); values.add("all"); List<String> found = new ArrayList<>(); for (JSONObject item : items) { JSONArray a = item.optJSONArray("Genres"); if (a != null) for (int i = 0; i < a.length(); i++) if (!a.optString(i).isEmpty()) found.add(a.optString(i)); } Collections.sort(found, String.CASE_INSENSITIVE_ORDER); values.addAll(found); return new ArrayList<>(values); }
    private String sortLabel() { return s(R.string.sort_by, "name".equals(catalogSort) ? s(R.string.sort_name) : "rating".equals(catalogSort) ? s(R.string.sort_rating) : "year".equals(catalogSort) ? s(R.string.sort_year) : s(R.string.sort_recent)); }
    private String filterLabel() { return s(R.string.filter_by, "unwatched".equals(catalogFilter) ? s(R.string.unwatched) : "started".equals(catalogFilter) ? s(R.string.started) : "favorites".equals(catalogFilter) ? s(R.string.my_list) : s(R.string.all_items)); }
    private String genreLabel() { return s(R.string.genre_by, "all".equals(catalogGenre) ? s(R.string.all_genres) : catalogGenre); }
    private String catalogSummary() {
        String status = "all".equals(catalogFilter) ? "" : "  ·  " + ("unwatched".equals(catalogFilter) ? s(R.string.unwatched) : "started".equals(catalogFilter) ? s(R.string.started) : s(R.string.my_list));
        String genre = "all".equals(catalogGenre) ? "" : "  ·  " + catalogGenre;
        return ("name".equals(catalogSort) ? s(R.string.sort_name) : "rating".equals(catalogSort) ? s(R.string.sort_rating) : "year".equals(catalogSort) ? s(R.string.sort_year) : s(R.string.sort_recent)) + status + genre;
    }

    private void showCatalogFilters(List<String> genres, Runnable apply, View returnFocus) {
        String[] pending = {catalogSort, catalogFilter, catalogGenre};
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(25), dp(20), dp(25), dp(18)); panel.setBackground(rounded(Color.rgb(17, 22, 34), 15));
        panel.addView(text(s(R.string.filter_and_sort), 22, Color.WHITE, true));
        TextView helper = text(s(R.string.filter_panel_helper), 12, MUTED, false); helper.setPadding(0, dp(3), 0, dp(10)); panel.addView(helper);
        ScrollView optionScroll = new ScrollView(this); optionScroll.setVerticalScrollBarEnabled(false); optionScroll.setClipToPadding(false);
        LinearLayout options = new LinearLayout(this); options.setOrientation(LinearLayout.VERTICAL); options.setPadding(0, 0, dp(5), dp(8)); optionScroll.addView(options);
        List<Button> optionButtons = new ArrayList<>();
        addCatalogFilterSection(options, optionButtons, s(R.string.order), "sort", new String[][] {{"recent", s(R.string.sort_recent)}, {"name", s(R.string.sort_name)}, {"rating", s(R.string.sort_rating)}, {"year", s(R.string.sort_year)}}, pending);
        addCatalogFilterSection(options, optionButtons, s(R.string.status), "status", new String[][] {{"all", s(R.string.all_items)}, {"unwatched", s(R.string.unwatched)}, {"started", s(R.string.started)}, {"favorites", s(R.string.my_list)}}, pending);
        String[][] genreOptions = new String[genres.size()][2]; for (int i = 0; i < genres.size(); i++) { genreOptions[i][0] = genres.get(i); genreOptions[i][1] = "all".equals(genres.get(i)) ? s(R.string.all_genres) : genres.get(i); }
        addCatalogFilterSection(options, optionButtons, s(R.string.genre), "genre", genreOptions, pending);
        for (Button option : optionButtons) option.setOnClickListener(v -> { String[] value = String.valueOf(v.getTag()).split("\\|", 2); if ("sort".equals(value[0])) pending[0] = value[1]; else if ("status".equals(value[0])) pending[1] = value[1]; else pending[2] = value[1]; refreshCatalogFilterOptions(optionButtons, pending); });
        panel.addView(optionScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); actions.setPadding(0, dp(10), 0, 0);
        Button reset = actionButton(s(R.string.reset), false); reset.setId(View.generateViewId()); Button confirm = actionButton(s(R.string.apply), true); confirm.setId(View.generateViewId()); actions.addView(reset, new LinearLayout.LayoutParams(dp(135), dp(44))); LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(dp(135), dp(44)); confirmParams.leftMargin = dp(9); actions.addView(confirm, confirmParams); panel.addView(actions);
        for (Button option : optionButtons) option.setNextFocusRightId(confirm.getId()); reset.setNextFocusRightId(confirm.getId()); confirm.setNextFocusLeftId(reset.getId());
        reset.setOnClickListener(v -> { pending[0] = "recent"; pending[1] = "all"; pending[2] = "all"; refreshCatalogFilterOptions(optionButtons, pending); });
        confirm.setOnClickListener(v -> { catalogSort = pending[0]; catalogFilter = pending[1]; catalogGenre = pending[2]; dialog.dismiss(); apply.run(); });
        dialog.setContentView(panel); dialog.setOnDismissListener(v -> returnFocus.requestFocus()); dialog.show();
        Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams params = new WindowManager.LayoutParams(); params.copyFrom(window.getAttributes()); params.width = dp(520); params.height = dp(430); params.dimAmount = .76f; window.setAttributes(params); }
        if (!optionButtons.isEmpty()) optionButtons.get(0).requestFocus();
    }

    private void addCatalogFilterSection(LinearLayout parent, List<Button> buttons, String title, String group, String[][] values, String[] pending) {
        TextView heading = text(title.toUpperCase(Locale.getDefault()), 10, ACCENT, true); heading.setPadding(0, buttons.isEmpty() ? dp(2) : dp(13), 0, dp(4)); parent.addView(heading);
        for (String[] value : values) { Button option = catalogFilterOption(value[1]); option.setTag(group + "|" + value[0]); buttons.add(option); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)); p.bottomMargin = dp(3); parent.addView(option, p); }
        refreshCatalogFilterOptions(buttons, pending);
    }

    private Button catalogFilterOption(String label) {
        Button button = new Button(this); button.setContentDescription(label); button.setAllCaps(false); button.setTextSize(13); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); button.setPadding(dp(16), 0, dp(12), 0); button.setFocusable(true);
        button.setOnFocusChangeListener((v, focused) -> { boolean selected = button.isActivated(); button.setTextColor(focused ? BG : selected ? Color.WHITE : Color.rgb(220, 224, 234)); button.setBackground(stroked(focused ? Color.WHITE : selected ? PRIMARY : Color.TRANSPARENT, focused ? Color.WHITE : selected ? Color.rgb(41, 35, 72) : Color.TRANSPARENT, 8, focused || selected ? 1 : 0)); }); return button;
    }

    private void refreshCatalogFilterOptions(List<Button> buttons, String[] pending) {
        for (Button button : buttons) { String[] value = String.valueOf(button.getTag()).split("\\|", 2); boolean selected = "sort".equals(value[0]) ? pending[0].equals(value[1]) : "status".equals(value[0]) ? pending[1].equals(value[1]) : pending[2].equals(value[1]); button.setActivated(selected); button.setText((selected ? "\u2713  " : "    ") + button.getContentDescription()); if (!button.hasFocus()) { button.setTextColor(selected ? Color.WHITE : Color.rgb(220, 224, 234)); button.setBackground(stroked(selected ? PRIMARY : Color.TRANSPARENT, selected ? Color.rgb(41, 35, 72) : Color.TRANSPARENT, 8, selected ? 1 : 0)); } }
    }

    private LinearLayout topNavigation(String selected) {
        activeNavigationButton = null; browsePrimaryAction = null;
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL); nav.setPadding(dp(30), 0, dp(28), 0); nav.setBackgroundColor(Color.rgb(8, 11, 19)); nav.setElevation(dp(6));
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL); brand.addView(brandMark(), new LinearLayout.LayoutParams(dp(29), dp(29))); TextView logo = text(AppIdentity.WORDMARK, 17, Color.WHITE, true); LinearLayout.LayoutParams logoText = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); logoText.leftMargin = dp(8); brand.addView(logo, logoText); nav.addView(brand, new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.MATCH_PARENT));
        nav.addView(navButton(s(R.string.home), "home", selected)); nav.addView(navButton(s(R.string.movies), "movies", selected)); nav.addView(navButton(s(R.string.series), "series", selected)); nav.addView(navButton(s(R.string.my_tutaua), "favorites", selected));
        View spacer = new View(this); nav.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        nav.addView(navButton("⌕  " + s(R.string.search), "search", selected));
        nav.addView(navButton("●  " + s(R.string.profile), "profile", selected));
        return nav;
    }

    private Button navButton(String label, String section, String selected) {
        Button b = new Button(this); b.setText(label); b.setTextSize(13); b.setTextColor(section.equals(selected) ? Color.WHITE : MUTED); b.setAllCaps(false); b.setFocusable(true); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(section.equals(selected) ? rounded(Color.rgb(24, 29, 43), 7) : rounded(Color.TRANSPARENT, 7));
        int width = "search".equals(section) ? 105 : "profile".equals(section) ? 120 : "favorites".equals(section) ? 135 : 90;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(width), dp(40)); p.rightMargin = dp(5); b.setLayoutParams(p);
        if (section.equals(selected)) activeNavigationButton = b;
        b.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && browsePrimaryAction != null) { if (activeBrowseScroll != null) activeBrowseScroll.scrollTo(0, 0); browsePrimaryAction.requestFocus(); return true; } return false; });
        b.setOnFocusChangeListener((v, focused) -> { b.setTextColor(focused || section.equals(selected) ? Color.WHITE : MUTED); b.setBackground(focused ? stroked(Color.WHITE, Color.rgb(38, 44, 61), 7, 2) : section.equals(selected) ? rounded(Color.rgb(24, 29, 43), 7) : rounded(Color.TRANSPARENT, 7)); });
        b.setOnClickListener(v -> { navigation.resetFocus(); if ("search".equals(section)) showSearch(); else if ("profile".equals(section)) showProfile(); else if (!section.equals(navigation.section())) showBrowse(section); }); return b;
    }

    private void showProfile() {
        navigation.section("profile"); newRoot(); root.addView(topNavigation("profile"), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(70), dp(46), dp(70), 0); root.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        page.addView(text(s(R.string.profile), 29, Color.WHITE, true)); TextView intro = text(s(R.string.profile_intro), 14, MUTED, false); intro.setPadding(0, dp(4), 0, dp(24)); page.addView(intro);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(30), dp(25), dp(30), dp(25)); card.setBackground(rounded(SURFACE, 14));
        card.addView(profileLine(s(R.string.username), prefs.getString("username", s(R.string.username_hint)))); card.addView(profileLine(s(R.string.server), server)); card.addView(profileLine(s(R.string.device), "Android TV · " + android.os.Build.MODEL)); card.addView(profileLine(s(R.string.version), AppIdentity.NAME + " " + AppIdentity.VERSION));
        page.addView(card, new LinearLayout.LayoutParams(dp(650), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView settingsLabel = text(s(R.string.playback_preferences), 11, MUTED, true); settingsLabel.setPadding(0, dp(20), 0, dp(8)); page.addView(settingsLabel);
        LinearLayout settings = new LinearLayout(this); Button autoplay = actionButton(s(R.string.next_episode_setting, prefs.getBoolean("autoplay", true) ? s(R.string.yes) : s(R.string.no)), false); settings.addView(autoplay, new LinearLayout.LayoutParams(dp(220), dp(44))); autoplay.setOnClickListener(v -> { boolean enabled = !prefs.getBoolean("autoplay", true); prefs.edit().putBoolean("autoplay", enabled).apply(); autoplay.setText(s(R.string.next_episode_setting, enabled ? s(R.string.yes) : s(R.string.no))); });
        Button subtitleSize = actionButton(s(R.string.subtitles_setting, subtitleSizeLabel()), false); LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(dp(190), dp(44)); subtitleParams.leftMargin = dp(9); settings.addView(subtitleSize, subtitleParams); subtitleSize.setOnClickListener(v -> { int current = prefs.getInt("subtitle_size", 100); int next = current < 100 ? 100 : current < 115 ? 115 : 90; prefs.edit().putInt("subtitle_size", next).apply(); subtitleSize.setText(s(R.string.subtitles_setting, subtitleSizeLabel())); });
        Button language = actionButton(s(R.string.language, languageLabel()), false); LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(dp(180), dp(44)); languageParams.leftMargin = dp(9); settings.addView(language, languageParams); language.setOnClickListener(v -> selectNextLanguage());
        Button disconnect = actionButton(s(R.string.sign_out), false); LinearLayout.LayoutParams disconnectParams = new LinearLayout.LayoutParams(dp(150), dp(44)); disconnectParams.leftMargin = dp(9); settings.addView(disconnect, disconnectParams); disconnect.setOnClickListener(v -> logout()); page.addView(settings); autoplay.requestFocus(); backAction = () -> showBrowse("home");
    }

    private LinearLayout profileLine(String label, String value) { LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL); TextView key = text(label, 11, ACCENT, true); line.addView(key, new LinearLayout.LayoutParams(dp(145), dp(45))); TextView content = text(value, 15, Color.WHITE, false); content.setGravity(Gravity.CENTER_VERTICAL); line.addView(content, new LinearLayout.LayoutParams(0, dp(45), 1)); return line; }
    private String subtitleSizeLabel() { int size = prefs.getInt("subtitle_size", 100); return size < 100 ? s(R.string.small) : size > 100 ? s(R.string.large) : s(R.string.medium); }

    private void renderBrowse(LinearLayout content, List<JSONObject> items, List<JSONObject> resume, List<JSONObject> nextUp, String section) {
        content.removeAllViews();
        browsePrimaryAction = null; browseSecondaryAction = null;
        if (items.isEmpty()) { content.addView(emptyCatalog(section)); return; }
        List<JSONObject> movies = filterType(items, "Movie");
        List<JSONObject> series = filterType(items, "Series");
        List<JSONObject> top = new ArrayList<>(items); Collections.sort(top, (a, b) -> Double.compare(b.optDouble("CommunityRating", 0), a.optDouble("CommunityRating", 0)));
        List<JSONObject> continueItems = mergeContinue(resume, nextUp);
        List<JSONObject> favorites = filterFavorites(items);
        List<JSONObject> unseen = filterUnseen(items);
        List<JSONObject> featuredCandidates = unseen.isEmpty() ? items : unseen;
        JSONObject featured = "movies".equals(section) ? chooseFeaturedExcluding(featuredCandidates, homeFeaturedId) : chooseFeatured(featuredCandidates);
        if ("home".equals(section)) homeFeaturedId = featured.optString("Id");
        if (!"favorites".equals(section)) content.addView(hero(featured), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
        Set<String> used = new LinkedHashSet<>(); if (!"favorites".equals(section)) used.add(editorialKey(featured));
        if ("home".equals(section)) {
            if (!continueItems.isEmpty()) { content.addView(sectionRow(s(R.string.continue_watching), s(R.string.continue_hub_subtitle), continueItems, true)); markEditorial(used, continueItems); }
            List<JSONObject> freshMovies = excludeContinued(movies, continueItems), freshSeries = excludeContinued(series, continueItems);
            addUniqueRow(content, s(R.string.recent_movies), s(R.string.library_new), freshMovies, false, used, 5);
            addUniqueRow(content, s(R.string.binge_series), s(R.string.another_episode), freshSeries, false, used, 5);
            String featuredGenre = mostCommonGenre(resume.isEmpty() ? items : resume); addUniqueRow(content, s(R.string.because_you_watch, featuredGenre), s(R.string.based_on_started), filterGenre(items, featuredGenre), false, used, 4);
            addUniqueRow(content, s(R.string.my_tutaua), s(R.string.personal_selection), favorites, false, used, 4);
            addUniqueRow(content, s(R.string.top_rated), s(R.string.top_rated_subtitle), top, false, used, 5);
        } else if ("movies".equals(section)) {
            if (!resume.isEmpty()) { content.addView(sectionRow(s(R.string.continue_watching), s(R.string.resume_movies), resume, true)); markEditorial(used, resume); }
            addUniqueRow(content, s(R.string.recently_added), s(R.string.latest_additions), movies, false, used, 5);
            addUniqueRow(content, s(R.string.unwatched), s(R.string.unwatched_subtitle), unseen, false, used, 5);
            addUniqueRow(content, s(R.string.best_rated), s(R.string.ratings_selection), top, false, used, 5);
            addGenreRows(content, movies, 3, used);
            content.addView(catalogEntry(section, movies.size()));
        } else if ("series".equals(section)) {
            if (!nextUp.isEmpty()) { content.addView(sectionRow(s(R.string.next_episode), s(R.string.next_episode_subtitle), nextUp, true)); markEditorial(used, nextUp); }
            addUniqueRow(content, s(R.string.recently_added), s(R.string.latest_additions), series, false, used, 5);
            addUniqueRow(content, s(R.string.unwatched), s(R.string.unwatched_subtitle), unseen, false, used, 5);
            addUniqueRow(content, s(R.string.best_rated), s(R.string.ratings_selection), top, false, used, 5);
            addGenreRows(content, series, 3, used);
            content.addView(catalogEntry(section, series.size()));
        } else {
            content.addView(editorialHeader(s(R.string.my_tutaua), s(R.string.my_tutaua_intro), section));
            if (!continueItems.isEmpty()) { content.addView(sectionRow(s(R.string.continue_watching), s(R.string.continue_hub_subtitle), continueItems, true)); markEditorial(used, continueItems); }
            addUniqueRow(content, s(R.string.favorites_title), s(R.string.personal_selection), favorites, false, used, 3);
            addUniqueRow(content, s(R.string.started), s(R.string.started_collection), filterStarted(items), false, used, 3);
            addUniqueRow(content, s(R.string.watch_again), s(R.string.watch_again_subtitle), filterPlayed(items), false, used, 4);
        }
        View bottom = new View(this); content.addView(bottom, new LinearLayout.LayoutParams(1, dp(42)));
        if (navigation.consumeRestore() && navigation.focusedMediaId() != null) content.post(() -> { View target = content.findViewWithTag("media:" + navigation.focusedMediaId()); if (target != null) target.requestFocus(); });
    }

    private View editorialHeader(String title, String subtitle, String section) {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(46), dp(7), dp(42), 0);
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.addView(text(title, 21, Color.WHITE, true)); labels.addView(text(subtitle, 11, MUTED, false)); bar.addView(labels, new LinearLayout.LayoutParams(0, dp(46), 1));
        return bar;
    }

    private void addGenreRows(LinearLayout content, List<JSONObject> source, int maximum, Set<String> used) {
        List<String> genres = catalogGenres(source); int added = 0;
        for (String genre : genres) { if ("all".equals(genre)) continue; if (addUniqueRow(content, genre, s(R.string.genre_collection), filterGenre(source, genre), false, used, 4)) if (++added >= maximum) break; }
    }

    private boolean addUniqueRow(LinearLayout content, String title, String subtitle, List<JSONObject> source, boolean landscape, Set<String> used, int minimum) {
        List<JSONObject> unique = new ArrayList<>(); for (JSONObject item : source) if (!used.contains(editorialKey(item))) unique.add(item);
        if (unique.size() < minimum) return false; content.addView(sectionRow(title, subtitle, unique, landscape)); markEditorial(used, unique.subList(0, Math.min(24, unique.size()))); return true;
    }

    private String editorialKey(JSONObject item) { String seriesId = item.optString("SeriesId"); return seriesId.isEmpty() ? item.optString("Id") : "series:" + seriesId; }
    private void markEditorial(Set<String> used, List<JSONObject> items) { for (JSONObject item : items) used.add(editorialKey(item)); }

    private View catalogEntry(String section, int count) {
        LinearLayout entry = new LinearLayout(this); entry.setGravity(Gravity.CENTER_VERTICAL); entry.setFocusable(true); entry.setClickable(true); entry.setPadding(dp(24), 0, dp(22), 0); entry.setBackground(rounded(SURFACE, 11));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.addView(text(s(R.string.explore_full_catalog), 17, Color.WHITE, true)); words.addView(text(s(R.string.catalog_entry_subtitle, count), 11, MUTED, false)); entry.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); TextView arrow = text("›", 28, MUTED, false); arrow.setGravity(Gravity.CENTER); entry.addView(arrow, new LinearLayout.LayoutParams(dp(42), dp(48)));
        entry.setOnFocusChangeListener((v, focused) -> { entry.setBackground(stroked(focused ? Color.WHITE : SURFACE, focused ? Color.rgb(29, 35, 51) : SURFACE, 11, focused ? 2 : 0)); arrow.setTextColor(focused ? Color.WHITE : MUTED); if (focused) ensureFullyVisible(entry, 8); }); entry.setOnClickListener(v -> showCatalog(section));
        LinearLayout wrapper = new LinearLayout(this); wrapper.setPadding(dp(52), dp(13), dp(52), dp(22)); wrapper.addView(entry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))); return wrapper;
    }

    private View emptyCatalog(String section) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(40), dp(105), dp(40), 0);
        box.addView(text("favorites".equals(section) ? "♡" : "◇", 42, ACCENT, false));
        TextView title = text("favorites".equals(section) ? s(R.string.empty_favorites) : s(R.string.empty_catalog), 25, Color.WHITE, true); title.setPadding(0, dp(12), 0, dp(8)); box.addView(title);
        TextView message = text("favorites".equals(section) ? s(R.string.empty_favorites_message) : s(R.string.empty_catalog_message), 15, MUTED, false); box.addView(message);
        Button action = actionButton("favorites".equals(section) ? s(R.string.explore_catalog) : s(R.string.try_again), true); LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(190), dp(46)); ap.topMargin = dp(22); box.addView(action, ap); action.setOnClickListener(v -> showBrowse("favorites".equals(section) ? "home" : section)); action.requestFocus(); return box;
    }

    private FrameLayout hero(JSONObject item) {
        FrameLayout hero = new FrameLayout(this);
        ImageView backdrop = new AdaptiveBannerImageView(); hero.addView(backdrop, match());
        loadBannerImage(backdrop, item.optString("Id"), 1600);
        View shade = new View(this); shade.setBackground(horizontalGradient(Color.argb(252, 7, 10, 18), Color.argb(105, 7, 10, 18), Color.argb(60, 7, 10, 18))); hero.addView(shade, match());
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(52), dp(13), 0, 0);
        TextView eyebrow = text(s(R.string.featured), 11, ACCENT, true); info.addView(eyebrow);
        TextView name = text(item.optString("Name"), 25, Color.WHITE, true); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(610), dp(36)); np.topMargin = dp(3); info.addView(name, np);
        ImageView titleLogo = new ImageView(this); titleLogo.setScaleType(ImageView.ScaleType.FIT_START); titleLogo.setVisibility(View.GONE); LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(360), dp(48)); logoParams.topMargin = dp(2); info.addView(titleLogo, logoParams); loadOptionalLogo(titleLogo, name, item.optString("Id"));
        TextView meta = text(metadataLine(item), 12, MUTED, false); meta.setSingleLine(true); meta.setEllipsize(TextUtils.TruncateAt.END); meta.setPadding(0, dp(2), 0, dp(3)); info.addView(meta);
        TextView overview = text(item.optString("Overview", s(R.string.default_overview)), 12, Color.rgb(224, 228, 238), false); overview.setMaxLines(2); overview.setEllipsize(TextUtils.TruncateAt.END); info.addView(overview, new LinearLayout.LayoutParams(dp(610), dp(34)));
        String primaryLabel = "Series".equals(item.optString("Type")) ? s(R.string.view_episodes) : resumePositionMs(item) > 0 ? s(R.string.continue_action) : s(R.string.play_action);
        LinearLayout actions = new LinearLayout(this); Button playNow = actionButton(primaryLabel, true); Button details = actionButton(s(R.string.more_information), false); playNow.setId(View.generateViewId()); details.setId(View.generateViewId()); browsePrimaryAction = playNow; browseSecondaryAction = details; playNow.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP && activeNavigationButton != null) { activeNavigationButton.requestFocus(); return true; } return false; }); details.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP && activeNavigationButton != null) { activeNavigationButton.requestFocus(); return true; } return false; }); actions.addView(playNow, new LinearLayout.LayoutParams(dp(174), dp(42))); LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(dp(174), dp(42)); detailsParams.leftMargin = dp(9); actions.addView(details, detailsParams); LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)); actionParams.topMargin = dp(7); info.addView(actions, actionParams);
        playNow.setOnClickListener(v -> { if ("Series".equals(item.optString("Type"))) showEpisodes(item); else play(item.optString("Id"), mediaSourceId(item), item.optString("Name"), resumePositionMs(item), () -> showBrowse(navigation.section())); }); details.setOnClickListener(v -> showDetails(item));
        hero.addView(info, new FrameLayout.LayoutParams(dp(760), ViewGroup.LayoutParams.MATCH_PARENT)); if (activeNavigationButton != null) activeNavigationButton.post(activeNavigationButton::requestFocus); return hero;
    }

    private LinearLayout sectionRow(String heading, String subtitle, List<JSONObject> source, boolean landscape) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL); section.setPadding(dp(46), dp(8), 0, dp(3)); section.setClipChildren(false); section.setClipToPadding(false);
        section.addView(text(heading, 18, Color.WHITE, true)); TextView sub = text(subtitle, 11, MUTED, false); sub.setSingleLine(true); sub.setEllipsize(TextUtils.TruncateAt.END); sub.setPadding(0, 0, dp(46), dp(6)); section.addView(sub);
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(dp(10), dp(6), dp(65), dp(8)); row.setClipChildren(false); scroll.addView(row);
        boolean firstBrowseRow = browsePrimaryAction != null && browsePrimaryAction.getNextFocusDownId() == View.NO_ID;
        int count = Math.min(source.size(), 24); View firstCard = null; for (int i = 0; i < count; i++) { View card = mediaCard(source.get(i), landscape, sub); card.setId(View.generateViewId()); if (firstCard == null) firstCard = card; if (firstBrowseRow) { card.setNextFocusUpId(browsePrimaryAction.getId()); card.setOnKeyListener((v, keyCode, event) -> { if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) { if (activeBrowseScroll != null) activeBrowseScroll.scrollTo(0, 0); browsePrimaryAction.requestFocus(); return true; } return false; }); } row.addView(card); }
        if (firstBrowseRow && firstCard != null) { browsePrimaryAction.setNextFocusDownId(firstCard.getId()); if (browseSecondaryAction != null) browseSecondaryAction.setNextFocusDownId(firstCard.getId()); }
        // Keep enough vertical room for the complete card (artwork, title and metadata),
        // the row padding, and the small focus scale used on TV. The old fixed heights
        // were shorter than their children, so the bottom of poster cards was clipped.
        section.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(landscape ? 194 : 230))); return section;
    }

    private void keepBrowseBlockVisible(LinearLayout content, ScrollView scroll, View focused) {
        if (focused == null || !isDescendantOf(focused, content)) return;
        View block = focused;
        while (block.getParent() instanceof View && block.getParent() != content) block = (View) block.getParent();
        if (block.getParent() != content) return;
        final View focusBlock = block;
        scroll.post(() -> {
            int[] blockLocation = new int[2], scrollLocation = new int[2]; focusBlock.getLocationOnScreen(blockLocation); scroll.getLocationOnScreen(scrollLocation);
            int margin = dp(9), top = blockLocation[1] - margin, bottom = blockLocation[1] + focusBlock.getHeight() + margin;
            int viewportTop = scrollLocation[1], viewportBottom = viewportTop + scroll.getHeight();
            int delta = bottom > viewportBottom ? bottom - viewportBottom : top < viewportTop ? top - viewportTop : 0;
            if (delta != 0) scroll.scrollBy(0, delta);
        });
    }

    private boolean isDescendantOf(View view, ViewGroup ancestor) { android.view.ViewParent parent = view.getParent(); while (parent != null) { if (parent == ancestor) return true; parent = parent.getParent(); } return false; }

    private View mediaCard(JSONObject item, boolean landscape, TextView contextualSubtitle) {
        int width = dp(landscape ? 210 : 110), imageHeight = dp(landscape ? 118 : 154);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setFocusable(true); card.setClickable(true); card.setClipChildren(false); card.setClipToPadding(false); card.setPadding(dp(5), dp(5), dp(5), dp(3)); card.setBackground(rounded(Color.TRANSPARENT, 12));
        FrameLayout artwork = new FrameLayout(this); artwork.setClipChildren(false); artwork.setClipToPadding(false); artwork.setPadding(dp(3), dp(3), dp(3), dp(3)); artwork.setBackground(stroked(Color.TRANSPARENT, SURFACE, 10, 0));
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setClipToOutline(true); image.setBackground(rounded(SURFACE, 8)); artwork.addView(image, match()); card.addView(artwork, new LinearLayout.LayoutParams(width, imageHeight));
        loadImage(image, item.optString("Id"), landscape ? "Backdrop" : "Primary", landscape ? 600 : 350);
        if (landscape) addProgress(artwork, item, width);
        String cardTitle = item.optString("Name"); if ("Episode".equals(item.optString("Type")) && !item.optString("SeriesName").isEmpty()) cardTitle = item.optString("SeriesName") + " · " + cardTitle;
        TextView name = text(cardTitle, 10, Color.rgb(232, 235, 243), true); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); name.setLineSpacing(0, .94f); name.setPadding(dp(1), dp(3), dp(1), 0); card.addView(name, new LinearLayout.LayoutParams(width, dp(30)));
        TextView cardMeta = text(cardMetadata(item), 9, MUTED, false); cardMeta.setMaxLines(1); cardMeta.setEllipsize(TextUtils.TruncateAt.END); cardMeta.setPadding(dp(1), 0, dp(1), 0); card.addView(cardMeta, new LinearLayout.LayoutParams(width, dp(16)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(width + dp(10), ViewGroup.LayoutParams.WRAP_CONTENT); cp.rightMargin = dp(10); card.setLayoutParams(cp);
        card.setTag("media:" + item.optString("Id")); card.setOnFocusChangeListener((v, focused) -> { artwork.animate().translationZ(focused ? dp(7) : 0).scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(145).start(); artwork.setBackground(stroked(focused ? Color.WHITE : Color.TRANSPARENT, focused ? Color.rgb(29, 35, 51) : SURFACE, 10, focused ? 3 : 0)); name.setTextColor(focused ? Color.WHITE : Color.rgb(232, 235, 243)); cardMeta.setTextColor(focused ? Color.rgb(210, 216, 230) : MUTED); if (focused) { contextualSubtitle.setText(focusContext(item)); navigation.rememberMedia(item.optString("Id")); ensureFullyVisible(card, 10); } });
        card.setOnClickListener(v -> showDetails(item)); return card;
    }

    private String cardMetadata(JSONObject item) {
        if ("Episode".equals(item.optString("Type"))) { int season = item.optInt("ParentIndexNumber", -1), episode = item.optInt("IndexNumber", -1); return season >= 0 && episode >= 0 ? "T" + season + " · E" + episode + "  ·  " + formatRuntime(item.optLong("RunTimeTicks")) : formatRuntime(item.optLong("RunTimeTicks")); }
        List<String> parts = new ArrayList<>(); if (item.optInt("ProductionYear", 0) > 0) parts.add(String.valueOf(item.optInt("ProductionYear"))); if (item.optLong("RunTimeTicks") > 0) parts.add(formatRuntime(item.optLong("RunTimeTicks"))); if (item.optDouble("CommunityRating", 0) > 0) parts.add("★ " + String.format(Locale.getDefault(), "%.1f", item.optDouble("CommunityRating"))); return TextUtils.join("  ·  ", parts);
    }

    private String focusContext(JSONObject item) {
        List<String> parts = new ArrayList<>();
        String metadata = cardMetadata(item); if (!metadata.isEmpty()) parts.add(metadata);
        JSONArray genres = item.optJSONArray("Genres");
        if (genres != null) for (int i = 0; i < Math.min(2, genres.length()); i++) { String genre = genres.optString(i); if (!genre.isEmpty()) parts.add(genre); }
        if (parts.isEmpty() && "Episode".equals(item.optString("Type")) && !item.optString("SeriesName").isEmpty()) parts.add(item.optString("SeriesName"));
        return TextUtils.join("  ·  ", parts);
    }

    private void addProgress(FrameLayout artwork, JSONObject item, int width) {
        JSONObject data = item.optJSONObject("UserData"); if (data == null) return;
        long pos = data.optLong("PlaybackPositionTicks"), total = item.optLong("RunTimeTicks"); if (pos <= 0 || total <= 0) return;
        int usableWidth = width - dp(16); FrameLayout track = new FrameLayout(this); track.setBackground(rounded(Color.argb(190, 28, 32, 44), 2));
        View fill = new View(this); fill.setBackground(rounded(ACCENT, 2)); track.addView(fill, new FrameLayout.LayoutParams((int) (usableWidth * Math.min(1d, pos / (double) total)), dp(3)));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(usableWidth, dp(3), Gravity.BOTTOM | Gravity.LEFT); tp.leftMargin = dp(8); tp.bottomMargin = dp(7); artwork.addView(track, tp);
    }

    private void showSearch() {
        navigation.section("search");
        newRoot();
        root.addView(topNavigation("search"), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(52), dp(22), dp(42), 0); root.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        page.addView(text(s(R.string.search_title), 25, Color.WHITE, true));
        TextView helper = text(s(R.string.search_hint), 12, MUTED, false); helper.setPadding(0, dp(2), 0, dp(13)); page.addView(helper);
        LinearLayout searchBar = new LinearLayout(this); EditText query = field(s(R.string.search_hint), EditorInfo.IME_ACTION_SEARCH); searchBar.addView(query, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button submit = actionButton(s(R.string.search), true); submit.setId(View.generateViewId()); query.setId(View.generateViewId()); LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(dp(140), dp(52)); sb.leftMargin = dp(10); searchBar.addView(submit, sb); page.addView(searchBar);
        ((TvEditText) query).setKeyboardExitTarget(submit); query.setNextFocusRightId(submit.getId()); submit.setNextFocusLeftId(query.getId());
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); results.setClipChildren(false);
        LinearLayout filters = new LinearLayout(this); filters.setPadding(0, dp(12), 0, 0); String[][] filterOptions = {{s(R.string.all), "All"}, {s(R.string.movies), "Movie"}, {s(R.string.series), "Series"}, {s(R.string.episodes), "Episode"}}; for (String[] option : filterOptions) { Button filter = seasonTabButton(option[0], option[1].equals(searchFilter)); filter.setTag(option[1]); LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(130), dp(38)); fp.rightMargin = dp(8); filters.addView(filter, fp); filter.setOnClickListener(v -> { searchFilter = option[1]; for (int i = 0; i < filters.getChildCount(); i++) { Button child = (Button) filters.getChildAt(i); boolean selected = searchFilter.equals(child.getTag()); child.setActivated(selected); if (!child.hasFocus()) { child.setTextColor(selected ? BG : Color.WHITE); child.setBackground(rounded(selected ? Color.WHITE : SURFACE_LIGHT, 8)); } } renderSearchResults(results, lastSearchResults, query.getText().toString().trim()); }); } page.addView(filters);
        ScrollView resultScroll = new ScrollView(this); resultScroll.setFillViewport(true); resultScroll.setClipToPadding(false); resultScroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1); rp.topMargin = dp(14); page.addView(resultScroll, rp);
        renderSearchStart(results, query);
        View.OnClickListener search = v -> {
            dismissKeyboard(query);
            submit.requestFocus();
            rememberSearch(query.getText().toString());
            performSearch(query.getText().toString(), results);
        };
        submit.setOnClickListener(search);
        query.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                dismissKeyboard(query);
                submit.requestFocus();
                rememberSearch(query.getText().toString());
                performSearch(query.getText().toString(), results);
                return true;
            }
            return false;
        });
        query.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                dismissKeyboard(query);
                submit.requestFocus();
                return true;
            }
            return false;
        });
        query.requestFocus();
        query.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) {} public void onTextChanged(CharSequence s, int start, int before, int count) {} public void afterTextChanged(Editable value) { if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch); String term = value.toString().trim(); if (term.length() < 2) { searchRequestId++; if (term.isEmpty()) renderSearchStart(results, query); else { results.removeAllViews(); TextView hint = text(s(R.string.search_minimum), 17, MUTED, false); hint.setPadding(0, dp(32), 0, 0); results.addView(hint); } return; } pendingSearch = () -> { pendingSearch = null; performSearch(term, results); }; searchHandler.postDelayed(pendingSearch, 650); } });
        backAction = () -> showBrowse("home");
    }

    private void renderSearchStart(LinearLayout results, EditText query) { results.removeAllViews(); List<String> recent = recentSearches(); if (recent.isEmpty()) { TextView empty = text(s(R.string.search_empty), 17, MUTED, false); empty.setPadding(0, dp(45), 0, 0); results.addView(empty); return; } TextView title = text(s(R.string.recent_searches), 16, Color.WHITE, true); title.setPadding(0, dp(20), 0, dp(10)); results.addView(title); LinearLayout row = new LinearLayout(this); for (String term : recent) { Button chip = seasonTabButton(term, false); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)); p.rightMargin = dp(8); row.addView(chip, p); chip.setOnClickListener(v -> { query.setText(term); query.setSelection(term.length()); dismissKeyboard(query); performSearch(term, results); }); } results.addView(row); }
    private List<String> recentSearches() { List<String> values = new ArrayList<>(); String saved = prefs.getString("recent_searches", ""); if (!saved.isEmpty()) Collections.addAll(values, saved.split("\\|", -1)); return values; }
    private void rememberSearch(String raw) { String term = raw.trim(); if (term.length() < 2) return; List<String> values = recentSearches(); for (int i = values.size() - 1; i >= 0; i--) if (values.get(i).equalsIgnoreCase(term)) values.remove(i); values.add(0, term); if (values.size() > 5) values = new ArrayList<>(values.subList(0, 5)); prefs.edit().putString("recent_searches", TextUtils.join("|", values)).apply(); }

    private void performSearch(String term, LinearLayout results) {
        term = term.trim(); if (term.length() < 2) { showError(s(R.string.search_minimum)); return; }
        results.removeAllViews(); ProgressBar p = new ProgressBar(this); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(48), dp(48)); pp.gravity = Gravity.CENTER; pp.topMargin = dp(50); results.addView(p, pp);
        int expected = generation; int requestId = ++searchRequestId; String searchTerm = term;
        io.execute(() -> { try {
            String path = "/Users/" + userId + "/Items?Recursive=true&SearchTerm=" + enc(searchTerm) + "&IncludeItemTypes=Movie,Series,Episode&Limit=36&EnableTotalRecordCount=false&Fields=PrimaryImageAspectRatio,Overview,Genres,BackdropImageTags,MediaSources";
            List<JSONObject> found = jsonList(request(server + path, "GET", null, token).getJSONArray("Items"));
            runOnUiThread(() -> { if (generation == expected && requestId == searchRequestId) { lastSearchResults = found; renderSearchResults(results, found, searchTerm); } });
        } catch (Exception e) { runOnUiThread(() -> { if (generation == expected && requestId == searchRequestId) { results.removeAllViews(); results.addView(errorView(s(R.string.search_error), e)); } }); } });
    }

    private void dismissKeyboard(EditText field) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.clearFocus();
    }

    private void focusFirstSearchResult(LinearLayout results) {
        if (getCurrentFocus() instanceof EditText) return;
        results.post(() -> {
            java.util.ArrayList<View> focusable = results.getFocusables(View.FOCUS_FORWARD);
            if (!focusable.isEmpty()) focusable.get(0).requestFocus();
        });
    }

    private String matches(int count) { return getResources().getQuantityString(R.plurals.matches, count, count); }
    private void renderSearchResults(LinearLayout results, List<JSONObject> found, String searchTerm) { results.removeAllViews(); List<JSONObject> visible = "All".equals(searchFilter) ? found : filterType(found, searchFilter); if (visible.isEmpty()) { TextView no = text(s(R.string.no_results, searchTerm, "All".equals(searchFilter) ? "" : s(R.string.in_filter)), 18, MUTED, false); no.setPadding(0, dp(40), 0, 0); results.addView(no); return; } if (!"All".equals(searchFilter)) { results.addView(sectionRow(searchFilterTitle(searchFilter), matches(visible.size()), visible, "Episode".equals(searchFilter))); return; } List<JSONObject> movies = filterType(visible, "Movie"), series = filterType(visible, "Series"), episodes = filterType(visible, "Episode"); if (!movies.isEmpty()) results.addView(sectionRow(s(R.string.movies), matches(movies.size()), movies, false)); if (!series.isEmpty()) results.addView(sectionRow(s(R.string.series), matches(series.size()), series, false)); if (!episodes.isEmpty()) results.addView(sectionRow(s(R.string.episodes), matches(episodes.size()), episodes, true)); }

    private String searchFilterTitle(String filter) { return "Movie".equals(filter) ? s(R.string.movies) : "Series".equals(filter) ? s(R.string.series) : s(R.string.episodes); }

    private void showDetails(JSONObject item) {
        newRoot();
        backAction = this::returnToCurrentSection;
        FrameLayout page = new FrameLayout(this); root.addView(page, match());
        ImageView backdrop = new ImageView(this); backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP); page.addView(backdrop, match());
        loadImage(backdrop, item.optString("Id"), "Backdrop", 1280);
        View overlay = new View(this); overlay.setBackground(horizontalGradient(Color.argb(255, 7, 10, 18), Color.argb(205, 7, 10, 18), Color.argb(85, 7, 10, 18))); page.addView(overlay, match());
        ScrollView detailScroll = new ScrollView(this); detailScroll.setFillViewport(true); detailScroll.setClipToPadding(false); LinearLayout detailBody = new LinearLayout(this); detailBody.setOrientation(LinearLayout.VERTICAL); detailBody.setClipChildren(false); detailScroll.addView(detailBody, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); page.addView(detailScroll, match());
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(70), dp(32), 0, dp(24));
        String itemType = item.optString("Type"); String kind = "Series".equals(itemType) ? s(R.string.series_kind) : "Episode".equals(itemType) ? s(R.string.episode_kind) : s(R.string.movie_kind);
        TextView eyebrow = text(kind, 12, Color.rgb(202, 208, 222), true); eyebrow.setLetterSpacing(.12f); info.addView(eyebrow);
        TextView name = text(item.optString("Name"), 32, Color.WHITE, true); LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(760), ViewGroup.LayoutParams.WRAP_CONTENT); np.topMargin = dp(13); info.addView(name, np);
        ImageView titleLogo = new ImageView(this); titleLogo.setScaleType(ImageView.ScaleType.FIT_START); titleLogo.setVisibility(View.GONE); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(430), dp(92)); lp.topMargin = dp(8); info.addView(titleLogo, lp); loadOptionalLogo(titleLogo, name, item.optString("Id"));
        TextView meta = text(metadataLine(item), 14, Color.rgb(238, 240, 246), false); meta.setPadding(0, dp(10), 0, dp(7)); info.addView(meta);
        String genres = joinJson(item.optJSONArray("Genres"), "  ·  "); if ("Episode".equals(itemType) && !item.optString("SeriesName").isEmpty()) genres = item.optString("SeriesName") + (genres.isEmpty() ? "" : "  ·  " + genres); TextView genre = text(genres, 14, ACCENT, false); info.addView(genre);
        TextView overview = text(item.optString("Overview", s(R.string.no_synopsis)), 15, Color.rgb(226, 230, 240), false); overview.setMaxLines(3); overview.setEllipsize(TextUtils.TruncateAt.END); LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(680), dp(72)); op.topMargin = dp(8); info.addView(overview, op);
        long resumePosition = resumePositionMs(item); boolean canResume = resumePosition > 0 && !"Series".equals(item.optString("Type"));
        if (canResume && item.optLong("RunTimeTicks") > 0) { FrameLayout progress = new FrameLayout(this); progress.setBackground(rounded(Color.argb(155, 83, 89, 105), 2)); View fill = new View(this); fill.setBackground(rounded(ACCENT, 2)); progress.addView(fill, new FrameLayout.LayoutParams(Math.max(dp(8), (int) (dp(360) * Math.min(1d, resumePosition * 10000d / item.optLong("RunTimeTicks")))), dp(4))); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(360), dp(4)); pp.topMargin = dp(5); pp.bottomMargin = dp(13); info.addView(progress, pp); }
        LinearLayout actions = new LinearLayout(this); Button play = actionButton("Series".equals(item.optString("Type")) ? s(R.string.view_episodes) : canResume ? s(R.string.continue_action) : s(R.string.play_action), true); actions.addView(play, new LinearLayout.LayoutParams(dp(174), dp(42)));
        Button restartButton = canResume ? actionButton(s(R.string.restart_action), false) : null; if (restartButton != null) { LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(190), dp(42)); rp.leftMargin = dp(9); actions.addView(restartButton, rp); }
        JSONObject userData = item.optJSONObject("UserData"); boolean favorite = userData != null && userData.optBoolean("IsFavorite");
        Button favoriteButton = actionButton(favorite ? s(R.string.in_my_list) : s(R.string.add_my_list), false); LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(136), dp(42)); fp.leftMargin = dp(9); actions.addView(favoriteButton, fp);
        info.addView(actions);
        LinearLayout credits = new LinearLayout(this); credits.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams creditsParams = new LinearLayout.LayoutParams(dp(700), ViewGroup.LayoutParams.WRAP_CONTENT); creditsParams.topMargin = dp(16); info.addView(credits, creditsParams); loadDetailCredits(item, credits);
        play.setOnClickListener(v -> { if ("Series".equals(item.optString("Type"))) showEpisodes(item); else play(item.optString("Id"), mediaSourceId(item), item.optString("Name"), resumePosition, () -> showDetails(item)); }); if (restartButton != null) restartButton.setOnClickListener(v -> play(item.optString("Id"), mediaSourceId(item), item.optString("Name"), 0, () -> showDetails(item))); favoriteButton.setOnClickListener(v -> toggleFavorite(item, favoriteButton));
        detailBody.addView(info, new LinearLayout.LayoutParams(dp(980), dp(520))); LinearLayout related = new LinearLayout(this); related.setOrientation(LinearLayout.VERTICAL); related.setClipChildren(false); related.setBackgroundColor(Color.argb(235, 7, 10, 18)); detailBody.addView(related, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); loadSimilarItems(item, related); View footer = new View(this); detailBody.addView(footer, new LinearLayout.LayoutParams(1, dp(32))); play.requestFocus();
    }

    private void loadSimilarItems(JSONObject item, LinearLayout target) {
        int expected = generation; io.execute(() -> { try {
            String path = "/Items/" + enc(item.optString("Id")) + "/Similar?UserId=" + enc(userId) + "&Limit=18&Fields=PrimaryImageAspectRatio,Overview,Genres,BackdropImageTags,MediaSources";
            List<JSONObject> similar = jsonList(request(server + path, "GET", null, token).getJSONArray("Items")); List<JSONObject> unique = new ArrayList<>(); Set<String> ids = new LinkedHashSet<>(); ids.add(editorialKey(item)); for (JSONObject candidate : similar) if (ids.add(editorialKey(candidate))) unique.add(candidate);
            runOnUiThread(() -> { if (generation != expected || unique.isEmpty()) return; target.removeAllViews(); target.addView(sectionRow(s(R.string.more_like_this), s(R.string.more_like_this_subtitle), unique, false)); });
        } catch (Exception ignored) {} });
    }

    private void loadDetailCredits(JSONObject item, LinearLayout target) {
        int expected = generation; io.execute(() -> { try {
            JSONObject full = request(server + "/Users/" + userId + "/Items/" + enc(item.optString("Id")), "GET", null, token);
            runOnUiThread(() -> { if (generation != expected) return; target.removeAllViews(); addCreditLine(target, s(R.string.cast_label), peopleNames(full, "Actor", 5)); addCreditLine(target, s(R.string.director_label), peopleNames(full, "Director", 3)); addCreditLine(target, s(R.string.studio_label), objectNames(full.optJSONArray("Studios"), 3)); });
        } catch (Exception ignored) {} });
    }

    private void addCreditLine(LinearLayout target, String label, String value) { if (value.isEmpty()) return; TextView line = text(label + "  " + value, 12, Color.rgb(184, 190, 205), false); line.setMaxLines(1); line.setEllipsize(TextUtils.TruncateAt.END); line.setPadding(0, dp(2), 0, dp(2)); target.addView(line); }
    private String peopleNames(JSONObject item, String type, int limit) { JSONArray people = item.optJSONArray("People"); if (people == null) return ""; List<String> names = new ArrayList<>(); for (int i = 0; i < people.length() && names.size() < limit; i++) { JSONObject person = people.optJSONObject(i); if (person != null && type.equals(person.optString("Type")) && !person.optString("Name").isEmpty()) names.add(person.optString("Name")); } return TextUtils.join(", ", names); }
    private String objectNames(JSONArray values, int limit) { if (values == null) return ""; List<String> names = new ArrayList<>(); for (int i = 0; i < values.length() && names.size() < limit; i++) { JSONObject value = values.optJSONObject(i); if (value != null && !value.optString("Name").isEmpty()) names.add(value.optString("Name")); } return TextUtils.join(", ", names); }

    private void toggleFavorite(JSONObject item, Button button) {
        JSONObject data = item.optJSONObject("UserData"); boolean favorite = data != null && data.optBoolean("IsFavorite"); button.setEnabled(false); button.setText(favorite ? s(R.string.removing) : s(R.string.saving));
        io.execute(() -> { try {
            request(server + "/Users/" + userId + "/FavoriteItems/" + item.optString("Id"), favorite ? "DELETE" : "POST", null, token);
            JSONObject updated = item.optJSONObject("UserData"); if (updated == null) { updated = new JSONObject(); item.put("UserData", updated); } updated.put("IsFavorite", !favorite);
            runOnUiThread(() -> { button.setEnabled(true); button.setText(!favorite ? s(R.string.in_my_list) : s(R.string.add_my_list)); });
        } catch (Exception e) { runOnUiThread(() -> { button.setEnabled(true); button.setText(favorite ? s(R.string.in_my_list) : s(R.string.add_my_list)); showError(s(R.string.favorite_error)); }); } });
    }

    private void showEpisodes(JSONObject series) {
        newRoot(); backAction = () -> showDetails(series);
        root.addView(simpleHeader(series.optString("Name"), s(R.string.episodes)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        ScrollView scroll = new ScrollView(this); scroll.setClipToPadding(false); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(60), 0, dp(60), dp(32)); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        ProgressBar p = new ProgressBar(this); list.addView(p, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80))); int expected = generation;
        io.execute(() -> { try {
            String path = "/Users/" + userId + "/Items?Recursive=true&ParentId=" + enc(series.optString("Id")) + "&IncludeItemTypes=Episode&SortBy=ParentIndexNumber,IndexNumber&Fields=Overview,Genres,BackdropImageTags,MediaSources";
            List<JSONObject> episodes = jsonList(request(server + path, "GET", null, token).getJSONArray("Items"));
            runOnUiThread(() -> { if (generation == expected) { list.removeAllViews(); renderEpisodes(list, episodes, series); } });
        } catch (Exception e) { runOnUiThread(() -> { if (generation == expected) { list.removeAllViews(); list.addView(errorView(s(R.string.episodes_error), e)); } }); } });
    }

    private LinearLayout simpleHeader(String title, String eyebrow) {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(48), 0, dp(48), 0); bar.setBackgroundColor(Color.rgb(9, 12, 21));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.addView(text(eyebrow.toUpperCase(Locale.getDefault()), 11, ACCENT, true)); labels.addView(text(title, 23, Color.WHITE, true)); bar.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return bar;
    }

    private void renderEpisodes(LinearLayout list, List<JSONObject> episodes, JSONObject series) {
        if (episodes.isEmpty()) { list.addView(text(s(R.string.no_episodes), 17, MUTED, false)); return; }
        JSONObject continueEpisode = null; for (JSONObject episode : episodes) { JSONObject data = episode.optJSONObject("UserData"); if (data != null && data.optLong("PlaybackPositionTicks") > 0 && !data.optBoolean("Played")) { continueEpisode = episode; break; } } if (continueEpisode == null) for (JSONObject episode : episodes) { JSONObject data = episode.optJSONObject("UserData"); if (data == null || !data.optBoolean("Played")) { continueEpisode = episode; break; } }
        View resumeCard = null; if (continueEpisode != null) { resumeCard = nextEpisodeSpotlight(continueEpisode, series); LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(146)); resumeParams.topMargin = dp(12); resumeParams.bottomMargin = dp(10); list.addView(resumeCard, resumeParams); }
        JSONObject firstEpisode = firstRegularEpisode(episodes); if (firstEpisode != null) { Button startSeries = actionButton(s(R.string.start_series), false); LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(220), dp(44)); startParams.bottomMargin = dp(16); list.addView(startSeries, startParams); startSeries.setOnClickListener(v -> play(firstEpisode.optString("Id"), mediaSourceId(firstEpisode), firstEpisode.optString("Name"), 0, () -> showEpisodes(series))); }
        java.util.Set<Integer> seasons = new java.util.TreeSet<>(); for (JSONObject episode : episodes) seasons.add(episode.optInt("ParentIndexNumber", 0));
        HorizontalScrollView seasonScroll = new HorizontalScrollView(this); seasonScroll.setHorizontalScrollBarEnabled(false); LinearLayout seasonBar = new LinearLayout(this); seasonBar.setPadding(0, 0, dp(30), dp(12)); seasonScroll.addView(seasonBar); list.addView(seasonScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        LinearLayout episodeContainer = new LinearLayout(this); episodeContainer.setOrientation(LinearLayout.VERTICAL); list.addView(episodeContainer);
        int firstSeason = continueEpisode == null ? seasons.iterator().next() : continueEpisode.optInt("ParentIndexNumber", seasons.iterator().next()); for (int season : seasons) { Button seasonButton = seasonTabButton(season == 0 ? s(R.string.specials) : s(R.string.season_number, season), season == firstSeason); seasonButton.setTag(season); LinearLayout.LayoutParams seasonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)); seasonParams.rightMargin = dp(8); seasonBar.addView(seasonButton, seasonParams); seasonButton.setOnClickListener(v -> { selectSeasonTab(seasonBar, season); renderSeasonEpisodes(episodeContainer, episodes, series, season, true); }); }
        renderSeasonEpisodes(episodeContainer, episodes, series, firstSeason, false); if (resumeCard != null) resumeCard.requestFocus(); else if (seasonBar.getChildCount() > 0) seasonBar.getChildAt(0).requestFocus();
    }

    private View nextEpisodeSpotlight(JSONObject episode, JSONObject series) {
        LinearLayout card = new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setFocusable(true); card.setClickable(true); card.setPadding(dp(7), dp(7), dp(22), dp(7)); card.setBackground(rounded(SURFACE, 12));
        FrameLayout art = new FrameLayout(this); art.setClipToOutline(true); art.setBackground(rounded(SURFACE_LIGHT, 9)); ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); art.addView(image, match()); addProgress(art, episode, dp(232)); card.addView(art, new LinearLayout.LayoutParams(dp(232), dp(130))); loadImage(image, episode.optString("Id"), "Primary", 600);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(dp(22), 0, dp(16), 0); TextView eyebrow = text(resumePositionMs(episode) > 0 ? s(R.string.continue_series) : s(R.string.next_to_watch), 11, ACCENT, true); eyebrow.setLetterSpacing(.09f); copy.addView(eyebrow); copy.addView(text(episode.optString("Name"), 20, Color.WHITE, true)); String episodeTitle = s(R.string.season_episode_runtime, episode.optInt("ParentIndexNumber"), episode.optInt("IndexNumber"), formatRuntime(episode.optLong("RunTimeTicks"))); TextView identity = text(episodeTitle, 13, Color.rgb(205, 211, 224), false); identity.setPadding(0, dp(4), 0, dp(5)); copy.addView(identity); TextView summary = text(episode.optString("Overview", s(R.string.no_description)), 12, MUTED, false); summary.setMaxLines(2); summary.setEllipsize(TextUtils.TruncateAt.END); copy.addView(summary); card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); TextView playIcon = text("▶", 22, Color.WHITE, true); playIcon.setGravity(Gravity.CENTER); card.addView(playIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        card.setOnFocusChangeListener((v, focused) -> { card.setBackground(stroked(focused ? Color.WHITE : SURFACE, focused ? Color.rgb(28, 34, 50) : SURFACE, 12, focused ? 2 : 0)); if (focused) ensureFullyVisible(card, 8); }); card.setOnClickListener(v -> playEpisodeWithChoice(episode, series)); return card;
    }

    private void renderSeasonEpisodes(LinearLayout container, List<JSONObject> episodes, JSONObject series, int selectedSeason, boolean focusFirst) {
        container.removeAllViews(); for (JSONObject episode : episodes) if (episode.optInt("ParentIndexNumber", 0) == selectedSeason) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(7), dp(7), dp(14), dp(7)); row.setFocusable(true); row.setClickable(true); row.setBackground(rounded(SURFACE, 10));
            FrameLayout artwork = new FrameLayout(this); artwork.setClipToOutline(true); artwork.setBackground(rounded(SURFACE_LIGHT, 8)); ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); artwork.addView(image, match()); addProgress(artwork, episode, dp(184)); row.addView(artwork, new LinearLayout.LayoutParams(dp(184), dp(103))); loadImage(image, episode.optString("Id"), "Primary", 460);
            LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(19), 0, dp(16), 0); words.addView(text(episode.optInt("IndexNumber", 0) + ".  " + episode.optString("Name"), 16, Color.WHITE, true)); JSONObject data = episode.optJSONObject("UserData"); long position = data == null ? 0 : data.optLong("PlaybackPositionTicks"); boolean watched = data != null && data.optBoolean("Played"); String status = watched ? s(R.string.watched) : position > 0 ? s(R.string.in_progress) : ""; String aired = episode.optString("PremiereDate"); if (aired.length() >= 10) aired = aired.substring(0, 10); String meta = formatRuntime(episode.optLong("RunTimeTicks")) + (aired.isEmpty() ? "" : "  ·  " + aired) + (status.isEmpty() ? "" : "  ·  " + status); TextView runtime = text(meta, 12, status.isEmpty() ? MUTED : ACCENT, false); runtime.setPadding(0, dp(3), 0, dp(5)); words.addView(runtime); TextView summary = text(episode.optString("Overview", s(R.string.no_description)), 13, MUTED, false); summary.setMaxLines(2); summary.setEllipsize(TextUtils.TruncateAt.END); words.addView(summary); row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); TextView playIcon = text(watched ? "✓" : "▶", 20, watched ? ACCENT : Color.WHITE, true); playIcon.setGravity(Gravity.CENTER); row.addView(playIcon, new LinearLayout.LayoutParams(dp(50), dp(50)));
            row.setOnFocusChangeListener((v, focused) -> { row.setBackground(stroked(focused ? Color.WHITE : SURFACE, focused ? Color.rgb(30, 36, 53) : SURFACE, 10, focused ? 2 : 0)); if (focused) ensureFullyVisible(row, 8); }); row.setOnClickListener(v -> playEpisodeWithChoice(episode, series)); LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116)); rowParams.bottomMargin = dp(7); container.addView(row, rowParams);
        } if (focusFirst && container.getChildCount() > 0) container.getChildAt(0).requestFocus();
    }

    private JSONObject firstRegularEpisode(List<JSONObject> episodes) {
        for (JSONObject episode : episodes) if (episode.optInt("ParentIndexNumber", 0) > 0) return episode;
        return episodes.isEmpty() ? null : episodes.get(0);
    }

    private void playEpisodeWithChoice(JSONObject episode, JSONObject series) {
        long position = resumePositionMs(episode); Runnable returnAction = () -> showEpisodes(series);
        if (position <= 0) { play(episode.optString("Id"), mediaSourceId(episode), episode.optString("Name"), 0, returnAction); return; }
        new AlertDialog.Builder(this).setTitle(s(R.string.playback_start_choice))
                .setItems(new String[]{s(R.string.continue_action), s(R.string.restart_action)}, (dialog, which) ->
                        play(episode.optString("Id"), mediaSourceId(episode), episode.optString("Name"), which == 0 ? position : 0, returnAction))
                .setNegativeButton(s(R.string.back), null).show();
    }

    private Button seasonTabButton(String label, boolean selected) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(13); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setFocusable(true); button.setActivated(selected); button.setTextColor(selected ? BG : Color.WHITE); button.setBackground(rounded(selected ? Color.WHITE : SURFACE_LIGHT, 8));
        button.setOnFocusChangeListener((v, focused) -> { boolean active = button.isActivated(); button.setTextColor(focused || active ? BG : Color.WHITE); button.setBackground(stroked(focused ? Color.WHITE : active ? Color.WHITE : SURFACE_LIGHT, focused ? Color.WHITE : active ? Color.WHITE : SURFACE_LIGHT, 8, focused ? 2 : 0)); if (focused) ensureFullyVisible(button, 8); }); return button;
    }

    private void selectSeasonTab(LinearLayout bar, int selectedSeason) { for (int i = 0; i < bar.getChildCount(); i++) { View child = bar.getChildAt(i); if (!(child instanceof Button) || !(child.getTag() instanceof Integer)) continue; Button button = (Button) child; boolean selected = ((Integer) button.getTag()) == selectedSeason; button.setActivated(selected); if (!button.hasFocus()) { button.setTextColor(selected ? BG : Color.WHITE); button.setBackground(rounded(selected ? Color.WHITE : SURFACE_LIGHT, 8)); } } }

    private void play(String id, String sourceId, String name, long resumePositionMs, Runnable returnAction) {
        newRoot(); playing = true; getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); backAction = returnAction; currentPlayerReturnAction = returnAction;
        serverAudioTracks.clear(); selectedAudioStreamIndex = -1;
        PlayerView video = new PlayerView(this); video.setBackgroundColor(Color.BLACK); video.setUseController(false); video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); video.setFocusable(true); applySubtitleStyle(video);
        activePlayerView = video;
        player = new ExoPlayer.Builder(this).build(); video.setPlayer(player);
        androidx.media3.common.TrackSelectionParameters.Builder savedTracks = player.getTrackSelectionParameters().buildUpon(); String preferredAudio = prefs.getString("audio_language", ""); String preferredText = prefs.getString("subtitle_language", ""); if (!preferredAudio.isEmpty()) savedTracks.setPreferredAudioLanguage(preferredAudio); if ("off".equals(preferredText)) savedTracks.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true); else if (!preferredText.isEmpty()) savedTracks.setPreferredTextLanguage(preferredText).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false); player.setTrackSelectionParameters(savedTracks.build());
        String directUrl = server + "/Videos/" + id + "/stream?static=true&api_key=" + enc(token) + "&MediaSourceId=" + enc(sourceId);
        String hlsUrl = compatibleStreamUrl(id, sourceId, -1);
        playingItemId = id; playingSourceId = sourceId; playSessionId = UUID.randomUUID().toString(); playbackReported = false; currentPlayMethod = "DirectPlay"; playbackFallbackUrl = hlsUrl; fallbackAttempted = false;
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) { if (state == Player.STATE_READY) { if (!fallbackAttempted && !hasSelectedAudioTrack()) { currentPlayMethod = "Conversió d’àudio"; restartCompatibleStream(selectedAudioStreamIndex); return; } if (!playbackReported) beginPlaybackReporting(); updatePlaybackDiagnostics(); } else if (state == Player.STATE_ENDED && nextEpisode != null && prefs.getBoolean("autoplay", true)) playNextEpisode(); }
            @Override public void onIsPlayingChanged(boolean isPlaying) { if (playbackReported) reportPlayback("/Sessions/Playing/Progress", player == null ? 0 : player.getCurrentPosition(), isPlaying); }
            @Override public void onPlayerError(PlaybackException error) { if (!fallbackAttempted) { fallbackAttempted = true; currentPlayMethod = "Transcode"; player.setMediaItem(new MediaItem.Builder().setUri(playbackFallbackUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build(), Math.max(0, player.getCurrentPosition())); player.prepare(); player.setPlayWhenReady(true); } else showPlaybackError(error); }
        });
        player.setMediaItem(new MediaItem.Builder().setUri(directUrl).setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(name).build()).build());
        if (resumePositionMs > 0) player.seekTo(resumePositionMs); player.prepare(); player.setPlayWhenReady(true);
        FrameLayout playerPage = new FrameLayout(this); playerPage.addView(video, match());
        seekFeedback = text("+10 s", 18, Color.WHITE, true); seekFeedback.setGravity(Gravity.CENTER); seekFeedback.setBackground(rounded(Color.argb(225, 10, 14, 23), 22)); seekFeedback.setVisibility(View.GONE); FrameLayout.LayoutParams feedbackParams = new FrameLayout.LayoutParams(dp(150), dp(58), Gravity.CENTER); playerPage.addView(seekFeedback, feedbackParams);
        playbackDiagnostics = text("", 12, Color.rgb(224, 229, 240), false); playbackDiagnostics.setPadding(dp(16), dp(11), dp(16), dp(11)); playbackDiagnostics.setBackground(rounded(Color.argb(224, 10, 14, 23), 9)); playbackDiagnostics.setVisibility(View.GONE); FrameLayout.LayoutParams diagnosticParams = new FrameLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP); diagnosticParams.rightMargin = dp(36); diagnosticParams.topMargin = dp(30); playerPage.addView(playbackDiagnostics, diagnosticParams);
        View bottomShade = new View(this); bottomShade.setBackground(verticalGradient(Color.TRANSPARENT, Color.argb(225, 5, 8, 15))); FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), Gravity.BOTTOM); playerPage.addView(bottomShade, shadeParams);
        playerControls = new LinearLayout(this); playerControls.setOrientation(LinearLayout.VERTICAL); playerControls.setPadding(dp(48), dp(5), dp(48), dp(8)); playerControls.setVisibility(View.GONE);
        playerTitle = text(name, 13, Color.WHITE, true); playerTitle.setMaxLines(1); playerControls.addView(playerTitle, new LinearLayout.LayoutParams(dp(900), dp(20)));
        playerSeekProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)); seekParams.bottomMargin = dp(7); playerControls.addView(playerSeekProgress, seekParams);
        LinearLayout controlRow = new LinearLayout(this); controlRow.setGravity(Gravity.CENTER_VERTICAL);
        playerPlayButton = playerControlButton("Ⅱ", true); playerPlayButton.setTextSize(17); playerAudioButton = playerControlButton(s(R.string.audio), false); playerSubtitleButton = playerControlButton("CC · " + s(R.string.no), false); playerQualityButton = playerControlButton(s(R.string.quality), false); playerInfoButton = playerControlButton(s(R.string.playback_info), false);
        Button[] controls = {playerPlayButton, playerAudioButton, playerSubtitleButton, playerQualityButton, playerInfoButton}; for (Button control : controls) control.setId(View.generateViewId()); for (int i = 0; i < controls.length; i++) { Button left = i == 0 ? null : controls[i - 1], right = i + 1 == controls.length ? null : controls[i + 1]; if (left != null) controls[i].setNextFocusLeftId(left.getId()); if (right != null) controls[i].setNextFocusRightId(right.getId()); wirePlayerControlNavigation(controls[i], left, right); }
        controlRow.addView(playerPlayButton, new LinearLayout.LayoutParams(dp(52), dp(38))); LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(dp(105), dp(38)); acp.leftMargin = dp(9); controlRow.addView(playerAudioButton, acp); LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(dp(105), dp(38)); scp.leftMargin = dp(7); controlRow.addView(playerSubtitleButton, scp); LinearLayout.LayoutParams qcp = new LinearLayout.LayoutParams(dp(112), dp(38)); qcp.leftMargin = dp(7); controlRow.addView(playerQualityButton, qcp); LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(dp(96), dp(38)); icp.leftMargin = dp(7); controlRow.addView(playerInfoButton, icp);
        View controlSpacer = new View(this); controlRow.addView(controlSpacer, new LinearLayout.LayoutParams(0, 1, 1)); playerTime = text("00:00", 14, Color.WHITE, true); playerTime.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT); controlRow.addView(playerTime, new LinearLayout.LayoutParams(dp(260), dp(42))); playerControls.addView(controlRow);
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92), Gravity.BOTTOM); playerPage.addView(playerControls, controlsParams);
        playerPlayButton.setOnClickListener(v -> { if (player != null) { if (player.isPlaying()) player.pause(); else player.play(); } }); playerAudioButton.setOnClickListener(v -> showTrackSelector(C.TRACK_TYPE_AUDIO, s(R.string.audio))); playerSubtitleButton.setOnClickListener(v -> showTrackSelector(C.TRACK_TYPE_TEXT, s(R.string.subtitles))); playerQualityButton.setOnClickListener(v -> showQualitySelector()); playerInfoButton.setOnClickListener(v -> { updatePlaybackDiagnostics(); boolean show = playbackDiagnostics.getVisibility() != View.VISIBLE; playbackDiagnostics.setVisibility(show ? View.VISIBLE : View.GONE); playerInfoButton.setActivated(show); applyPlayerControlStyle(playerInfoButton, playerInfoButton.hasFocus(), false); });
        video.setOnKeyListener((v, keyCode, event) -> handlePlayerKey(video, keyCode, event));
        skipButton = playerControlButton(s(R.string.skip_intro), false); skipButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(dp(170), dp(42), Gravity.RIGHT | Gravity.BOTTOM); skipParams.rightMargin = dp(54); skipParams.bottomMargin = dp(92); playerPage.addView(skipButton, skipParams);
        skipButton.setOnClickListener(v -> { if (player != null && activeSkipSegment != null) { player.seekTo(activeSkipSegment.endMs); activeSkipSegment = null; skipButton.setVisibility(View.GONE); video.requestFocus(); } });
        nextEpisodeCard = buildNextEpisodeCard(); FrameLayout.LayoutParams nextParams = new FrameLayout.LayoutParams(dp(520), dp(148), Gravity.RIGHT | Gravity.BOTTOM); nextParams.rightMargin = dp(48); nextParams.bottomMargin = dp(48); playerPage.addView(nextEpisodeCard, nextParams);
        root.addView(playerPage, match()); video.requestFocus(); playbackHandler.post(controlsUpdater); loadServerAudioTracks(id, sourceId); loadSkipSegments(id); loadNextEpisode(id);
    }

    private boolean handlePlayerKey(PlayerView video, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || player == null) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            long step = event.isLongPress() || event.getRepeatCount() >= 8 ? 60000 : event.getRepeatCount() >= 3 ? 30000 : 10000;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) step = -step;
            player.seekTo(Math.max(0, Math.min(player.getDuration() > 0 ? player.getDuration() : Long.MAX_VALUE, player.getCurrentPosition() + step)));
            if (playerControls != null) playerControls.setVisibility(View.GONE);
            playbackHandler.removeCallbacks(hidePlayerControls);
            showSeekFeedback(step); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { if (player.isPlaying()) player.pause(); else player.play(); showPlayerControls(true); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { showPlayerControls(true); return true; }
        return false;
    }

    private void showPlayerControls(boolean focus) { if (playerControls == null) return; playerControls.setVisibility(View.VISIBLE); if (focus && playerPlayButton != null) playerPlayButton.requestFocus(); playbackHandler.removeCallbacks(hidePlayerControls); playbackHandler.postDelayed(hidePlayerControls, 4000); }

    private void showSeekFeedback(long step) { if (seekFeedback == null || player == null) return; seekFeedback.setText((step > 0 ? "+" : "−") + (Math.abs(step) / 1000) + " s\n" + formatPosition(player.getCurrentPosition())); seekFeedback.setVisibility(View.VISIBLE); playbackHandler.removeCallbacks(hideSeekFeedback); playbackHandler.postDelayed(hideSeekFeedback, 850); }

    private String compatibleStreamUrl(String id, String sourceId, int audioStreamIndex) { int height = prefs.getInt("video_quality_height", 2160), width = height >= 2160 ? 3840 : height >= 1080 ? 1920 : height >= 720 ? 1280 : 854; long bitrate = height >= 2160 ? 120000000L : height >= 1080 ? 40000000L : height >= 720 ? 12000000L : 5000000L; return server + "/Videos/" + id + "/master.m3u8?api_key=" + enc(token) + "&MediaSourceId=" + enc(sourceId) + "&DeviceId=" + enc(deviceId()) + (audioStreamIndex >= 0 ? "&AudioStreamIndex=" + audioStreamIndex : "") + "&VideoCodec=h264,hevc&AudioCodec=aac&TranscodingContainer=ts&SegmentContainer=ts&TranscodingProtocol=hls&MaxStreamingBitrate=" + bitrate + "&VideoBitrate=" + Math.max(1000000L, bitrate - 320000L) + "&AudioBitrate=320000&MaxWidth=" + width + "&MaxHeight=" + height + "&MaxAudioChannels=2&TranscodingMaxAudioChannels=2&AllowVideoStreamCopy=true&AllowAudioStreamCopy=false&EnableAutoStreamCopy=true&RequireAvc=false"; }

    private boolean hasSelectedAudioTrack() {
        if (player == null) return false;
        for (Tracks.Group group : player.getCurrentTracks().getGroups())
            if (group.getType() == C.TRACK_TYPE_AUDIO)
                for (int i = 0; i < group.length; i++) if (group.isTrackSelected(i)) return true;
        return false;
    }

    private void showQualitySelector() { Dialog dialog = new Dialog(this); LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(28), dp(24), dp(28), dp(24)); panel.setBackground(rounded(Color.rgb(17, 22, 34), 16)); panel.addView(text(s(R.string.video_quality), 23, Color.WHITE, true)); TextView helper = text(s(R.string.quality_helper), 13, MUTED, false); helper.setPadding(0, dp(5), 0, dp(16)); panel.addView(helper); int selected = prefs.getInt("video_quality_height", 2160); int[] heights = {2160, 1080, 720, 480}; String[] labels = {s(R.string.quality_original), "1080p", "720p", "480p"}; Button first = null; for (int i = 0; i < heights.length; i++) { int height = heights[i]; Button option = trackChoiceButton(labels[i], selected == height); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)); p.topMargin = dp(6); panel.addView(option, p); if (first == null) first = option; option.setOnClickListener(v -> { prefs.edit().putInt("video_quality_height", height).apply(); restartCompatibleStream(); dialog.dismiss(); }); } dialog.setContentView(panel); dialog.setOnDismissListener(v -> { if (activePlayerView != null) activePlayerView.requestFocus(); }); dialog.show(); Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); WindowManager.LayoutParams params = new WindowManager.LayoutParams(); params.copyFrom(window.getAttributes()); params.width = dp(500); params.height = WindowManager.LayoutParams.WRAP_CONTENT; params.dimAmount = .72f; window.setAttributes(params); } if (first != null) first.requestFocus(); }

    private void restartCompatibleStream() { restartCompatibleStream(selectedAudioStreamIndex); }

    private void restartCompatibleStream(int audioStreamIndex) { if (player == null || playingItemId == null) return; long position = player.getCurrentPosition(); selectedAudioStreamIndex = audioStreamIndex; currentPlayMethod = "Remux/Transcode"; fallbackAttempted = true; playbackFallbackUrl = compatibleStreamUrl(playingItemId, playingSourceId, audioStreamIndex); player.setMediaItem(new MediaItem.Builder().setUri(playbackFallbackUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build(), position); player.prepare(); player.play(); }

    private void updatePlaybackDiagnostics() { if (playbackDiagnostics == null || player == null) return; Format format = player.getVideoFormat(); String resolution = format == null || format.width <= 0 ? s(R.string.detecting) : format.width + " × " + format.height; String codec = format == null || format.codecs == null ? "—" : format.codecs.toUpperCase(Locale.getDefault()); String bitrate = format == null || format.bitrate <= 0 ? "—" : String.format(Locale.getDefault(), "%.1f Mbps", format.bitrate / 1000000f); playbackDiagnostics.setText(s(R.string.playback_diagnostics, currentPlayMethod == null ? "—" : currentPlayMethod, resolution, codec, bitrate)); }

    private void applySubtitleStyle(PlayerView video) { if (video.getSubtitleView() == null) return; float scale = prefs.getInt("subtitle_size", 100) / 100f; video.getSubtitleView().setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale); video.getSubtitleView().setStyle(new CaptionStyleCompat(Color.WHITE, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null)); video.getSubtitleView().setBottomPaddingFraction(0.09f); }

    private Button playerControlButton(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setFocusable(true); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(12), 0, dp(12), 0);
        applyPlayerControlStyle(b, false, primary);
        b.setOnFocusChangeListener((v, focused) -> { applyPlayerControlStyle(b, focused, primary); if (focused) { playbackHandler.removeCallbacks(hidePlayerControls); playbackHandler.postDelayed(hidePlayerControls, 4000); } });
        b.setOnKeyListener((v, keyCode, event) -> { if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN && activePlayerView != null) { playerControls.setVisibility(View.GONE); activePlayerView.requestFocus(); return true; } return false; }); return b;
    }

    private void applyPlayerControlStyle(Button button, boolean focused, boolean round) { int radius = round ? 19 : 7; boolean active = button.isActivated(); int idleFill = Color.argb(228, 25, 30, 43), idleStroke = active ? ACCENT : Color.rgb(73, 81, 101); button.setTextColor(focused ? BG : active ? ACCENT : Color.rgb(232, 235, 243)); button.setBackground(stroked(focused ? Color.WHITE : idleStroke, focused ? Color.WHITE : idleFill, radius, focused ? 2 : active ? 2 : 1)); }

    private void wirePlayerControlNavigation(Button button, Button left, Button right) { button.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() != KeyEvent.ACTION_DOWN) return false; if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null) { left.requestFocus(); return true; } if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null) { right.requestFocus(); return true; } if (keyCode == KeyEvent.KEYCODE_DPAD_UP && activePlayerView != null) { playerControls.setVisibility(View.GONE); activePlayerView.requestFocus(); return true; } return false; }); }

    private boolean contextualPlayerActionFocused() { return skipButton != null && skipButton.hasFocus() || nextEpisodeCard != null && nextEpisodeCard.hasFocus(); }

    private void loadServerAudioTracks(String itemId, String sourceId) {
        int expectedGeneration = generation;
        io.execute(() -> {
            try {
                JSONObject item = request(server + "/Users/" + enc(userId) + "/Items/" + enc(itemId) + "?Fields=MediaSources", "GET", null, token);
                JSONArray sources = item.optJSONArray("MediaSources"); JSONObject source = null;
                if (sources != null) for (int i = 0; i < sources.length(); i++) { JSONObject candidate = sources.optJSONObject(i); if (candidate != null && sourceId.equals(candidate.optString("Id"))) { source = candidate; break; } }
                if (source == null && sources != null && sources.length() > 0) source = sources.optJSONObject(0);
                List<ServerAudioTrack> found = new ArrayList<>(); JSONArray streams = source == null ? null : source.optJSONArray("MediaStreams");
                if (streams != null) for (int i = 0; i < streams.length(); i++) { JSONObject stream = streams.optJSONObject(i); if (stream == null || !"Audio".equalsIgnoreCase(stream.optString("Type"))) continue; found.add(new ServerAudioTrack(stream.optInt("Index", i), stream.optString("Language"), stream.optString("DisplayTitle", stream.optString("Title")), stream.optString("Codec"), stream.optInt("Channels"), stream.optBoolean("IsDefault"))); }
                String preferred = prefs.getString("audio_language", ""); int selected = -1;
                for (ServerAudioTrack track : found) if (!preferred.isEmpty() && preferred.equalsIgnoreCase(track.language)) { selected = track.index; break; }
                if (selected < 0) for (ServerAudioTrack track : found) if (track.isDefault) { selected = track.index; break; }
                if (selected < 0 && !found.isEmpty()) selected = found.get(0).index;
                int initialSelection = selected;
                runOnUiThread(() -> { if (generation != expectedGeneration || !playing) return; serverAudioTracks.clear(); serverAudioTracks.addAll(found); if (selectedAudioStreamIndex < 0) selectedAudioStreamIndex = initialSelection; });
            } catch (Exception ignored) {}
        });
    }

    private void showServerAudioSelector(String title) {
        Dialog dialog = new Dialog(this); LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(28), dp(24), dp(28), dp(24)); panel.setBackground(rounded(Color.rgb(17, 22, 34), 16));
        panel.addView(text(title, 23, Color.WHITE, true)); TextView helper = text(s(R.string.select_audio), 13, MUTED, false); helper.setPadding(0, dp(5), 0, dp(12)); panel.addView(helper);
        TextView compatibility = text(s(R.string.audio_conversion_note), 11, Color.rgb(148, 158, 180), false); compatibility.setPadding(0, 0, 0, dp(10)); panel.addView(compatibility);
        Button first = null;
        for (ServerAudioTrack track : serverAudioTracks) { Button option = trackChoiceButton(track.label(), track.index == selectedAudioStreamIndex); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)); params.topMargin = dp(6); panel.addView(option, params); if (first == null) first = option; option.setOnClickListener(v -> { if (!track.language.isEmpty()) prefs.edit().putString("audio_language", track.language).apply(); restartCompatibleStream(track.index); dialog.dismiss(); }); }
        dialog.setContentView(panel); dialog.setOnDismissListener(v -> { if (activePlayerView != null) activePlayerView.requestFocus(); }); dialog.show(); Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams params = new WindowManager.LayoutParams(); params.copyFrom(window.getAttributes()); params.width = dp(600); params.height = WindowManager.LayoutParams.WRAP_CONTENT; params.dimAmount = .72f; window.setAttributes(params); } if (first != null) first.requestFocus();
    }

    private void showTrackSelector(int trackType, String title) {
        if (trackType == C.TRACK_TYPE_AUDIO && !serverAudioTracks.isEmpty()) { showServerAudioSelector(title); return; }
        if (player == null) return; List<TrackChoice> choices = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) if (group.getType() == trackType) for (int i = 0; i < group.length; i++) choices.add(new TrackChoice(trackLabel(group.getTrackFormat(i), choices.size() + 1), group, i));
        Dialog dialog = new Dialog(this); LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(28), dp(24), dp(28), dp(24)); panel.setBackground(rounded(Color.rgb(17, 22, 34), 16));
        panel.addView(text(title, 23, Color.WHITE, true)); TextView helper = text(trackType == C.TRACK_TYPE_AUDIO ? s(R.string.select_audio) : s(R.string.select_subtitles), 13, MUTED, false); helper.setPadding(0, dp(5), 0, dp(18)); panel.addView(helper);
        boolean anySelected = false; for (TrackChoice choice : choices) if (choice.group.isTrackSelected(choice.index)) anySelected = true;
        Button first = null; if (trackType == C.TRACK_TYPE_TEXT) { Button off = trackChoiceButton(s(R.string.subtitles_off), !anySelected); panel.addView(off, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))); first = off; off.setOnClickListener(v -> { if (player != null) { player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(trackType).setTrackTypeDisabled(trackType, true).build()); prefs.edit().putString("subtitle_language", "off").apply(); } dialog.dismiss(); }); }
        for (TrackChoice choice : choices) { Button option = trackChoiceButton(choice.label, choice.group.isTrackSelected(choice.index)); LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)); optionParams.topMargin = dp(6); panel.addView(option, optionParams); if (first == null) first = option; option.setOnClickListener(v -> { if (player != null) { player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(trackType).setTrackTypeDisabled(trackType, false).setOverrideForType(new TrackSelectionOverride(choice.group.getMediaTrackGroup(), choice.index)).build()); String language = choice.group.getTrackFormat(choice.index).language; if (language != null) prefs.edit().putString(trackType == C.TRACK_TYPE_AUDIO ? "audio_language" : "subtitle_language", language).apply(); } dialog.dismiss(); }); }
        if (choices.isEmpty() && trackType == C.TRACK_TYPE_AUDIO) { TextView empty = text(s(R.string.no_alternative_audio), 15, MUTED, false); empty.setPadding(0, dp(15), 0, dp(15)); panel.addView(empty); }
        dialog.setContentView(panel); dialog.setOnDismissListener(v -> { if (activePlayerView != null) activePlayerView.requestFocus(); }); Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams params = new WindowManager.LayoutParams(); params.copyFrom(window.getAttributes()); params.width = dp(560); params.height = WindowManager.LayoutParams.WRAP_CONTENT; params.dimAmount = .72f; window.setAttributes(params); } dialog.show(); if (first != null) first.requestFocus();
    }

    private Button trackChoiceButton(String label, boolean selected) { Button button = actionButton((selected ? "✓  " : "    ") + label, selected); button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); button.setPadding(dp(18), 0, dp(18), 0); return button; }

    private String trackLabel(Format format, int fallbackIndex) { String language = format.label; if (language == null || language.trim().isEmpty()) language = format.language == null || format.language.isEmpty() ? s(R.string.track_number, fallbackIndex) : new Locale(format.language).getDisplayLanguage(getResources().getConfiguration().locale); StringBuilder detail = new StringBuilder(); if (format.codecs != null && !format.codecs.isEmpty()) detail.append(format.codecs.toUpperCase(Locale.getDefault())); if (format.channelCount > 0) { if (detail.length() > 0) detail.append(" · "); detail.append(format.channelCount == 6 ? "5.1" : format.channelCount == 8 ? "7.1" : format.channelCount + ".0"); } return detail.length() == 0 ? language : language + "  ·  " + detail; }

    private static final class TrackChoice { final String label; final Tracks.Group group; final int index; TrackChoice(String label, Tracks.Group group, int index) { this.label = label; this.group = group; this.index = index; } }

    private final class ServerAudioTrack { final int index, channels; final String language, title, codec; final boolean isDefault; ServerAudioTrack(int index, String language, String title, String codec, int channels, boolean isDefault) { this.index = index; this.language = language == null ? "" : language; this.title = title == null ? "" : title; this.codec = codec == null ? "" : codec; this.channels = channels; this.isDefault = isDefault; } String label() { String name = title; if (name.isEmpty()) name = language.isEmpty() ? s(R.string.track_number, index + 1) : new Locale(language).getDisplayLanguage(getResources().getConfiguration().locale); List<String> details = new ArrayList<>(); if (!codec.isEmpty()) details.add(codec.toUpperCase(Locale.getDefault())); if (channels > 0) details.add(channels == 6 ? "5.1" : channels == 8 ? "7.1" : channels + ".0"); return details.isEmpty() ? name : name + "  ·  " + TextUtils.join(" · ", details); } }

    private String selectedTrackCode(int type) { if (player == null) return "—"; for (Tracks.Group group : player.getCurrentTracks().getGroups()) if (group.getType() == type) for (int i = 0; i < group.length; i++) if (group.isTrackSelected(i)) { String language = group.getTrackFormat(i).language; return language == null || language.isEmpty() ? s(R.string.automatic) : language.toUpperCase(Locale.getDefault()); } return type == C.TRACK_TYPE_TEXT ? s(R.string.no) : s(R.string.automatic); }

    private void showPlaybackError(PlaybackException error) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(s(R.string.playback_error_title)).setMessage(s(R.string.playback_error_message, error.getErrorCodeName())).setPositiveButton(s(R.string.retry), (d, w) -> { if (player != null) { player.prepare(); player.play(); } }).setNegativeButton(s(R.string.back), (d, w) -> { if (backAction != null) backAction.run(); }).setCancelable(false).show());
    }

    private void loadSkipSegments(String itemId) {
        int expectedGeneration = generation;
        io.execute(() -> { try {
            JSONArray items = request(server + "/MediaSegments/" + enc(itemId), "GET", null, token).optJSONArray("Items");
            List<SkipSegment> found = new ArrayList<>();
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject segment = items.optJSONObject(i); if (segment == null) continue;
                String type = segment.optString("Type"); String label;
                if ("Intro".equalsIgnoreCase(type)) label = s(R.string.skip_intro);
                else if ("Recap".equalsIgnoreCase(type)) label = s(R.string.skip_recap);
                else if ("Outro".equalsIgnoreCase(type) || "Credits".equalsIgnoreCase(type)) label = s(R.string.skip_credits);
                else continue;
                long start = segment.optLong("StartTicks") / 10000L, end = segment.optLong("EndTicks") / 10000L;
                if (end > start) found.add(new SkipSegment(start, end, label, type));
            }
            runOnUiThread(() -> { if (generation == expectedGeneration && player != null) { skipSegments.clear(); skipSegments.addAll(found); playbackHandler.removeCallbacks(skipSegmentWatcher); playbackHandler.post(skipSegmentWatcher); } });
        } catch (Exception ignored) {} });
    }

    private LinearLayout buildNextEpisodeCard() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(10), dp(10), dp(12), dp(10)); card.setBackground(stroked(Color.rgb(104, 113, 138), Color.argb(248, 14, 18, 28), 12, 1)); card.setVisibility(View.GONE);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setTag("next_image"); card.addView(image, new LinearLayout.LayoutParams(dp(180), dp(101)));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(14), 0, 0, 0); LinearLayout heading = new LinearLayout(this); TextView eyebrow = text(s(R.string.up_next), 10, ACCENT, true); heading.addView(eyebrow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); nextEpisodeCountdownText = text("10 s", 11, MUTED, true); heading.addView(nextEpisodeCountdownText); info.addView(heading); TextView title = text(s(R.string.next_episode), 15, Color.WHITE, true); title.setTag("next_title"); title.setMaxLines(1); info.addView(title);
        nextEpisodeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); nextEpisodeProgress.setMax(1000); nextEpisodeProgress.setProgress(1000); LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)); progressParams.topMargin = dp(7); progressParams.bottomMargin = dp(7); info.addView(nextEpisodeProgress, progressParams);
        LinearLayout actions = new LinearLayout(this); Button playNext = playerControlButton(s(R.string.watch_now), true); playNext.setTag("next_play"); playNext.setOnClickListener(v -> playNextEpisode()); Button cancel = playerControlButton(s(R.string.cancel), false); cancel.setOnClickListener(v -> cancelNextEpisode()); actions.addView(playNext, new LinearLayout.LayoutParams(dp(112), dp(38))); LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(104), dp(38)); cancelParams.leftMargin = dp(6); actions.addView(cancel, cancelParams); info.addView(actions); card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); return card;
    }

    private void loadNextEpisode(String itemId) {
        int expectedGeneration = generation;
        io.execute(() -> { try {
            JSONObject current = request(server + "/Users/" + userId + "/Items/" + enc(itemId), "GET", null, token); String seriesId = current.optString("SeriesId"); if (seriesId.isEmpty()) return;
            String path = "/Users/" + userId + "/Items?Recursive=true&ParentId=" + enc(seriesId) + "&IncludeItemTypes=Episode&SortBy=ParentIndexNumber,IndexNumber&Fields=Overview,Genres,BackdropImageTags,MediaSources&Limit=500";
            List<JSONObject> episodes = jsonList(request(server + path, "GET", null, token).getJSONArray("Items")); JSONObject following = null;
            for (int i = 0; i + 1 < episodes.size(); i++) if (itemId.equals(episodes.get(i).optString("Id"))) { following = episodes.get(i + 1); break; }
            JSONObject result = following; runOnUiThread(() -> { if (generation == expectedGeneration) { nextEpisode = result; if (activeSkipSegment != null && "Outro".equalsIgnoreCase(activeSkipSegment.type)) showNextEpisodeCard(); } });
        } catch (Exception ignored) {} });
    }

    private void showNextEpisodeCard() {
        if (nextEpisode == null || nextEpisodeCard == null || nextEpisodeCard.getVisibility() == View.VISIBLE) return;
        TextView title = nextEpisodeCard.findViewWithTag("next_title"); ImageView image = nextEpisodeCard.findViewWithTag("next_image"); title.setText("T" + nextEpisode.optInt("ParentIndexNumber") + " E" + nextEpisode.optInt("IndexNumber") + "  ·  " + nextEpisode.optString("Name")); loadImage(image, nextEpisode.optString("Id"), "Primary", 500);
        nextEpisodeCard.setVisibility(View.VISIBLE); skipButton.setVisibility(View.GONE); boolean autoplay = prefs.getBoolean("autoplay", true); nextEpisodeProgress.setVisibility(autoplay ? View.VISIBLE : View.INVISIBLE); nextEpisodeCountdownText.setText(autoplay ? "10 s" : s(R.string.manual)); playbackHandler.removeCallbacks(nextEpisodeCountdown); if (autoplay) { nextEpisodeDeadline = android.os.SystemClock.uptimeMillis() + 10000; playbackHandler.post(nextEpisodeCountdown); } Button playNext = nextEpisodeCard.findViewWithTag("next_play"); playNext.requestFocus();
    }

    private void cancelNextEpisode() { playbackHandler.removeCallbacks(nextEpisodeCountdown); if (nextEpisodeCard != null) nextEpisodeCard.setVisibility(View.GONE); if (activePlayerView != null) activePlayerView.requestFocus(); }

    private void playNextEpisode() { if (nextEpisode == null) return; JSONObject episode = nextEpisode; Runnable returnAction = currentPlayerReturnAction; playbackHandler.removeCallbacks(nextEpisodeCountdown); play(episode.optString("Id"), mediaSourceId(episode), episode.optString("Name"), 0, returnAction); }

    private void beginPlaybackReporting() { playbackReported = true; reportPlayback("/Sessions/Playing", player == null ? 0 : player.getCurrentPosition(), true); playbackHandler.removeCallbacks(progressReporter); playbackHandler.postDelayed(progressReporter, 10000); }

    private void reportPlayback(String endpoint, long positionMs, boolean isPlayingNow) {
        if (playingItemId == null) return; String item = playingItemId, source = playingSourceId, session = playSessionId, method = currentPlayMethod;
        io.execute(() -> { try { JSONObject body = new JSONObject().put("ItemId", item).put("MediaSourceId", source).put("PlaySessionId", session).put("PositionTicks", Math.max(0, positionMs) * 10000L).put("CanSeek", true).put("IsPaused", !isPlayingNow).put("PlayMethod", method); request(server + endpoint, "POST", body.toString(), token); } catch (Exception ignored) {} });
    }

    private void returnToCurrentSection() { if ("search".equals(navigation.section())) showSearch(); else { navigation.requestRestore(); showBrowse(navigation.section()); } }

    private Button actionButton(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false); b.setFocusable(true); int idleFill = primary ? Color.rgb(49, 57, 75) : SURFACE_LIGHT; int idleStroke = primary ? Color.rgb(91, 101, 124) : Color.rgb(73, 81, 101); b.setBackground(stroked(idleStroke, idleFill, 9, 1));
        b.setOnFocusChangeListener((v, focused) -> { b.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).translationZ(focused ? dp(7) : 0).setDuration(120).start(); b.setTextColor(focused ? BG : Color.WHITE); b.setBackground(stroked(focused ? Color.WHITE : idleStroke, focused ? Color.WHITE : idleFill, 9, focused ? 2 : 1)); if (focused) ensureFullyVisible(b, 8); }); return b;
    }

    private ImageView brandMark() { ImageView mark = new ImageView(this); mark.setImageResource(R.drawable.tutaua_horse); mark.setScaleType(ImageView.ScaleType.FIT_CENTER); return mark; }

    private View errorView(String message, Exception e) { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(40), dp(180), dp(40), 0); box.addView(text(message, 25, Color.WHITE, true)); TextView detail = text(friendly(e), 15, MUTED, false); detail.setPadding(0, dp(12), 0, 0); box.addView(detail); return box; }

    private List<JSONObject> jsonList(JSONArray array) { List<JSONObject> result = new ArrayList<>(); for (int i = 0; i < array.length(); i++) { JSONObject o = array.optJSONObject(i); if (o != null) result.add(o); } return result; }
    private boolean hasImage(JSONObject item, String type) {
        JSONObject tags = item.optJSONObject("ImageTags");
        return tags != null && tags.has(type) && !tags.optString(type).isEmpty();
    }
    private JSONObject chooseFeatured(List<JSONObject> items) {
        for (JSONObject item : items) if (hasImage(item, "Banner")) return item;
        for (JSONObject item : items) if (item.optJSONArray("BackdropImageTags") != null && item.optJSONArray("BackdropImageTags").length() > 0) return item;
        return items.get(0);
    }
    private JSONObject chooseFeaturedExcluding(List<JSONObject> items, String excludedId) {
        if (excludedId != null && !excludedId.isEmpty()) {
            for (JSONObject item : items) if (!excludedId.equals(item.optString("Id")) && hasImage(item, "Banner")) return item;
            for (JSONObject item : items) if (!excludedId.equals(item.optString("Id")) && item.optJSONArray("BackdropImageTags") != null && item.optJSONArray("BackdropImageTags").length() > 0) return item;
        }
        if (items.size() > 1) return items.get(1);
        return chooseFeatured(items);
    }
    private List<JSONObject> filterType(List<JSONObject> all, String type) { List<JSONObject> out = new ArrayList<>(); for (JSONObject i : all) if (type.equals(i.optString("Type"))) out.add(i); return out; }
    private List<JSONObject> filterResume(List<JSONObject> all) { List<JSONObject> out = new ArrayList<>(); for (JSONObject i : all) { JSONObject u = i.optJSONObject("UserData"); if (u != null && u.optLong("PlaybackPositionTicks") > 0 && !u.optBoolean("Played")) out.add(i); } return out; }
    private List<JSONObject> filterUnseen(List<JSONObject> all) { List<JSONObject> out = new ArrayList<>(); for (JSONObject i : all) { JSONObject u = i.optJSONObject("UserData"); if (u == null || (!u.optBoolean("Played") && u.optLong("PlaybackPositionTicks") == 0)) out.add(i); } return out; }
    private String mostCommonGenre(List<JSONObject> all) { java.util.Map<String, Integer> counts = new java.util.HashMap<>(); for (JSONObject item : all) { JSONArray genres = item.optJSONArray("Genres"); if (genres != null) for (int i = 0; i < genres.length(); i++) { String genre = genres.optString(i); if (!genre.isEmpty()) { Integer previous = counts.get(genre); counts.put(genre, previous == null ? 1 : previous + 1); } } } String best = "Drama"; int maximum = 0; for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) if (entry.getValue() > maximum) { best = entry.getKey(); maximum = entry.getValue(); } return best; }
    private List<JSONObject> filterGenre(List<JSONObject> all, String genre) { List<JSONObject> result = new ArrayList<>(); for (JSONObject item : all) { JSONArray genres = item.optJSONArray("Genres"); if (genres != null) for (int i = 0; i < genres.length(); i++) if (genre.equals(genres.optString(i))) { result.add(item); break; } } return result; }
    private List<JSONObject> filterFavorites(List<JSONObject> all) { List<JSONObject> result = new ArrayList<>(); for (JSONObject item : all) { JSONObject data = item.optJSONObject("UserData"); if (data != null && data.optBoolean("IsFavorite")) result.add(item); } return result; }
    private List<JSONObject> filterStarted(List<JSONObject> all) { List<JSONObject> result = new ArrayList<>(); for (JSONObject item : all) { JSONObject data = item.optJSONObject("UserData"); if (data != null && data.optLong("PlaybackPositionTicks") > 0 && !data.optBoolean("Played")) result.add(item); } return result; }
    private List<JSONObject> filterPlayed(List<JSONObject> all) { List<JSONObject> result = new ArrayList<>(); for (JSONObject item : all) { JSONObject data = item.optJSONObject("UserData"); if (data != null && data.optBoolean("Played")) result.add(item); } return result; }
    private List<JSONObject> mergeContinue(List<JSONObject> resume, List<JSONObject> nextUp) {
        List<JSONObject> result = new ArrayList<>(); Set<String> keys = new LinkedHashSet<>();
        for (JSONObject item : resume) { String key = continueKey(item); if (keys.add(key)) result.add(item); }
        for (JSONObject item : nextUp) { String key = continueKey(item); if (keys.add(key)) result.add(item); }
        return result;
    }
    private String continueKey(JSONObject item) { String seriesId = item.optString("SeriesId"); return seriesId.isEmpty() ? item.optString("Id") : "series:" + seriesId; }
    private List<JSONObject> excludeContinued(List<JSONObject> source, List<JSONObject> continued) {
        Set<String> keys = new LinkedHashSet<>(); for (JSONObject item : continued) keys.add(continueKey(item));
        List<JSONObject> result = new ArrayList<>(); for (JSONObject item : source) { String key = "Series".equals(item.optString("Type")) ? "series:" + item.optString("Id") : item.optString("Id"); if (!keys.contains(key)) result.add(item); } return result;
    }
    private long resumePositionMs(JSONObject item) { JSONObject data = item.optJSONObject("UserData"); return data == null ? 0 : data.optLong("PlaybackPositionTicks") / 10000L; }
    private String mediaSourceId(JSONObject item) { JSONArray sources = item.optJSONArray("MediaSources"); if (sources != null && sources.length() > 0 && sources.optJSONObject(0) != null) { String id = sources.optJSONObject(0).optString("Id"); if (!id.isEmpty()) return id; } return item.optString("Id"); }

    private String metadataLine(JSONObject item) {
        StringBuilder b = new StringBuilder(); int year = item.optInt("ProductionYear"); if (year > 0) b.append(year);
        long ticks = item.optLong("RunTimeTicks"); if (ticks > 0) appendMeta(b, formatRuntime(ticks)); String rating = item.optString("OfficialRating"); if (!rating.isEmpty()) appendMeta(b, rating);
        double score = item.optDouble("CommunityRating"); if (score > 0) appendMeta(b, String.format(Locale.getDefault(), "★ %.1f", score));
        String quality = videoQuality(item); if (!quality.isEmpty()) appendMeta(b, quality);
        String series = item.optString("SeriesName"); if (!series.isEmpty()) appendMeta(b, "T" + item.optInt("ParentIndexNumber") + " E" + item.optInt("IndexNumber")); return b.toString();
    }
    private void appendMeta(StringBuilder b, String value) { if (b.length() > 0) b.append("  ·  "); b.append(value); }
    private String formatRuntime(long ticks) { long minutes = ticks / 600000000L; if (minutes <= 0) return ""; return minutes >= 60 ? (minutes / 60) + " h " + (minutes % 60) + " min" : minutes + " min"; }
    private String formatPosition(long milliseconds) { if (milliseconds < 0) milliseconds = 0; long seconds = milliseconds / 1000, hours = seconds / 3600, minutes = (seconds % 3600) / 60; return hours > 0 ? String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds % 60) : String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds % 60); }
    private String joinJson(JSONArray values, String separator) { if (values == null) return ""; StringBuilder b = new StringBuilder(); for (int i = 0; i < values.length(); i++) { if (b.length() > 0) b.append(separator); b.append(values.optString(i)); } return b.toString(); }
    private String videoQuality(JSONObject item) { JSONArray sources = item.optJSONArray("MediaSources"); if (sources == null || sources.length() == 0) return ""; JSONArray streams = sources.optJSONObject(0).optJSONArray("MediaStreams"); if (streams == null) return ""; for (int i = 0; i < streams.length(); i++) { JSONObject stream = streams.optJSONObject(i); if (stream != null && "Video".equals(stream.optString("Type"))) { int width = stream.optInt("Width"); if (width >= 3800) return "4K"; if (width >= 1900) return "Full HD"; if (width >= 1200) return "HD"; } } return ""; }

    private void loadImage(ImageView target, String id, String type, int maxWidth) {
        String primary = server + "/Items/" + id + "/Images/Primary?maxWidth=" + maxWidth + "&quality=86&api_key=" + enc(token);
        String requested = server + "/Items/" + id + "/Images/" + type + "?maxWidth=" + maxWidth + "&quality=86&api_key=" + enc(token);
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request = Glide.with(this).load(requested).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).override(maxWidth, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL);
        if (!"Primary".equals(type)) request = request.error(Glide.with(this).load(primary).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).override(maxWidth, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL));
        request.into(target);
    }

    private void loadBannerImage(ImageView target, String id, int maxWidth) {
        String suffix = "?maxWidth=" + maxWidth + "&quality=88&api_key=" + enc(token);
        String banner = server + "/Items/" + id + "/Images/Banner" + suffix;
        String backdrop = server + "/Items/" + id + "/Images/Backdrop" + suffix;
        String primary = server + "/Items/" + id + "/Images/Primary" + suffix;
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> primaryRequest = Glide.with(this).load(primary).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).override(maxWidth, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL);
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> backdropRequest = Glide.with(this).load(backdrop).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).override(maxWidth, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL).error(primaryRequest);
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(banner).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).override(maxWidth, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL).error(backdropRequest).into(target);
    }

    private void loadOptionalLogo(ImageView target, TextView fallback, String id) {
        io.execute(() -> { HttpURLConnection c = null; try { c = (HttpURLConnection) new URL(server + "/Items/" + id + "/Images/Logo?maxWidth=780&quality=90").openConnection(); c.setRequestProperty("X-Emby-Token", token); c.setConnectTimeout(5000); c.setReadTimeout(9000); if (c.getResponseCode() != 200) return; try (InputStream in = c.getInputStream()) { Bitmap bitmap = BitmapFactory.decodeStream(in); if (bitmap != null) runOnUiThread(() -> { target.setImageBitmap(bitmap); target.setVisibility(View.VISIBLE); fallback.setVisibility(View.GONE); }); } } catch (Exception ignored) {} finally { if (c != null) c.disconnect(); } });
    }

    private JSONObject request(String url, String method, String body, String authToken) throws Exception { return JellyfinClient.request(url, method, body, authToken, authHeader()); }

    @Override public void onBackPressed() { if (backAction != null) backAction.run(); else if (!"home".equals(navigation.section()) && !token.isEmpty()) showBrowse("home"); else super.onBackPressed(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { if (adminEscape.dispatch(event)) return true; if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && playing) { if (nextEpisodeCard != null && nextEpisodeCard.getVisibility() == View.VISIBLE) cancelNextEpisode(); else if (backAction != null) backAction.run(); return true; } return super.dispatchKeyEvent(event); }
    @Override protected void onUserLeaveHint() {
        returnHomeOnResume = true;
        super.onUserLeaveHint();
    }
    private String authHeader() { return "MediaBrowser Client=\"" + AppIdentity.NAME + "\", Device=\"Android TV\", DeviceId=\"" + deviceId() + "\", Version=\"" + AppIdentity.VERSION + "\""; }
    private String deviceId() {
        String id = prefs.getString("device_id", "");
        if (!id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        prefs.edit().putString("device_id", id).apply();
        return id;
    }
    private void releasePlayer() { playbackHandler.removeCallbacks(progressReporter); playbackHandler.removeCallbacks(skipSegmentWatcher); playbackHandler.removeCallbacks(controlsUpdater); playbackHandler.removeCallbacks(nextEpisodeCountdown); playbackHandler.removeCallbacks(hidePlayerControls); playbackHandler.removeCallbacks(hideSeekFeedback); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); if (player != null) { long position = player.getCurrentPosition(); if (playbackReported) reportPlayback("/Sessions/Playing/Stopped", position, false); player.release(); player = null; } skipSegments.clear(); skipButton = null; seekFeedback = null; playbackDiagnostics = null; activePlayerView = null; activeSkipSegment = null; playerControls = null; playerTime = null; playerTitle = null; playerPlayButton = null; playerAudioButton = null; playerSubtitleButton = null; playerQualityButton = null; playerInfoButton = null; playerSeekProgress = null; nextEpisode = null; nextEpisodeCard = null; nextEpisodeProgress = null; nextEpisodeCountdownText = null; currentPlayerReturnAction = null; playbackReported = false; playingItemId = null; playingSourceId = null; playSessionId = null; currentPlayMethod = null; playbackFallbackUrl = null; fallbackAttempted = false; }
    private void logout() { secureTokenStore.clear(); prefs.edit().remove("userId").apply(); token = ""; userId = ""; showLogin(); }
    private void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private String friendly(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private String enc(String value) { return Uri.encode(value); }
    private void ensureFullyVisible(View view, int marginDp) {
        view.post(() -> {
            int margin = dp(marginDp);
            Rect requested = new Rect(); view.getDrawingRect(requested); requested.inset(-margin, -margin);
            view.requestRectangleOnScreen(requested, true);
            android.view.ViewParent parent = view.getParent();
            while (parent instanceof View) {
                if (parent instanceof ScrollView) {
                    ScrollView vertical = (ScrollView) parent;
                    int[] itemLocation = new int[2], scrollLocation = new int[2];
                    view.getLocationOnScreen(itemLocation); vertical.getLocationOnScreen(scrollLocation);
                    int itemTop = itemLocation[1] - margin, itemBottom = itemLocation[1] + view.getHeight() + margin;
                    int viewportTop = scrollLocation[1], viewportBottom = viewportTop + vertical.getHeight();
                    int delta = itemBottom > viewportBottom ? itemBottom - viewportBottom : itemTop < viewportTop ? itemTop - viewportTop : 0;
                    if (delta != 0) vertical.scrollBy(0, delta);
                    break;
                }
                parent = parent.getParent();
            }
        });
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable stroked(int stroke, int fill, int radius, int width) { GradientDrawable d = rounded(fill, radius); if (width > 0) d.setStroke(dp(width), stroke); return d; }
    private GradientDrawable horizontalGradient(int start, int center, int end) { return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, center, end}); }
    private GradientDrawable verticalGradient(int start, int end) { return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{start, end}); }
    @Override protected void onStop() {
        if (returnHomeOnResume) releasePlayer();
        else if (player != null && player.isPlaying()) player.pause();
        playbackHandler.removeCallbacks(nextEpisodeCountdown);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        if (returnHomeOnResume && !token.isEmpty()) {
            returnHomeOnResume = false;
            showBrowse("home");
        }
    }

    @Override protected void onDestroy() { adminEscape.cancel(); releasePlayer(); io.shutdownNow(); super.onDestroy(); }
}
