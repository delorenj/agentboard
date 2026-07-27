package com.zellij.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import android.os.Looper;

public class GesturePadView extends View {

    public interface GestureListener {
        void onSwipeUp();
        void onSwipeDown();
        void onSwipeLeft();
        void onSwipeRight();
        void onTap();
        void onLongPress();
    }

    private GestureListener listener;
    private GestureDetector gestureDetector;
    private String label = "";
    private String subLabel = "";
    private Paint bgPaint;
    private Paint textPaint;
    private Paint subTextPaint;
    private Paint flashPaint;
    private boolean flashActive = false;
    private final Handler flashHandler = new Handler(Looper.getMainLooper());

    private static final int SWIPE_THRESHOLD = 60;
    private static final int SWIPE_VELOCITY_THRESHOLD = 60;

    public GesturePadView(Context context) {
        super(context);
        init();
    }

    public GesturePadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GesturePadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF2A2A2A);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setColor(0xFFAAAAAA);
        subTextPaint.setTextSize(18f);
        subTextPaint.setTextAlign(Paint.Align.CENTER);

        flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flashPaint.setColor(0x334CAF50);

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                triggerFlash();
                if (listener != null) listener.onTap();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                triggerFlash();
                if (listener != null) listener.onLongPress();
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (listener == null || e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        triggerFlash();
                        if (diffX > 0) {
                            listener.onSwipeRight();
                        } else {
                            listener.onSwipeLeft();
                        }
                        return true;
                    }
                } else {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        triggerFlash();
                        if (diffY > 0) {
                            listener.onSwipeDown();
                        } else {
                            listener.onSwipeUp();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    public void setSubLabel(String subLabel) {
        this.subLabel = subLabel;
        invalidate();
    }

    public void setOnGestureListener(GestureListener listener) {
        this.listener = listener;
    }

    private void triggerFlash() {
        flashActive = true;
        invalidate();
        flashHandler.postDelayed(() -> {
            flashActive = false;
            invalidate();
        }, 120);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cornerRadius = 12f;
        RectF rect = new RectF(4, 4, getWidth() - 4, getHeight() - 4);

        // Background
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

        // Flash overlay
        if (flashActive) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, flashPaint);
        }

        // Label
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        canvas.drawText(label, centerX, centerY, textPaint);

        // Sub-label (legend)
        if (!subLabel.isEmpty()) {
            canvas.drawText(subLabel, centerX, centerY + 28, subTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }
}
