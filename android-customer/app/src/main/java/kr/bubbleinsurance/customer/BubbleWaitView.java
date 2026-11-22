package kr.bubbleinsurance.customer;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.SystemClock;
import android.view.*;

/** Local loading illustration; never reads the authentication page or personal data. */
public final class BubbleWaitView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap mascot;
    private SoundPool sound;
    private int pop;
    private boolean soundReady;
    private long start=SystemClock.uptimeMillis(), tapped=-1000;
    private final RectF target=new RectF();
    private boolean reducedMotion;
    public BubbleWaitView(Context context){
        super(context);setBackgroundColor(Color.WHITE);setClickable(true);setFocusable(true);
        setContentDescription("인증 준비 중인 버블이. 누르면 톡 소리가 나요.");
        try(java.io.InputStream in=context.getAssets().open("assets/bubble-smile.png")){mascot=BitmapFactory.decodeStream(in);}catch(Exception ignored){}
        try{reducedMotion=android.provider.Settings.Global.getFloat(context.getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1)==0;}catch(Exception ignored){}
        sound=new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sound.setOnLoadCompleteListener((pool,id,status)->soundReady=status==0);
        try(android.content.res.AssetFileDescriptor fd=context.getAssets().openFd("pop.wav")){pop=sound.load(fd,1);}catch(Exception ignored){}
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);long now=SystemClock.uptimeMillis();float density=getResources().getDisplayMetrics().density;
        float side=Math.min(getWidth()*.58f,240*density),cx=getWidth()/2f,cy=getHeight()*.46f;
        float bob=reducedMotion?0:(float)Math.sin((now-start)/650.0)*7*density;
        float age=(now-tapped)/1000f;
        float bounce=age>=0&&age<.4f&&!reducedMotion?(float)Math.sin(age/.4*Math.PI)*.08f:0;
        float drawSide=side*(1+bounce);target.set(cx-drawSide/2,cy-drawSide/2+bob,cx+drawSide/2,cy+drawSide/2+bob);
        if(mascot!=null)c.drawBitmap(mascot,null,target,paint);
        if(age>=0&&age<.55f){
            float progress=age/.55f;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(Color.argb((int)(190*(1-progress)),142,177,201));
            for(int i=0;i<8;i++){double a=i*Math.PI/4;float radius=side*(.28f+progress*.32f);float x=cx+(float)Math.cos(a)*radius,y=cy+bob+(float)Math.sin(a)*radius;c.drawCircle(x,y,(3+5*(1-progress))*density,paint);}
            paint.setStyle(Paint.Style.FILL);
        }
        if(!reducedMotion || (age>=0&&age<.55f))postInvalidateOnAnimation();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_UP){if(target.contains(e.getX(),e.getY()))performClick();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();long now=SystemClock.uptimeMillis();if(now-tapped<140)return true;tapped=now;if(soundReady&&sound!=null)sound.play(pop,.28f,.28f,1,0,1);invalidate();return true;}
    @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(sound!=null){sound.release();sound=null;}}
}
