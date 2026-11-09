const $=id=>document.getElementById(id);let channels=[],epg={},category='All Channels',current=null,hls=null,recent=[],favorites=loadFavorites();
function loadFavorites(){try{const saved=JSON.parse(localStorage.getItem('cadenceFavoritesV2')||'[]');return new Set(Array.isArray(saved)?saved:[])}catch{return new Set()}}
function persistFavorites(){try{localStorage.setItem('cadenceFavoritesV2',JSON.stringify([...favorites]))}catch(e){status('Could not save favorites: '+e.message)}}
const escapeHtml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function status(s){$('status').textContent=s}function settings(show){
 const panel=$('settingsPanel');
 panel.classList.toggle('hidden',!show);
 if(show)requestAnimationFrame(()=>{$('closeSettings')?.focus()});
 else document.activeElement?.blur();
}
// On a TV, focusing a text field must not summon the on-screen keyboard.
// Press OK/Enter to explicitly switch the highlighted field into editing.
let tvEditing=null;
function tvTextFields(){return [...document.querySelectorAll('#settingsPanel input:not([type=checkbox]):not([type=range]), #settingsPanel textarea')]}
function tvEndEdit(){
 if(tvEditing){tvEditing.readOnly=true;tvEditing.dataset.tvEditing='false';tvEditing.blur();tvEditing=null;}
}
document.addEventListener('DOMContentLoaded',()=>{
 tvTextFields().forEach(el=>{
  el.readOnly=true;
  el.addEventListener('click',()=>{if(!tvEditing){tvStartEdit(el)}});
  el.addEventListener('blur',()=>{if(tvEditing===el){el.readOnly=true;tvEditing=null}});
 });
});
function tvStartEdit(el){
 tvEndEdit();tvEditing=el;el.readOnly=false;el.dataset.tvEditing='true';
 el.focus();if(window.AndroidBridge){const event=new Event('tv-edit-start');el.dispatchEvent(event);}
}
document.addEventListener('keydown',e=>{
 const panel=$('settingsPanel');
 if(!panel||panel.classList.contains('hidden'))return;
 if(e.key==='Backspace'){return;} // Never hijack password deletion.
 if(e.key==='Escape'||e.key==='BrowserBack'){
  if(tvEditing){tvEndEdit();$('closeSettings')?.focus();}
  else settings(false);
  e.preventDefault();e.stopImmediatePropagation();return;
 }
 const controls=[...panel.querySelectorAll('button,input,select,textarea')].filter(el=>!el.disabled&&el.getClientRects().length);
 const focused=document.activeElement;
 if((e.key==='Enter'||e.key===' ')&&focused?.matches('input[readonly],textarea[readonly]')){
  tvStartEdit(focused);e.preventDefault();return;
 }
 if(tvEditing)return; // Allow keyboard input without D-pad focus changes.
 if(e.key==='ArrowUp'||e.key==='ArrowDown'){
  const index=controls.indexOf(focused);
  controls[(Math.max(index,0)+(e.key==='ArrowDown'?1:-1)+controls.length)%controls.length]?.focus();
  e.preventDefault();e.stopImmediatePropagation();
 }
},true);

function parseM3U(txt){const lines=txt.replace(/\r/g,'').split('\n');let items=[],info=null;for(let l of lines){l=l.trim();if(l.startsWith('#EXTINF:')){let attr=k=>{let m=l.match(new RegExp(k+'="([^"]*)"','i'));return m?m[1]:''};info={name:l.split(',').slice(1).join(',').trim()||'Unnamed Channel',id:attr('tvg-id'),logo:attr('tvg-logo'),group:attr('group-title')||'Uncategorized'};}else if(l&&!l.startsWith('#')&&info){items.push({...info,url:l});info=null}}return items}
function parseXML(txt){const doc=new DOMParser().parseFromString(txt,'text/xml');if(doc.querySelector('parsererror'))throw Error('Invalid XMLTV');const out={};for(let p of doc.querySelectorAll('programme')){let id=p.getAttribute('channel'),start=xmlDate(p.getAttribute('start')),stop=xmlDate(p.getAttribute('stop'));if(!id||!start||!stop)continue;(out[id]??=[]).push({start,stop,title:p.querySelector('title')?.textContent||'Program',desc:p.querySelector('desc')?.textContent||''})}for(let list of Object.values(out))list.sort((a,b)=>a.start-b.start);return out}
function xmlDate(s){if(!s)return null;const m=s.match(/^(\d{4})(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)?\s*([+-]\d{4})?/);if(!m)return null;let iso=`${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]||'00'}Z`;let d=Date.parse(iso);if(m[7]){const t=m[7],mins=(+t.slice(1,3)*60 + +t.slice(3,5))*(t[0]==='+'?1:-1);d-=mins*60000}return d}
function savePlaylist(text){const parsed=parseM3U(text);if(!parsed.length)throw Error('No valid channels found');channels=parsed;window.epgSource='m3u';reconcileFavorites();localStorage.removeItem('cadenceM3U');category='All Channels';current=null;render();settings(false);status(`Loaded ${channels.length} channels`)}
function saveEpg(text){epg=parseXML(text);window.epgNameLookup=null;try{localStorage.setItem('cadenceEPG',text)}catch(e){console.warn('EPG too large to cache:',e.message)};localStorage.setItem('cadenceEPGUpdated',String(Date.now()));updateEpgStamp();render();status(`Loaded guide for ${Object.keys(epg).length} channel IDs`)}
function getPrograms(ch){
  const exact=epg[ch.id]||epg[ch.name];if(exact)return exact;
  const clean=x=>String(x||'').toLowerCase().replace(/[^a-z0-9]/g,'');
  const key=clean(ch.id||ch.name);
  if(!key)return [];
  if(!window.epgNameLookup){window.epgNameLookup=new Map(Object.entries(epg).map(([k,v])=>[clean(k),v]));}
  return window.epgNameLookup.get(key)||[];
}
function nowProgram(ch){let n=Date.now();return getPrograms(ch).find(p=>p.start<=n&&p.stop>n)}
// v2.3.4: Active Categories are saved locally. Newly discovered groups default to active.
const OSIRIS_CATEGORY_PREFS='projectOsirisActiveCategoriesV1';
function loadCategoryPrefs(){try{const v=JSON.parse(localStorage.getItem(OSIRIS_CATEGORY_PREFS)||'{}');return v&&typeof v==='object'&&!Array.isArray(v)?v:{}}catch{return {}}}
let categoryPrefs=loadCategoryPrefs();
function isCategoryActive(group){return categoryPrefs[group]!==false}
function visibleChannels(){return channels.filter(c=>isCategoryActive(c.group))}
function renderActiveCategories(){
 const list=$('activeCategoriesList');if(!list)return;
 const groups=[...new Set(channels.map(c=>c.group))].sort((a,b)=>a.localeCompare(b));
 // Preserve the category-list scroll offset across guide and settings re-renders.
 const previousScrollTop=list.scrollTop;
 list.replaceChildren();
 $('activeCategoriesHint').textContent=groups.length?`${groups.length} categories from your current service. Checked categories appear in the guide and category picker.`:'Connect an M3U playlist or Xtream account to see your categories.';
 for(const group of groups){
  const label=document.createElement('label');label.className='active-category-option';
  const check=document.createElement('input');check.type='checkbox';check.checked=isCategoryActive(group);
  const name=document.createElement('span');name.textContent=group;
  const count=document.createElement('small');count.textContent=String(channels.filter(c=>c.group===group).length);
  check.addEventListener('change',()=>{
   categoryPrefs[group]=check.checked;
   try{localStorage.setItem(OSIRIS_CATEGORY_PREFS,JSON.stringify(categoryPrefs))}catch(e){status('Could not save categories: '+e.message)}
   if(category!=='All Channels'&&category!=='Favorites'&&!isCategoryActive(category))category='All Channels';
   render();if(mvPickerOpen)renderGuide();
  });
  label.append(check,name,count);list.append(label);
 }
 list.scrollTop=previousScrollTop;
}
// v2.3.8: bulk category controls use the same persisted preferences as individual checkboxes.
function setAllCategories(active){
 const groups=[...new Set(channels.map(c=>c.group))];
 if(!groups.length){status('Connect a service to load categories first.');return;}
 const next={...categoryPrefs};for(const group of groups)next[group]=active;
 try{localStorage.setItem(OSIRIS_CATEGORY_PREFS,JSON.stringify(next))}catch(e){status('Could not save categories: '+e.message);return;}
 categoryPrefs=next;
 if(!active||!isCategoryActive(category))category='All Channels';
 render();
 status(`${active?'Enabled':'Disabled'} all ${groups.length} categories.`);
}
function renderCategories(){
 let counts={};for(let c of visibleChannels())counts[c.group]=(counts[c.group]||0)+1;
 const visible=visibleChannels();let cats=['All Channels','Favorites',...Object.keys(counts).sort()];
 $('categories').innerHTML=cats.map(c=>`<button class="category ${c===category?'active':''}" data-category="${escapeHtml(c)}"><span>${escapeHtml(c)}</span><small>${c==='All Channels'?visible.length:c==='Favorites'?visible.filter(isFavorite).length:counts[c]}</small></button>`).join('');
 document.querySelectorAll('[data-category]').forEach(b=>b.onclick=()=>{if(activeView==='multiview'&&!mvPickerOpen)return;category=b.dataset.category;render()});
 renderActiveCategories();
}
function legacyChannelKey(c){return [c.group,c.name,c.id].join('\u001f')}
function channelKey(c){return c.streamId!=null?'xtream:'+String(c.streamId):legacyChannelKey(c)}
function isFavorite(c){return favorites.has(channelKey(c))||favorites.has(legacyChannelKey(c))}
function reconcileFavorites(){let changed=false;for(const c of channels){const old=legacyChannelKey(c),key=channelKey(c);if(old!==key&&favorites.has(old)){favorites.delete(old);favorites.add(key);changed=true}}if(changed)persistFavorites()}
function filtered(){let q=$('search').value.toLowerCase();return visibleChannels().filter(c=>(category==='All Channels'||(category==='Favorites'?isFavorite(c):c.group===category))&&(!q||c.name.toLowerCase().includes(q)||getPrograms(c).some(p=>p.title.toLowerCase().includes(q))))}
const GUIDE_SLOTS=24, GUIDE_SLOT_WIDTH=190, GUIDE_CHANNEL_WIDTH=190;
function renderGuide(){
 const sc=$('epgScroll'),savedX=sc.scrollLeft,savedY=sc.scrollTop;
 const cs=filtered();$('guideTitle').textContent=category==='All Channels'?'TV Guide':category;$('guideInfo').textContent=`${cs.length} channels · scroll right for upcoming programs`;
 const now=Date.now(),half=1800000,base=Math.floor(now/half)*half,fmt=t=>new Date(t).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'});
 const times=Array.from({length:GUIDE_SLOTS},(_,i)=>`<div>${fmt(base+i*half)}</div>`).join('');
 let html=`<div class="epg-head"><div>Channels</div>${times}</div>`;
 for(const c of cs.slice(0,1000)){
  const index=channels.indexOf(c),progs=getPrograms(c);
  html+=`<div class="epg-row"><div class="channel ${current?.url===c.url?'selected':''}" data-channel="${index}">${c.logo?`<img src="${escapeHtml(c.logo)}" onerror="this.style.display='none'">`:''}<span>${escapeHtml(c.name)}</span></div>`;
  for(let i=0;i<GUIDE_SLOTS;i++){const t=base+i*half+half/2,p=progs.find(x=>x.start<=t&&x.stop>t);html+=`<button class="program ${p&&p.start<=now&&p.stop>now?'current':''}" data-channel="${index}" title="${escapeHtml(p?.desc||'')}">${escapeHtml(p?.title||'No guide data')}</button>`}html+='</div>';
 }
 $('epg').innerHTML=html;sc.scrollLeft=savedX;sc.scrollTop=savedY;guideSync();
 $('epg').querySelectorAll('[data-channel]').forEach(e=>e.onclick=()=>{const index=+e.dataset.channel;if(mvPickerOpen){mvPick(mvPickerTarget,index);mvClosePicker();mvResumePlayback(mvPickerTarget);}else playChannel(channels[index]);});
}
function guideSync(){const s=$('epgScroll');$('guideTimeline').value=String(Math.round(s.scrollLeft/Math.max(1,s.scrollWidth-s.clientWidth)*20))}
function renderDetails(){let p=current&&nowProgram(current);$('programTitle').textContent=p?.title||current?.name||'Live TV';$('programMeta').textContent=current?`${current.name}${p?' · '+new Date(p.start).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})+' – '+new Date(p.stop).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'}):''}`:'Choose a channel';$('description').textContent=p?.desc|| (current?'Live channel playback':'Browse channels and select a program to watch.');$('progress').style.width=p?Math.min(100,Math.max(0,(Date.now()-p.start)/(p.stop-p.start)*100))+'%':'0%';$('favorite').textContent=current&&isFavorite(current)?'★ Favorited':'☆ Favorite'}
function render(){renderCategories();renderGuide();renderDetails()}
const OSIRIS_DVR_PREF='projectOsirisTenMinuteDvrOnV1';
let dvrEnabled=localStorage.getItem(OSIRIS_DVR_PREF)!=='false';
let playbackGeneration=0,dvrActive=false,dvrReady=false;
function clearPlayer(){const v=$('video');if(hls){hls.destroy();hls=null}v.pause();v.removeAttribute('src');v.load();}
function attachSource(url){const video=$('video');if(window.Hls?.isSupported()&&/\.m3u8(?:\?|$)/i.test(url)){hls=new Hls({enableWorker:true,liveSyncDurationCount:2,liveMaxLatencyDurationCount:8,backBufferLength:610,maxBufferLength:45});hls.loadSource(url);hls.attachMedia(video);hls.on(Hls.Events.MANIFEST_PARSED,()=>video.play().catch(()=>{}));hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal)status('Playback error: '+data.details)})}else{video.src=url;video.play().catch(()=>{})}}
async function playChannel(c){if(!c)return;if(activeView==='multiview')stopMultiview();const generation=++playbackGeneration;current=c;localStorage.setItem('cadenceLastChannelV2',channelKey(c));recent=[channelKey(c),...recent.filter(x=>x!==channelKey(c))].slice(0,30);localStorage.setItem('cadenceRecentV2',JSON.stringify(recent));clearPlayer();dvrActive=false;dvrReady=false;$('empty').classList.add('hidden');render();status(dvrEnabled?'Starting 10-minute buffer…':'Starting live stream (DVR off)…');
  try{await window.cadence.dvrStop();if(generation!==playbackGeneration)return;const availability=await window.cadence.dvrStatus();if(!availability.available){status('DVR unavailable · playing live directly');attachSource(c.url);return}const result=await window.cadence.dvrStart(c.url,!dvrEnabled);if(generation!==playbackGeneration||result?.cancelled)return;dvrActive=dvrEnabled;dvrReady=dvrEnabled;attachSource(result.url);status(dvrEnabled?'DVR active · 10-minute rolling buffer':'Live playback active · DVR off');}
  catch(err){if(generation!==playbackGeneration)return;status('DVR unavailable: '+err.message+' · trying direct playback');try{attachSource(c.url)}catch(e){status('Playback failed: '+e.message)}}
}
function liveEdge(v){return v.seekable.length?v.seekable.end(v.seekable.length-1):null}
function seekRelative(seconds){if(!dvrEnabled)return;const v=$('video'),end=liveEdge(v);if(end===null)return;const begin=v.seekable.start(0);try{v.currentTime=Math.max(begin,Math.min(end,v.currentTime+seconds));if(v.paused)v.play().catch(()=>{})}catch{}}
function goLive(){if(!dvrEnabled)return;const v=$('video'),end=liveEdge(v);if(end!==null){try{v.currentTime=Math.max(v.seekable.start(0),end-0.5);v.play().catch(()=>{})}catch{}}}
let activeView='live',categoriesOpen=false;
function showView(view){
  if(view==='settings'){settings(true);return}
  if(view==='categories'){
    // Categories can only open from a Multiview tile's Select Channel button.
    if(activeView==='live'||activeView==='multiview')return;
    categoriesOpen=!categoriesOpen;
    $('layout').classList.toggle('categories-open',categoriesOpen);
    return;
  }
  categoriesOpen=false;$('layout').classList.remove('categories-open');
  if(activeView==='multiview' && view!=='multiview')stopMultiview();
  if(view==='multiview' && activeView!=='multiview')startMultiview();
  activeView=view;
  $('mvChannelBrowser').classList.add('hidden');
  if(view==='multiview')mvRenderBrowser();
  if(view!=='live')$('backToMultiview').classList.add('hidden');
  document.querySelectorAll('.nav').forEach(n=>n.classList.toggle('active',n.dataset.view===view));
  $('layout').classList.toggle('view-multiview',view==='multiview');
  $('layout').classList.toggle('view-live',view==='live');
  $('layout').classList.toggle('view-guide',view!=='live' && view!=='multiview');
  if(view==='favorites')category='Favorites';
  else if(view==='guide'||view==='live')category='All Channels';
  else if(view==='recent'){category='All Channels';$('search').value=''}
  render();
  if(view==='recent'){
    const ids=new Set(recent);
    document.querySelectorAll('.epg-row').forEach(r=>{const el=r.querySelector('[data-channel]');if(el&&!ids.has(channelKey(channels[+el.dataset.channel])))r.remove()});
  }
  if(view==='live' && !current && channels.length){
    const last=localStorage.getItem('cadenceLastChannelV2');
    const match=channels.find(c=>channelKey(c)===last);
    if(match)playChannel(match);
  }
}
function nav(view){showView(view)}

document.querySelectorAll('.nav').forEach(b=>b.onclick=()=>nav(b.dataset.view));$('setup').onclick=()=>settings(true);$('closeSettings').onclick=()=>settings(false);$('saveSettings').onclick=()=>{settings(false);};$('search').oninput=renderGuide;$('refresh').onclick=()=>refreshEpg(true);$('favorite').onclick=()=>{if(!current)return;isFavorite(current)?(favorites.delete(channelKey(current)),favorites.delete(legacyChannelKey(current))):favorites.add(channelKey(current));persistFavorites();render()};$('fullscreen').onclick=()=>$('video').requestFullscreen?.();$('playPause').onclick=()=>{$('video').paused?$('video').play():$('video').pause()};$('back30').onclick=()=>seekRelative(-30);$('forward30').onclick=()=>seekRelative(30);$('goLive').onclick=goLive;
// v2.3.3: Show an inline validation message immediately below the relevant setting.
const settingErrors={xtream:'xtreamPassword',m3u:'m3uUrl',epg:'epgUrl',hours:'epgHours'};
function settingInvalid(kind){
 const input=$(settingErrors[kind]);if(!input)return;
 let message=document.getElementById('invalid-'+kind);
 if(!message){message=document.createElement('div');message.id='invalid-'+kind;message.className='setting-invalid';message.setAttribute('role','alert');
   const anchor=kind==='xtream'?$('xtreamConnect'):kind==='m3u'?$('loadM3u'):kind==='epg'?$('loadEpg'):input;
   anchor.insertAdjacentElement('afterend',message);
 }
 message.textContent='Invalid';input.classList.add('setting-input-invalid');
}
function clearSettingInvalid(kind){const message=document.getElementById('invalid-'+kind);if(message)message.remove();$(settingErrors[kind])?.classList.remove('setting-input-invalid')}
[['xtreamServer','xtream'],['xtreamUsername','xtream'],['xtreamPassword','xtream'],['m3uUrl','m3u'],['epgUrl','epg'],['epgHours','hours']].forEach(([id,kind])=>$(id).addEventListener('input',()=>clearSettingInvalid(kind)));
$('xtreamConnect').onclick=async()=>{
  const btn=$('xtreamConnect');btn.disabled=true;
  try{
    clearSettingInvalid('xtream');status('Connecting to Xtream provider…');
    const result=await window.cadence.xtreamLogin({server:$('xtreamServer').value,username:$('xtreamUsername').value,password:$('xtreamPassword').value});
    channels=result.channels;reconcileFavorites();category='All Channels';current=null;window.epgSource='xtream';
    localStorage.removeItem('cadenceM3U');
    let saved=true;try{await window.cadence.profileSave({kind:'xtream',server:$('xtreamServer').value,username:$('xtreamUsername').value,password:$('xtreamPassword').value})}catch(err){saved=false;console.warn('Secure save failed',err)}
    $('xtreamPassword').value='';render();settings(false);
    $('savedProviderLabel').textContent=saved?'Xtream account saved':'Not saved';void refreshEpg(false);
    status(`Xtream connected · ${channels.length} channels${saved?' · saved for next launch':' · secure save unavailable'}`);
  }catch(e){settingInvalid('xtream');status('Xtream error: '+e.message)}finally{btn.disabled=false}
};
$('loadM3u').onclick=async()=>{try{clearSettingInvalid('m3u');status('Loading playlist…');const url=$('m3uUrl').value.trim();const text=await window.cadence.loadUrl(url);savePlaylist(text);try{await window.cadence.profileSave({kind:'m3u-url',url});$('savedProviderLabel').textContent='M3U URL saved'}catch(e){status('Playlist loaded but not saved: '+e.message)}}catch(e){settingInvalid('m3u');status('Error: '+e.message)}};$('openM3u').onclick=async()=>{try{const t=await window.cadence.openFile([{name:'M3U Playlists',extensions:['m3u','m3u8','txt']}]);if(t){savePlaylist(t);try{await window.cadence.profileSave({kind:'m3u-file',playlist:t});$('savedProviderLabel').textContent='Local M3U saved'}catch(e){status('Playlist loaded but not saved: '+e.message)}}}catch(e){status('Error: '+e.message)}};$('loadEpg').onclick=async()=>{try{clearSettingInvalid('epg');status('Loading guide…');localStorage.setItem('cadenceEpgUrlV1',$('epgUrl').value.trim());saveEpg(await window.cadence.loadUrl($('epgUrl').value.trim()))}catch(e){settingInvalid('epg');status('Error: '+e.message)}};$('openEpg').onclick=async()=>{try{const t=await window.cadence.openFile([{name:'XMLTV',extensions:['xml','txt']}]);if(t)saveEpg(t)}catch(e){status('Error: '+e.message)}};
const epgDefaults={auto:false,hours:1,onLaunch:true};
function epgPrefs(){try{return {...epgDefaults,...JSON.parse(localStorage.getItem('cadenceEpgPrefsV1')||'{}')}}catch{return {...epgDefaults}}}
let epgTimer=null,epgBusy=false;
function updateEpgStamp(){const t=Number(localStorage.getItem('cadenceEPGUpdated')||0);$('epgLastRefresh').textContent=t?'Last refreshed: '+new Date(t).toLocaleString():'Never refreshed'}
function syncEpgPrefs(){
 const p=epgPrefs();$('epgAutoRefresh').checked=!!p.auto;$('epgHours').value=String(p.hours);$('epgOnLaunch').checked=!!p.onLaunch;updateEpgStamp();scheduleEpg();
}
function saveEpgPrefs(){
 const hours=Number($('epgHours').value);
 if(!Number.isFinite(hours)||hours<0.25||hours>720){settingInvalid('hours');status('Guide refresh hours must be between 0.25 and 720');return}
 clearSettingInvalid('hours');localStorage.setItem('cadenceEpgPrefsV1',JSON.stringify({auto:$('epgAutoRefresh').checked,hours,onLaunch:$('epgOnLaunch').checked}));scheduleEpg();
}
function scheduleEpg(){
 if(epgTimer)clearInterval(epgTimer);epgTimer=null;
 const p=epgPrefs();if(!p.auto)return;
 const ms=p.hours*3600000;
 epgTimer=setInterval(()=>{if(channels.length)void refreshEpg(false)},ms);
}
async function refreshEpg(manual=false){
 if(epgBusy)return;
 if(!channels.length){if(manual){settingInvalid('epg');status('Connect a provider before refreshing the guide')}return}
 const url=$('epgUrl').value.trim();
 if(!url&&window.epgSource!=='xtream'){if(manual){settingInvalid('epg');status('Enter an XMLTV EPG URL in Settings first')}return}
 epgBusy=true;clearSettingInvalid('epg');status('Refreshing TV guide…');
 try{
   const xml=url?await window.cadence.loadUrl(url):await window.cadence.xtreamEpg();
   saveEpg(xml);
 }catch(e){if(manual)settingInvalid('epg');status('Guide refresh failed: '+e.message)}finally{epgBusy=false}
}
$('epgAutoRefresh').onchange=saveEpgPrefs;$('epgHours').onchange=saveEpgPrefs;$('epgOnLaunch').onchange=saveEpgPrefs;
$('epgRefreshNow').onclick=()=>refreshEpg(true);
$('dvrEnabled').checked=dvrEnabled;
$('dvrEnabled').addEventListener('change',async e=>{
 dvrEnabled=e.target.checked;localStorage.setItem(OSIRIS_DVR_PREF,String(dvrEnabled));
 // Restart live playback to switch between the 10-minute buffer and short live-only HLS.
 if(activeView==='live'&&current){await playChannel(current)}
 else if(!dvrEnabled){await window.cadence.dvrStop().catch(()=>{});status('10-minute DVR turned off')}
 else status('10-minute DVR turned on');
});
$('epgUrl').value=localStorage.getItem('cadenceEpgUrlV1')||'';
$('epgUrl').onchange=()=>localStorage.setItem('cadenceEpgUrlV1',$('epgUrl').value.trim());
syncEpgPrefs();showView('live');
function tick(){let d=new Date();$('clock').innerHTML=`${d.toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}<small>${d.toLocaleDateString([],{weekday:'short',month:'short',day:'numeric'})}</small>`;renderDetails();const v=$('video');if(dvrEnabled&&v.seekable.length){let start=v.seekable.start(0),end=v.seekable.end(v.seekable.length-1),behind=Math.max(0,end-v.currentTime);$('bufferLabel').textContent=behind>2?`${Math.floor(behind/60)}:${String(Math.floor(behind%60)).padStart(2,'0')} behind live`:'Live playback';$('timeLabel').textContent=behind>2?'DVR · REWIND':'LIVE';$('seek').disabled=false;$('seek').value=end>start?100*(v.currentTime-start)/(end-start):100}else{$('seek').disabled=true;$('bufferLabel').textContent='Live playback';$('timeLabel').textContent='LIVE'}}$('seek').oninput=e=>{if(!dvrEnabled)return;const v=$('video');if(v.seekable.length){let a=v.seekable.start(0),b=v.seekable.end(v.seekable.length-1);try{v.currentTime=a+(b-a)*(+e.target.value/100)}catch{}}};
localStorage.removeItem('cadenceFavorites');localStorage.removeItem('cadenceRecent');try{let xml=localStorage.getItem('cadenceEPG');if(xml)epg=parseXML(xml);recent=JSON.parse(localStorage.getItem('cadenceRecentV2')||'[]')}catch(e){console.warn(e)}
async function restoreProvider(){
 try{
  const saved=await window.cadence.profileLoad();
  if(!saved){const legacy=localStorage.getItem('cadenceM3U');if(legacy){savePlaylist(legacy);try{await window.cadence.profileSave({kind:'m3u-file',playlist:legacy})}catch(e){console.warn(e)}localStorage.removeItem('cadenceM3U')}return}
  $('savedProviderLabel').textContent=saved.kind==='xtream'?'Xtream account saved':'M3U playlist saved';
  if(saved.kind==='xtream'){
    $('xtreamServer').value=saved.server;$('xtreamUsername').value=saved.username;
    const result=await window.cadence.xtreamLogin({server:saved.server,username:saved.username,password:saved.password});
    window.epgSource='xtream';channels=result.channels;reconcileFavorites();status(`Restored ${channels.length} Xtream channels`);
  }else if(saved.kind==='m3u-url'){
    $('m3uUrl').value=saved.url;savePlaylist(await window.cadence.loadUrl(saved.url));status(`Restored ${channels.length} M3U channels`);
  }else if(saved.kind==='m3u-file'){savePlaylist(saved.playlist);status(`Restored ${channels.length} M3U channels`)}
 }catch(e){status('Saved provider could not be restored: '+e.message)}
}
$('forgetAccount').onclick=async()=>{try{await window.cadence.profileClear();localStorage.removeItem('cadenceM3U');$('savedProviderLabel').textContent='No saved provider';$('xtreamServer').value='';$('xtreamUsername').value='';$('xtreamPassword').value='';$('m3uUrl').value='';status('Saved provider removed. Current playback remains available until restart.')}catch(e){status('Unable to remove provider: '+e.message)}};
render();tick();setInterval(tick,1000);restoreProvider().finally(()=>{
  render();
  if(!channels.length){settings(true);return}
  const last=localStorage.getItem('cadenceLastChannelV2');
  const match=channels.find(c=>channelKey(c)===last);
  if(match)void playChannel(match);
  if(epgPrefs().onLaunch)void refreshEpg(false);
});

window.addEventListener('beforeunload',()=>{window.cadence.dvrStop()});

// Each Multiview screen has its own FFmpeg HLS transcoder; single-view DVR remains independent.
let mvPlayers=[],mvAudio=0,mvSelected=0,mvHighlightTimer=null,mvPickerOpen=false,mvPickerTarget=0,mvSoloReturn=null;
function mvMessage(msg){$('mvStatus').textContent=msg}
function mvDestroy(tile){if(!tile)return;if(tile.slot!==undefined){tile.generation=(tile.generation||0)+1;window.cadence.mvStop(tile.slot).catch(()=>{})};tile.video.onerror=null;tile.video.onplaying=null;try{tile.hls?.destroy()}catch{};try{tile.video.pause();tile.video.removeAttribute('src');tile.video.load()}catch{};tile.hls=null}
function mvFlash(i,hold=false){
 clearTimeout(mvHighlightTimer);mvPlayers.forEach((t,j)=>t.element.classList.toggle('mv-highlight',j===i));
 if(!hold)mvHighlightTimer=setTimeout(()=>mvPlayers.forEach(t=>t.element.classList.remove('mv-highlight')),2000);
}
function mvSelect(i,hold=false){mvSelected=i;mvSetAudio(i);mvFlash(i,hold);mvRenderBrowser()}
function mvSetAudio(i){mvAudio=i;mvPlayers.forEach((t,j)=>{t.video.muted=j!==i;t.audioButton.textContent=j===i?'🔊 Audio on':'🔇 Hear audio'});}
function mvOpenPicker(i){
 mvPickerTarget=i;mvSelected=i;mvPickerOpen=true;mvFlash(i,true);
 $('layout').classList.add('mv-picking','categories-open');categoriesOpen=true;
 $('mvChannelBrowser').classList.add('hidden');
 $('search').value='';render();
 mvMessage(`Choosing channel for Screen ${i+1}`);
}
function mvClosePicker(){
 mvPickerOpen=false;$('layout').classList.remove('mv-picking','categories-open');categoriesOpen=false;
 mvFlash(mvPickerTarget,false);
 requestAnimationFrame(()=>mvResumePlayback(mvPickerTarget));
}
function mvResumePlayback(i){
 const tile=mvPlayers[i];if(!tile||!tile.channel)return;
 const video=tile.video;
 // Playback may be blocked when the grid is hidden during channel selection.
 const attempt=()=>{if(!tile.channel)return;video.play().catch(err=>mvMessage(`Screen ${i+1}: Playback blocked (${err.message}). Click the screen to retry.`))};
 requestAnimationFrame(()=>requestAnimationFrame(attempt));
}
async function mvPick(i,channelIndex){
 const tile=mvPlayers[i];if(!tile)return;
 const ch=channels[channelIndex];if(!ch||!isCategoryActive(ch.group))return;
 mvDestroy(tile);tile.channel=ch;tile.label.textContent=ch.name;tile.placeholder.textContent='Preparing stream…';tile.placeholder.classList.remove('hidden');
 const generation=tile.generation;
 const video=tile.video;video.muted=true;video.autoplay=true;video.playsInline=true;
 const fail=msg=>{if(tile.generation!==generation)return;tile.placeholder.textContent=`Playback error: ${msg}. Click screen to retry.`;tile.placeholder.classList.remove('hidden');mvMessage(`Screen ${i+1}: ${msg}`)};
 video.onerror=()=>fail(video.error?.message||'Video format or stream unavailable');
 video.onplaying=()=>{if(tile.generation!==generation)return;tile.placeholder.classList.add('hidden');video.muted=i!==mvAudio};
 mvSelect(i,false);mvMessage(`Screen ${i+1}: ${ch.name} · Preparing FFmpeg playback`);
 try{
  const result=await window.cadence.mvStart(i,ch.url);
  if(tile.generation!==generation||mvPlayers[i]!==tile){await window.cadence.mvStop(i);return}
  if(!window.Hls?.isSupported()&&!video.canPlayType('application/vnd.apple.mpegurl'))throw Error('HLS player unavailable');
  if(window.Hls?.isSupported()){
   const h=new Hls({enableWorker:true,liveSyncDurationCount:2,liveMaxLatencyDurationCount:8,backBufferLength:15,maxBufferLength:20});tile.hls=h;
   h.on(Hls.Events.MEDIA_ATTACHED,()=>h.loadSource(result.url));
   h.on(Hls.Events.MANIFEST_PARSED,()=>{if(!mvPickerOpen)mvResumePlayback(i)});
   h.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal){if(data.type===Hls.ErrorTypes.NETWORK_ERROR)h.startLoad();else if(data.type===Hls.ErrorTypes.MEDIA_ERROR)h.recoverMediaError();else fail(data.details||'HLS playback failed')}});
   h.attachMedia(video);
  }else{video.src=result.url;video.load()}
  if(!mvPickerOpen)mvResumePlayback(i);
  mvMessage(`Screen ${i+1}: ${ch.name} · Stream ready`);
 }catch(e){fail(e.message)}
}

function mvRenderBrowser(){
 const box=$('mvChannelList');if(!box)return;
 const q=$('mvSearch').value.toLowerCase(),matches=channels.map((c,i)=>({c,i})).filter(({c})=>isCategoryActive(c.group)&&(category==='All Channels'||(category==='Favorites'?isFavorite(c):c.group===category))&&c.name.toLowerCase().includes(q));
 box.replaceChildren();
 for(const {c,i} of matches.slice(0,500)){
  const b=document.createElement('button');b.className='mv-channel-option';b.textContent=c.name;b.title=c.group;
  b.onclick=()=>mvPick(mvSelected,i);box.append(b);
 }
 if(matches.length>500){const note=document.createElement('small');note.textContent='Showing first 500 matches; use search to narrow results';box.append(note)}
}
function mvBuild(){
 const count=Math.min(4,Math.max(2,Number($('mvCount').value)||4));
 const previous=mvPlayers.map(t=>t.channel?channelKey(t.channel):null);
 mvPlayers.forEach(mvDestroy);mvPlayers=[];const grid=$('mvGrid');grid.dataset.count=String(count);grid.replaceChildren();
 for(let i=0;i<count;i++){
  const element=document.createElement('div');element.className='mv-tile';element.tabIndex=0;element.setAttribute('role','button');element.setAttribute('aria-label',`Select screen ${i+1}`);
  const video=document.createElement('video');video.autoplay=true;video.playsInline=true;video.muted=true;video.controls=false;
  const top=document.createElement('div');top.className='mv-tile-top';
  const label=document.createElement('span');label.className='mv-tile-label';label.textContent=`Screen ${i+1} · Choose a channel`;
  const bottom=document.createElement('div');bottom.className='mv-tile-bottom';
  const audioButton=document.createElement('button');audioButton.textContent='🔇 Hear audio';audioButton.onclick=e=>{e.stopPropagation();mvSelect(i)};
  const expand=document.createElement('button');expand.textContent='↗ Watch solo';expand.onclick=e=>{e.stopPropagation();const c=mvPlayers[i]?.channel;if(!c)return;mvSoloReturn={keys:mvPlayers.map(t=>t.channel?channelKey(t.channel):null),count:mvPlayers.length,audio:mvAudio};showView('live');playChannel(c);$('backToMultiview').classList.remove('hidden');};
  const choose=document.createElement('button');choose.textContent='▤ Select Channel';choose.onclick=e=>{e.stopPropagation();mvOpenPicker(i)};
  const placeholder=document.createElement('div');placeholder.className='mv-placeholder';placeholder.textContent=`Screen ${i+1} · Choose a category and channel on the left`;
  element.onclick=()=>{mvSelect(i);if(mvPlayers[i]?.channel&&mvPlayers[i].video.paused)mvResumePlayback(i)};element.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();mvSelect(i)}};
  bottom.append(choose,audioButton,expand);top.append(label);element.append(video,placeholder,top,bottom);grid.append(element);
  mvPlayers.push({element,video,label,placeholder,audioButton,hls:null,channel:null,slot:i,generation:0});
  const wanted=previous[i]||(i===0&&current?channelKey(current):null);
  if(wanted){const j=channels.findIndex(c=>channelKey(c)===wanted);if(j>=0)mvPick(i,j)}
 }
 mvSelected=Math.min(mvSelected,count-1);mvSelect(mvSelected,false);
 mvMessage('Choose a screen, category and channel. Multiview uses separate FFmpeg playback streams; provider connection limits apply.');
}
function startMultiview(){clearPlayer();window.cadence.dvrStop().catch(()=>{});category='All Channels';mvBuild();if(mvSoloReturn){const saved=mvSoloReturn;mvSoloReturn=null;$('mvCount').value=String(saved.count);mvBuild();saved.keys.forEach((key,i)=>{const j=channels.findIndex(c=>channelKey(c)===key);if(j>=0)mvPick(i,j)});mvSetAudio(Math.min(saved.audio,mvPlayers.length-1));}$('backToMultiview').classList.add('hidden')}
function stopMultiview(){mvPickerOpen=false;$('layout').classList.remove('mv-picking');clearTimeout(mvHighlightTimer);mvPlayers.forEach(mvDestroy);mvPlayers=[];$('mvGrid').replaceChildren();if(current){const c=current;current=null;setTimeout(()=>{if(activeView!=='multiview')playChannel(c)},0)}}
$('backToMultiview').onclick=()=>showView('multiview');
$('mvCancelPick').onclick=mvClosePicker;
$('mvCount').onchange=mvBuild;
$('mvSearch').oninput=mvRenderBrowser;
function syncMultiviewFullscreen(){
 const full=document.fullscreenElement===$('multiviewPanel');
 $('mvFullscreen').classList.toggle('hidden',full);
 $('mvExitFullscreen').classList.toggle('hidden',!full);
 $('mvExitMode').classList.toggle('hidden',!full);
}
$('mvFullscreen').onclick=async()=>{try{await $('multiviewPanel').requestFullscreen();}catch(e){mvMessage('Fullscreen unavailable: '+e.message)}};
$('mvExitFullscreen').onclick=async()=>{if(document.fullscreenElement)await document.exitFullscreen()};
$('mvExitMode').onclick=async()=>{if(document.fullscreenElement)await document.exitFullscreen();showView('live')};
document.addEventListener('fullscreenchange',syncMultiviewFullscreen);

$('epgScroll').addEventListener('scroll',guideSync);
$('guideTimeline').oninput=e=>{const s=$('epgScroll');s.scrollLeft=(s.scrollWidth-s.clientWidth)*Number(e.target.value)/20};
$('guideEarlier').onclick=()=>{$('epgScroll').scrollBy({left:-4*GUIDE_SLOT_WIDTH,behavior:'smooth'})};
$('guideLater').onclick=()=>{$('epgScroll').scrollBy({left:4*GUIDE_SLOT_WIDTH,behavior:'smooth'})};
$('guideNow').onclick=()=>{$('epgScroll').scrollTo({left:0,behavior:'smooth'})};

// Bulk buttons are registered once and remain available after settings re-renders.
$('checkAllCategories')?.addEventListener('click',()=>setAllCategories(true));
$('uncheckAllCategories')?.addEventListener('click',()=>setAllCategories(false));
