package kr.bubbleinsurance.customer;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String HIRA="https://www.hira.or.kr/dummy.do?pgmid=HIRAA030009200000";
    private static final int NAVY=Color.rgb(13,44,77);
    private LinearLayout root, bar;
    private WebView home, portal;
    private final java.util.ArrayList<WebView> authWindows=new java.util.ArrayList<>();
    private TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean downloading=false;
    private boolean openSimpleAuth=false;
    private boolean selectKakao=false;
    private final Handler loadingHandler=new Handler(Looper.getMainLooper());
    private BubbleWaitView waitCover;
    private final java.util.Map<WebView,BubbleWaitView> popupCovers=new java.util.HashMap<>();
    private int loadingSession=0;
    private boolean preparing=false;
    private PdfRenderer pdf;
    private ParcelFileDescriptor pdfFile;
    private int page=0;
    private ImageView preview;
    private TextView pageLabel;
    private File records;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(227,236,243));
        getWindow().setNavigationBarColor(Color.WHITE);
        records=new File(getFilesDir(),"records"); records.mkdirs();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        home=new WebView(this); configure(home);
        home.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                if(!r.isForMainFrame())return false;
                if("bubble.local".equals(r.getUrl().getHost())){
                    if("/connect".equals(r.getUrl().getPath())) {openSimpleAuth=true;selectKakao=true;openPortal();}
                    else if("/records".equals(r.getUrl().getPath())) showRecords();
                }
                return true;
            }
        });
        showHome();home.loadUrl("file:///android_asset/index.html");
    }
    private void configure(WebView w){
        w.setBackgroundColor(Color.WHITE);
        w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);
        w.getSettings().setAllowFileAccess(false);w.getSettings().setAllowContentAccess(false);
        w.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        w.getSettings().setSupportMultipleWindows(false);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(w,false);
        w.setWebChromeClient(new WebChromeClient());
    }
    private void attach(View v){if(v.getParent()!=null)((android.view.ViewGroup)v.getParent()).removeView(v);root.addView(v,new LinearLayout.LayoutParams(-1,0,1));}
    private void showHome(){cancelPreparation();closePdf();root.removeAllViews();attach(home);}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextColor(NAVY);b.setOnClickListener(v->action.run());return b;}
    private void toolbar(String title){root.removeAllViews();bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(button("닫기",this::showHome));status=new TextView(this);status.setText(title);status.setTextColor(NAVY);bar.addView(status,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);}
    private void consent(){new AlertDialog.Builder(this).setTitle("진료정보 연결")
        .setMessage("심평원에서 본인인증 후 다운로드를 눌러 주세요. 지원되는 진료기록 파일은 버블 앱 내부에 저장됩니다.\n\n이 시험 버전은 서버나 설계사에게 전송하지 않습니다. 실제 심평원 인증·다운로드 호환성을 확인하는 단계입니다.")
        .setNegativeButton("취소",null).setPositiveButton("동의하고 계속",(d,w)->openPortal()).show();}
    private void openPortal(){
        toolbar("심평원 본인인증");
        bar.addView(button("다시 시도",()->{if(portal!=null){portal.stopLoading();openSimpleAuth=true;selectKakao=true;beginPreparation();portal.loadUrl(HIRA);}}));
        bar.addView(button("받은 기록",this::showRecords));
        if(portal==null){
            portal=new WebView(this);configure(portal);
            configurePortal(portal);
            portal.loadUrl(HIRA);
        }
        attach(portal);
        if(openSimpleAuth || selectKakao)beginPreparation();
        if(openSimpleAuth && portal.getUrl()!=null){portal.evaluateJavascript("window.__bubbleNavStarted=false", value->advanceToSimpleAuth(portal,portal.getUrl()));}
    }
    private void configurePortal(WebView view){
        view.getSettings().setSupportMultipleWindows(true);
        view.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();String scheme=u.getScheme();
                if("https".equals(scheme))return false;
                if(!r.isForMainFrame())return true;
                try {
                    if("intent".equals(scheme)){
                        Intent i=Intent.parseUri(u.toString(),Intent.URI_INTENT_SCHEME);
                        i.addCategory(Intent.CATEGORY_BROWSABLE);i.setComponent(null);i.setSelector(null);
                        i.setAction(Intent.ACTION_VIEW);i.setFlags(0);
                        Uri data=i.getData();
                        if(data==null || unsafeScheme(data.getScheme()))throw new IllegalArgumentException();
                        startActivity(i);
                    }else if(!unsafeScheme(scheme))startActivity(new Intent(Intent.ACTION_VIEW,u));
                }catch(Exception e){message("인증 앱을 열지 못했어요. 해당 인증 앱의 설치 여부를 확인해 주세요.");}
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,Bitmap icon){if(status!=null)status.setText("심평원 연결 중…");}
            @Override public void onPageFinished(WebView v,String url){if(status!=null&&!downloading)status.setText("본인인증 후 기록 다운로드");advanceToSimpleAuth(v,url);advanceToKakao(v,url);}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()&&status!=null)status.setText("연결 실패 · 다시 시도");}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame()&&status!=null)status.setText("사이트 응답 오류 · 다시 시도");}
        });
        view.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView parent,boolean dialog,boolean gesture,Message result){
                boolean requestedSimpleAuth=parent==portal && openSimpleAuth && parent.getUrl()!=null && trusted(Uri.parse(parent.getUrl())) && "/anyid".equals(Uri.parse(parent.getUrl()).getPath());
                if((!gesture && !requestedSimpleAuth) || authWindows.size()>=3)return false;
                if(requestedSimpleAuth)openSimpleAuth=false;
                WebView child=new WebView(MainActivity.this);configure(child);configurePortal(child);
                Dialog popup=new Dialog(MainActivity.this);LinearLayout layout=new LinearLayout(MainActivity.this);layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(button("인증 창 닫기",popup::dismiss));layout.addView(child,new LinearLayout.LayoutParams(-1,0,1));
                FrameLayout stage=new FrameLayout(MainActivity.this);stage.setBackgroundColor(Color.WHITE);stage.addView(layout,new FrameLayout.LayoutParams(-1,-1));
                if(preparing){BubbleWaitView cover=new BubbleWaitView(MainActivity.this);popupCovers.put(child,cover);stage.addView(cover,new FrameLayout.LayoutParams(-1,-1));}
                popup.setContentView(stage);popup.setOnDismissListener(d->{popupCovers.remove(child);authWindows.remove(child);child.destroy();if(preparing)cancelPreparation();});
                authWindows.add(child);popup.show();popup.getWindow().setLayout(-1,-1);
                child.setTag(popup);
                ((WebView.WebViewTransport)result.obj).setWebView(child);result.sendToTarget();return true;
            }
            @Override public void onCloseWindow(WebView window){if(window.getTag() instanceof Dialog)((Dialog)window.getTag()).dismiss();}
        });
        view.setDownloadListener((url,agent,disposition,mime,size)->receive(url,agent,size));
    }
    private void advanceToSimpleAuth(WebView view,String url){
        if(!openSimpleAuth || view!=portal || url==null)return;
        Uri uri=Uri.parse(url);if(!trusted(uri))return;
        boolean intro="/dummy.do".equals(uri.getPath()) && "HIRAA030009200000".equals(uri.getQueryParameter("pgmid"));
        boolean auth="/anyid".equals(uri.getPath());if(!intro&&!auth)return;
        String selector=intro ? "Array.from(document.querySelectorAll('a')).find(a=>a.textContent.replace(/\\s/g,'').includes('내진료정보열람')&&a.textContent.includes('조회하기'))" : "document.querySelector('a[title=\"간편인증 새창열림\"]')";
        // Only advance the two public navigation controls. Never fill identity fields,
        // choose a certificate provider, accept terms or submit an authentication request.
        view.evaluateJavascript("(function(){if(window.__bubbleNavStarted)return;window.__bubbleNavStarted=true;let n=0;function next(){let a="+selector+";if(a&&a.getClientRects().length){a.click();return;}if(++n<60)setTimeout(next,500);}next();})()",null);
    }
    private void advanceToKakao(WebView view,String url){
        if(!selectKakao || url==null || !authWindows.contains(view))return;
        Uri uri=Uri.parse(url);
        if(!"https".equals(uri.getScheme()) || !"easysign.anyid.go.kr".equals(uri.getHost()) || !"/esign/".equals(uri.getPath()))return;
        selectKakao=false;
        // The user explicitly chose Kakao as the initial provider. No identity values,
        // consent checkboxes or authentication submission are touched.
        view.evaluateJavascript("(function(){if(window.__bubbleKakaoStarted)return;window.__bubbleKakaoStarted=true;let n=0;function choose(){let img=document.querySelector('img[alt=\"KAKAO(카카오)\"]');let a=img&&img.closest('a');if(a&&a.getClientRects().length){a.click();requestAnimationFrame(()=>requestAnimationFrame(()=>{window.__bubbleKakaoReady=true;}));return;}if(++n<60)setTimeout(choose,500);}choose();})()",null);
        checkAuthReady(view,loadingSession,0);
    }
    private void beginPreparation(){
        removeCover(waitCover);waitCover=new BubbleWaitView(this);preparing=true;final int session=++loadingSession;
        addContentView(waitCover,new android.view.ViewGroup.LayoutParams(-1,-1));
        loadingHandler.postDelayed(()->{if(preparing&&session==loadingSession){cancelPreparation();message("인증창 준비가 지연되고 있어요. 다시 시도하거나 현재 화면에서 간편인증을 눌러 주세요.");}},45000);
    }
    private void removeCover(View cover){if(cover!=null&&cover.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)cover.getParent()).removeView(cover);}
    private void cancelPreparation(){preparing=false;loadingSession++;openSimpleAuth=false;selectKakao=false;removeCover(waitCover);waitCover=null;for(BubbleWaitView v:popupCovers.values())removeCover(v);popupCovers.clear();}
    private void checkAuthReady(WebView view,int session,int attempt){
        if(!preparing||session!=loadingSession||!authWindows.contains(view)||attempt>=80)return;
        String current=view.getUrl();if(current==null||!"easysign.anyid.go.kr".equals(Uri.parse(current).getHost()))return;
        view.evaluateJavascript("Boolean(window.__bubbleKakaoReady && Array.from(document.querySelectorAll('input')).some(e=>e.type==='text'&&e.getClientRects().length))",value->{
            if(!preparing||session!=loadingSession)return;
            if("true".equals(value)){cancelPreparation();}
            else loadingHandler.postDelayed(()->checkAuthReady(view,session,attempt+1),350);
        });
    }
    private static boolean unsafeScheme(String s){return s==null||s.matches("(?i)(file|content|javascript|data|http|intent|about)");}
    static boolean trusted(Uri u){String h=u.getHost();return "https".equals(u.getScheme())&&h!=null&&(h.equals("hira.or.kr")||h.endsWith(".hira.or.kr"))&&(u.getPort()==-1||u.getPort()==443);}
    private void receive(String url,String agent,long size){
        if(downloading)return;
        if(!trusted(Uri.parse(url))){message("이 다운로드 방식은 아직 지원하지 않습니다. 파일을 받지 않았습니다.");return;}
        if(size>30*1024*1024){message("파일이 너무 큽니다. 최대 30MB까지 받을 수 있습니다.");return;}
        downloading=true;status.setText("진료기록 받는 중…");
        final String cookies=android.webkit.CookieManager.getInstance().getCookie(url);
        worker.execute(()->{
            File tmp=null;
            try {
                tmp=File.createTempFile("record-",".part",records);
                URL next=new URL(url);HttpURLConnection conn=null;
                try {
                    for(int redirect=0;redirect<6;redirect++){
                        if(!trusted(Uri.parse(next.toString())))throw new IOException("unsupported host");
                        conn=(HttpURLConnection)next.openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(20000);conn.setReadTimeout(30000);
                        conn.setRequestProperty("User-Agent",agent);
                        if(cookies!=null && next.getHost().equals(new URL(url).getHost()))conn.setRequestProperty("Cookie",cookies);
                        int code=conn.getResponseCode();
                        if(code>=300&&code<400){String location=conn.getHeaderField("Location");if(location==null)throw new IOException();next=new URL(next,location);conn.disconnect();conn=null;continue;}
                        if(code!=200)throw new IOException("HTTP "+code);
                        break;
                    }
                    if(conn==null||conn.getResponseCode()!=200)throw new IOException();
                    try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(tmp)){
                        byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>30*1024*1024)throw new IOException("too large");out.write(buf,0,n);}
                    }
                } finally {if(conn!=null)conn.disconnect();}
                byte[] magic=new byte[5];try(FileInputStream in=new FileInputStream(tmp)){if(in.read(magic)!=5)throw new IOException();}
                String ext;
                if(new String(magic,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))ext=".pdf";
                else if((magic[0]&255)==0x50&&(magic[1]&255)==0x4b)ext=".zip";
                else if((magic[0]&255)==0xd0&&(magic[1]&255)==0xcf)ext=".xls";
                else throw new IOException("unsupported format");
                File saved=new File(records,"진료기록-"+System.currentTimeMillis()+ext);if(!tmp.renameTo(saved))throw new IOException();
                runOnUiThread(()->{downloading=false;showRecords();message("기록을 앱에 저장했어요. 서버에는 전송하지 않았습니다.");});
            }catch(Exception e){if(tmp!=null)tmp.delete();runOnUiThread(()->{downloading=false;if(status!=null)status.setText("파일 받기 실패");message("파일을 받지 못했습니다. 인증 만료 또는 지원하지 않는 다운로드 형식일 수 있습니다. 다시 다운로드해 주세요.");});}
        });
    }
    private void showRecords(){
        closePdf();toolbar("받은 진료기록");ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setPadding(24,20,24,24);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);attach(scroll);
        File[] files=records.listFiles((dir,name)->!name.endsWith(".part"));
        if(files==null||files.length==0){TextView empty=new TextView(this);empty.setText("아직 받은 진료기록이 없어요.");empty.setTextColor(NAVY);list.addView(empty);return;}
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files){list.addView(button(f.getName(),()->{if(f.getName().endsWith(".pdf"))openPdf(f);else message("파일은 저장되었습니다. 이 형식의 내용 보기는 아직 준비 중입니다.");}));list.addView(button("삭제",()->new AlertDialog.Builder(this).setMessage("이 기록을 기기에서 삭제할까요?").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{if(!f.delete())message("삭제하지 못했습니다.");showRecords();}).show()));}
    }
    private void openPdf(File file){
        closePdf();
        try{pdfFile=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);pdf=new PdfRenderer(pdfFile);page=0;toolbar("진료기록");bar.addView(button("목록",this::showRecords));preview=new ImageView(this);preview.setAdjustViewBounds(true);ScrollView scroll=new ScrollView(this);scroll.addView(preview);attach(scroll);LinearLayout controls=new LinearLayout(this);controls.addView(button("이전",()->{if(page>0){page--;renderPage();}}));pageLabel=new TextView(this);controls.addView(pageLabel);controls.addView(button("다음",()->{if(pdf!=null&&page+1<pdf.getPageCount()){page++;renderPage();}}));root.addView(controls);renderPage();}
        catch(Exception e){closePdf();showRecords();message("이 PDF는 열 수 없습니다. 암호가 있거나 지원되지 않는 파일일 수 있습니다.");}
    }
    private void renderPage(){if(pdf==null||pdf.getPageCount()==0)return;try(PdfRenderer.Page p=pdf.openPage(page)){int width=Math.min(getResources().getDisplayMetrics().widthPixels,1600);int height=Math.min(5000,Math.max(1,(int)((double)width*p.getHeight()/p.getWidth())));Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);preview.setImageBitmap(b);pageLabel.setText((page+1)+" / "+pdf.getPageCount());}}
    private void closePdf(){if(pdf!=null){pdf.close();pdf=null;}if(pdfFile!=null){try{pdfFile.close();}catch(IOException ignored){}pdfFile=null;}}
    private void message(String s){if(!isFinishing())new AlertDialog.Builder(this).setMessage(s).setPositiveButton("확인",null).show();}
    @Override public void onBackPressed(){if(preparing){cancelPreparation();showHome();return;}if(home.getParent()!=null){super.onBackPressed();}else if(portal!=null&&portal.getParent()!=null&&portal.canGoBack())portal.goBack();else showHome();}
    @Override protected void onDestroy(){cancelPreparation();loadingHandler.removeCallbacksAndMessages(null);closePdf();for(WebView w:new java.util.ArrayList<>(authWindows)){if(w.getTag() instanceof Dialog)((Dialog)w.getTag()).dismiss();}worker.shutdownNow();if(portal!=null)portal.destroy();if(home!=null)home.destroy();super.onDestroy();}
}
