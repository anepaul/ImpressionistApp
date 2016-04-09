package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class ImpressionistView extends View {
    private final String TAG = getClass().getSimpleName();

    private ImageView _imageView;
    private Bitmap _originalImage;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();

    private PaintStroke _currPaintStroke = new PaintStroke();

    private int _alpha = 150;
    private Point _lastPoint = new Point();
    private long _lastPointTime = -1;
    private Paint _paintBorder = new Paint();
    private BrushType _brushType = BrushType.Square;
    private int _minBrushRadius = 5;

    private boolean _firstTouch = true;
    private boolean _ready = false;

    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setStrokeWidth(_minBrushRadius);

        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(50);

        _ready = false;

        //_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView){
        _imageView = imageView;
        _originalImage = imageView.getDrawingCache();
        if (_originalImage != null) _ready = true;
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        _brushType = brushType;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        //TODO
        _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
        _offScreenCanvas = new Canvas(_offScreenBitmap);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(_offScreenBitmap != null) {
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){

        //TODO
        //Basically, the way this works is to liste for Touch Down and Touch Move events and determine where those
        //touch locations correspond to the bitmap in the ImageView. You can then grab info about the bitmap--like the pixel color--
        //at that location
        if (_ready) {

            int touchX = (int) motionEvent.getX();
            int touchY = (int) motionEvent.getY();
            long time = motionEvent.getEventTime();

            switch (motionEvent.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (_firstTouch) {
                        _lastPointTime = time;
                        _lastPoint = new Point(touchX, touchY);
                    }
                    paintTheCanvas(touchX, touchY, time);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (motionEvent.getPointerCount() < 2) {
                        paintTheCanvas(touchX, touchY, time);
                    }
                    break;
                case MotionEvent.ACTION_UP:

                    break;
                case MotionEvent.ACTION_POINTER_DOWN:

                    break;
            }
        }


        return true;
    }

    private void paintTheCanvas(int x, int y, long time) {
        if (x > 0 && y > 0 && x < getWidth() && y < getHeight()) {
            Point point = new Point(x,y);
            int radius = _minBrushRadius;
            radius += getSpeed(point, _lastPoint, time, _lastPointTime);
            Log.i(TAG, "paintTheCanvas: speed = " + getSpeed(point, _lastPoint, time, _lastPointTime));
            _currPaintStroke.point.set(x, y);
            _currPaintStroke.paint.setColor(_originalImage.getPixel(x, y));
            switch (_brushType) {
                case Circle:
                    _offScreenCanvas.drawCircle(x, y, radius/2, _currPaintStroke.paint);
                    break;
                case Square:
                    _offScreenCanvas.drawRect(makeRect(x,y,radius/2),_currPaintStroke.paint);
                    break;
            }
            invalidate(makeRect(x,y,radius));
            _lastPoint=point;
            _lastPointTime = time;
        }
    }

    private int getSpeed(Point p1, Point p2, long t1, long t2) {
        int c = (int) Math.sqrt(Math.pow(p1.x - p2.x, 2.0) + Math.pow(p1.y - p2.y, 2.0));
        c = Math.abs(c) * 10;
        if (t1 != t2) {
            return c / (int) (t1 - t2);
        } else {
            return c;
        }
    }

    private Rect makeRect(int cx, int cy, int radius) {
        return new Rect(cx - (radius), cy - (radius), cx + (radius), cy + (radius));
    }




    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }

    public class PaintStroke {
        Paint paint;
        Point point;

        public PaintStroke(Paint paint, Point point) {
            this.paint = paint;
            this.point = point;
        }

        public PaintStroke() {
            paint = new Paint();
            point = new Point();
        }
    }
}

