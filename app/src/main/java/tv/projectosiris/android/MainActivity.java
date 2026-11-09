package tv.projectosiris.android;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import android.view.*;import android.graphics.Color;
import org.json.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class MainActivity extends Activity {
 WebView web; JSONObject profile=null; JSONObject session=null; int fileId=-1;
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
 web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
 web.getSettings().setMediaPlaybackRequiresUserGesture(false);web.getSettings().setAllowFileAccess(true);
 web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
 web.setWebChromeClient(new WebChromeClient());web.setWebViewClient(new WebViewClient());
 web.addJavascriptInterface(new Bridge(),"AndroidBridge");web.setFocusable(true);web.setFocusableInTouchMode(true);
 setContentView(web);web.loadUrl("file:///android_asset/index.html");web.requestFocus();}
 void respond(int id,boolean ok,Object value){runOnUiThread(()->{String v=JSONObject.quote(String.valueOf(value));String js="window.__osirisResolve("+id+","+ok+","+(ok?String.valueOf(value):v)+")";web.evaluateJavascript(js,null);});}
 void success(int id,Object v){respond(id,true,v==null?"null":v instanceof String?JSONObject.quote((String)v):v);}
 void fail(int id,String s){respond(id,false,s);}
 String fetch(String raw)throws Exception{
 URL url=new URL(raw);String scheme=url.getProtocol();if(!scheme.equals("http")&&!scheme.equals("https"))throw new Exception("Only HTTP(S) URLs are supported");
 HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(65000);c.setRequestProperty("User-Agent","ProjectOsirisAndroidTV/2.3.9");
 try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>25000000)throw new IOException("Response too large");}return out.toString("UTF-8");}finally{c.disconnect();}
 }
 String q(String s)throws Exception{return java.net.URLEncoder.encode(s,"UTF-8");}
 String api(String server,String user,String pass,String action)throws Exception{
 String base=server.replaceAll("/+$","");return base+"/player_api.php?username="+q(user)+"&password="+q(pass)+(action.isEmpty()?"":"&action="+q(action));
 }
 class Bridge{
 @JavascriptInterface public void call(int id,String method,String raw){
 new Thread(()->{try{
 JSONArray a=new JSONArray(raw);
 switch(method){
 case "profileSave": profile=a.getJSONObject(0);getPreferences(MODE_PRIVATE).edit().putString("provider_profile",profile.toString()).apply();success(id,new JSONObject().put("saved",true).put("kind",profile.optString("kind")));break;
 case "profileLoad": if(profile==null){String saved=getPreferences(MODE_PRIVATE).getString("provider_profile",null);if(saved!=null)profile=new JSONObject(saved);}success(id,profile);break;
 case "profileClear":profile=null;getPreferences(MODE_PRIVATE).edit().remove("provider_profile").apply();success(id,true);break;
 case "loadUrl":success(id,fetch(a.getString(0)));break;
 case "xtreamLogin":{
 JSONObject d=a.getJSONObject(0);String server=d.getString("server").replaceAll("/+$",""),user=d.getString("username"),pass=d.getString("password");
 JSONObject account=new JSONObject(fetch(api(server,user,pass,"")));
 if(!"1".equals(account.getJSONObject("user_info").optString("auth")))throw new Exception("Provider rejected login");
 JSONArray groups=new JSONArray(fetch(api(server,user,pass,"get_live_categories")));
 JSONArray streams=new JSONArray(fetch(api(server,user,pass,"get_live_streams")));
 Map<String,String> names=new HashMap<>();for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);names.put(g.optString("category_id"),g.optString("category_name","Uncategorized"));}
 JSONArray channels=new JSONArray();for(int i=0;i<streams.length();i++){JSONObject c=streams.getJSONObject(i);String streamId=c.optString("stream_id"),ext=c.optString("container_extension","ts").replaceAll("[^a-zA-Z0-9]","");
 JSONObject ch=new JSONObject();ch.put("name",c.optString("name","Channel"));ch.put("streamId",streamId);ch.put("id",c.optString("epg_channel_id"));ch.put("logo",c.optString("stream_icon"));ch.put("group",names.getOrDefault(c.optString("category_id"),"Uncategorized"));ch.put("url",server+"/live/"+q(user)+"/"+q(pass)+"/"+q(streamId)+"."+(ext.isEmpty()?"ts":ext));channels.put(ch);}
 session=new JSONObject().put("server",server).put("username",user).put("password",pass);
 success(id,new JSONObject().put("channels",channels).put("count",channels.length()).put("accountStatus",account.getJSONObject("user_info").optString("status","Active")));break;}
 case "xtreamEpg":if(session==null)throw new Exception("Connect Xtream first");
 success(id,fetch(session.getString("server")+"/xmltv.php?username="+q(session.getString("username"))+"&password="+q(session.getString("password"))));break;
 case "openFile":fileId=id;runOnUiThread(()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,42);});break;
 case "dvrStop":success(id,true);break;
 case "dvrStart":fail(id,"Android DVR not implemented in this preview");break;
 default:fail(id,"Unsupported: "+method);
 }
 }catch(Exception e){fail(id,e.getMessage());}}).start();
 }
 }
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==42&&fileId>=0){int id=fileId;fileId=-1;if(result!=RESULT_OK||data==null){success(id,null);return;}try(InputStream in=getContentResolver().openInputStream(data.getData())){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>25000000)throw new IOException("File too large");}success(id,out.toString("UTF-8"));}catch(Exception e){fail(id,e.getMessage());}}}
 @Override public void onBackPressed(){
 web.evaluateJavascript("(function(){var p=document.getElementById('settingsPanel');if(!p||p.classList.contains('hidden'))return false;var a=document.activeElement;if(a&&a.matches('input,textarea')){a.blur();return true;}var b=document.getElementById('closeSettings');if(b)b.click();return true;})()",v->{if(!"true".equals(v))super.onBackPressed();});
}
}
