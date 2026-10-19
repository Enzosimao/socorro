package br.com.socorro;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class SignalView extends View {
    private final float[] samples = new float[100];
    private int count;
    private boolean dark;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    SignalView(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void add(double value, boolean dark) {
        this.dark=dark;if(!Double.isFinite(value))return;
        if(count<samples.length)samples[count++]=(float)value;
        else {System.arraycopy(samples,1,samples,0,samples.length-1);samples[samples.length-1]=(float)value;}
        invalidate();
    }
    @Override protected void onDraw(Canvas c){
        float d=getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(dark?0xff333333:0xffe2e6dc);
        float max=4;for(int i=0;i<count;i++)max=Math.max(max,samples[i]+0.3f);
        for(int i=1;i<4;i++)c.drawLine(0,getHeight()*i/4f,getWidth(),getHeight()*i/4f,paint);
        if(count<2)return;
        Path path=new Path();
        for(int i=0;i<count;i++){float x=getWidth()*i/99f,y=getHeight()-8*d-samples[i]/max*(getHeight()-16*d);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
        paint.setColor(dark?0xffdddddd:0xff28594d);paint.setStrokeWidth(2*d);paint.setStrokeCap(Paint.Cap.ROUND);c.drawPath(path,paint);
    }
}
