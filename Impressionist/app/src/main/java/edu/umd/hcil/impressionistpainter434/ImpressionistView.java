package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.text.MessageFormat;


public class ImpressionistView extends View {
    private final String TAG = getClass().getSimpleName();

    private ImageView _imageView;
    private Bitmap _originalImage;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();

    private int _alpha = 150;
    private Point _lastPoint = new Point();
    private long _lastPointTime = -1;
    private Paint _paintBorder = new Paint();
    private BrushType _brushType = BrushType.Square;
    private int _minBrushRadius = 5;
    private Matrix _dstMatrix = null;
    private Matrix _srcMatrix = null;
    private Point _currPoint = new Point();

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
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     * - http://stackoverflow.com/a/15538856
     * - http://stackoverflow.com/a/26930938
     *
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView) {
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

        int top = (imgViewH - heightActual) / 2;
        int left = (imgViewW - widthActual) / 2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
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

    public void setMatrix(Matrix matrix) {
        if (matrix != null) {
            _dstMatrix = matrix;
            _srcMatrix = new Matrix();
            if (!_dstMatrix.invert(_srcMatrix)) {
                Log.w(TAG, "setMatrix: Matrix is not invertible");
            }
            invalidate();
        }
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
        _offScreenCanvas = new Canvas(_offScreenBitmap);
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(_offScreenBitmap != null) {
//            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
            if (_dstMatrix == null) {
                canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
            } else {
                canvas.drawBitmap(_offScreenBitmap, _dstMatrix, _paint);
            }
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){

        if (_ready) {
            _currPoint.x = (int) motionEvent.getX();
            _currPoint.y = (int) motionEvent.getY();

            long time = motionEvent.getEventTime();

            switch (motionEvent.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    updateLastTouchEvent(motionEvent);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (motionEvent.getPointerCount() < 2) {
                        paintCanvas(mapXY(_currPoint, _srcMatrix), getSpeed(_currPoint, _lastPoint, time, _lastPointTime));
                        updateLastTouchEvent(motionEvent);
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

    public void paintTheCanvas() {
        for (int x = 0; x < _offScreenBitmap.getWidth(); x += 10) {
            for (int y = 0; y < _offScreenBitmap.getHeight(); y += 10) {
                paintCanvas(mapXY(new Point(x + (int) (Math.random() * 5), y + (int) (Math.random() * 5)), _srcMatrix),
                        (int) (Math.random() * 10));
            }
        }
        postInvalidate();
    }

    private Point mapXY(Point p, Matrix m) {
        float[] values = new float[2];
        values[0] = p.x;
        values[1] = p.y;
        m.mapPoints(values);
        return new Point((int) values[0], (int) values[1]);
    }

    private void updateLastTouchEvent(MotionEvent motionEvent) {
        _lastPointTime = motionEvent.getEventTime();
        _lastPoint = new Point((int) motionEvent.getX(), (int) motionEvent.getY());
    }

    private void paintCanvas(Point point, int speed) {
//        float [] outPoints = mapXY(point, _dstMatrix);
//        float [] inPoints = mapXY(point, _srcMatrix);
//        int outX = (int) outPoints[0];
//        int outY = (int) outPoints[1];
        int outX = point.x;
        int outY = point.y;
//        int inX = (int) inPoints[0];
//        int inY = (int) inPoints[1];
        int inX = point.x;
        int inY = point.y;
        if (inX > 0 && inY > 0 && inX < _originalImage.getWidth() && inY < _originalImage.getHeight()) {
            int radius = 10 + speed;
            // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
            float[] f = new float[9];
            _dstMatrix.getValues(f);
            final float scale = f[Matrix.MSCALE_X] / 3;
            radius /= scale;
//            Log.i(TAG, "paintCanvas: radius = " + radius + ", speed = " + speed);
            _paint.setColor(_originalImage.getPixel(inX, inY));
            switch (_brushType) {
                case Circle:
                    _offScreenCanvas.drawCircle(outX, outY, radius / 2, _paint);
                    break;
                case Square:
                    _offScreenCanvas.drawRect(makeRect(outX, outY, radius / 2), _paint);
                    break;
            }
            invalidate(makeRect(inX, inY, radius));
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

//    public class PaintStroke {
//        Paint paint;
//        Point point;
//
//        public PaintStroke(Paint paint, Point point) {
//            this.paint = paint;
//            this.point = point;
//        }
//
//        public PaintStroke() {
//            paint = new Paint();
//            point = new Point();
//        }
//    }
}

