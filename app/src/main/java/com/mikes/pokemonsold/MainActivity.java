package com.mikes.pokemonsold;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String CHANNEL = "pokemon_sold_candidates";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> jobs = new ArrayList<>();
    private FrameLayout root;
    private WebView appWeb, ebayWeb, scanWeb;
    private LinearLayout browserBar;
    private JSONObject currentJob;
    private String browserTracker = "", browserSearch = "";
    private boolean scanning = false, monitor = false;
    private int jobIndex = 0, monitorMinutes = 30;
    private Runnable periodic, timeout;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(21,31,51));
        getWindow().setNavigationBarColor(Color.rgb(13,18,32));
        root = new FrameLayout(this);
        setContentView(root);

        appWeb = createWebView();
        appWeb.getSettings().setAllowFileAccess(true);
        appWeb.addJavascriptInterface(new Bridge(),"AndroidBridge");
        appWeb.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                if("file".equalsIgnoreCase(r.getUrl().getScheme())) return false;
                openExternal(r.getUrl().toString()); return true;
            }
        });
        root.addView(appWeb,new FrameLayout.LayoutParams(-1,-1));
        appWeb.loadUrl("file:///android_asset/index.html");

        ebayWeb = createWebView();
        root.addView(ebayWeb,new FrameLayout.LayoutParams(-1,-1));
        ebayWeb.setVisibility(View.GONE);
        ebayWeb.setWebViewClient(new WebViewClient(){});

        browserBar = new LinearLayout(this);
        browserBar.setBackgroundColor(Color.rgb(23,31,49));
        browserBar.setPadding(4,4,4,4);
        Button back = new Button(this); back.setText("Back to tracker");
        Button capture = new Button(this); capture.setText("Capture candidates");
        browserBar.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        browserBar.addView(capture,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(browserBar,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        browserBar.setVisibility(View.GONE);
        back.setOnClickListener(v->closeBrowser());
        capture.setOnClickListener(v->captureVisible());

        scanWeb = createWebView();
        scanWeb.setAlpha(0.01f);
        root.addView(scanWeb,new FrameLayout.LayoutParams(2,2,Gravity.TOP|Gravity.LEFT));
        scanWeb.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url) {
                if(!scanning)return;
                JSONObject expected=currentJob;
                handler.postDelayed(()->{
                    if(scanning && expected==currentJob) extract(v,expected,false);
                },3000);
            }
        });
        if(Build.VERSION.SDK_INT>=26) {
            NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            n.createNotificationChannel(new NotificationChannel(CHANNEL,"New sold listing candidates",NotificationManager.IMPORTANCE_DEFAULT));
        }
        if(Build.VERSION.SDK_INT>=33 &&
           checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
        }
    }

    @SuppressLint("SetJavaScriptEnabled") private WebView createWebView(){
        WebView w=new WebView(this);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.setWebChromeClient(new WebChromeClient());
        return w;
    }

    private boolean isEbay(String url) {
        try {
            Uri u=Uri.parse(url);
            String host=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) &&
                host!=null && (host.equals("ebay.co.uk")||host.endsWith(".ebay.co.uk"));
        }catch(Exception e){return false;}
    }

    private void openExternal(String url){
        if(!isEbay(url))return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){Toast.makeText(this,"Cannot open eBay",Toast.LENGTH_SHORT).show();}
    }
    private void openBrowser(String url,String tracker,String search){
        if(!isEbay(url))return;
        browserTracker=tracker;browserSearch=search;
        appWeb.setVisibility(View.GONE);
        ebayWeb.setVisibility(View.VISIBLE);
        browserBar.setVisibility(View.VISIBLE);
        ebayWeb.loadUrl(url);
    }
    private void closeBrowser(){
        ebayWeb.stopLoading();
        ebayWeb.setVisibility(View.GONE);
        browserBar.setVisibility(View.GONE);
        appWeb.setVisibility(View.VISIBLE);
    }
    private void captureVisible(){
        JSONObject job=new JSONObject();
        try{job.put("tracker",browserTracker);job.put("search",browserSearch);}
        catch(Exception ignored){}
        extract(ebayWeb,job,true);
    }

    // Only candidate prices are captured: accepted Best Offer prices are NOT verified.
    // The user must confirm actual values before they appear in the statistical graphs.
    private static final String EXTRACT_JS =
        "(function(){let out=[],seen=new Set();"+
        "const nodes=document.querySelectorAll('li.s-item,li.s-card,div.s-card,div.s-item');"+
        "for(const e of nodes){if(out.length>=100)break;"+
        "let title=(e.querySelector('.s-item__title,.s-card__title,h3')?.innerText||'').trim();"+
        "let price=(e.querySelector('.s-item__price,.s-card__price')?.innerText||'');"+
        "let a=e.querySelector('a.s-item__link,a.s-card__link,a[href*=\"/itm/\"]');"+
        "let link=a?.href||'';"+
        "let m=price.match(/£\\s*([0-9,]+(?:\\.[0-9]{1,2})?)/);"+
        "if(!title||!link.includes('/itm/')||!m)continue;"+
        "let text=(e.innerText||'').replace(/\\s+/g,' ').slice(0,700);"+
        "if(!/\\bsold\\b|\\bended\\b/i.test(text))continue;"+
        "let dm=text.match(/\\b(?:sold|ended)\\s+(?:on\\s+)?(\\d{1,2}\\s+[a-zA-Z]{3,9}\\s+20\\d{2})/i);"+
        "let stamp=dm?Date.parse(dm[1]):NaN;"+
        "let date=Number.isFinite(stamp)?new Date(stamp).toISOString().slice(0,10):null;"+
        "let key=link.split('?')[0];if(seen.has(key))continue;seen.add(key);"+
        "out.push({title,price:Number(m[1].replaceAll(',','')),url:link,date,offer:/best offer|or best offer/i.test(text)});"+
        "}return JSON.stringify({sales:out});})()";

    private void extract(WebView web,JSONObject job,boolean visible){
        web.evaluateJavascript(EXTRACT_JS,result->{
            try{
                String raw=(String)new JSONTokener(result).nextValue();
                JSONObject parsed=new JSONObject(raw),payload=new JSONObject();
                payload.put("job",job);
                JSONArray sales=parsed.optJSONArray("sales");
                payload.put("sales",sales==null?new JSONArray():sales);
                appWeb.evaluateJavascript("window.onNativeCapture("+JSONObject.quote(payload.toString())+")",null);
                if(visible){
                    closeBrowser();
                    Toast.makeText(this,"Candidates captured; verify actual prices",Toast.LENGTH_SHORT).show();
                }else nextJob();
            }catch(Exception e) {
                if(visible)Toast.makeText(this,"Could not read sold listings on this page",Toast.LENGTH_LONG).show();
                else nextJob();
            }
        });
    }

    private void beginScan(String json) {
        if(scanning)return;
        jobs.clear();
        try{
            JSONArray values=new JSONArray(json);
            for(int i=0;i<Math.min(60,values.length());i++){
                JSONObject item=values.optJSONObject(i);
                if(item!=null && item.optString("url").startsWith("https://www.ebay.co.uk/sch/")) jobs.add(item);
            }
        }catch(Exception e){return;}
        if(jobs.isEmpty()){
            appWeb.evaluateJavascript("window.onScanFinished()",null);return;
        }
        scanning=true;jobIndex=0;nextJob();
    }

    private void nextJob(){
        if(timeout!=null)handler.removeCallbacks(timeout);
        if(!scanning)return;
        if(jobIndex>=jobs.size()){
            scanning=false;currentJob=null;
            appWeb.evaluateJavascript("window.onScanFinished()",null);
            return;
        }
        currentJob=jobs.get(jobIndex++);
        JSONObject expected=currentJob;
        scanWeb.stopLoading();
        scanWeb.loadUrl(expected.optString("url"));
        timeout=()->{if(scanning && currentJob==expected)nextJob();};
        handler.postDelayed(timeout,18000);
    }

    private void notifyNew(int count,String name){
        if(count<1)return;
        if(Build.VERSION.SDK_INT>=33 &&
           checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent intent=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle(count+" new potential Pokémon card sales")
            .setContentText(name+" — check actual sold prices")
            .setContentIntent(pi).setAutoCancel(true).build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()%100000),n);
    }
    private void reschedule(){
        if(periodic!=null)handler.removeCallbacks(periodic);
        if(!monitor)return;
        periodic=()->{
            if(!scanning)appWeb.evaluateJavascript("window.getMonitorJobs()",value->{
                try{beginScan((String)new JSONTokener(value).nextValue());}
                catch(Exception ignored){}
            });
            reschedule();
        };
        handler.postDelayed(periodic,Math.max(15,monitorMinutes)*60000L);
    }

    @Override protected void onResume(){
        super.onResume();
        if(appWeb!=null)appWeb.evaluateJavascript("window.onAppForeground&&window.onAppForeground()",null);
    }
    @Override protected void onPause(){
        super.onPause();
        if(periodic!=null)handler.removeCallbacks(periodic);
    }
    @Override public void onBackPressed(){
        if(ebayWeb!=null && ebayWeb.getVisibility()==View.VISIBLE)closeBrowser();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        scanning=false;handler.removeCallbacksAndMessages(null);
        if(appWeb!=null)appWeb.destroy();
        if(ebayWeb!=null)ebayWeb.destroy();
        if(scanWeb!=null)scanWeb.destroy();
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface public void openEbay(String url,String tracker,String search){
            runOnUiThread(()->openBrowser(url,tracker,search));
        }
        @JavascriptInterface public void openExternal(String url){
            runOnUiThread(()->MainActivity.this.openExternal(url));
        }
        @JavascriptInterface public void startScan(String requests){
            runOnUiThread(()->beginScan(requests));
        }
        @JavascriptInterface public void onNewSales(int count,String title){
            runOnUiThread(()->notifyNew(count,title));
        }
        @JavascriptInterface public void setMonitoring(boolean enabled,int interval){
            runOnUiThread(()->{
                monitor=enabled;monitorMinutes=interval;reschedule();
            });
        }
        @JavascriptInterface public void shareText(String filename,String content){
            runOnUiThread(()->{
                Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT,filename);
                i.putExtra(Intent.EXTRA_TEXT,content);
                startActivity(Intent.createChooser(i,"Export backup"));
            });
        }
    }
}
