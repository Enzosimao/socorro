package br.com.socorro;

import android.content.Context;
import android.graphics.*;
import android.view.View;

 
final class Glyph extends View {
    private final String name; private int color; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    Glyph(Context c,String name,int color){super(c);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void tint(int color){this.color=color;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();float size=Math.min(getWidth(),getHeight());canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        Path a=new Path();
        switch(name){
            case "shield":a.moveTo(12,3);a.lineTo(20,6);a.lineTo(20,12);a.cubicTo(20,17,16,20,12,22);a.cubicTo(8,20,4,17,4,12);a.lineTo(4,6);a.close();canvas.drawPath(a,p);canvas.drawLine(8,12,11,15,p);canvas.drawLine(11,15,16,9,p);break;
            case "home":a.moveTo(3,11);a.lineTo(12,3);a.lineTo(21,11);canvas.drawPath(a,p);canvas.drawRoundRect(5,10,19,21,1,1,p);canvas.drawRect(10,15,14,21,p);break;
            case "wave":a.moveTo(2,12);a.lineTo(6,12);a.lineTo(9,5);a.lineTo(13,20);a.lineTo(17,9);a.lineTo(19,12);a.lineTo(22,12);canvas.drawPath(a,p);break;
            case "history":canvas.drawCircle(12,12,8,p);canvas.drawLine(12,7,12,12,p);canvas.drawLine(12,12,16,14,p);break;
            case "settings":canvas.drawLine(4,6,20,6,p);canvas.drawLine(4,12,20,12,p);canvas.drawLine(4,18,20,18,p);canvas.drawCircle(9,6,2,p);canvas.drawCircle(16,12,2,p);canvas.drawCircle(10,18,2,p);break;
            case "pin":canvas.drawCircle(12,9,2.5f,p);a.moveTo(12,22);a.cubicTo(6,15,4,12,5,8);a.cubicTo(7,0,17,0,19,8);a.cubicTo(20,12,18,15,12,22);canvas.drawPath(a,p);break;
            case "arrow":canvas.drawLine(5,12,19,12,p);canvas.drawLine(14,7,19,12,p);canvas.drawLine(14,17,19,12,p);break;
            case "back":canvas.drawLine(19,12,5,12,p);canvas.drawLine(10,7,5,12,p);canvas.drawLine(10,17,5,12,p);break;
            case "alert":canvas.drawCircle(12,12,9,p);canvas.drawLine(12,7,12,13,p);canvas.drawCircle(12,17,0.5f,p);break;
            case "person":canvas.drawCircle(12,7,4,p);canvas.drawArc(4,13,20,25,180,180,false,p);break;
            default:canvas.drawCircle(12,12,8,p);
        }
        canvas.restore();
    }
}
