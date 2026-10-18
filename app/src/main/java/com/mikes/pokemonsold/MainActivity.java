package com.mikes.pokemonsold;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayList<JSONObject> jobs=new ArrayList<>();
 private FrameLayout root; private WebView app,ebay,scanner;
 private LinearLayout bar; private JSONObject current;
 private int index=0,minutes=30; private boolean scanning=false,monitor=false;
 private Runnable timer,timeout;
 private String visibleTracker="",visibleSearch="";
 private static final String CHANNEL="sold-candidates";
 @SuppressLint("SetJavaScriptEnabled")
 public void onCreate(Bundle b){
  super.onCreate(b);
  getWindow().setStatusBarColor(Color.rgb(21,31,51));
  root=new FrameLayout(this);setContentView(root);
  app=web();app.getSettings().setAllowFileAccess(true);
  app.addJavascriptInterface(new Bridge(),"AndroidBridge");
  app.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
    if("file".equals(req.getUrl().getScheme()))return false;
    external(req.getUrl().toString());return true;
   }
  });
  root.addView(app,new FrameLayout.LayoutParams(-1,-1));
  app.loadUrl("file:///android_asset/index.html");
  ebay=web();root.addView(ebay,new FrameLayout.LayoutParams(-1,-1));ebay.setVisibility(View.GONE);
  bar=new LinearLayout(this);bar.setBackgroundColor(Color.rgb(23,31,49));
  Button back=new Button(this);back.setText("Back to tracker");
  Button capture=new Button(this);capture.setText("Capture candidates");
  bar.addView(back,new LinearLayout.LayoutParams(0,-2,1));bar.addView(capture,new LinearLayout.LayoutParams(0,-2,1));
  root.addView(bar,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));bar.setVisibility(View.GONE);
  back.setOnClickListener(v->closeBrowser());capture.setOnClickListener(v->capture());
  scanner=web();FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(2,2,Gravity.TOP|Gravity.LEFT);
  scanner.setAlpha(.01f);root.addView(scanner,p);
  scanner.setWebViewClient(new WebViewClient(){
   @Override public void onPageFinished(WebView v,String url){
    if(!scanning)return;
    JSONObject expected=current;
    handler.postDelayed(()->{if(scanning&&current==expected)extract(v,expected,false);},3000);
   }
  });
  if(Build.VERSION.SDK_INT>=26){
   ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"New eBay candidates",NotificationManager.IMPORTANCE_DEFAULT));
  }
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
 }
 @SuppressLint("SetJavaScriptEnabled") private WebView web(){
  WebView v=new WebView(this);v.getSettings().setJavaScriptEnabled(true);v.getSettings().setDomStorageEnabled(true);
  v.setWebChromeClient(new WebChromeClient());return v;
 }
 private void external(String url){
  if(!url.startsWith("https://"))return;
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}
 }
 private void closeBrowser(){ebay.stopLoading();ebay.setVisibility(View.GONE);bar.setVisibility(View.GONE);app.setVisibility(View.VISIBLE);}
 private void capture(){
  JSONObject j=new JSONObject();
  try{j.put("tracker",visibleTracker);j.put("search",visibleSearch);}catch(Exception ignored){}
  extract(ebay,j,true);
 }
 private static final String EXTRACT="(function(){let out=[],seen=new Set();"+
 "for(const e of document.querySelectorAll('li.s-item,li.s-card,div.s-card,div.s-item')){if(out.length>=100)break;"+
 "let title=(e.querySelector('.s-item__title,.s-card__title,h3')?.innerText||'').trim();"+
 "let priceText=(e.querySelector('.s-item__price,.s-card__price')?.innerText||'');"+
 "let url=e.querySelector('a.s-item__link,a.s-card__link,a[href*=\\\"/itm/\\\"]')?.href||'';"+
 "let m=priceText.replaceAll(',','').match(/£\\s*(\\d+(?:\\.\\d{1,2})?)/);"+
 "if(!title||!m||!url.includes('/itm/'))continue;"+
 "let text=(e.innerText||'').replace(/\\s+/g,' ').slice(0,700);"+
 "if(!/\\bsold\\b|\\bended\\b/i.test(text))continue;"+
 "let key=url.split('?')[0];if(seen.has(key))continue;seen.add(key);"+
 "let dateMatch=text.match(/\\b(?:sold|ended)\\s+(?:on\\s+)?(\\d{1,2}\\s+[a-zA-Z]{3,9}\\s+20\\d{2})/i);"+
 "let dt=dateMatch?Date.parse(dateMatch[1]):NaN;let date=Number.isFinite(dt)?new Date(dt).toISOString().slice(0,10):null;"+
 "out.push({title,price:Number(m[1]),url,date,offer:/best offer|or best offer/i.test(text)});"+
 "}return JSON.stringify({sales:out});})()";
 private void extract(WebView v,JSONObject job,boolean visible){
  v.evaluateJavascript(EXTRACT,value->{
   try{
    String raw=(String)new JSONTokener(value).nextValue();
    JSONObject results=new JSONObject(raw),data=new JSONObject();
    data.put("job",job);data.put("sales",results.optJSONArray("sales")!=null?results.getJSONArray("sales"):new JSONArray());
    app.evaluateJavascript("window.onNativeCapture("+JSONObject.quote(data.toString())+")",null);
   }catch(Exception ignored){}
   if(visible)closeBrowser();else next();
  });
 }
 private void begin(String json){
  if(scanning)return;
  jobs.clear();
  try{
   JSONArray items=new JSONArray(json);
   for(int i=0;i<Math.min(items.length(),60);i++){
    JSONObject j=items.optJSONObject(i);
    if(j!=null&&j.optString("url").startsWith("https://www.ebay.co.uk/sch/"))jobs.add(j);
   }
  }catch(Exception ignored){}
  scanning=true;index=0;next();
 }
 private void next(){
  if(timeout!=null)handler.removeCallbacks(timeout);
  if(!scanning)return;
  if(index>=jobs.size()){
   scanning=false;current=null;
   app.evaluateJavascript("window.onScanFinished()",null);return;
  }
  current=jobs.get(index++);
  JSONObject expected=current;
  scanner.stopLoading();scanner.loadUrl(current.optString("url"));
  timeout=()->{if(scanning&&current==expected)next();};
  handler.postDelayed(timeout,20000);
 }
 private void schedule(){
  if(timer!=null)handler.removeCallbacks(timer);
  if(!monitor)return;
  timer=()->{
   if(!scanning){
    app.evaluateJavascript("window.getMonitorJobs()",val->{
     try{begin((String)new JSONTokener(val).nextValue());}catch(Exception ignored){}
    });
   }
   schedule();
  };
  handler.postDelayed(timer,Math.max(minutes,15)*60000L);
 }
 private void notification(int count,String title){
  if(count<=0)return;
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  Intent intent=new Intent(this,MainActivity.class);
  PendingIntent pi=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_search)
   .setContentTitle(count+" new possible sold listings").setContentText(title+" — verify final prices")
   .setContentIntent(pi).setAutoCancel(true).build();
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()%100000),n);
 }
 @Override protected void onResume(){super.onResume();if(app!=null)app.evaluateJavascript("window.onAppForeground && window.onAppForeground()",null);}
 @Override protected void onPause(){super.onPause();if(timer!=null)handler.removeCallbacks(timer);}
 @Override public void onBackPressed(){if(ebay!=null&&ebay.getVisibility()==View.VISIBLE)closeBrowser();else super.onBackPressed();}
 @Override protected void onDestroy(){scanning=false;handler.removeCallbacksAndMessages(null);app.destroy();ebay.destroy();scanner.destroy();super.onDestroy();}
 public class Bridge{
  @JavascriptInterface public void openEbay(String url,String tracker,String search){runOnUiThread(()->{
   if(!url.startsWith("https://www.ebay.co.uk/sch/"))return;
   visibleTracker=tracker;visibleSearch=search;app.setVisibility(View.GONE);ebay.setVisibility(View.VISIBLE);bar.setVisibility(View.VISIBLE);ebay.loadUrl(url);
  });}
  @JavascriptInterface public void openExternal(String url){runOnUiThread(()->external(url));}
  @JavascriptInterface public void startScan(String json){runOnUiThread(()->begin(json));}
  @JavascriptInterface public void onNewSales(int count,String title){runOnUiThread(()->notification(count,title));}
  @JavascriptInterface public void setMonitoring(boolean enabled,int interval){runOnUiThread(()->{monitor=enabled;minutes=interval;schedule();});}
  @JavascriptInterface public void shareText(String filename,String content){runOnUiThread(()->{
   Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,filename);i.putExtra(Intent.EXTRA_TEXT,content);
   startActivity(Intent.createChooser(i,"Save or share backup"));
  });}
 }
}