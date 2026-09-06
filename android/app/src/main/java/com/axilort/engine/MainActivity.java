package com.axilort.engine;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private View splash;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 4101;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        File root=storageRoot(); new File(root,"projects").mkdirs();

        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(Color.BLACK);
        splash=createSplash(); layout.addView(splash,new LinearLayout.LayoutParams(-1,-1));
        webView=new WebView(this); webView.setVisibility(View.GONE); layout.addView(webView,new LinearLayout.LayoutParams(-1,-1)); setContentView(layout);

        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false); s.setMediaPlaybackRequiresUserGesture(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); s.setLoadsImagesAutomatically(true); s.setJavaScriptCanOpenWindowsAutomatically(true);
        webView.addJavascriptInterface(new AxilortStorage(),"AxilortStorage");
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){
            if(url.contains("projects.html")) injectNativeProjects(view);
            new Handler().postDelayed(()->{splash.animate().alpha(0f).setDuration(350).withEndAction(()->{splash.setVisibility(View.GONE);webView.setVisibility(View.VISIBLE);}).start();},450);
        }});
        webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){if(filePathCallback!=null)filePathCallback.onReceiveValue(null);filePathCallback=callback;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(intent,FILE_CHOOSER_REQUEST);return true;}});
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void injectNativeProjects(WebView view){
        String js="javascript:(()=>{if(typeof AxilortStorage==='undefined')return;const area=document.getElementById('projectArea'),btn=document.getElementById('newProject');if(!area||!btn)return;const esc=s=>String(s).replace(/[&<>\"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c]));function render(){let a=[];try{a=JSON.parse(AxilortStorage.list('projects')||'[]').filter(x=>x.folder)}catch(e){}area.innerHTML='';if(!a.length){area.innerHTML='<div class=\"empty\"><div class=\"empty-icon\">□</div><strong>No projects yet</strong><span>Create your first project to see it here.</span></div>';return}a.forEach(x=>{const d=document.createElement('div');d.className='project-bar';d.innerHTML='<div class=\"project-icon\">□</div><div class=\"project-info\"><strong>'+esc(x.name)+'</strong><small>axilort-engine-data / projects / '+esc(x.name)+'</small></div><button class=\"more\" type=\"button\">⋮</button><div class=\"menu\"><button data-a=\"open\">Open Assets</button><button data-a=\"rename\">Rename</button><button class=\"danger\" data-a=\"delete\">Delete</button></div>';const menu=d.querySelector('.menu');d.querySelector('.more').onclick=e=>{e.stopPropagation();document.querySelectorAll('.menu.show').forEach(m=>m.classList.remove('show'));menu.classList.add('show')};d.querySelector('[data-a=open]').onclick=()=>location.href='assets.html?project='+encodeURIComponent(x.name);d.querySelector('[data-a=rename]').onclick=()=>{let n=prompt('New project name',x.name);if(!n)return;if(!/^[^\\\\/]+$/.test(n.trim()))return alert('Invalid project name');if(AxilortStorage.rename('projects/'+x.name,'projects/'+n.trim()))render();else alert('Could not rename project')};d.querySelector('[data-a=delete]').onclick=()=>{if(confirm('Delete '+x.name+' and all its files?')){if(AxilortStorage.delete('projects/'+x.name))render();else alert('Could not delete project')}};area.appendChild(d)})}btn.onclick=()=>{let name=prompt('Project name');if(!name)return;name=name.trim();if(!/^[^\\\\/]+$/.test(name)){alert('Invalid project name');return}if(AxilortStorage.createProject(name)){render()}else alert('Could not create project. The name may already exist.')} ;render();})();";view.evaluateJavascript(js,null);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=FILE_CHOOSER_REQUEST||filePathCallback==null)return;Uri[] results=null;if(resultCode==RESULT_OK&&data!=null){if(data.getClipData()!=null){int n=data.getClipData().getItemCount();results=new Uri[n];for(int i=0;i<n;i++)results[i]=data.getClipData().getItemAt(i).getUri();}else if(data.getData()!=null)results=new Uri[]{data.getData()};}filePathCallback.onReceiveValue(results);filePathCallback=null;}

    private File storageRoot(){File base=getExternalFilesDir(null);if(base==null)base=getFilesDir();File root=new File(base,"axilort-engine-data");if(!root.exists())root.mkdirs();return root;}
    private File safeFile(String relative)throws Exception{File root=storageRoot().getCanonicalFile();String rel=relative==null?"":relative.replace('\\','/');File file=new File(root,rel).getCanonicalFile();String rp=root.getPath();if(!file.getPath().equals(rp)&&!file.getPath().startsWith(rp+File.separator))throw new SecurityException("Invalid path");return file;}

    public class AxilortStorage {
        @JavascriptInterface public String rootPath(){return storageRoot().getAbsolutePath();}
        @JavascriptInterface public boolean createProject(String name){try{if(name==null)return false;name=name.trim();if(name.isEmpty()||name.contains("/")||name.contains("\\"))return false;File p=safeFile("projects/"+name);if(p.exists())return false;if(!p.mkdirs())return false;new File(p,"assets").mkdirs();new File(p,"scenes").mkdirs();new File(p,"scripts").mkdirs();writeText("projects/"+name+"/project.json","{\n  \"name\": \""+name.replace("\\","\\\\").replace("\"","\\\"")+"\",\n  \"engine\": \"Axilort Engine\",\n  \"version\": \"1.0.0\"\n}\n");return true;}catch(Exception e){return false;}}
        @JavascriptInterface public String list(String relative){try{File dir=safeFile(relative);if(!dir.exists()&& !dir.mkdirs())return "[]";JSONArray out=new JSONArray();File[] fs=dir.listFiles();if(fs==null)return out.toString();for(File f:fs){JSONObject o=new JSONObject();o.put("name",f.getName());o.put("path",relative==null||relative.isEmpty()?f.getName():relative+"/"+f.getName());o.put("folder",f.isDirectory());o.put("size",f.isFile()?f.length():0);o.put("modified",f.lastModified());out.put(o);}return out.toString();}catch(Exception e){return "[]";}}
        @JavascriptInterface public boolean mkdir(String relative){try{return safeFile(relative).mkdirs();}catch(Exception e){return false;}}
        @JavascriptInterface public boolean exists(String relative){try{return safeFile(relative).exists();}catch(Exception e){return false;}}
        @JavascriptInterface public boolean delete(String relative){try{File f=safeFile(relative);if(f.getCanonicalPath().equals(storageRoot().getCanonicalPath()))return false;deleteRecursive(f);return !f.exists();}catch(Exception e){return false;}}
        private void deleteRecursive(File f){if(f.isDirectory()){File[] c=f.listFiles();if(c!=null)for(File x:c)deleteRecursive(x);}f.delete();}
        @JavascriptInterface public boolean rename(String from,String to){try{File a=safeFile(from),b=safeFile(to);if(!a.exists()||b.exists())return false;File p=b.getParentFile();if(p!=null)p.mkdirs();return a.renameTo(b);}catch(Exception e){return false;}}
        @JavascriptInterface public boolean writeBase64(String relative,String data){try{File f=safeFile(relative);File p=f.getParentFile();if(p!=null)p.mkdirs();try(FileOutputStream out=new FileOutputStream(f)){out.write(Base64.decode(data,Base64.DEFAULT));}return true;}catch(Exception e){return false;}}
        @JavascriptInterface public boolean writeText(String relative,String text){try{File f=safeFile(relative);File p=f.getParentFile();if(p!=null)p.mkdirs();try(FileOutputStream out=new FileOutputStream(f)){out.write((text==null?"":text).getBytes(StandardCharsets.UTF_8));}return true;}catch(Exception e){return false;}}
        @JavascriptInterface public String readBase64(String relative){try{File f=safeFile(relative);if(!f.isFile())return "";try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);}}catch(Exception e){return "";}}
    }

    private View createSplash(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setBackgroundColor(Color.rgb(5,5,5));ImageView logo=new ImageView(this);try(InputStream in=getAssets().open("images/logo.png")){logo.setImageBitmap(BitmapFactory.decodeStream(in));}catch(Exception ignored){logo.setImageResource(com.axilort.engine.R.drawable.axilort_logo);}logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);box.addView(logo,new LinearLayout.LayoutParams(180,180));TextView title=new TextView(this);title.setText("AXILORT ENGINE");title.setTextColor(Color.WHITE);title.setTextSize(22);title.setTypeface(Typeface.DEFAULT_BOLD);title.setGravity(Gravity.CENTER);box.addView(title,new LinearLayout.LayoutParams(-1,55));TextView studio=new TextView(this);studio.setText("AXILORT STUDIOS");studio.setTextColor(Color.rgb(150,150,150));studio.setTextSize(12);studio.setGravity(Gravity.CENTER);box.addView(studio,new LinearLayout.LayoutParams(-1,35));ProgressBar p=new ProgressBar(this);p.setIndeterminate(true);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(42,42);pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=28;box.addView(p,pp);return box;}
    @Override public void onBackPressed(){if(webView!=null&&webView.getVisibility()==View.VISIBLE&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
