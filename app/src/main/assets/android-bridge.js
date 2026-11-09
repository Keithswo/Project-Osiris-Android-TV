// Promise-compatible replacement for the Electron preload API.
(function(){
let next=0;const pending=new Map();
window.__osirisResolve=(id,ok,value)=>{const p=pending.get(id);if(!p)return;pending.delete(id);(ok?p.resolve:p.reject)(ok?value:new Error(String(value)))};
function call(method,...args){return new Promise((resolve,reject)=>{const id=++next;pending.set(id,{resolve,reject});AndroidBridge.call(id,method,JSON.stringify(args))})}
window.cadence={
 profileSave:d=>call('profileSave',d),profileLoad:()=>call('profileLoad'),profileClear:()=>call('profileClear'),
 xtreamLogin:d=>call('xtreamLogin',d),xtreamEpg:()=>call('xtreamEpg'),
 openFile:filters=>call('openFile',filters),loadUrl:url=>call('loadUrl',url),
 dvrStart:(url,liveOnly)=>call('dvrStart',url,liveOnly),dvrStop:()=>call('dvrStop'),
 dvrStatus:()=>Promise.resolve({available:false,active:false}),
 mvStart:(slot,url)=>Promise.resolve({url}),mvStop:()=>Promise.resolve(true)
};
// Keep Backspace available to the Android keyboard for text editing.
})();
