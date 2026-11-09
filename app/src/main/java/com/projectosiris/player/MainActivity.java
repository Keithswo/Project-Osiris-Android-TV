package com.projectosiris.player;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.text.SimpleDateFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import android.text.TextUtils;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@UnstableApi
public class MainActivity extends Activity {
 static class Channel { String name,group,url,id,logo; Channel(String n,String g,String u,String i,String l){name=n;group=g;url=u;id=i;logo=l;} }
 final ArrayList<Channel> channels=new ArrayList<>();
 final ArrayList<ExoPlayer> players=new ArrayList<>();
 final Map<ExoPlayer,Integer> playerTiles=new HashMap<>();
 final ExecutorService executor=Executors.newSingleThreadExecutor();
 android.content.SharedPreferences prefs;
 LinearLayout root,body,toolbar; TextView info; String currentView="live"; int targetTile=-1; int screenCount=4;
 final ArrayList<Channel> tileChannels=new ArrayList<>(Arrays.asList(null,null,null,null));
 final Map<String,String> guide=new HashMap<>();
 static class Program {long start,end;String title;Program(long a,long b,String t){start=a;end=b;title=t;}}
 final Map<String,ArrayList<Program>> schedule=new HashMap<>();
 int audioTile=0;String categorySelection="All Channels";String previousView="live";
 final Handler uiHandler=new Handler(Looper.getMainLooper());
 int white=Color.WHITE, muted=Color.rgb(175,175,175), dark=Color.rgb(5,5,5), blue=Color.rgb(84,84,84), panel=Color.rgb(22,22,22), edge=Color.rgb(104,104,104);
 Dialog fullscreenDialog; ExoPlayer fullscreenPlayer;
 LinearLayout navBar; final ArrayList<LinearLayout> navButtons=new ArrayList<>();
 @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("osiris_android",MODE_PRIVATE);screenCount=prefs.getInt("screens",4);buildShell();String type=prefs.getString("sourceType","");if(!type.isEmpty())loadSaved();}
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 GradientDrawable shape(int fill,int border,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(border!=0)g.setStroke(dp(1),border);return g;}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(white);t.setTextSize(size);t.setPadding(dp(12),dp(9),dp(12),dp(9));return t;}
 Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setTextColor(white);b.setTextSize(12);b.setAllCaps(false);b.setBackground(shape(panel,edge,8));b.setPadding(dp(8),dp(6),dp(8),dp(6));b.setOnClickListener(v->action.run());b.setFocusable(true);return b;}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 void add(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
 void section(LinearLayout parent,String heading,String sub){TextView h=text(heading,23);h.setTypeface(null,Typeface.BOLD);add(parent,h);if(sub!=null){TextView t=text(sub,13);t.setTextColor(muted);add(parent,t);}gap(parent,10);}
 GradientDrawable chrome(int top,int bottom,int stroke,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
 LinearLayout card(){LinearLayout c=column();c.setPadding(dp(9),dp(9),dp(9),dp(9));c.setBackground(chrome(Color.rgb(25,25,25),Color.rgb(8,8,8),Color.rgb(96,96,96),12));return c;}
 void buildShell(){
  root=column();root.setBackgroundColor(dark);setContentView(root);
  LinearLayout header=column();header.setBackground(chrome(Color.rgb(26,26,26),Color.rgb(3,3,3),0,0));root.addView(header,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(dp(12),dp(4),dp(12),dp(4));
  ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.osiris_icon);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("Project Osiris");brand.addView(logo,new LinearLayout.LayoutParams(dp(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?34:58),dp(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?34:58)));
  brand.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
  TextView search=text("⌕",28);search.setGravity(Gravity.CENTER);search.setContentDescription("Browse channels");search.setOnClickListener(v->chooseCategory(-1));brand.addView(search,new LinearLayout.LayoutParams(dp(40),dp(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?34:52)));
  TextView menu=text("⋮",25);menu.setGravity(Gravity.CENTER);menu.setContentDescription("Settings");menu.setOnClickListener(v->showSettings());brand.addView(menu,new LinearLayout.LayoutParams(dp(34),dp(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?34:52)));add(header,brand);
  View separator=new View(this);separator.setBackgroundColor(Color.rgb(100,100,100));header.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
  navBar=new LinearLayout(this);navBar.setOrientation(LinearLayout.VERTICAL);navBar.setPadding(dp(3),dp(4),dp(3),dp(4));
  String[] labels={"Live TV","Guide","Multiview","Categories","Settings"};String[] symbols={"▣","▦","⊞","☷","⚙"};
  for(int i=0;i<labels.length;i++){
   final String name=labels[i];LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setFocusable(true);item.setClickable(true);item.setId(View.generateViewId());
   TextView icon=text(symbols[i],21);icon.setGravity(Gravity.CENTER);icon.setPadding(0,0,0,0);item.addView(icon,new LinearLayout.LayoutParams(-1,dp(25)));
   TextView title=text(name,11);title.setSingleLine(true);title.setGravity(Gravity.CENTER);title.setPadding(0,0,0,0);item.addView(title,new LinearLayout.LayoutParams(-1,dp(19)));
   item.setOnClickListener(v->{targetTile=-1;if(name.equals("Settings")){showSettings();return;}previousView=currentView;currentView=name.equals("Live TV")?"live":name.equals("Multiview")?"multi":name.equals("Categories")?"categories":"guide";render();});
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(dp(1),0,dp(1),0);navBar.addView(item,lp);navButtons.add(item);
  }
  info=text("Add an M3U or Xtream account in Settings to begin.",11);info.setTextColor(muted);info.setPadding(dp(12),dp(3),dp(12),dp(6));add(header,info);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundResource(R.drawable.osiris_background);body=column();body.setPadding(dp(9),dp(10),dp(9),dp(22));scroll.addView(body);LinearLayout mainArea=new LinearLayout(this);mainArea.setOrientation(LinearLayout.HORIZONTAL);
  mainArea.addView(navBar,new LinearLayout.LayoutParams(dp(140),-1));
  mainArea.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
  root.addView(mainArea,new LinearLayout.LayoutParams(-1,0,1));
  for(int i=0;i<navButtons.size();i++){final int pos=i;LinearLayout item=navButtons.get(i);
   item.setOnFocusChangeListener((v,hasFocus)->updateNav());
   item.setNextFocusUpId(navButtons.get((i+navButtons.size()-1)%navButtons.size()).getId());
   item.setNextFocusDownId(navButtons.get((i+1)%navButtons.size()).getId());
  }
  navButtons.get(0).requestFocus();render();
 }
 void updateNav(){String active=currentView.equals("multi")?"Multiview":currentView.equals("guide")?"Guide":currentView.equals("categories")?"Categories":"Live TV";String[] labels={"Live TV","Guide","Multiview","Categories","Settings"};for(int i=0;i<navButtons.size();i++){LinearLayout item=navButtons.get(i);boolean selected=labels[i].equals(active);boolean focused=item.hasFocus();item.setBackground(chrome(focused?Color.rgb(110,110,110):selected?Color.rgb(75,75,75):Color.rgb(15,15,15),selected?Color.rgb(22,22,22):Color.rgb(5,5,5),focused?Color.WHITE:selected?Color.WHITE:Color.rgb(36,36,36),9));}}
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(root!=null){releasePlayers();buildShell();}}
 void message(String s){info.setText(s);}
 void releasePlayers(){for(ExoPlayer p:players){p.release();}players.clear();playerTiles.clear();}
 @Override protected void onStop(){super.onStop();releasePlayers();}
 @Override protected void onStart(){super.onStart();if(root!=null&&fullscreenDialog==null)render();}
 @Override protected void onDestroy(){if(fullscreenDialog!=null)fullscreenDialog.dismiss();releasePlayers();executor.shutdownNow();super.onDestroy();}
 boolean enabled(String group){return prefs.getBoolean("cat_"+group,true);}
 ArrayList<String> groups(){TreeSet<String> groups=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(Channel c:channels)groups.add(c.group);return new ArrayList<>(groups);}
 ArrayList<Channel> filtered(String group){ArrayList<Channel> result=new ArrayList<>();for(Channel c:channels)if(enabled(c.group)&&(group.equals("All Channels")||c.group.equals(group)))result.add(c);return result;}
 void render(){releasePlayers();body.removeAllViews();updateNav();if(currentView.equals("multi"))renderMultiview();else if(currentView.equals("guide"))renderGuide();else if(currentView.equals("categories"))renderCategories();else if(currentView.equals("categoryChannels"))renderCategoryChannels();else renderLive();}
 String channelNumber(Channel c,int index){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*(\\d{1,5})\\b").matcher(c.name);return m.find()?m.group(1):String.valueOf(index+1);}
 String cleanName(Channel c){return c.name.replaceFirst("^\\s*\\d{1,5}\\s*[-.:]?\\s*","");}
 void channelRow(LinearLayout holder,Channel c,int index,String last){
  boolean selected=c.url.equals(last);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(5),dp(2),dp(5),dp(2));row.setBackground(chrome(selected?Color.rgb(72,72,72):Color.rgb(17,17,17),selected?Color.rgb(31,31,31):Color.rgb(7,7,7),selected?Color.WHITE:Color.rgb(46,46,46),7));
  TextView num=text(channelNumber(c,index),11);num.setTextColor(muted);num.setPadding(dp(3),0,dp(2),0);num.setGravity(Gravity.CENTER);row.addView(num,new LinearLayout.LayoutParams(dp(44),dp(43)));
  TextView name=text(cleanName(c),13);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(3),0,dp(3),0);row.addView(name,new LinearLayout.LayoutParams(0,dp(43),1));
  TextView star=text(favorite(c)?"★":"☆",22);star.setGravity(Gravity.CENTER);star.setPadding(0,0,0,0);star.setContentDescription("Toggle favorite");star.setOnClickListener(v->{prefs.edit().putBoolean("fav_"+c.url,!favorite(c)).apply();render();});row.addView(star,new LinearLayout.LayoutParams(dp(40),dp(43)));
  row.setOnClickListener(v->{prefs.edit().putString("lastUrl",c.url).apply();render();});row.setFocusable(true);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(3);holder.addView(row,lp);
 }
 boolean favorite(Channel c){return prefs.getBoolean("fav_"+c.url,false);}
 void renderLive(){
  String last=prefs.getString("lastUrl","");Channel chosen=null;for(Channel c:channels)if(c.url.equals(last)){chosen=c;break;}
  final Channel selected=chosen;boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE && getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=600;
  LinearLayout playerPanel=card();
  if(chosen!=null){makePlayer(playerPanel,chosen,wide?260:210);TextView name=text(guide.containsKey(chosen.id)?guide.get(chosen.id):cleanName(chosen),19);name.setTypeface(null,Typeface.BOLD);name.setPadding(dp(7),dp(9),dp(7),dp(1));add(playerPanel,name);
   TextView details=text(cleanName(chosen)+"  •  LIVE",12);details.setTextColor(muted);details.setPadding(dp(7),0,dp(7),dp(5));add(playerPanel,details);
   add(playerPanel,button("⛶  Expand live preview",()->openFullscreen(selected)));
  }else{TextView placeholder=text("Select a channel to start watching",16);placeholder.setTextColor(muted);placeholder.setGravity(Gravity.CENTER);playerPanel.addView(placeholder,new LinearLayout.LayoutParams(-1,dp(205)));}
  LinearLayout listing=card();LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER_VERTICAL);
  String group=prefs.getString("guideCategory","All Channels");ArrayList<Channel> items=filtered(group);
  Button choose=button(group+" ("+items.size()+")  ▾",()->chooseCategory(-1));controls.addView(choose,new LinearLayout.LayoutParams(0,dp(46),1));
  Button search=button("⌕",()->chooseChannelFromGroup(-1,group));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(dp(47),dp(46));slp.leftMargin=dp(5);controls.addView(search,slp);add(listing,controls);gap(listing,7);
  int max=Math.min(items.size(),120);for(int i=0;i<max;i++)channelRow(listing,items.get(i),i,last);
  if(items.size()>max)add(listing,text("Showing first "+max+" channels. Choose a category to narrow the list.",12));
  if(wide){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,2);left.rightMargin=dp(10);row.addView(listing,left);row.addView(playerPanel,new LinearLayout.LayoutParams(0,-2,3));add(body,row);}
  else{add(body,playerPanel);gap(body,12);add(body,listing);}
 }
 void renderCategories(){
  section(body,"Categories","Select a category to browse its channels.");
  for(String g:groups())if(enabled(g)){
   int count=0;for(Channel c:channels)if(g.equals(c.group))count++;
   final String category=g;
   add(body,button(g+"   •   "+count+" channels   ›",()->{
    categorySelection=category;currentView="categoryChannels";render();
   }));gap(body,5);
  }
 }
 void renderCategoryChannels(){
  section(body,categorySelection,"Browse channels in this category.");
  add(body,button("← Categories",()->{currentView="categories";render();}));
  String last=prefs.getString("lastUrl","");ArrayList<Channel> items=filtered(categorySelection);
  int limit=Math.min(300,items.size());for(int i=0;i<limit;i++)channelRow(body,items.get(i),i,last);
 }
 void openFullscreen(Channel c){
  if(fullscreenDialog!=null)return;
  releasePlayers();
  Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
  fullscreenDialog=dialog;
  LinearLayout screen=column();screen.setBackgroundColor(Color.BLACK);
  Button close=button("← Back to Live TV",()->dialog.dismiss());add(screen,close);
  Runnable hide=()->close.setVisibility(View.GONE);
  uiHandler.postDelayed(hide,2000);
  screen.setOnTouchListener((v,e)->{close.setVisibility(View.VISIBLE);uiHandler.removeCallbacks(hide);uiHandler.postDelayed(hide,2000);return false;});
  fullscreenPlayer=new ExoPlayer.Builder(this).build();
  PlayerView view=new PlayerView(this);view.setPlayer(fullscreenPlayer);view.setUseController(true);
  view.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
  screen.addView(view,new LinearLayout.LayoutParams(-1,0,1));
  view.setOnTouchListener((v,e)->{if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){close.setVisibility(View.VISIBLE);uiHandler.removeCallbacks(hide);uiHandler.postDelayed(hide,2000);}return false;});
  dialog.setContentView(screen);
  dialog.setOnDismissListener(d->{if(fullscreenPlayer!=null){fullscreenPlayer.release();fullscreenPlayer=null;}fullscreenDialog=null;render();});
  dialog.show();
  fullscreenPlayer.setMediaItem(MediaItem.fromUri(c.url));fullscreenPlayer.prepare();fullscreenPlayer.play();
 }
 void renderGuide(){
  section(body,"Live TV Guide","Choose a category, then swipe horizontally to see the next two hours.");
  String selected=prefs.getString("guideCategory","");
  add(body,button(selected.isEmpty()?"Select a Category":selected+"  ▾",()->chooseCategory(-1)));
  if(selected.isEmpty()){add(body,text("Select a category to display the program timeline.",14));return;}
  ArrayList<Channel> items=filtered(selected);
  if(items.isEmpty()){add(body,text("No channels in this category.",14));return;}
  long now=System.currentTimeMillis();long begin=(now/1800000L)*1800000L;long finish=now+7200000L;
  HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setFillViewport(false);
  LinearLayout table=column();int labelWidth=145;int timeWidth=720;
  LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);
  TextView label=text("CHANNEL",12);heading.addView(label,new LinearLayout.LayoutParams(dp(labelWidth),dp(48)));
  LinearLayout ticks=new LinearLayout(this);ticks.setOrientation(LinearLayout.HORIZONTAL);
  SimpleDateFormat clock=new SimpleDateFormat("h:mm a",Locale.getDefault());
  for(int i=0;i<5;i++){TextView tick=text(clock.format(new Date(begin+i*1800000L)),12);tick.setTextColor(muted);ticks.addView(tick,new LinearLayout.LayoutParams(dp(180),dp(48)));}
  heading.addView(ticks,new LinearLayout.LayoutParams(dp(timeWidth+180),dp(48)));add(table,heading);
  int max=Math.min(100,items.size());
  for(int i=0;i<max;i++){
   Channel c=items.get(i);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
   TextView name=text(cleanName(c),12);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setBackground(shape(panel,edge,4));
   name.setOnClickListener(v->{prefs.edit().putString("lastUrl",c.url).apply();previousView="guide";currentView="live";render();});
   row.addView(name,new LinearLayout.LayoutParams(dp(labelWidth),dp(55)));
   LinearLayout timeline=new LinearLayout(this);timeline.setOrientation(LinearLayout.HORIZONTAL);
   ArrayList<Program> programs=schedule.get(c.id);long cursor=begin;
   if(programs!=null)for(Program program:programs){
    if(program.end<=begin||program.start>=finish)continue;
    long left=Math.max(begin,program.start),right=Math.min(finish,program.end);
    if(left>cursor){addGuideCell(timeline,"No EPG",left-cursor,c);cursor=left;}
    if(right>cursor){addGuideCell(timeline,program.title,right-cursor,c);cursor=right;}
   }
   if(cursor<finish)addGuideCell(timeline,"No EPG available",finish-cursor,c);
   row.addView(timeline,new LinearLayout.LayoutParams(-2,dp(55)));add(table,row);
  }
  horizontal.addView(table);add(body,horizontal);
 }
 void addGuideCell(LinearLayout row,String title,long duration,Channel c){
  int width=Math.max(28,(int)(duration*360.0/3600000.0));
  TextView cell=text(title,12);cell.setSingleLine(true);cell.setEllipsize(TextUtils.TruncateAt.END);
  cell.setGravity(Gravity.CENTER_VERTICAL);cell.setBackground(shape(panel,edge,4));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(width),dp(54));lp.setMargins(dp(1),0,dp(1),dp(1));row.addView(cell,lp);
  cell.setOnClickListener(v->{prefs.edit().putString("lastUrl",c.url).apply();previousView="guide";currentView="live";render();});
 }
 void renderMultiview(){section(body,"Multiview","Select Channel on a screen to browse categories and the guide. Stream limits depend on your device and provider.");
  add(body,button("Screens: "+screenCount+"  •  Change Layout",()->new AlertDialog.Builder(this).setItems(new String[]{"2","3","4"},(d,n)->{screenCount=n+2;prefs.edit().putInt("screens",screenCount).apply();render();}).show()));gap(body,12);
  int width=getResources().getConfiguration().screenWidthDp;boolean grid=width>=650;
  if(grid){for(int start=0;start<screenCount;start+=2){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);for(int i=start;i<Math.min(screenCount,start+2);i++){LinearLayout tile=makeTile(i,180);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(tile,lp);}add(body,row);}}
  else{for(int i=0;i<screenCount;i++){add(body,makeTile(i,205));gap(body,10);}}
 }
 LinearLayout makeTile(int i,int videoHeight){final int slot=i;LinearLayout frame=column();frame.setPadding(dp(9),dp(9),dp(9),dp(9));frame.setBackground(shape(panel,edge,12));Channel c=tileChannels.get(i);
  TextView heading=text("SCREEN "+(i+1)+(c!=null?"  •  "+c.name:"  •  Not selected"),14);heading.setTypeface(null,Typeface.BOLD);add(frame,heading);
  if(c!=null)makePlayer(frame,c,videoHeight,i);else{TextView empty=text("Select a channel to begin",14);empty.setTextColor(muted);empty.setGravity(Gravity.CENTER);empty.setBackground(shape(Color.rgb(10,16,25),0,8));frame.addView(empty,new LinearLayout.LayoutParams(-1,dp(videoHeight)));}
  gap(frame,7);if(c!=null){
   final Button[] audioRef=new Button[1];
   audioRef[0]=button(audioTile==i?"🔊 Main Audio":"Select Main Audio",()->{
    audioTile=slot;for(ExoPlayer p:players)p.setVolume(playerTiles.getOrDefault(p,-1)==audioTile?1f:0f);
    frame.setBackground(shape(panel,Color.YELLOW,12));audioRef[0].setText("🔊 Main Audio");
    uiHandler.postDelayed(()->{frame.setBackground(shape(panel,edge,12));},2000);
    });add(frame,audioRef[0]);
  }
  add(frame,button("Select Channel",()->chooseCategory(slot)));if(c!=null){gap(frame,5);add(frame,button("Watch Solo",()->{prefs.edit().putString("lastUrl",c.url).apply();currentView="live";render();}));}return frame;
 }
 void makePlayer(LinearLayout holder,Channel c,int heightDp){makePlayer(holder,c,heightDp,-1);}
 void makePlayer(LinearLayout holder,Channel c,int heightDp,int tile){ExoPlayer p=new ExoPlayer.Builder(this).build();players.add(p);playerTiles.put(p,tile);p.setVolume(tile<0||tile==audioTile?1f:0f);PlayerView v=new PlayerView(this);v.setPlayer(p);v.setUseController(true);int h=(int)(getResources().getDisplayMetrics().density*heightDp);holder.addView(v,new LinearLayout.LayoutParams(-1,h));p.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException error){message("Playback failed: "+error.getErrorCodeName()+" — "+c.name);}});try{p.setMediaItem(MediaItem.fromUri(c.url));p.prepare();p.setPlayWhenReady(true);}catch(Exception ex){message("Cannot open stream: "+ex.getMessage());}}
 void chooseCategory(int tile){targetTile=tile;ArrayList<String> names=groups();names.removeIf(g->!enabled(g));names.add(0,"All Channels");new AlertDialog.Builder(this).setTitle("Choose category").setItems(names.toArray(new String[0]),(d,index)->{String group=names.get(index);if(tile==-1&&currentView.equals("guide"))prefs.edit().putString("guideCategory",group).apply();if(tile==-1&&currentView.equals("guide")){render();return;}chooseChannelFromGroup(tile,group);}).setNegativeButton("Cancel",null).show();}
 void chooseChannel(int tile){chooseCategory(tile);}
 void chooseChannelFromGroup(int tile,String group){ArrayList<Channel> items=filtered(group);if(items.isEmpty()){message("No visible channels in this category.");return;}EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Search channels");LinearLayout box=column();add(box,search);ListView list=new ListView(this);ArrayList<Channel> shown=new ArrayList<>(items);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);for(Channel c:shown)adapter.add(c.name);list.setAdapter(adapter);box.addView(list,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*350)));
 AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Select channel").setView(box).setNegativeButton("Cancel",null).create();list.setOnItemClickListener((parent,view,pos,id)->{Channel c=shown.get(pos);if(tile>=0){tileChannels.set(tile,c);currentView="multi";}else{prefs.edit().putString("lastUrl",c.url).apply();currentView="live";}dialog.dismiss();render();});search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){shown.clear();adapter.clear();for(Channel c:items)if(c.name.toLowerCase(Locale.ROOT).contains(s.toString().toLowerCase(Locale.ROOT))){shown.add(c);adapter.add(c.name);}adapter.notifyDataSetChanged();}public void afterTextChanged(android.text.Editable e){}});dialog.show();}
 void renderChannelButtons(LinearLayout holder,ArrayList<Channel> items,int tile){int max=Math.min(250,items.size());for(int i=0;i<max;i++){Channel c=items.get(i);String now=guide.get(c.id);add(holder,button(c.name+(now==null?"":" · "+now),()->{prefs.edit().putString("lastUrl",c.url).apply();currentView="live";render();}));}if(items.size()>max)add(holder,text("Showing first "+max+" channels. Use category selection to narrow the list.",13));}
 void showSettings(){
  LinearLayout settings=column();settings.setPadding(dp(12),dp(8),dp(12),dp(8));
  ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.addView(settings);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Project Osiris Settings").setView(sc).create();
  section(settings,"M3U Playlist","Enter your provider playlist URL.");
  EditText m3u=input("M3U URL",prefs.getString("m3u",""));add(settings,m3u);
  section(settings,"Xtream Codes","Connect with your authorized provider account.");
  EditText server=input("Server URL",prefs.getString("server",""));EditText user=input("Username",prefs.getString("user",""));
  EditText pass=input("Password",prefs.getString("pass",""));pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  add(settings,server);add(settings,user);add(settings,pass);
  section(settings,"XMLTV EPG (optional)","Add guide program information.");
  EditText epg=input("XMLTV URL",prefs.getString("epgUrl",""));add(settings,epg);
  section(settings,"Active Categories","Choose which channel groups appear in the app.");
  ArrayList<String> categoryNames=groups();Map<String,Boolean> staged=new HashMap<>();
  for(String g:categoryNames)staged.put(g,enabled(g));
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
  LinearLayout catList=column();ArrayList<CheckBox> checks=new ArrayList<>();
  Button all=button("Check All",()->{for(CheckBox cb:checks)cb.setChecked(true);});
  Button none=button("Uncheck All",()->{for(CheckBox cb:checks)cb.setChecked(false);});
  actions.addView(all,new LinearLayout.LayoutParams(0,dp(45),1));actions.addView(none,new LinearLayout.LayoutParams(0,dp(45),1));add(settings,actions);
  for(String g:categoryNames){CheckBox cb=new CheckBox(this);cb.setText(g);cb.setTextColor(white);cb.setChecked(staged.get(g));
   cb.setOnCheckedChangeListener((b,on)->staged.put(g,on));checks.add(cb);add(catList,cb);}
  // Category list is part of the main settings ScrollView: every category is reachable.
  add(settings,catList);
  add(settings,text("10-minute DVR is not available in this Android build.",12));
  LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.HORIZONTAL);
  Button save=button("Save Settings",()->{
   android.content.SharedPreferences.Editor edit=prefs.edit();
   for(String g:categoryNames)edit.putBoolean("cat_"+g,Boolean.TRUE.equals(staged.get(g)));
   String oldM3u=prefs.getString("m3u","");String oldServer=prefs.getString("server","");
   String oldUser=prefs.getString("user","");String oldPass=prefs.getString("pass","");
   String m=m3u.getText().toString().trim(),sv=server.getText().toString().trim(),u=user.getText().toString().trim(),pw=pass.getText().toString();
   String e=epg.getText().toString().trim();edit.putString("epgUrl",e);edit.apply();
   dialog.dismiss();render();
   if(!sv.isEmpty()&&!u.isEmpty()&&!pw.isEmpty()&&(!sv.equals(oldServer)||!u.equals(oldUser)||!pw.equals(oldPass)))loadXtream(sv,u,pw,true);
   else if(!m.isEmpty()&&!m.equals(oldM3u))loadM3u(m,true);
   if(!e.isEmpty()&&!e.equals(prefs.getString("loadedEpgUrl",""))){prefs.edit().putString("loadedEpgUrl",e).apply();loadGuide(e);}
  });
  Button exit=button("Exit (Discard)",()->dialog.dismiss());
  footer.addView(save,new LinearLayout.LayoutParams(0,dp(48),1));footer.addView(exit,new LinearLayout.LayoutParams(0,dp(48),1));add(settings,footer);
  dialog.show();
 }
 EditText input(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(white);e.setHintTextColor(muted);e.setHint(hint);e.setText(value);return e;}
 void loadSaved(){if(prefs.getString("sourceType","").equals("m3u"))loadM3u(prefs.getString("m3u",""),false);else if(prefs.getString("sourceType","").equals("xtream"))loadXtream(prefs.getString("server",""),prefs.getString("user",""),prefs.getString("pass",""),false);String epg=prefs.getString("epgUrl","");if(!epg.isEmpty())loadGuide(epg);}
 String fetch(String url) throws Exception {URL u=new URL(url);URLConnection conn=u.openConnection();conn.setConnectTimeout(12000);conn.setReadTimeout(20000);conn.setRequestProperty("User-Agent","ProjectOsirisAndroid/2.3.7");try(InputStream stream=conn.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1){out.write(b,0,n);if(out.size()>25_000_000)throw new IOException("Playlist too large");}return out.toString("UTF-8");}}
 String attr(String s,String key){java.util.regex.Matcher m=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(key)+"=\\\"([^\\\"]*)\\\"").matcher(s);return m.find()?m.group(1):"";}
 void loadM3u(String url,boolean save){message("Loading M3U…");executor.execute(()->{try{String data=fetch(url);ArrayList<Channel> found=new ArrayList<>();String name="",group="Other",id="",logo="";for(String raw:data.split("\\r?\\n")){String line=raw.trim();if(line.startsWith("#EXTINF")){name=line.contains(",")?line.substring(line.indexOf(',')+1).trim():"Channel";group=attr(line,"group-title");if(group.isEmpty())group="Other";id=attr(line,"tvg-id");logo=attr(line,"tvg-logo");}else if(!line.isEmpty()&&!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){found.add(new Channel(name.isEmpty()?"Channel":name,group,line,id,logo));name="";}}if(found.isEmpty())throw new Exception("No playable HTTP channels found");runOnUiThread(()->{channels.clear();channels.addAll(found);if(save)prefs.edit().putString("sourceType","m3u").putString("m3u",url).apply();message("Loaded "+channels.size()+" channels");render();});}catch(Exception e){runOnUiThread(()->message("Invalid M3U: "+e.getMessage()));}});}
 String enc(String s){try{return java.net.URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}
 void loadXtream(String server,String user,String pass,boolean save){message("Connecting Xtream…");executor.execute(()->{try{String base=server.replaceAll("/+$","");String auth=base+"/player_api.php?username="+enc(user)+"&password="+enc(pass);JSONObject account=new JSONObject(fetch(auth));JSONObject userInfo=account.optJSONObject("user_info");if(userInfo==null||!"Active".equalsIgnoreCase(userInfo.optString("status","")))throw new Exception("Account not active or credentials invalid");JSONArray cats=new JSONArray(fetch(auth+"&action=get_live_categories"));Map<String,String> groups=new HashMap<>();for(int i=0;i<cats.length();i++){JSONObject c=cats.getJSONObject(i);groups.put(c.optString("category_id"),c.optString("category_name","Other"));}JSONArray streams=new JSONArray(fetch(auth+"&action=get_live_streams"));ArrayList<Channel> found=new ArrayList<>();for(int i=0;i<streams.length();i++){JSONObject c=streams.getJSONObject(i);String streamId=c.optString("stream_id");String ext=c.optString("container_extension","ts");if(ext.isEmpty())ext="ts";String url=base+"/live/"+enc(user)+"/"+enc(pass)+"/"+streamId+"."+ext;found.add(new Channel(c.optString("name","Channel"),groups.getOrDefault(c.optString("category_id"),"Other"),url,c.optString("epg_channel_id",streamId),c.optString("stream_icon")));}if(found.isEmpty())throw new Exception("No live streams returned");runOnUiThread(()->{channels.clear();channels.addAll(found);if(save)prefs.edit().putString("sourceType","xtream").putString("server",base).putString("user",user).putString("pass",pass).apply();message("Connected: "+found.size()+" live channels");render();});}catch(Exception e){runOnUiThread(()->message("Invalid Xtream: "+e.getMessage()));}});}
 void loadGuide(String url){message("Loading XMLTV guide…");executor.execute(()->{try{
  String xml=fetch(url);XmlPullParser parser=Xml.newPullParser();parser.setInput(new StringReader(xml));
  Map<String,String> now=new HashMap<>();Map<String,ArrayList<Program>> programs=new HashMap<>();
  String id="",title="",start="",stop="";boolean titleTag=false;int event;long current=System.currentTimeMillis();
  while((event=parser.next())!=XmlPullParser.END_DOCUMENT){
   if(event==XmlPullParser.START_TAG){if(parser.getName().equals("programme")){id=parser.getAttributeValue(null,"channel");start=parser.getAttributeValue(null,"start");stop=parser.getAttributeValue(null,"stop");title="";}if(parser.getName().equals("title"))titleTag=true;}
   else if(event==XmlPullParser.TEXT&&titleTag)title+=parser.getText();
   else if(event==XmlPullParser.END_TAG){if(parser.getName().equals("title"))titleTag=false;
    if(parser.getName().equals("programme")&&id!=null&&start!=null&&stop!=null){
     long a=epgTime(start),b=epgTime(stop);if(b>a&&b>current-3600000L&&a<current+10800000L){
      programs.computeIfAbsent(id,k->new ArrayList<>()).add(new Program(a,b,title));
      if(a<=current&&b>current)now.put(id,title);
     }
    }
   }
  }
  for(ArrayList<Program> entries:programs.values())entries.sort(Comparator.comparingLong(p->p.start));
  runOnUiThread(()->{guide.clear();guide.putAll(now);schedule.clear();schedule.putAll(programs);message("XMLTV guide loaded");if(currentView.equals("guide"))render();});
 }catch(Exception e){runOnUiThread(()->message("Invalid XMLTV: "+e.getMessage()));}});}
 long epgTime(String stamp){try{
  String raw=stamp.trim();SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);format.setLenient(false);
  if(raw.length()==14)raw+=" +0000";else if(raw.length()>14&&raw.charAt(14)!=' ')raw=raw.substring(0,14)+" "+raw.substring(14).trim();
  return format.parse(raw).getTime();
 }catch(Exception e){return -1L;}}
 @Override public void onBackPressed(){
  if(fullscreenDialog!=null){fullscreenDialog.dismiss();return;}
  if(currentView.equals("categoryChannels")){currentView="categories";render();return;}
  if(!currentView.equals("live")){currentView=previousView.equals(currentView)?"live":previousView;previousView="live";render();return;}
  super.onBackPressed();
 }
}
