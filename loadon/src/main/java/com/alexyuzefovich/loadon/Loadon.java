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
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.customview.view.AbsSavedState;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

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

    private static final Class<?>[] PROGRESS_INDICATOR_CONSTRUCTOR_SIGNATURE =
            new Class<?>[]{Context.class, AttributeSet.class, int.class, int.class};
    
    private String text = "";
    private float textSize = DEFAULT_TEXT_SIZE;
    private int textColor = DEFAULT_TEXT_COLOR;

    private TextPaint textPaint = new TextPaint();

    private StaticLayout textLayout;

    @NonNull
    private State state = State.NORMAL;

    private int textWidth;
    private int textHeight;

    private int currentAnimatedWidth;

    @NonNull
    private ValueAnimator sizeAnimator = new ValueAnimator();

    @NonNull
    private ProgressIndicator progressIndicator = new DefaultProgressIndicator();

    @NonNull
    private AnimatorSet stateAnimator = new AnimatorSet();


    public Loadon(@NonNull Context context) {
        this(context, null);
    }

    public Loadon(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Loadon(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Loadon(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes
    ) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null) {
            TypedArray ta = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.Loadon,
                    0, 0);
            initTextDrawing(ta);
            final String progressIndicatorClassName = ta.getString(R.styleable.Loadon_progressIndicator);
            createProgressIndicator(context, progressIndicatorClassName, attrs, defStyleAttr, defStyleRes);
            ta.recycle();
        }
        initProgressIndicator();
        initStateAnimator();
    }


    //TODO global fields textWidth textHeight not updating. Fix it
    public void setText(String text) {
        this.text = text;
        applyTextChanges(true);
    }

    public void setTextSize(float textSize) {
        this.textSize = textSize;
        applyTextChanges(false);
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
        applyTextChanges(false);
    }

    public void setProgressIndicator(@NonNull ProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;
        invalidate();
    }


    private void applyTextChanges(boolean reMeasure) {
        makeLayout();
        invalidate();
        if (reMeasure) {
            requestLayout();
        }
    }

    private void initTextDrawing(@NonNull TypedArray ta) {
        text = ta.getString(R.styleable.Loadon_text);
        textSize = ta.getDimension(R.styleable.Loadon_textSize, DEFAULT_TEXT_SIZE);
        textColor = ta.getColor(R.styleable.Loadon_textColor, DEFAULT_TEXT_COLOR);

        textPaint.setTextSize(textSize);
        textPaint.setColor(textColor);
        textPaint.setAntiAlias(true);

        makeLayout();
        textWidth = textLayout.getWidth();
        textHeight = textLayout.getHeight();
    }

    private void initStateAnimator() {
        initSizeAnimator();
        stateAnimator.playSequentially(
                sizeAnimator,
                progressIndicator.getAnimator()
        );
    }

    private void initSizeAnimator() {
        sizeAnimator.addUpdateListener(animation -> {
            currentAnimatedWidth = (int) animation.getAnimatedValue();
            invalidate();
            requestLayout();
        });
        sizeAnimator.addListener(new AnimatorListenerAdapter() {
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

    private void createProgressIndicator(
            @NonNull Context context,
            @Nullable String className,
            @NonNull AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes
    ) {
        if (className != null) {
            className = getFullClassName(context, className.trim());
            if (!className.isEmpty()) {
                try {
                    ClassLoader classLoader;
                    if (isInEditMode()) {
                        // Stupid layoutlib cannot handle simple class loaders.
                        classLoader = this.getClass().getClassLoader();
                    } else {
                        classLoader = context.getClassLoader();
                    }
                    Class<? extends Loadon.ProgressIndicator> progressIndicatorClass =
                            Class.forName(className, false, classLoader)
                                    .asSubclass(Loadon.ProgressIndicator.class);
                    Constructor<? extends Loadon.ProgressIndicator> constructor;
                    Object[] constructorArgs = null;
                    try {
                        constructor = progressIndicatorClass
                                .getConstructor(PROGRESS_INDICATOR_CONSTRUCTOR_SIGNATURE);
                        constructorArgs = new Object[]{context, attrs, defStyleAttr, defStyleRes};
                    } catch (NoSuchMethodException e) {
                        try {
                            constructor = progressIndicatorClass.getConstructor();
                        } catch (NoSuchMethodException e1) {
                            e1.initCause(e);
                            throw new IllegalStateException(attrs.getPositionDescription()
                                    + ": Error creating ProgressIndicator " + className, e1);
                        }
                    }
                    constructor.setAccessible(true);
                    progressIndicator = constructor.newInstance(constructorArgs);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Unable to find ProgressIndicator " + className, e);
                } catch (InvocationTargetException | InstantiationException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Could not instantiate the ProgressIndicator: " + className, e);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Cannot access non-public constructor " + className, e);
                } catch (ClassCastException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Class is not a ProgressIndicator " + className, e);
                }
            }
        }
    }

    @NonNull
    private String getFullClassName(
            @NonNull Context context,
            @NonNull String className
    ) {
        if (className.charAt(0) == '.') {
            return context.getPackageName() + className;
        }
        if (className.contains(".")) {
            return className;
        }
        final Package loadonPackage = Loadon.class.getPackage();
        return loadonPackage != null ? loadonPackage.getName() + '.' + className : "";
    }

    private int getExpandedWidth() {
        return textWidth + getPaddingLeft() + getPaddingRight();
    }

    private int getCollapsedWidth() {
        return textHeight + getPaddingLeft() + getPaddingRight();
    }

    private void getDrawingRect(@NonNull RectF rectF) {
        final float left = getPaddingStart();
        final float top = getPaddingTop();
        final float right = getWidth() - getPaddingEnd();
        final float bottom = getHeight() - getPaddingBottom();
        rectF.set(left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = state == State.NORMAL ? getExpandedWidth() : currentAnimatedWidth;
        int height = textHeight + getPaddingTop() + getPaddingBottom();
        if (state != State.NORMAL) {
            final float sizeMultiplier = ((float) currentAnimatedWidth - getCollapsedWidth()) / (getExpandedWidth() - getCollapsedWidth());
            final int multipliedAlpha = (int) (sizeMultiplier * 255);
            final float multipliedTextSize = sizeMultiplier * textSize;
            textPaint.setAlpha(multipliedAlpha);
            textPaint.setTextSize(multipliedTextSize);
        } else {
            textPaint.setTextSize(textSize);
            textPaint.setAlpha(255);
        }
        makeLayout();
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state != State.LOADING) {
            canvas.save();
            final float translationX = (getWidth() - textLayout.getWidth()) / 2f;
            final float translationY = (getHeight() - textLayout.getHeight()) / 2f;
            canvas.translate(translationX, translationY);
            textLayout.draw(canvas);
            canvas.restore();
        } else {
            progressIndicator.draw(this, canvas);
        }
    }

    private void makeLayout() {
        final int measuredTextWidth = (int) textPaint.measureText(text);
        textLayout = new StaticLayout(
                text,
                textPaint,
                measuredTextWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1f, 0, false);
    }

    public void startLoading() {
        if (state == State.LOADING) {
            return;
        }
        startStateAnimation(getWidth(), getCollapsedWidth());
    }

    public void stopLoading() {
        if (state == State.NORMAL) {
            return;
        }
        startStateAnimation(getWidth(), getExpandedWidth());
    }

    private void startStateAnimation(int sizeStartValue, int sizeEndValue) {
        startStateAnimation(sizeStartValue, sizeEndValue, 0);
    }

    private void startStateAnimation(int sizeStartValue, int sizeEndValue, long currentPlayTime) {
        stateAnimator.cancel();
        sizeAnimator.setIntValues(sizeStartValue, sizeEndValue);
        stateAnimator.start();
        sizeAnimator.setCurrentPlayTime(currentPlayTime);
        state = sizeStartValue > sizeEndValue ? State.COLLAPSING : State.EXTENDING;
    }

    @NonNull
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.state = state;
        savedState.x = currentAnimatedWidth;
        savedState.sizeAnimationPlayTime = sizeAnimator.getCurrentPlayTime();
        savedState.indicatorAnimationPlayTime = progressIndicator.getAnimatorCurrentPlayTime();
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(@Nullable Parcelable parcelableState) {
        if (!(parcelableState instanceof SavedState)) {
            super.onRestoreInstanceState(parcelableState);
            return;
        }
        SavedState savedState = (SavedState) parcelableState;
        super.onRestoreInstanceState(savedState.getSuperState());
        state = savedState.state;
        currentAnimatedWidth = savedState.x;
        final long sizeAnimationPlayTime = savedState.sizeAnimationPlayTime;
        final long indicatorAnimationPlayTime = savedState.indicatorAnimationPlayTime;
        switch (state) {
            case EXTENDING: {
                startStateAnimation(currentAnimatedWidth, getExpandedWidth(), sizeAnimationPlayTime);
                return;
            }
            case LOADING: {
                progressIndicator.startAnimatorWithTime(indicatorAnimationPlayTime);
                return;
            }
            case COLLAPSING: {
                startStateAnimation(currentAnimatedWidth, getCollapsedWidth(), sizeAnimationPlayTime);
            }
        }
    }


    public abstract static class ProgressIndicator {

        interface AnimatedValueUpdatedListener {
            void onAnimatedValueUpdated();
        }


        private int progressIndicatorColor;

        private float currentAnimatedValue = 0f;

        @NonNull
        private ValueAnimator progressIndicatorAnimator = new ValueAnimator();

        @Nullable
        private AnimatedValueUpdatedListener animatedValueUpdatedListener;


        public ProgressIndicator() {
            initProgressIndicatorAnimator();
        }

        public ProgressIndicator(
                @NonNull Context context,
                @Nullable AttributeSet attrs,
                int defStyleAttr,
                int defStyleRes
        ) {
            TypedArray ta = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.Loadon,
                    defStyleAttr,
                    defStyleRes
            );
            final int textColor = ta.getColor(R.styleable.Loadon_textColor, DEFAULT_TEXT_COLOR);
            progressIndicatorColor = ta.getColor(R.styleable.Loadon_progressIndicatorColor, textColor);
            ta.recycle();
            initProgressIndicatorAnimator();
        }


        public int getProgressIndicatorColor() {
            return progressIndicatorColor;
        }

        public float getCurrentAnimatedValue() {
            return currentAnimatedValue;
        }

        @NonNull
        ValueAnimator getAnimator() {
            return progressIndicatorAnimator;
        }

        long getAnimatorCurrentPlayTime() {
            return progressIndicatorAnimator.getCurrentPlayTime();
        }


        public abstract float[] getValues();
        public abstract int getRepeatCount();
        public abstract int getRepeatMode();
        public abstract long getDuration();
        @NonNull
        public abstract TimeInterpolator getInterpolator();


        public void setAnimatedValueUpdatedListener(@Nullable AnimatedValueUpdatedListener animatedValueUpdatedListener) {
            this.animatedValueUpdatedListener = animatedValueUpdatedListener;
        }


        private void initProgressIndicatorAnimator() {
            progressIndicatorAnimator.setFloatValues(getValues());
            progressIndicatorAnimator.setRepeatCount(getRepeatCount());
            progressIndicatorAnimator.setRepeatMode(getRepeatMode());
            progressIndicatorAnimator.setInterpolator(getInterpolator());
            progressIndicatorAnimator.setDuration(getDuration());
            progressIndicatorAnimator.addUpdateListener(animation -> {
                currentAnimatedValue = (float) animation.getAnimatedValue();
                if (animatedValueUpdatedListener != null) {
                    animatedValueUpdatedListener.onAnimatedValueUpdated();
                }
            });
        }

        void startAnimatorWithTime(long currentPlayTime) {
            progressIndicatorAnimator.start();
            progressIndicatorAnimator.setCurrentPlayTime(currentPlayTime);
        }

        public abstract void draw(Loadon loadon, Canvas canvas);

    }

    public static class DefaultProgressIndicator extends ProgressIndicator {

        private static final float STROKE_SIZE = 10f;

        private static final float MIN_ARC_ANGLE = 30f;
        private static final float FULL_ARC_ANGLE = 270f;

        private static final float START_ANIMATION_VALUE = 0f;
        private static final float END_ANIMATION_VALUE = 8f;

        private static final long ANIMATION_DURATION = 8000L;

        @NonNull
        private RectF indicatorRect = new RectF();

        @NonNull
        private Paint paint = new Paint();

        public DefaultProgressIndicator() {
            super();
            initPaint();
        }

        public DefaultProgressIndicator(
                @NonNull Context context,
                @Nullable AttributeSet attrs,
                int defStyleAttr,
                int defStyleRes
        ) {
            super(context, attrs, defStyleAttr, defStyleRes);
            initPaint();
        }

        private void initPaint() {
            paint.setColor(getProgressIndicatorColor());
            paint.setAntiAlias(true);
            paint.setStrokeWidth(STROKE_SIZE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }


        @Override
        public float[] getValues() {
            return new float[] { START_ANIMATION_VALUE, END_ANIMATION_VALUE };
        }

        @Override
        public int getRepeatCount() {
            return ValueAnimator.INFINITE;
        }

        @Override
        public int getRepeatMode() {
            return ValueAnimator.RESTART;
        }

        @Override
        public long getDuration() {
            return ANIMATION_DURATION;
        }

        @NonNull
        @Override
        public TimeInterpolator getInterpolator() {
            return new LinearInterpolator();
        }


        @Override
        public void draw(Loadon loadon, Canvas canvas) {
            loadon.getDrawingRect(indicatorRect);
            indicatorRect.inset(STROKE_SIZE, STROKE_SIZE);

            final int iteration = (int) getCurrentAnimatedValue() / 2;
            final float animValue = getCurrentAnimatedValue() - iteration * 2f;

            canvas.save();

            final float rotationAngle = -90f * iteration + 360f * animValue;
            canvas.rotate(rotationAngle, indicatorRect.centerX(), indicatorRect.centerY());

            final float startAngle = animValue <= 1f
                    ? 0f
                    : FULL_ARC_ANGLE * (animValue - 1f);
            final float sweepAngle = animValue <= 1f
                    ? FULL_ARC_ANGLE * animValue + MIN_ARC_ANGLE
                    : FULL_ARC_ANGLE * (2f - animValue) + MIN_ARC_ANGLE;
            canvas.drawArc(indicatorRect, startAngle, sweepAngle, false, paint);

            canvas.restore();
        }

    }


    static class SavedState extends AbsSavedState {

        State state;

        int x;

        long sizeAnimationPlayTime;

        long indicatorAnimationPlayTime;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(@NonNull Parcel source, ClassLoader loader) {
            super(source, loader);
            if (loader == null) {
                loader = getClass().getClassLoader();
            }
            readFromParcel(source);
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(state.ordinal());
            out.writeInt(x);
            out.writeLong(sizeAnimationPlayTime);
            out.writeLong(indicatorAnimationPlayTime);
        }

        private void readFromParcel(@NonNull Parcel in) {
            state = State.values()[in.readInt()];
            x = in.readInt();
            sizeAnimationPlayTime = in.readLong();
            indicatorAnimationPlayTime = in.readLong();
        }

        public static final Creator<SavedState> CREATOR =
                new ClassLoaderCreator<SavedState>() {
                    @NonNull
                    @Override
                    public SavedState createFromParcel(@NonNull Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @NonNull
                    @Override
                    public SavedState createFromParcel(@NonNull Parcel in) {
                        return new SavedState(in, null);
                    }

                    @NonNull
                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

}