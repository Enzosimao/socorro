package br.com.socorro;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONArray;

 
final class TraceView extends View {
    private JSONArray points = new JSONArray();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean dark;
    TraceView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void update(JSONArray points, boolean dark) { this.points = points; this.dark = dark; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        c.drawColor(dark ? Color.rgb(18,18,18) : Color.rgb(234,238,229));
        paint.setColor(dark ? 0xff333333 : 0xffdce3d6); paint.setStrokeWidth(d);
        for (float x=0; x<getWidth(); x+=32*d) c.drawLine(x,0,x,getHeight(),paint);
        for (float y=0; y<getHeight(); y+=32*d) c.drawLine(0,y,getWidth(),y,paint);
        if (points.length()==0) {
            paint.setColor(dark ? 0xffbbbbbb : 0xff6e7c70); paint.setTextSize(13*getResources().getDisplayMetrics().scaledDensity); paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("O traçado aparece com o GPS", getWidth()/2f, getHeight()/2f+4*d, paint); return;
        }
        double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minY=Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
        double cos=Math.cos(Math.toRadians(points.optJSONArray(0).optDouble(0)));
        for(int i=0;i<points.length();i++){JSONArray p=points.optJSONArray(i);double x=p.optDouble(1)*cos,y=-p.optDouble(0);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        double rangeX=Math.max(0.0003,maxX-minX),rangeY=Math.max(0.0003,maxY-minY);
        double scale=Math.min((getWidth()-64*d)/rangeX,(getHeight()-48*d)/rangeY);
        Path path=new Path();float lastX=0,lastY=0,firstX=0,firstY=0;
        for(int i=0;i<points.length();i++){
            JSONArray p=points.optJSONArray(i);float x=(float)(getWidth()/2.0+(p.optDouble(1)*cos-(minX+maxX)/2)*scale);
            float y=(float)(getHeight()/2.0+(-p.optDouble(0)-(minY+maxY)/2)*scale);
            if(i==0){path.moveTo(x,y);firstX=x;firstY=y;}else path.lineTo(x,y);lastX=x;lastY=y;
        }
        paint.setStyle(Paint.Style.STROKE);paint.setColor(dark?0xffdddddd:0xff28594d);paint.setStrokeWidth(3*d);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff28594d);c.drawCircle(firstX,firstY,5*d,paint);paint.setColor(0xffbf523f);c.drawCircle(lastX,lastY,7*d,paint);
        paint.setColor(Color.WHITE);c.drawCircle(lastX,lastY,3*d,paint);
    }
}
