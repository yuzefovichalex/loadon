package com.alexyuzefovich.loadon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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

import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.Shapeable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Loadon extends View implements Shapeable {

    enum State {
        NORMAL,
        COLLAPSING,
        LOADING,
        EXTENDING,
        SUCCEED,
        FAILED;

        boolean isIndicationState() {
            return this == LOADING || this == SUCCEED || this == FAILED;
        }
    }

    private static final long SIZE_ANIMATION_DURATION = 500L;

    private static final float DEFAULT_TEXT_SIZE = 15f;
    private static final int DEFAULT_TEXT_COLOR = Color.BLACK;

    private static final Class<?>[] PROGRESS_INDICATOR_CONSTRUCTOR_SIGNATURE =
            new Class<?>[]{Context.class, AttributeSet.class, int.class, int.class};
    
    private String text = "";
    private float textSize = DEFAULT_TEXT_SIZE;
    private int textColor = DEFAULT_TEXT_COLOR;

    private final TextPaint textPaint = new TextPaint();

    private StaticLayout textLayout;

    @NonNull
    private State state = State.NORMAL;

    private int textWidth;
    private int textHeight;

    private int currentAnimatedWidth;

    @NonNull
    private final ValueAnimator sizeAnimator = new ValueAnimator();

    private ProgressIndicator progressIndicator;

    @NonNull
    private final AnimatorSet stateAnimator = new AnimatorSet();

    @NonNull
    private final LoadonBackgroundHelper loadonBackgroundHelper;


    public Loadon(@NonNull Context context) {
        this(context, null);
    }

    public Loadon(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.loadonStyle);
    }

    public Loadon(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Loadon);
    }

    public Loadon(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes
    ) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initFromAttrs(context, attrs, defStyleAttr, defStyleRes);
        initProgressIndicator();
        initStateAnimator();

        loadonBackgroundHelper = new LoadonBackgroundHelper(this, attrs, defStyleAttr, defStyleRes);
    }

    private void initFromAttrs(
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

        text = ta.getString(R.styleable.Loadon_text);
        textSize = ta.getDimension(R.styleable.Loadon_textSize, DEFAULT_TEXT_SIZE);
        textColor = ta.getColor(R.styleable.Loadon_textColor, DEFAULT_TEXT_COLOR);

        textPaint.setTextSize(textSize);
        textPaint.setColor(textColor);
        textPaint.setAntiAlias(true);

        // Initially we set textWidth as full text width in one line (without line breaks).
        // In onMeasure we'll get final view desired (or max) width, so we can set correct width.
        textWidth = (int) textPaint.measureText(text);

        final String progressIndicatorClassName = ta.getString(R.styleable.Loadon_progressIndicator);
        createProgressIndicator(context, progressIndicatorClassName, attrs, defStyleAttr, defStyleRes);

        ta.recycle();
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
        if (progressIndicator == null) {
            progressIndicator = new DefaultProgressIndicator(getContext());
        }
        progressIndicator.setDrawingListener(this::invalidate);
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

    @Override
    public void setBackground(Drawable background) {
        super.setBackground(background);
    }

    void setBackgroundInternal(Drawable background) {
        super.setBackground(background);
    }

    @Override
    public void setBackgroundColor(int color) {
        if (loadonBackgroundHelper.isUserBackgroundSet()) {
            loadonBackgroundHelper.setBackgroundColor(color);
        } else {
            super.setBackgroundColor(color);
        }
    }

    @Override
    public void setBackgroundTintList(@Nullable ColorStateList tint) {
        if (loadonBackgroundHelper.isUserBackgroundSet()) {
            loadonBackgroundHelper.setBackgroundTint(tint);
        } else {
            super.setBackgroundTintList(tint);
        }
    }

    @Override
    public void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (loadonBackgroundHelper.isUserBackgroundSet()) {
            loadonBackgroundHelper.setBackgroundTintMode(tintMode);
        } else {
            super.setBackgroundTintMode(tintMode);
        }
    }

    @NonNull
    @Override
    public ShapeAppearanceModel getShapeAppearanceModel() {
        return loadonBackgroundHelper.getShapeAppearanceModel();
    }

    @Override
    public void setShapeAppearanceModel(@NonNull ShapeAppearanceModel shapeAppearanceModel) {
        loadonBackgroundHelper.setShapeAppearanceModel(shapeAppearanceModel);
    }


    private void applyTextChanges(boolean reMeasure) {
        makeLayout(getWidth());
        invalidate();
        if (reMeasure) {
            requestLayout();
        }
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
        final int desiredWidth;
        final int desiredHeight;

        final int finalWidth;
        final int finalHeight;

        if (textLayout == null) {
            // First measure: make text layout and update textWidth based on possible view width
            desiredWidth = getExpandedWidth();
            finalWidth = resolveSize(desiredWidth, widthMeasureSpec);

            int targetTextWidth = excludeHorizontalPadding(Math.min(desiredWidth, finalWidth));
            makeLayout(targetTextWidth);

            desiredHeight = textHeight + getPaddingTop() + getPaddingBottom();
            finalHeight = resolveSize(desiredHeight, heightMeasureSpec);
        } else {
            // Second and others measure: simple handle normal or on animation re-measure
            desiredWidth = state == State.NORMAL ? getExpandedWidth() : currentAnimatedWidth;
            desiredHeight = textHeight + getPaddingTop() + getPaddingBottom();

            if (state != State.NORMAL) {
                final float sizeMultiplier = ((float) currentAnimatedWidth - getCollapsedWidth()) / (getExpandedWidth() - getCollapsedWidth());
                final int multipliedAlpha = (int) (sizeMultiplier * 255);
                final float multipliedTextSize = sizeMultiplier * textSize;
                textPaint.setAlpha(multipliedAlpha);
                textPaint.setTextSize(multipliedTextSize);

                finalWidth = desiredWidth;
                finalHeight = desiredHeight;
            } else {
                textPaint.setTextSize(textSize);
                textPaint.setAlpha(255);

                finalWidth = resolveSize(desiredWidth, widthMeasureSpec);
                finalHeight = resolveSize(desiredHeight, heightMeasureSpec);
            }

            final int targetTextWidth = excludeHorizontalPadding(finalWidth);
            makeLayout(targetTextWidth);
        }

        setMeasuredDimension(finalWidth, finalHeight);
    }

    private int excludeHorizontalPadding(int paddedWidth) {
        return paddedWidth - getPaddingStart() - getPaddingEnd();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!state.isIndicationState()) {
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

    private void makeLayout(int availableWidth) {
        final boolean isFirstMake = textLayout == null;
        textLayout = new StaticLayout(
                text,
                textPaint,
                availableWidth,
                Layout.Alignment.ALIGN_CENTER,
                1f, 0, false);
        // We firstly get availableWidth (view width from onMeasure), so finally set textWidth & textHeight
        if (isFirstMake) {
            textWidth = textLayout.getWidth();
            textHeight = textLayout.getHeight();
        }
    }

    public void startLoading() {
        if (state == State.LOADING) {
            return;
        }
        startStateAnimation(getWidth(), getCollapsedWidth());
    }

    public void stopLoading(boolean isSuccessful) {
        if (state == State.NORMAL) {
            return;
        }
        if (isSuccessful) {
            state = State.SUCCEED;
            progressIndicator.onSuccess();
        } else {
            state = State.FAILED;
            progressIndicator.onFailure();
        }
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


    public interface LoadingFinishListener {
        void onSuccess();
        void onFailure();
    }

    public abstract static class ProgressIndicator implements LoadingFinishListener {

        interface DrawingListener {
            void requestDraw();
        }


        private int progressIndicatorColor;

        private float currentAnimatedValue = 0f;

        @NonNull
        private final ValueAnimator progressIndicatorAnimator = new ValueAnimator();

        @Nullable
        private DrawingListener drawingListener;


        public ProgressIndicator(@NonNull Context context) {
            this(context, null, R.attr.loadonStyle, R.style.Loadon);
        }

        public ProgressIndicator(
                @NonNull Context context,
                @Nullable AttributeSet attrs,
                int defStyleAttr,
                int defStyleRes
        ) {
            initFromAttrs(context, attrs, defStyleAttr, defStyleRes);
            initProgressIndicatorAnimator();
        }

        private void initFromAttrs(
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


        public void setDrawingListener(@Nullable DrawingListener drawingListener) {
            this.drawingListener = drawingListener;
        }


        private void initProgressIndicatorAnimator() {
            progressIndicatorAnimator.setFloatValues(getValues());
            progressIndicatorAnimator.setRepeatCount(getRepeatCount());
            progressIndicatorAnimator.setRepeatMode(getRepeatMode());
            progressIndicatorAnimator.setInterpolator(getInterpolator());
            progressIndicatorAnimator.setDuration(getDuration());
            progressIndicatorAnimator.addUpdateListener(animation -> {
                currentAnimatedValue = (float) animation.getAnimatedValue();
                requestDraw();
            });
        }

        protected void requestDraw() {
            if (drawingListener != null) {
                drawingListener.requestDraw();
            }
        }

        void startAnimatorWithTime(long currentPlayTime) {
            progressIndicatorAnimator.start();
            progressIndicatorAnimator.setCurrentPlayTime(currentPlayTime);
        }

        public abstract void draw(@NonNull Loadon loadon, @NonNull Canvas canvas);

    }

    public static class DefaultProgressIndicator extends ProgressIndicator {

        private static final float STROKE_SIZE = 10f;

        private static final float MIN_ARC_ANGLE = 30f;
        private static final float FULL_ARC_ANGLE = 270f;

        private static final float START_ANIMATION_VALUE = 0f;
        private static final float END_ANIMATION_VALUE = 8f;

        private static final long ANIMATION_DURATION = 8000L;

        @NonNull
        private final RectF indicatorRect = new RectF();

        @NonNull
        private final Paint paint = new Paint();

        private int successIconAnimatedValue;

        @NonNull
        private final ValueAnimator successIconAnimator = new ValueAnimator();

        @NonNull
        private final ValueAnimator failureIconAnimator = new ValueAnimator();


        public DefaultProgressIndicator(@NonNull Context context) {
            this(context, null, R.attr.loadonStyle, R.style.Loadon);
        }

        public DefaultProgressIndicator(
                @NonNull Context context,
                @Nullable AttributeSet attrs,
                int defStyleAttr,
                int defStyleRes
        ) {
            super(context, attrs, defStyleAttr, defStyleRes);
            initPaint();
            initSuccessIconAnimator();
            initFailureIconAnimator();
        }

        private void initPaint() {
            paint.setColor(getProgressIndicatorColor());
            paint.setAntiAlias(true);
            paint.setStrokeWidth(STROKE_SIZE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        private void initSuccessIconAnimator() {
            successIconAnimator.setIntValues(0, 100);
            successIconAnimator.setDuration(5000);
            successIconAnimator.addUpdateListener(animation -> {
                successIconAnimatedValue = (int) animation.getAnimatedValue();
                requestDraw();
            });
        }

        private void initFailureIconAnimator() {

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
        public void onSuccess() {
            successIconAnimator.start();
        }

        @Override
        public void onFailure() {
            failureIconAnimator.start();
        }

        @Override
        public void draw(@NonNull Loadon loadon, @NonNull Canvas canvas) {
            switch (loadon.state) {
                case LOADING: {
                    drawIndicator(loadon, canvas);
                    return;
                }
                case SUCCEED: {
                    drawSuccessIcon(loadon, canvas);
                    return;
                }
                case FAILED: {
                    drawFailureIcon(loadon, canvas);
                    return;
                }
                default: { }
            }
        }

        private void drawIndicator(@NonNull Loadon loadon, @NonNull Canvas canvas) {
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

        private void drawSuccessIcon(@NonNull Loadon loadon, @NonNull Canvas canvas) {
            loadon.getDrawingRect(indicatorRect);
            float multiplier = successIconAnimatedValue / 100f;
            canvas.drawCircle(indicatorRect.centerX(), indicatorRect.centerY(), loadon.getWidth() / 2f * multiplier, paint);
        }

        private void drawFailureIcon(@NonNull Loadon loadon, @NonNull Canvas canvas) {

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