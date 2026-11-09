package tv.projectosiris.android;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import android.view.*;import android.graphics.Color;
import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;import android.security.keystore.KeyGenParameterSpec;import android.security.keystore.KeyProperties;import android.util.Base64;import java.security.KeyStore;import org.json.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class MainActivity extends Activity {
 WebView web; JSONObject profile=null; JSONObject session=null; int fileId=-1;
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
 web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
 web.getSettings().setMediaPlaybackRequiresUserGesture(false);web.getSettings().setAllowFileAccess(true);
 web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
 web.setWebChromeClient(new WebChromeClient());web.setWebViewClient(new WebViewClient());
 web.addJavascriptInterface(new Bridge(),"AndroidBridge");web.setFocusable(true);web.setFocusableInTouchMode(true);
 setContentView(web);web.loadUrl("file:///android_asset/index.html");}
 void respond(int id,boolean ok,Object value){runOnUiThread(()->{String v=JSONObject.quote(String.valueOf(value));String js="window.__osirisResolve("+id+","+ok+","+(ok?String.valueOf(value):v)+")";web.evaluateJavascript(js,null);});}
 void success(int id,Object v){respond(id,true,v==null?"null":v instanceof String?JSONObject.quote((String)v):v);}
 void fail(int id,String s){respond(id,false,s);}
 String fetch(String raw)throws Exception{
 URL url=new URL(raw);String scheme=url.getProtocol();if(!scheme.equals("http")&&!scheme.equals("https"))throw new Exception("Only HTTP(S) URLs are supported");
 HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(65000);c.setInstanceFollowRedirects(true);c.setRequestProperty("Accept","application/json, */*");c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
 int code=c.getResponseCode();if(code==401||code==403)throw new IOException("Provider returned HTTP "+code+" (access denied)");if(code>=400)throw new IOException("Provider returned HTTP "+code);try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>25000000)throw new IOException("Response too large");}return out.toString("UTF-8");}finally{c.disconnect();}
 }
 String encrypt(String plain)throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  String alias="osiris_provider_key_v1";
  if(!ks.containsAlias(alias)){
   KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   kg.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
   kg.generateKey();
  }
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,ks.getKey(alias,null));
  return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
 }
 String decrypt(String encoded)throws Exception{
  String[] parts=encoded.split(":",2);
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,ks.getKey("osiris_provider_key_v1",null),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
  return new String(cipher.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),StandardCharsets.UTF_8);
 }
 String request(String url)throws Exception{
  IOException last=null;
  for(int attempt=0;attempt<2;attempt++){
   try{return fetch(url);}catch(IOException ex){last=ex;if(attempt==0)Thread.sleep(650);}
  }
  throw last;
 }
 String q(String s)throws Exception{return java.net.URLEncoder.encode(s,"UTF-8");}
 String api(String server,String user,String pass,String action)throws Exception{
 String base=server.replaceAll("/+$","").replaceAll("(?i)/player_api\\.php$","");return base+"/player_api.php?username="+q(user)+"&password="+q(pass)+(action.isEmpty()?"":"&action="+q(action));
 }
 class Bridge{
 @JavascriptInterface public void call(int id,String method,String raw){
 new Thread(()->{try{
 JSONArray a=new JSONArray(raw);
 switch(method){
 case "profileSave":{
  JSONObject next=a.getJSONObject(0);
  String encrypted=encrypt(next.toString());
  boolean saved=getPreferences(MODE_PRIVATE).edit().putString("provider_profile_encrypted",encrypted).remove("provider_profile").commit();
  if(!saved)throw new Exception("Unable to save provider on device");
  profile=next;success(id,new JSONObject().put("saved",true).put("kind",profile.optString("kind")));break;}
 case "profileLoad":{
  if(profile==null){
   String encrypted=getPreferences(MODE_PRIVATE).getString("provider_profile_encrypted",null);
   if(encrypted!=null)profile=new JSONObject(decrypt(encrypted));
   else{
    String legacy=getPreferences(MODE_PRIVATE).getString("provider_profile",null);
    if(legacy!=null){
     JSONObject old=new JSONObject(legacy);
     if(!getPreferences(MODE_PRIVATE).edit().putString("provider_profile_encrypted",encrypt(old.toString())).remove("provider_profile").commit())
      throw new Exception("Unable to migrate saved provider");
     profile=old;
    }
   }
  }
  success(id,profile);break;}
 case "profileClear":profile=null;session=null;getPreferences(MODE_PRIVATE).edit().remove("provider_profile_encrypted").remove("provider_profile").commit();success(id,true);break;
 case "loadUrl":success(id,fetch(a.getString(0)));break;
 case "xtreamLogin":{
 JSONObject d=a.getJSONObject(0);
 String server=d.getString("server").trim().replaceAll("/+$","").replaceAll("(?i)/player_api\\.php$",""),user=d.getString("username"),pass=d.getString("password");
 if(!server.startsWith("http://")&&!server.startsWith("https://"))throw new Exception("Server URL must start with http:// or https://");
 JSONObject account;
 try{account=new JSONObject(request(api(server,user,pass,"")));}
 catch(org.json.JSONException ex){throw new Exception("Provider returned an unexpected authentication response");}
 JSONObject info=account.optJSONObject("user_info");
 if(info==null)throw new Exception("Provider did not return Xtream account information; check the server URL and port");
 String auth=String.valueOf(info.opt("auth"));
 if(!("1".equals(auth)||"true".equalsIgnoreCase(auth)))throw new Exception("Provider rejected this login (auth="+auth+"). Check server URL and port; credentials were not erased");
 // Authentication is complete even if categories or channel download fails.
 session=new JSONObject().put("server",server).put("username",user).put("password",pass);
 JSONArray groups=new JSONArray();
 String categoryWarning="";
 try{groups=new JSONArray(request(api(server,user,pass,"get_live_categories")));}
 catch(Exception ex){categoryWarning="Categories unavailable: "+ex.getMessage();}
 JSONArray streams;
 try{streams=new JSONArray(request(api(server,user,pass,"get_live_streams")));}
 catch(Exception ex){throw new Exception("Login accepted, but live channels could not be downloaded: "+ex.getMessage());}
 Map<String,String> names=new HashMap<>();
 for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);names.put(g.optString("category_id"),g.optString("category_name","Uncategorized"));}
 JSONArray channels=new JSONArray();
 for(int i=0;i<streams.length();i++){
  JSONObject c=streams.getJSONObject(i);
  String streamId=c.optString("stream_id"),ext=c.optString("container_extension","ts").replaceAll("[^a-zA-Z0-9]","");
  JSONObject ch=new JSONObject();ch.put("name",c.optString("name","Channel"));ch.put("streamId",streamId);ch.put("id",c.optString("epg_channel_id"));
  ch.put("logo",c.optString("stream_icon"));ch.put("group",names.getOrDefault(c.optString("category_id"),"Uncategorized"));
  ch.put("url",server+"/live/"+q(user)+"/"+q(pass)+"/"+q(streamId)+"."+(ext.isEmpty()?"ts":ext));channels.put(ch);
 }
 success(id,new JSONObject().put("channels",channels).put("count",channels.length()).put("warning",categoryWarning).put("accountStatus",info.optString("status","Active")));break;}
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
 @Override public void onBackPressed(){web.evaluateJavascript("(function(){var t=document.getElementById('guideTimeline');if(document.activeElement===t){document.getElementById('guideEarlier').focus();return true}var p=document.getElementById('settingsPanel');if(p&&!p.classList.contains('hidden')){var b=document.getElementById('closeSettings');if(b)b.click();return true}return false})()",v->{if(!"true".equals(v))super.onBackPressed();});}
}
