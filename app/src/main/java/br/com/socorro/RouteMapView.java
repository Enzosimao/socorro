package br.com.socorro;

import android.content.Context;
import android.graphics.*;
import android.net.http.HttpResponseCache;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

 
final class RouteMapView extends View {
    private final JSONArray points;
    private final boolean dark;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path path=new Path();
    private final RectF tileRect=new RectF();
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> loading=ConcurrentHashMap.newKeySet();
    private final Map<String,Long> failures=new ConcurrentHashMap<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private final GestureDetector gestures;
    private final ScaleGestureDetector pinch;
    private final float density;
    private double centerX,centerY;
    private int zoom=16;
    private boolean closed,painted;
    private float scale=1;
    RouteMapView(Context context,JSONArray points,boolean dark){
        super(context);this.points=points;this.dark=dark;density=getResources().getDisplayMetrics().density;
        setContentDescription("Mapa do trajeto. Arraste para mover e use dois dedos ou os botões para aproximar.");
        try{if(HttpResponseCache.getInstalled()==null)HttpResponseCache.install(new File(context.getCacheDir(),"map-tiles"),20L*1024*1024);}catch(IOException ignored){}
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){
                if(!pinch.isInProgress()){centerX+=dx/world();centerY=Math.max(0,Math.min(1,centerY+dy/world()));invalidate();}return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e){changeZoom(1,e.getX(),e.getY());return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){performClick();return true;}
        });
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){scale*=d.getScaleFactor();if(scale>1.3f){changeZoom(1,d.getFocusX(),d.getFocusY());scale=1;}else if(scale<0.77f){changeZoom(-1,d.getFocusX(),d.getFocusY());scale=1;}return true;}
            @Override public void onScaleEnd(ScaleGestureDetector d){scale=1;}
        });
    }
    int pointCount(){return points.length();}
    boolean isPainted(){return painted;}
    private double world(){return 256.0*density*(1L<<zoom);}
    private double x(JSONArray p){return (p.optDouble(1)+180)/360;}
    private double y(JSONArray p){double lat=Math.toRadians(Math.max(-85.0511,Math.min(85.0511,p.optDouble(0))));return (1-Math.log(Math.tan(lat)+1/Math.cos(lat))/Math.PI)/2;}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);fit();}
    void fit(){
        if(points.length()==0||getWidth()==0||getHeight()==0)return;
        double minX=1,maxX=0,minY=1,maxY=0;
        for(int i=0;i<points.length();i++){JSONArray p=points.optJSONArray(i);minX=Math.min(minX,x(p));maxX=Math.max(maxX,x(p));minY=Math.min(minY,y(p));maxY=Math.max(maxY,y(p));}
        centerX=(minX+maxX)/2;centerY=(minY+maxY)/2;zoom=16;
        while(zoom>2&&((maxX-minX)*world()>getWidth()-64*density||(maxY-minY)*world()>getHeight()-64*density))zoom--;
        invalidate();
    }
    void zoomBy(int delta){changeZoom(delta,getWidth()/2f,getHeight()/2f);}
    private void changeZoom(int delta,float focusX,float focusY){
        double wx=centerX+(focusX-getWidth()/2.0)/world(),wy=centerY+(focusY-getHeight()/2.0)/world();
        zoom=Math.max(2,Math.min(19,zoom+delta));centerX=wx-(focusX-getWidth()/2.0)/world();centerY=Math.max(0,Math.min(1,wy-(focusY-getHeight()/2.0)/world()));invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event){pinch.onTouchEvent(event);gestures.onTouchEvent(event);return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(dark?0xff121212:0xffe8ece5);
        double size=world(),left=centerX*size-getWidth()/2.0,top=centerY*size-getHeight()/2.0;
        float tile=256*density;int count=1<<zoom,shown=0;boolean failed=false;
        int firstX=(int)Math.floor(left/tile),firstY=(int)Math.floor(top/tile);
        paint.setStyle(Paint.Style.FILL);paint.setStrokeWidth(density);paint.setColor(dark?0xff333333:0xffd8e0d3);
        for(int ty=firstY;ty*tile<top+getHeight();ty++)for(int tx=firstX;tx*tile<left+getWidth();tx++){
            if(ty<0||ty>=count)continue;
            int wrappedX=Math.floorMod(tx,count);String key=zoom+"/"+wrappedX+"/"+ty;
            tileRect.set((float)(tx*tile-left),(float)(ty*tile-top),(float)((tx+1)*tile-left),(float)((ty+1)*tile-top));
            Bitmap bitmap=cache.get(key);
            if(bitmap!=null){paint.setColor(Color.WHITE);canvas.drawBitmap(bitmap,null,tileRect,paint);shown++;}
            else {paint.setColor(dark?0xff333333:0xffd8e0d3);paint.setStyle(Paint.Style.STROKE);canvas.drawRect(tileRect,paint);paint.setStyle(Paint.Style.FILL);request(key);}
            failed|=failures.containsKey(key);
        }
        path.reset();float firstPX=0,firstPY=0,lastX=0,lastY=0;
        for(int i=0;i<points.length();i++){
            JSONArray p=points.optJSONArray(i);float px=(float)(x(p)*size-left),py=(float)(y(p)*size-top);
            if(i==0){path.moveTo(px,py);firstPX=px;firstPY=py;}else path.lineTo(px,py);lastX=px;lastY=py;
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.WHITE);paint.setStrokeWidth(8*density);canvas.drawPath(path,paint);
        paint.setColor(0xff28594d);paint.setStrokeWidth(4*density);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        if(points.length()>0){paint.setColor(Color.WHITE);canvas.drawCircle(firstPX,firstPY,9*density,paint);canvas.drawCircle(lastX,lastY,10*density,paint);paint.setColor(0xff28594d);canvas.drawCircle(firstPX,firstPY,6*density,paint);paint.setColor(0xffbf523f);canvas.drawCircle(lastX,lastY,7*density,paint);}
        if(shown==0){String message=failed?"Mapa-base indisponível. Traçado GPS preservado.":"Carregando ruas · traçado GPS disponível";paint.setColor(0xfffffdf5);tileRect.set(12*density,getHeight()-48*density,getWidth()-12*density,getHeight()-12*density);canvas.drawRoundRect(tileRect,8*density,8*density,paint);paint.setColor(0xff37453e);paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);canvas.drawText(message,22*density,getHeight()-26*density,paint);}
        painted=true;
    }
    private void request(String key){
        Long failedAt=failures.get(key);if(closed||failedAt!=null&&SystemClock.elapsedRealtime()-failedAt<60000||!loading.add(key))return;
        worker.execute(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL("https://tile.openstreetmap.org/"+key+".png").openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setUseCaches(true);
                connection.setRequestProperty("User-Agent","SocorroAndroid/1.1 (academic Android project)");
                if(connection.getResponseCode()!=200)throw new IOException("Mapa indisponível");
                Bitmap bitmap;try(InputStream in=connection.getInputStream()){bitmap=BitmapFactory.decodeStream(in);}
                if(bitmap==null)throw new IOException("Imagem inválida");cache.put(key,bitmap);failures.remove(key);
            }catch(Exception e){failures.put(key,SystemClock.elapsedRealtime());}
            finally{if(connection!=null)connection.disconnect();loading.remove(key);main.post(()->{if(!closed)invalidate();});}
        });
    }
    @Override protected void onDetachedFromWindow(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
}
