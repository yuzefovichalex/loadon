package com.alexyuzefovich.loadon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Loadon extends View {

    enum State {
        NORMAL,
        COLLAPSING,
        LOADING,
        EXTENDING
    }

    private static final long SIZE_ANIMATION_DURATION = 500L;

    private static final float DEFAULT_TEXT_SIZE = 15f;
    private static final int DEFAULT_TEXT_COLOR = Color.BLACK;

    private TextPaint textPaint = new TextPaint();

    private String text = "";
    private float textSize = DEFAULT_TEXT_SIZE;
    private int textColor = DEFAULT_TEXT_COLOR;

    private StaticLayout staticLayout;

    private State state = State.NORMAL;

    private int normalWidth;
    private int normalHeight;

    private int x;

    @NonNull
    private ValueAnimator sizeAnimator = new ValueAnimator();

    @NonNull
    private ProgressIndicator progressIndicator = new DefaultProgressIndicator();

    @NonNull
    private AnimatorSet stateAnimator = new AnimatorSet();


    public Loadon(Context context) {
        super(context, null);
    }

    public Loadon(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray ta = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.Loadon,
                    0, 0);
            initTextDrawing(ta);
            ta.recycle();
        }
        initStateAnimator();
    }

    public Loadon(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Loadon(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private void initTextDrawing(@NonNull TypedArray ta) {
        text = ta.getString(R.styleable.Loadon_text);
        textSize = ta.getDimension(R.styleable.Loadon_textSize, DEFAULT_TEXT_SIZE);
        textColor = ta.getColor(R.styleable.Loadon_textColor, DEFAULT_TEXT_COLOR);

        textPaint.setTextSize(textSize);
        textPaint.setColor(textColor);
        textPaint.setAntiAlias(true);

        makeLayout();
        normalWidth = staticLayout.getWidth();
        normalHeight = staticLayout.getHeight();
    }

    private void initStateAnimator() {
        initSizeAnimator();
        initProgressIndicator();
        stateAnimator.playSequentially(
                sizeAnimator,
                progressIndicator.getAnimator()
        );
    }

    private void initSizeAnimator() {
        sizeAnimator.addUpdateListener(animation -> {
            x = (int) animation.getAnimatedValue();
            invalidate();
            requestLayout();
        });
        sizeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                switch (state) {
                    case COLLAPSING:
                    case LOADING: {
                        state = State.EXTENDING;
                        break;
                    }
                    case EXTENDING:
                    case NORMAL: {
                        state = State.COLLAPSING;
                        break;
                    }
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                switch (state) {
                    case COLLAPSING: {
                        state = State.LOADING;
                        break;
                    }
                    case EXTENDING: {
                        state = State.NORMAL;
                        break;
                    }
                }
            }
        });
        sizeAnimator.setDuration(SIZE_ANIMATION_DURATION);
    }

    private void initProgressIndicator() {
        progressIndicator.setAnimatedValueUpdatedListener(this::invalidate);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = state == State.NORMAL ? normalWidth : x;
        if (state != State.NORMAL) {
            final float sizeMultiplier = ((float) x - normalHeight) / (normalWidth - normalHeight);
            final int multipliedAlpha = (int) (sizeMultiplier * 255);
            final float multipliedTextSize = sizeMultiplier * this.textSize;
            textPaint.setAlpha(multipliedAlpha);
            textPaint.setTextSize(multipliedTextSize);
        } else {
            textPaint.setTextSize(textSize);
            textPaint.setAlpha(255);
        }
        makeLayout();
        setMeasuredDimension(width, normalHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state != State.LOADING) {
            canvas.save();
            final float translationX = (getWidth() - staticLayout.getWidth()) / 2f;
            final float translationY = (normalHeight - staticLayout.getHeight()) / 2f;
            canvas.translate(translationX, translationY);
            staticLayout.draw(canvas);
            canvas.restore();
        } else {
            progressIndicator.draw(this, canvas);
        }
    }

    private void makeLayout() {
        final int textWidth = (int) textPaint.measureText(text);
        staticLayout = new StaticLayout(
                text,
                textPaint,
                textWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1f, 0, false);
    }

    public void startLoading() {
        if (state == State.LOADING) {
            return;
        }
        stateAnimator.cancel();
        sizeAnimator.setIntValues(getWidth(), normalHeight);
        stateAnimator.start();
    }

    public void stopLoading() {
        if (state == State.NORMAL) {
            return;
        }
        stateAnimator.cancel();
        sizeAnimator.setIntValues(getWidth(), normalWidth);
        sizeAnimator.start();
    }


    public abstract static class ProgressIndicator {

        interface AnimatedValueUpdatedListener {
            void onAnimatedValueUpdated();
        }


        private int currentAnimatedValue = 0;

        @NonNull
        private ValueAnimator progressIndicatorAnimator = new ValueAnimator();

        @Nullable
        private AnimatedValueUpdatedListener animatedValueUpdatedListener;


        public ProgressIndicator() {
            initProgressIndicatorAnimator();
        }


        public int getCurrentAnimatedValue() {
            return currentAnimatedValue;
        }

        @NonNull
        ValueAnimator getAnimator() {
            return progressIndicatorAnimator;
        }


        public abstract int getRepeatCount();
        public abstract int getRepeatMode();
        public abstract long getDuration();
        @NonNull
        public abstract TimeInterpolator getInterpolator();


        public void setAnimatedValueUpdatedListener(@Nullable AnimatedValueUpdatedListener animatedValueUpdatedListener) {
            this.animatedValueUpdatedListener = animatedValueUpdatedListener;
        }


        private void initProgressIndicatorAnimator() {
            progressIndicatorAnimator.setIntValues(0, 100);
            progressIndicatorAnimator.setRepeatCount(getRepeatCount());
            progressIndicatorAnimator.setRepeatMode(getRepeatMode());
            progressIndicatorAnimator.setInterpolator(getInterpolator());
            progressIndicatorAnimator.setDuration(getDuration());
            progressIndicatorAnimator.addUpdateListener(animation -> {
                currentAnimatedValue = (int) animation.getAnimatedValue();
                if (animatedValueUpdatedListener != null) {
                    animatedValueUpdatedListener.onAnimatedValueUpdated();
                }
            });
        }

        public abstract void draw(Loadon loadon, Canvas canvas);

    }

    public static class DefaultProgressIndicator extends ProgressIndicator {

        @NonNull
        private Paint paint = new Paint();

        public DefaultProgressIndicator() {
            super();
            initPaint();
        }

        private void initPaint() {
            paint.setColor(Color.BLACK);
            paint.setAntiAlias(true);
        }


        @Override
        public int getRepeatCount() {
            return ValueAnimator.INFINITE;
        }

        @Override
        public int getRepeatMode() {
            return ValueAnimator.REVERSE;
        }

        @Override
        public long getDuration() {
            return 500L;
        }

        @NonNull
        @Override
        public TimeInterpolator getInterpolator() {
            return new LinearInterpolator();
        }


        @Override
        public void draw(Loadon loadon, Canvas canvas) {
            canvas.drawCircle(
                    loadon.getWidth() / 2f,
                    loadon.getHeight() / 2f,
                    loadon.getWidth() / 2f * (getCurrentAnimatedValue() / 100f),
                    paint
            );
        }

    }

}