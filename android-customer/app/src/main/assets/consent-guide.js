(function () {
  'use strict';
  function classifyLabel(text) {
    const s = String(text || '').replace(/\s+/g, '');
    if (!s || s.length > 160) return 'unknown';
    if (/동의하지|동의안|비동의|미동의|동의않/.test(s)) return 'decline';
    if (!/동의/.test(s)) return 'unknown';
    if (/전체|모두|일괄/.test(s)) return 'all';
    if (/선택|선택사항/.test(s)) return 'optional';
    return 'agree';
  }
  function nextStep(items) {
    if (items.some(x => x.kind === 'decline' && x.checked)) return {kind:'declined'};
    const item = items.find(x => x.kind === 'agree' && !x.checked && !x.disabled);
    if (item) return {kind:'consent', id:item.id};
    if (items.some(x => x.kind === 'agree' && !x.checked)) return {kind:'unknown'};
    if (items.some(x => x.kind === 'agree')) return {kind:'next'};
    return {kind:'unknown'};
  }
  if (typeof module !== 'undefined' && module.exports) {module.exports={classifyLabel,nextStep};return;}
  const KEY='__bubbleConsentGuide';
  if (window[KEY]) {window[KEY].update();return;}
  let marked=null, oldOutline='', oldOffset='';
  let introHandled=false;
  function introNotice(){
    if(introHandled)return null;
    const scope=document.querySelector('main,#contents,#content')||document.body;
    const text=(scope.innerText||'').trim();
    const targets=Array.from(document.querySelectorAll('a,button,input[type=button],input[type=submit]')).filter(el=>available(el)&&!el.disabled&&el.getAttribute('aria-disabled')!=='true'&&(el.value||el.textContent||'').replace(/\s/g,'')==='확인후다음으로이동');
    if(!text.includes('서비스 추진배경 및 목적')||targets.length!==1||text.length>24000)return null;
    return {text,button:targets[0]};
  }
  function clear(){if(marked){marked.style.outline=oldOutline;marked.style.outlineOffset=oldOffset;marked=null;}}
  function labelFor(el){
    const labels=el.labels?Array.from(el.labels):[];
    const label=labels.find(l=>l.getClientRects().length) || el.closest('label');
    return {node:label||el,text:el.getAttribute('aria-label')||(label?label.textContent:'')};
  }
  function available(el){const css=getComputedStyle(el);return css.display!=='none'&&css.visibility!=='hidden'&&el.getClientRects().length>0;}
  function point(el,kind){
    const r=el.getBoundingClientRect();
    if (r.top>=innerHeight-30 || r.bottom>innerHeight) return {kind:'scroll',direction:'down'};
    if (r.bottom<=0 || r.top<0) return {kind:'scroll',direction:'up'};
    if(marked!==el){clear();marked=el;oldOutline=el.style.outline;oldOffset=el.style.outlineOffset;el.style.outline='3px solid #245b8c';el.style.outlineOffset='5px';}
    return {kind};
  }
  const api={state:{kind:'none'},update(){
    clear();
    const controls=Array.from(document.querySelectorAll('input[type="checkbox"],input[type="radio"]'));
    const records=controls.map((el,i)=>{const label=labelFor(el);return {id:i,el,node:label.node,kind:classifyLabel(label.text),checked:el.checked,disabled:el.disabled};}).filter(x=>available(x.node));
    const step=nextStep(records);
    if(step.kind==='consent')api.state=point(records.find(x=>x.id===step.id).node,'consent');
    else if(step.kind==='declined')api.state={kind:'declined'};
    else if(step.kind==='next'){
      const forms=Array.from(new Set(records.filter(x=>x.kind==='agree').map(x=>x.el.form).filter(Boolean)));
      const scope=forms.length===1?forms[0]:document;
      const buttons=Array.from(scope.querySelectorAll('button,input[type="submit"],a[role="button"],a.btn, a.button')).filter(el=>available(el)&&!el.disabled&&el.getAttribute('aria-disabled')!=='true').filter(el=>/^(다음|다음단계|확인|동의하고계속|동의후계속|조회|조회하기)$/.test((el.tagName==='INPUT'?el.value:el.textContent).replace(/\s+/g,'')));
      api.state=buttons.length===1?point(buttons[0],'next'):{kind:'review'};
    }else api.state={kind:'none'};
    return api.state;
  },clear};
  api.intro=function(){const n=introNotice();return n?{text:n.text}:null;};
  api.dismissIntro=function(){introHandled=true;};
  api.acceptIntro=function(expected){
    const n=introNotice();if(!n||n.text!==expected)return false;
    introHandled=true;n.button.click();return true;
  };
  document.addEventListener('change',function(event){
    if(!event.isTrusted || !event.target.matches('input[type=checkbox],input[type=radio]'))return;
    if(classifyLabel(labelFor(event.target).text)!=='agree'||!event.target.checked)return;
    const form=event.target.form;if(!form)return;
    const items=Array.from(form.querySelectorAll('input[type=checkbox],input[type=radio]')).map(el=>({kind:classifyLabel(labelFor(el).text),checked:el.checked,disabled:el.disabled}));
    if(nextStep(items).kind!=='next')return;
    const buttons=Array.from(form.querySelectorAll('button,a,input[type=submit],input[type=button]')).filter(el=>available(el)&&!el.disabled&&el.getAttribute('aria-disabled')!=='true'&&/^(다음|다음단계|확인|동의하고계속|동의후계속)$/.test((el.value||el.textContent||'').replace(/\s/g,'')));
    if(buttons.length===1)buttons[0].click();
  });
  window[KEY]=api;
  api.update();
})();
