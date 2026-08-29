package com.yukista.tutaua.box;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

public final class AdminActivity extends Activity {
    private static final int BG=Color.rgb(7,10,18), SURFACE=Color.rgb(18,24,37), FIELD=Color.rgb(29,36,52), MUTED=Color.rgb(150,160,184);
    private EditText jellyfin, tvApi, tvStream, updates, channel, timeout;
    private Button back, save, libraryKey, liveTvKey, gamesKey, captureTarget;
    private Switch gamesEnabled;
    private boolean consumeCapturedKeyUp;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); immersive(); getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        Map<String,String> config=BoxConfig.values(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(64),dp(28),dp(64),dp(26)); page.setBackgroundColor(BG); page.setFocusableInTouchMode(true);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); TextView title=text(getString(R.string.admin_title),30,Color.WHITE,true); names.addView(title); names.addView(text(getString(R.string.admin_subtitle),14,MUTED,false)); header.addView(names);
        header.addView(new View(this),new LinearLayout.LayoutParams(0,1,1)); back=button(getString(R.string.back),false); back.setOnClickListener(v->finish()); header.addView(back,new LinearLayout.LayoutParams(dp(190),dp(52))); page.addView(header,new LinearLayout.LayoutParams(-1,dp(72)));

        LinearLayout columns=new LinearLayout(this); LinearLayout.LayoutParams columnsParams=new LinearLayout.LayoutParams(-1,0,1); columnsParams.topMargin=dp(18); page.addView(columns,columnsParams);
        LinearLayout services=panel(getString(R.string.services),getString(R.string.services_helper));
        jellyfin=field(services,getString(R.string.jellyfin_server),config.get(BoxConfig.JELLYFIN_URL),getString(R.string.jellyfin_hint),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        tvApi=field(services,getString(R.string.tv_api),config.get(BoxConfig.TV_API_URL),getString(R.string.tv_api_hint),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        tvStream=field(services,getString(R.string.tv_stream),config.get(BoxConfig.TV_STREAM_URL),getString(R.string.tv_stream_hint),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout device=panel(getString(R.string.device_updates),getString(R.string.device_updates_helper));
        updates=field(device,getString(R.string.update_manifest),config.get(BoxConfig.UPDATE_MANIFEST_URL),getString(R.string.update_manifest_hint),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        channel=field(device,getString(R.string.update_channel),config.get(BoxConfig.UPDATE_CHANNEL),getString(R.string.update_channel_hint),InputType.TYPE_CLASS_TEXT);
        timeout=field(device,getString(R.string.screen_timeout),config.get(BoxConfig.SCREEN_TIMEOUT_MINUTES),getString(R.string.screen_timeout_hint),InputType.TYPE_CLASS_NUMBER);
        libraryKey=keyAssignmentField(device,getString(R.string.library_button),BoxConfig.keyCodeOrDefault(config.get(BoxConfig.LIBRARY_KEYCODE),BoxConfig.DEFAULT_LIBRARY_KEYCODE));
        liveTvKey=keyAssignmentField(device,getString(R.string.live_tv_button),BoxConfig.keyCodeOrDefault(config.get(BoxConfig.LIVE_TV_KEYCODE),BoxConfig.DEFAULT_LIVE_TV_KEYCODE));
        gamesKey=keyAssignmentField(device,getString(R.string.games_button),BoxConfig.keyCodeOrDefault(config.get(BoxConfig.GAMES_KEYCODE),BoxConfig.DEFAULT_GAMES_KEYCODE));
        gamesEnabled = new Switch(this); gamesEnabled.setText(getString(R.string.games_enabled)); gamesEnabled.setTextColor(Color.WHITE); gamesEnabled.setTextSize(14); gamesEnabled.setChecked(Boolean.parseBoolean(config.get(BoxConfig.GAMES_ENABLED))); gamesEnabled.setFocusable(true); LinearLayout.LayoutParams gep=new LinearLayout.LayoutParams(-1,dp(52)); gep.topMargin=dp(8); device.addView(gamesEnabled,gep);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1); left.rightMargin=dp(10); LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1); right.leftMargin=dp(10); columns.addView(services,left); columns.addView(device,right);

        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(66)); ap.topMargin=dp(18); page.addView(actions,ap);
        Button androidSettings=button(getString(R.string.android_settings),false); androidSettings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_SETTINGS))); actions.addView(androidSettings,new LinearLayout.LayoutParams(dp(270),dp(54)));
        save=button(getString(R.string.save_changes),true); save.setOnClickListener(v->save()); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(250),dp(54)); sp.leftMargin=dp(14); actions.addView(save,sp);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(page); setContentView(scroll);
        back.requestFocus(); back.post(() -> { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(back.getWindowToken(),0); immersive(); });
    }

    private LinearLayout panel(String heading,String helper) { LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(20),dp(24),dp(18)); box.setBackground(background(SURFACE,Color.rgb(50,59,79),16,1)); TextView h=text(heading,12,Color.rgb(176,157,255),true); h.setLetterSpacing(.12f); box.addView(h); TextView d=text(helper,13,MUTED,false); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(4); p.bottomMargin=dp(8); box.addView(d,p); return box; }
    private EditText field(LinearLayout form,String label,String value,String hint,int inputType) { TextView caption=text(label,13,Color.rgb(208,214,228),true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(11); form.addView(caption,lp); EditText input=new EditText(this); input.setSingleLine(); input.setText(value); input.setHint(hint); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.rgb(103,113,138)); input.setTextSize(14); input.setInputType(inputType); input.setPadding(dp(13),0,dp(13),0); input.setBackground(background(FIELD,Color.rgb(55,65,88),9,1)); input.setOnFocusChangeListener((v,focused)->input.setBackground(background(FIELD,focused?Color.WHITE:Color.rgb(55,65,88),9,focused?2:1))); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(46)); fp.topMargin=dp(6); form.addView(input,fp); return input; }
    private void save() {
        if(!BoxConfig.validUrl(jellyfin.getText().toString(),false)||!BoxConfig.validUrl(tvApi.getText().toString(),false)||!BoxConfig.validUrl(tvStream.getText().toString(),true)||!BoxConfig.validUrl(updates.getText().toString(),true)){toast(R.string.invalid_urls);return;}
        String updateChannel=channel.getText().toString().trim(); if(!BoxConfig.validChannel(updateChannel)){toast(R.string.invalid_update_channel);return;}
        int minutes; try{minutes=Integer.parseInt(timeout.getText().toString().trim());}catch(Exception e){toast(R.string.invalid_screen_timeout);return;} if(!BoxConfig.validTimeoutMinutes(minutes)){toast(R.string.invalid_screen_timeout);return;}
        int libraryCode=(Integer)libraryKey.getTag(),liveTvCode=(Integer)liveTvKey.getTag(),gamesCode=(Integer)gamesKey.getTag();if(hasDuplicateNonZero(libraryCode,liveTvCode,gamesCode)){toast(R.string.duplicate_remote_button);return;}
        boolean stored=BoxConfig.preferences(this).edit().putString(BoxConfig.JELLYFIN_URL,BoxConfig.trimUrl(jellyfin.getText().toString())).putString(BoxConfig.TV_API_URL,BoxConfig.trimUrl(tvApi.getText().toString())).putString(BoxConfig.TV_STREAM_URL,tvStream.getText().toString().trim()).putString(BoxConfig.UPDATE_MANIFEST_URL,updates.getText().toString().trim()).putString(BoxConfig.UPDATE_CHANNEL,updateChannel).putString(BoxConfig.SCREEN_TIMEOUT_MINUTES,String.valueOf(minutes)).putString(BoxConfig.LIBRARY_KEYCODE,String.valueOf(libraryCode)).putString(BoxConfig.LIVE_TV_KEYCODE,String.valueOf(liveTvCode)).putString(BoxConfig.GAMES_KEYCODE,String.valueOf(gamesCode)).putBoolean(BoxConfig.GAMES_ENABLED,gamesEnabled.isChecked()).commit();
        if(!stored){toast(R.string.save_failed);return;} getContentResolver().notifyChange(ConfigProvider.URI,null); save.setEnabled(false); save.setText(R.string.applying); final int selected=minutes;
        new Thread(()->{boolean applied=DeviceSettings.applyScreenTimeoutMinutes(selected); runOnUiThread(()->{save.setEnabled(true);save.setText(R.string.save_changes);toast(applied?R.string.saved:R.string.saved_timeout_failed);});},"box-save-settings").start();
    }
    private boolean hasDuplicateNonZero(int first,int second,int third){return first!=0&&(first==second||first==third)||second!=0&&second==third;}
    private Button button(String value,boolean primary){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);int idle=primary?Color.rgb(104,76,228):Color.rgb(34,42,60);b.setBackground(background(idle,primary?Color.rgb(145,120,255):Color.rgb(66,77,103),10,1));b.setOnFocusChangeListener((v,f)->{b.setTextColor(f?BG:Color.WHITE);b.setBackground(background(f?Color.WHITE:idle,f?Color.WHITE:Color.rgb(66,77,103),10,f?2:1));});return b;}
    private Button keyAssignmentField(LinearLayout form,String label,int keyCode){TextView caption=text(label,13,Color.rgb(208,214,228),true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(11);form.addView(caption,lp);Button value=button(keyCodeLabel(keyCode),false);value.setTag(keyCode);value.setOnClickListener(v->{captureTarget=value;value.setText(R.string.press_remote_button);});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(46));bp.topMargin=dp(6);form.addView(value,bp);return value;}
    private String keyCodeLabel(int keyCode){return keyCode==0?getString(R.string.remote_button_disabled):getString(R.string.remote_button_assigned,KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_",""));}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(consumeCapturedKeyUp&&event.getAction()==KeyEvent.ACTION_UP){consumeCapturedKeyUp=false;return true;}if(captureTarget!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){Button target=captureTarget;captureTarget=null;consumeCapturedKeyUp=true;if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){target.setText(keyCodeLabel((Integer)target.getTag()));toast(R.string.remote_capture_cancelled);}else{target.setTag(event.getKeyCode());target.setText(keyCodeLabel(event.getKeyCode()));toast(R.string.remote_button_captured);}return true;}if(captureTarget!=null)return true;return super.dispatchKeyEvent(event);}
    private GradientDrawable background(int fill,int stroke,int radius,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(width),stroke);return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private void toast(int stringId){Toast.makeText(this,stringId,Toast.LENGTH_LONG).show();}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
