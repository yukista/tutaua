package com.yukista.tutaua.box;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

public final class AdminActivity extends Activity {
    private static final int BG=Color.rgb(7,10,18), SURFACE=Color.rgb(18,24,37), FIELD=Color.rgb(29,36,52), MUTED=Color.rgb(150,160,184);
    private EditText jellyfin, tvApi, tvStream, updates, channel, timeout;
    private Button back, save;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); immersive(); getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        Map<String,String> config=BoxConfig.values(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(64),dp(28),dp(64),dp(26)); page.setBackgroundColor(BG); page.setFocusableInTouchMode(true);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); TextView title=text("Administració",30,Color.WHITE,true); names.addView(title); names.addView(text("Configuració central de Tutaua Box",14,MUTED,false)); header.addView(names);
        header.addView(new View(this),new LinearLayout.LayoutParams(0,1,1)); back=button("←  Tornar",false); back.setOnClickListener(v->finish()); header.addView(back,new LinearLayout.LayoutParams(dp(190),dp(52))); page.addView(header,new LinearLayout.LayoutParams(-1,dp(72)));

        LinearLayout columns=new LinearLayout(this); LinearLayout.LayoutParams columnsParams=new LinearLayout.LayoutParams(-1,0,1); columnsParams.topMargin=dp(18); page.addView(columns,columnsParams);
        LinearLayout services=panel("SERVEIS","Connexions utilitzades per les aplicacions");
        jellyfin=field(services,"Servidor Jellyfin",config.get(BoxConfig.JELLYFIN_URL),"http://servidor:8096",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        tvApi=field(services,"API de televisió",config.get(BoxConfig.TV_API_URL),"http://servidor:5001",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        tvStream=field(services,"Stream TV (opcional)",config.get(BoxConfig.TV_STREAM_URL),"Es genera des de l’API si queda buit",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout device=panel("DISPOSITIU I ACTUALITZACIONS","Comportament i manteniment de la caixa");
        updates=field(device,"Manifest d’actualitzacions",config.get(BoxConfig.UPDATE_MANIFEST_URL),"URL del catàleg",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        channel=field(device,"Canal d’actualització",config.get(BoxConfig.UPDATE_CHANNEL),"stable",InputType.TYPE_CLASS_TEXT);
        timeout=field(device,"Repòs de pantalla (minuts)",config.get(BoxConfig.SCREEN_TIMEOUT_MINUTES),"1–120 minuts",InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1); left.rightMargin=dp(10); LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1); right.leftMargin=dp(10); columns.addView(services,left); columns.addView(device,right);

        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(66)); ap.topMargin=dp(18); page.addView(actions,ap);
        Button androidSettings=button("Configuració Android",false); androidSettings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_SETTINGS))); actions.addView(androidSettings,new LinearLayout.LayoutParams(dp(270),dp(54)));
        save=button("Desar canvis",true); save.setOnClickListener(v->save()); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(250),dp(54)); sp.leftMargin=dp(14); actions.addView(save,sp);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(page); setContentView(scroll);
        back.requestFocus(); back.post(() -> { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(back.getWindowToken(),0); immersive(); });
    }

    private LinearLayout panel(String heading,String helper) { LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(20),dp(24),dp(18)); box.setBackground(background(SURFACE,Color.rgb(50,59,79),16,1)); TextView h=text(heading,12,Color.rgb(176,157,255),true); h.setLetterSpacing(.12f); box.addView(h); TextView d=text(helper,13,MUTED,false); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(4); p.bottomMargin=dp(8); box.addView(d,p); return box; }
    private EditText field(LinearLayout form,String label,String value,String hint,int inputType) { TextView caption=text(label,13,Color.rgb(208,214,228),true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(11); form.addView(caption,lp); EditText input=new EditText(this); input.setSingleLine(); input.setText(value); input.setHint(hint); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.rgb(103,113,138)); input.setTextSize(14); input.setInputType(inputType); input.setPadding(dp(13),0,dp(13),0); input.setBackground(background(FIELD,Color.rgb(55,65,88),9,1)); input.setOnFocusChangeListener((v,focused)->input.setBackground(background(FIELD,focused?Color.WHITE:Color.rgb(55,65,88),9,focused?2:1))); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(46)); fp.topMargin=dp(6); form.addView(input,fp); return input; }
    private void save() {
        if(!validUrl(jellyfin.getText().toString(),true)||!validUrl(tvApi.getText().toString(),false)||!validUrl(tvStream.getText().toString(),true)||!validUrl(updates.getText().toString(),true)){toast("Revisa les URLs");return;}
        String updateChannel=channel.getText().toString().trim(); if(!updateChannel.matches("[a-zA-Z0-9._-]{1,32}")){toast("Canal d’actualització no vàlid");return;}
        int minutes; try{minutes=Integer.parseInt(timeout.getText().toString().trim());}catch(Exception e){toast("El repòs ha de ser entre 1 i 120 minuts");return;} if(minutes<1||minutes>120){toast("El repòs ha de ser entre 1 i 120 minuts");return;}
        boolean stored=BoxConfig.preferences(this).edit().putString(BoxConfig.JELLYFIN_URL,trim(jellyfin)).putString(BoxConfig.TV_API_URL,trim(tvApi)).putString(BoxConfig.TV_STREAM_URL,tvStream.getText().toString().trim()).putString(BoxConfig.UPDATE_MANIFEST_URL,updates.getText().toString().trim()).putString(BoxConfig.UPDATE_CHANNEL,updateChannel).putString(BoxConfig.SCREEN_TIMEOUT_MINUTES,String.valueOf(minutes)).commit();
        if(!stored){toast("No s’ha pogut desar la configuració");return;} getContentResolver().notifyChange(ConfigProvider.URI,null); save.setEnabled(false); save.setText("Aplicant…"); final int selected=minutes;
        new Thread(()->{boolean applied=DeviceSettings.applyScreenTimeoutMinutes(selected); runOnUiThread(()->{save.setEnabled(true);save.setText("Desar canvis");toast(applied?"Configuració desada":"Configuració desada, però no s’ha pogut aplicar el repòs");});},"box-save-settings").start();
    }
    private String trim(EditText value){return value.getText().toString().trim().replaceAll("/+$","");}
    private boolean validUrl(String value,boolean emptyAllowed){value=value.trim();if(value.isEmpty())return emptyAllowed;try{Uri uri=Uri.parse(value);return uri.getHost()!=null&&("https".equals(uri.getScheme())||"http".equals(uri.getScheme()));}catch(Exception ignored){return false;}}
    private Button button(String value,boolean primary){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);int idle=primary?Color.rgb(104,76,228):Color.rgb(34,42,60);b.setBackground(background(idle,primary?Color.rgb(145,120,255):Color.rgb(66,77,103),10,1));b.setOnFocusChangeListener((v,f)->{b.setTextColor(f?BG:Color.WHITE);b.setBackground(background(f?Color.WHITE:idle,f?Color.WHITE:Color.rgb(66,77,103),10,f?2:1));});return b;}
    private GradientDrawable background(int fill,int stroke,int radius,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(width),stroke);return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
