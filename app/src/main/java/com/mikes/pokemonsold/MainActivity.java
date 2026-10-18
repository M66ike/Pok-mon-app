package com.mikes.pokemonsold;
import android.app.Activity;import android.os.Bundle;import android.webkit.WebView;import android.webkit.WebViewClient;import android.webkit.JavascriptInterface;import android.annotation.SuppressLint;import android.content.Intent;import android.net.Uri;
public class MainActivity extends Activity {
WebView web;
@SuppressLint("SetJavaScriptEnabled") public void onCreate(Bundle b){super.onCreate(b);web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.addJavascriptInterface(new Bridge(),"Android");web.setWebViewClient(new WebViewClient());setContentView(web);web.loadUrl("file:///android_asset/index.html");}
public class Bridge{@JavascriptInterface public void open(String url){runOnUiThread(()->{try{Uri u=Uri.parse(url);if(!"https".equals(u.getScheme())||!"www.ebay.co.uk".equals(u.getHost()))return;startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){}});}}
@Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}