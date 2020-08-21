package com.alexyuzefovich.loadon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Loadon extends View {

    enum State {
        NORMAL,
        COLLAPSING,
        LOADING,
        EXTENDING
    }

    private static final int BASE_OFFSET = 32;

    private static final long SIZE_ANIMATION_DURATION = 5000L;

    private static final float DEFAULT_TEXT_SIZE = 15f;
    private static final int DEFAULT_TEXT_COLOR = Color.BLACK;

    private TextPaint textPaint = new TextPaint();

    private String text = "";
    private float textSize = DEFAULT_TEXT_SIZE;
    private int textColor = DEFAULT_TEXT_COLOR;

    private StaticLayout staticLayout;

    private State state = State.NORMAL;

    @NonNull
    private ValueAnimator sizeAnimator = new ValueAnimator();

    private int x;

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
        initSizeAnimator();
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

        final int textWidth = (int) textPaint.measureText(text);
        staticLayout = new StaticLayout(
                text,
                textPaint,
                textWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1f, 0, false);
    }

    private void initSizeAnimator() {
        sizeAnimator.addUpdateListener(animation -> {
            x = (int) animation.getAnimatedValue();
            requestLayout();
            invalidate();
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = state == State.NORMAL ? staticLayout.getWidth() + BASE_OFFSET * 2 : x;
        int height = staticLayout.getHeight() + BASE_OFFSET * 2;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();

        if (state != State.NORMAL) {
            float m = ((float) x - getHeight()) / (staticLayout.getWidth() + 64 - getHeight());
            int alpha = (int) (m * 255);
            float textSize = m * this.textSize;
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(textSize);
        } else {
            textPaint.setTextSize(textSize);
            textPaint.setAlpha(255);
        }
        canvas.translate(32, 32);


        staticLayout.draw(canvas);
        canvas.restore();
    }

    public void startLoading() {
        if (state == State.LOADING) {
            return;
        }
        sizeAnimator.cancel();
        sizeAnimator.setIntValues(getWidth(), getHeight());
        sizeAnimator.start();
    }

    public void stopLoading() {
        if (state == State.NORMAL) {
            return;
        }
        sizeAnimator.cancel();
        sizeAnimator.setIntValues(getWidth(), staticLayout.getWidth() + 64);
        sizeAnimator.start();
    }

}