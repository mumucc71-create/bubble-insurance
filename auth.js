const db=window.supabase.createClient('https://isnhhkqorpntnsepiixw.supabase.co','sb_publishable_W1o4IMAHrP00Os2XGOKJ9Q_rWcXJLpe');
window.bubbleDB=db;
let mode='login';
const el=s=>document.querySelector(s);
function authMessage(s){el('#authMessage').textContent=s;}
function setMode(m){mode=m;el('#authTitle').textContent=m==='signup'?'회원가입':'로그인';el('#authSubmit').textContent=m==='signup'?'회원가입':'로그인';el('#authSwitch').textContent=m==='signup'?'이미 가입했어요 · 로그인':'처음이세요? 회원가입';el('#authPassword').autocomplete=m==='signup'?'new-password':'current-password';el('#authPassword').minLength=m==='signup'?8:1;authMessage('');}
el('#authSwitch').onclick=()=>setMode(mode==='login'?'signup':'login');
el('#authForm').onsubmit=async e=>{e.preventDefault();const b=el('#authSubmit');b.disabled=true;authMessage('확인 중이에요.');try{const email=el('#authEmail').value.trim(),password=el('#authPassword').value;const {data,error}=mode==='signup'?await db.auth.signUp({email,password,options:{emailRedirectTo:new URL('index.html',location.href).href}}):await db.auth.signInWithPassword({email,password});if(error)throw error;el('#authPassword').value='';if(mode==='signup'&&!data.session){authMessage('가입 확인 메일을 보냈어요. 이메일의 확인 링크를 누른 뒤 로그인해 주세요.');}else{authMessage('');}}catch(error){const code=error.code||'';authMessage(code==='invalid_credentials'?'이메일 또는 비밀번호를 확인해 주세요.':code==='email_not_confirmed'?'이메일의 가입 확인 링크를 먼저 눌러 주세요.':/rate|limit/.test(code)?'요청이 많아요. 잠시 후 다시 시도해 주세요.':code==='email_address_not_authorized'?'현재 인증 메일 발송 설정이 필요해요. 관리자에게 알려 주세요.':'연결하지 못했어요. '+(error.message||'잠시 후 다시 시도해 주세요.'));}finally{b.disabled=false;}};
function showAccount(session){const signed=!!session;el('#authScreen').hidden=signed;el('.phone').hidden=!signed;el('#accountEmail').textContent=session?.user?.email||'';el('#authPassword').value='';if(!signed){document.querySelectorAll('dialog[open]').forEach(d=>d.close());el('#customerForm').reset();el('#search').value='';}window.dispatchEvent(new CustomEvent('bubble-session',{detail:session}));}
db.auth.onAuthStateChange((_event,session)=>{setTimeout(()=>showAccount(session),0);});
el('#logout').onclick=async()=>{const {error}=await db.auth.signOut();if(error){el('#accountMessage').textContent='로그아웃하지 못했어요. 다시 시도해 주세요.';return;}el('#accountDialog').close();};
el('#accountClose').onclick=()=>el('#accountDialog').close();
db.auth.getSession().then(({data,error})=>{if(error)authMessage('로그인 정보를 확인하지 못했어요. 다시 로그인해 주세요.');showAccount(data?.session||null);});
