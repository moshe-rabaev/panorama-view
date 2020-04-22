package pl.rjuszczyk.panorama.viewer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.PointF;

import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import java.util.Timer;
import java.util.TimerTask;

import pl.rjuszczyk.panorama.R;
import pl.rjuszczyk.panorama.multitouch.MoveGestureDetector;
import pl.rjuszczyk.panorama.multitouch.RotateGestureDetector;

import pl.rjuszczyk.panorama.gyroscope.GyroscopeHandler;

public class PanoramaGLSurfaceView extends GLSurfaceView {
    public static float MAX_X_ROT = 88f;
    public static float MIN_X_ROT = -88f;
    private final boolean isGyroscopeEnabled;
    private float initialRotationX = 0;
    private float initialRotationY = 0;
    private float initialRotationZ = 0;

    private float DEFAULT_TOUCH_SCALE = 0.06f;
    private float TOUCH_SCALE = 0.06f;

    MoveGestureDetector mMoveDetector;
    RawImageDrawer mImageDrawer;
    RotateGestureDetector mRotateGestureDetector;
    GestureDetector mFlingDetector;

    GyroscopeHandler gyroscopeHandler;
    GyroscopeHandler gyroscopeHandler2;
    private float mDefaultModelScale = 1f;
    private PanoramaRenderer mPanoramaRenderer;
    private float mScaleFactor = 1;
    int beginEvents = 0;

    private ScaleGestureDetector mScaleGestureDetector;
    private boolean isGyroAvailable;
    private int currentGyroHandler = 1;
    private float[] currentRotationMatrix;
    private float[] currentRotationMatrix2;
    private float currentProgress = 0;
    private int targetProgress;
    boolean autoCorrection = false;
    private Timer timer;

    public PanoramaGLSurfaceView(Context context) {
        super(context);
        isGyroscopeEnabled = true;
        if (isInEditMode())
            return;

        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        //mGestureDetector = new GestureDetector(context, new MyOnGestureListener());
        mMoveDetector = new MoveGestureDetector(context, new MoveListener());
        mRotateGestureDetector = new RotateGestureDetector(context, new MyRotateGestureDetector());
        mScaleFactor = mDefaultModelScale;

        mImageDrawer = new RawImageDrawer(getResources());


        mPanoramaRenderer = new PanoramaRenderer(context, mImageDrawer, R.raw.sphere, 0, 0, 0);
        mPanoramaRenderer.setModelScale(mDefaultModelScale);

        setEGLContextClientVersion(2);
        super.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(mPanoramaRenderer);
    }

    public PanoramaGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (isInEditMode()) {
            isGyroscopeEnabled = false;
            return;
        }


        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PanoramaGLSurfaceView, 0, 0);
        initialRotationX = a.getFloat(R.styleable.PanoramaGLSurfaceView_initialRotationX, 0);
        initialRotationY = a.getFloat(R.styleable.PanoramaGLSurfaceView_initialRotationY, 0);
        initialRotationZ = a.getFloat(R.styleable.PanoramaGLSurfaceView_initialRotationZ, 0);

        int imageResource = a.getResourceId(R.styleable.PanoramaGLSurfaceView_img, -1);
        isGyroscopeEnabled = a.getBoolean(R.styleable.PanoramaGLSurfaceView_gyroscopeEnabled, true);


        a.recycle();

        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        //mGestureDetector = new GestureDetector(context, new MyOnGestureListener());
        mMoveDetector = new MoveGestureDetector(context, new MoveListener());
        mRotateGestureDetector = new RotateGestureDetector(context, new MyRotateGestureDetector());
        mFlingDetector = new GestureDetector(getContext(), new GestureDetector.OnGestureListener() {

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

                mPanoramaRenderer.speedX = -TOUCH_SCALE * velocityX;
                mPanoramaRenderer.speedY = -TOUCH_SCALE * velocityY;
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {

            }

            @Override
            public boolean onDown(MotionEvent motionEvent) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent motionEvent) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {

                return false;
            }

            @Override
            public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                return false;
            }
        });
        mScaleFactor = mDefaultModelScale;

        mImageDrawer = new RawImageDrawer(getResources());

        mPanoramaRenderer = new PanoramaRenderer(context, mImageDrawer, R.raw.sphere, initialRotationX, initialRotationY, initialRotationZ);
        mPanoramaRenderer.setModelScale(mDefaultModelScale);


        setEGLContextClientVersion(2);
        super.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(mPanoramaRenderer);

        if (imageResource != -1) {
            setTexDrawableResourceID(imageResource);
        }
    }


    public boolean onTouchEvent(MotionEvent event) {
        boolean retVal = mRotateGestureDetector.onTouchEvent(event);
        retVal = mScaleGestureDetector.onTouchEvent(event) || retVal;

        retVal = mMoveDetector.onTouchEvent(event) || retVal;
        retVal = mFlingDetector.onTouchEvent(event) || retVal;
        return retVal || super.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {


        super.onDetachedFromWindow();
    }

    public void onPause() {
        if(gyroscopeHandler != null) {
            gyroscopeHandler.stop();
        }

        if(gyroscopeHandler2 != null) {
            gyroscopeHandler2.stop();
        }

        if (autoCorrection) {
            timer.cancel();
        }
    }

    public void onResume() {
        if (isInEditMode())
            return;

        currentGyroHandler = 2;

        isGyroAvailable = true;

        if (isGyroscopeEnabled) {
            gyroscopeHandler = new GyroscopeHandler();
            gyroscopeHandler2 = new GyroscopeHandler();


            if (isGyroscopeEnabled)
                gyroscopeHandler.start(getContext(), new GyroscopeHandler.OnGyroscopeChanged() {


                    @Override
                    public void onGyroscopeChange(double x, double y, double z) {

                    }


                    @Override
                    public void onGyroscopeChanged2(float[] currentRotationMatrix) {
                        if (isGyroAvailable) {
                            PanoramaGLSurfaceView.this.currentRotationMatrix = currentRotationMatrix;
                            if (currentGyroHandler == 2) {
                                currentProgress = lerp(0.01f, currentProgress, targetProgress);
                                if (currentProgress < 0.01 && targetProgress == 0)
                                    currentProgress = 0;
                                if (currentProgress > 0.99 && targetProgress == 1)
                                    currentProgress = 1;
                                MyLog.d("gyro1", "onGyroscopeChanged2: currentProgress = " + currentProgress);
                            } else {
                                if (PanoramaGLSurfaceView.this.currentRotationMatrix == null ||
                                        PanoramaGLSurfaceView.this.currentRotationMatrix2 == null) {
                                    return;
                                }

                                float[] rotationMatrix = getCurrentRotationMatrix(
                                        PanoramaGLSurfaceView.this.currentRotationMatrix,
                                        PanoramaGLSurfaceView.this.currentRotationMatrix2,
                                        currentProgress
                                );
                                setModelRotationMatrix(rotationMatrix);
                            }
                        }
                    }

                    @Override
                    public void onGyroscopeNotAvailable() {
                        isGyroAvailable = false;
                    }
                });


            gyroscopeHandler2.start(getContext(), new GyroscopeHandler.OnGyroscopeChanged() {


                @Override
                public void onGyroscopeChange(double x, double y, double z) {

                }

                @Override
                public void onGyroscopeChanged2(float[] currentRotationMatrix) {
                    if (isGyroAvailable) {
                        PanoramaGLSurfaceView.this.currentRotationMatrix2 = currentRotationMatrix;
                        if (currentGyroHandler == 1) {
                            currentProgress = lerp(0.01f, currentProgress, targetProgress);
                            MyLog.d("gyro2", "onGyroscopeChanged2: currentProgress = " + currentProgress);
                            if (currentProgress < 0.01 && targetProgress == 0) currentProgress = 0;
                            if (currentProgress > 0.99 && targetProgress == 1) currentProgress = 1;

                        } else {
                            if (PanoramaGLSurfaceView.this.currentRotationMatrix == null ||
                                    PanoramaGLSurfaceView.this.currentRotationMatrix2 == null) {
                                return;
                            }

                            float[] rotationMatrix = getCurrentRotationMatrix(
                                    PanoramaGLSurfaceView.this.currentRotationMatrix,
                                    PanoramaGLSurfaceView.this.currentRotationMatrix2,
                                    currentProgress
                            );
                            setModelRotationMatrix(rotationMatrix);
                        }
                    }
                }

                @Override
                public void onGyroscopeNotAvailable() {
                    isGyroAvailable = false;
                }
            });
        }

        if (autoCorrection) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    reset();
                }
            }, 0, 10000);
        }
    }

    private void setModelRotationMatrix(float[] rotationMatrix) {
        mPanoramaRenderer.setModelRotationMatrix(rotationMatrix);
    }

    public void setOnDrawListener(OnDrawListener onDrawListener) {
        mPanoramaRenderer.setOnDrawListener(onDrawListener);
    }


    float[] getCurrentRotationMatrix(
            float[] matrix1,
            float[] matrix2,
            float progress
    ) {
        float[] matrixResult = new float[matrix1.length];
        for (int i = 0; i < matrix1.length; i++) {
            matrixResult[i] = lerp(progress, matrix1[i], matrix2[i]);
        }

        return matrixResult;
    }

    float lerp(float t, float a, float b) {
        return (1 - t) * a + t * b;
    }

    public void reset() {
        if (gyroscopeHandler == null || gyroscopeHandler2 == null) {
            return;
        }

        if (currentGyroHandler == 1) {
            gyroscopeHandler.reset();
            gyroscopeHandler.restart();
            currentGyroHandler = 2;
            targetProgress = 0;
        } else {
            gyroscopeHandler2.reset();
            gyroscopeHandler2.restart();
            currentGyroHandler = 1;
            targetProgress = 1;
        }

//		mPanoramaRenderer.resetCamera();
    }


    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor = mPanoramaRenderer.getModelScale() / detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.30f, Math.min(mScaleFactor, 1.5f));
            TOUCH_SCALE = DEFAULT_TOUCH_SCALE * mScaleFactor;
            //     mPanoramaRenderer.mCameraZ *= mScaleFactor;
            mPanoramaRenderer.setModelScale(mScaleFactor);
            invalidate();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            beginEvents++;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            beginEvents--;
            super.onScaleEnd(detector);

        }
    }

    private class MyRotateGestureDetector implements RotateGestureDetector.OnRotateGestureListener {

        @Override
        public boolean onRotate(RotateGestureDetector detector) {
            mPanoramaRenderer.rotateZ(detector.getRotationDegreesDelta());
            return true;
        }

        @Override
        public boolean onRotateBegin(RotateGestureDetector detector) {
            beginEvents++;
            return true;
        }

        @Override
        public void onRotateEnd(RotateGestureDetector detector) {
            beginEvents--;
        }
    }


    public void setPanoramaResourceId(int tex_resourceID) {
        mPanoramaRenderer.setTex_resourceID(tex_resourceID);
    }

    public void setTexDrawableResourceID(int tex_resourceID) {
        mPanoramaRenderer.setTex_resourceID(tex_resourceID);
    }

    public void setPanoramaBitmap(Bitmap bitmap) {
        mPanoramaRenderer.setTextureBitmap(bitmap);
    }

    public float[] getMVPMatrix() {
        return mPanoramaRenderer.getMVPMatrix();
    }

    public PointF unProject(double latitude, double longitude) {
        float[] posNew = MatrixCalculator.calculateOnScreenPosNormalized(getMVPMatrix(), latitude, longitude);

        int w = getWidth();
        int h = getHeight();
        final int x = (int) (w * (1f + posNew[0]) / 2f);
        int y = (int) (h * (1f + posNew[1]) / 2f);
        y = h - y;

        return new PointF(x, y);
    }

    private class MoveListener implements MoveGestureDetector.OnMoveGestureListener {
        @Override
        public boolean onMove(MoveGestureDetector detector) {
            if (beginEvents != 0) {
                return true;
            }
            float distanceX = detector.getFocusDelta().x;
            float distanceY = detector.getFocusDelta().y;

            mPanoramaRenderer.rotate(-distanceX * TOUCH_SCALE, -distanceY * TOUCH_SCALE);
            return true;
        }

        @Override
        public boolean onMoveBegin(MoveGestureDetector detector) {
            return true;
        }

        @Override
        public void onMoveEnd(MoveGestureDetector detector) {

        }
    }
}
