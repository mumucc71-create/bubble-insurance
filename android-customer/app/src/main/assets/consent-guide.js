(function(){
'use strict';
const KEY='__bubbleConsentGuide';
if(window[KEY]){window[KEY].update();return;}
const compact=s=>String(s||'').replace(/\s+/g,'');
const available=e=>e&&e.getClientRects().length&&getComputedStyle(e).display!=='none'&&getComputedStyle(e).visibility!=='hidden';
const scope=()=>document.querySelector('main,#contents,#content')||document.body;
const text=()=>String(scope().innerText||'').trim();
const controls=()=>Array.from(scope().querySelectorAll('input[type=checkbox],input[type=radio]'));
const buttons=()=>Array.from(scope().querySelectorAll('a,button,input[type=button],input[type=submit]')).filter(e=>available(e)&&!e.disabled&&e.getAttribute('aria-disabled')!=='true');
const label=e=>e.getAttribute('aria-label')||Array.from(e.labels||[]).map(l=>l.textContent).join(' ')||e.closest('label')?.textContent||'';
const kind=s=>/동의하지|동의안|비동의|미동의|동의않/.test(compact(s))?'decline':/전체|모두|일괄/.test(s)&&/동의/.test(s)?'all':/동의/.test(s)?'agree':'unknown';
let introHandled=false,paused=false,enabled=false,lastClick=0,downloaded=false,periodChosen=false;
const acted=new WeakSet(), resolved=new WeakSet();
let pending=null;
function choices(){return controls().filter(e=>!e.disabled&&available(e.labels?.[0]||e)&&kind(label(e))==='agree').map((e)=>{
 const container=e.closest('fieldset,tr,section,.agree_box,.agreeBox,.agreement')||e.parentElement;
 const context=(container?.innerText||label(e)).trim();
 return {el:e,label:label(e).trim(),optional:/선택|선택사항/.test(context)&&!/필수/.test(context),checked:e.checked};
});}
function notice(){
 if(introHandled||paused)return null;
 const content=text(), matches=buttons().filter(e=>compact(e.value||e.textContent)==='확인후다음으로이동');
 if(!content.includes('서비스 추진배경 및 목적')||matches.length!==1||content.length>40000)return null;
 return {text:content,button:matches[0]};
}
function snapshot(){
 const all=choices();if(!all.length||all.every(c=>resolved.has(c.el))||paused)return null;
 const content=text();if(!content||content.length>60000)return null;
 const data={text:content,choices:all.map((c,i)=>({id:i,label:c.label,optional:c.optional,checked:c.checked}))};
 pending={data,all};return data;
}
function clickOnce(e){if(!e||acted.has(e)||Date.now()-lastClick<1800)return false;acted.add(e);lastClick=Date.now();e.click();return true;}
function exact(pattern){const b=buttons().filter(e=>pattern.test(compact(e.value||e.textContent)));return b.length===1?b[0]:null;}
const api={state:{kind:'none'},setEnabled(value){enabled=!!value;},intro(){const n=notice();return n?{text:n.text}:null;},dismissIntro(){if(!introHandled)paused=true;},acceptIntro(expected){const n=notice();if(!n||n.text!==expected)return false;introHandled=true;enabled=true;return clickOnce(n.button);},consents:snapshot,
 applyConsents(expected,decisions){
  const fresh=snapshot();let old;try{old=JSON.parse(expected);}catch(e){return false;}if(!fresh||fresh.text!==old.text||!Array.isArray(old.choices)||fresh.choices.length!==old.choices.length||fresh.choices.some((c,i)=>c.label!==old.choices[i].label||c.checked!==old.choices[i].checked)||!Array.isArray(decisions)||decisions.length!==pending.all.length)return false;
  const all=pending.all;
  // Validate every choice and its matching decline control before changing anything.
  const targets=all.map((c,i)=>{if(typeof decisions[i]!=='boolean')return null;if(decisions[i])return c.el;if(c.el.type==='checkbox')return c.el;const no=controls().filter(e=>e.type==='radio'&&e.name===c.el.name&&kind(label(e))==='decline'&&!e.disabled);return no.length===1?no[0]:null;});
  if(targets.some(e=>!e))return false;
  all.forEach((c,i)=>{const target=targets[i];if(c.el.type==='checkbox'){if(c.el.checked!==decisions[i])target.click();}else if(!target.checked)target.click();resolved.add(c.el);});
  if(all.some((c,i)=>c.el.checked!==decisions[i]))return false;
  paused=all.some((c,i)=>!decisions[i]&&!c.optional);enabled=!paused;pending=null;return true;
 },pause(){paused=true;},update(){
  api.state={kind:'none'};
  if(paused){api.state={kind:'paused'};return;}
  if(notice()||snapshot()){api.state={kind:'consent'};return;}
  if(choices().some(c=>!resolved.has(c.el))){api.state={kind:'review'};return;}
  if(!enabled||Date.now()-lastClick<1800)return;
  const body=text();if(!/진료정보/.test(body))return;
  if(/진료정보요약|진료정보상세|진료내역/.test(compact(body))){
   const download=exact(/^전체다운로드[↓⇩]?$/);
   if(download&&!downloaded&&clickOnce(download)){downloaded=true;api.state={kind:'downloading'};}return;
  }
  // Only the record-query screen may set the previously requested five-year range.
  if(/대상기간/.test(body)&&/병.?의원|약국/.test(body)){
   const five=exact(/^5년$/);
   if(!periodChosen){if(five&&clickOnce(five))periodChosen=true;else api.state={kind:'review'};return;}
   const query=exact(/^(조회|조회하기)$/);if(query&&clickOnce(query))api.state={kind:'querying'};return;
  }
  const next=exact(/^(다음|다음단계|확인후다음으로이동|동의하고계속|동의후계속)$/);
  if(next&&clickOnce(next))api.state={kind:'advancing'};
 }};
window[KEY]=api;api.update();
})();
