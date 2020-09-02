package com.alexyuzefovich.loadon;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

class LoadonBackgroundHelper {

    @NonNull
    private Loadon loadon;

    @NonNull
    private ShapeAppearanceModel shapeAppearanceModel;

    @Nullable
    private RippleDrawable rippleDrawable;

    @Nullable
    private ColorStateList rippleColor;

    @Nullable
    private ColorStateList backgroundTint;

    @Nullable
    private PorterDuff.Mode backgroundTintMode;

    private boolean isUserBackgroundSet;


    public LoadonBackgroundHelper(
            @NonNull Loadon loadon,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes
    ) {
        this.loadon = loadon;

        final Context context = loadon.getContext();
        shapeAppearanceModel = ShapeAppearanceModel
                .builder(context, attrs, defStyleAttr, defStyleRes)
                .build();

        TypedArray ta = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.Loadon,
                defStyleAttr,
                defStyleRes
        );

        rippleColor = ta.getColorStateList(R.styleable.Loadon_rippleColor);

        backgroundTint = ta.getColorStateList(R.styleable.Loadon_android_backgroundTint);

        backgroundTintMode = parseTintMode(
                ta.getInt(R.styleable.Loadon_android_backgroundTintMode, -1),
                PorterDuff.Mode.SRC_IN
        );

        isUserBackgroundSet = ta.hasValue(R.styleable.Loadon_android_background);
        if (!isUserBackgroundSet) {
            loadon.setBackgroundInternal(createBackground());
        }

        ta.recycle();
    }


    public boolean isUserBackgroundSet() {
        return isUserBackgroundSet;
    }

    @NonNull
    public ShapeAppearanceModel getShapeAppearanceModel() {
        return shapeAppearanceModel;
    }

    public void setShapeAppearanceModel(@NonNull ShapeAppearanceModel shapeAppearanceModel) {
        this.shapeAppearanceModel = shapeAppearanceModel;
        updateShape(shapeAppearanceModel);
    }

    public void setBackgroundColor(int color) {
        if (getBackgroundDrawable() != null) {
            getBackgroundDrawable().setTint(color);
        }
    }

    public void setBackgroundTint(@Nullable ColorStateList tint) {
        if (backgroundTint != tint) {
            backgroundTint = tint;
            final Drawable backgroundDrawable = getBackgroundDrawable();
            if (backgroundDrawable != null) {
                DrawableCompat.setTintList(backgroundDrawable, backgroundTint);
            }
        }
    }

    public void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (backgroundTintMode != tintMode) {
            backgroundTintMode = tintMode;
            final Drawable backgroundDrawable = getBackgroundDrawable();
            if (backgroundDrawable != null && backgroundTintMode != null) {
                DrawableCompat.setTintMode(backgroundDrawable, backgroundTintMode);
            }
        }
    }


    @NonNull
    private RippleDrawable createBackground() {
        MaterialShapeDrawable backgroundDrawable = new MaterialShapeDrawable(shapeAppearanceModel);
        MaterialShapeDrawable maskDrawable = new MaterialShapeDrawable(shapeAppearanceModel);
        rippleDrawable =
                new RippleDrawable(
                        sanitizeRippleDrawableColor(rippleColor),
                        backgroundDrawable,
                        maskDrawable
                );
        return rippleDrawable;
    }

    @NonNull
    private ColorStateList sanitizeRippleDrawableColor(@Nullable ColorStateList rippleColor) {
        if (rippleColor != null) {
            return rippleColor;
        }
        return ColorStateList.valueOf(Color.TRANSPARENT);
    }

    private void updateShape(@NonNull ShapeAppearanceModel shapeAppearanceModel) {
        final MaterialShapeDrawable backgroundDrawable = getBackgroundDrawable();
        if (backgroundDrawable != null) {
            backgroundDrawable.setShapeAppearanceModel(shapeAppearanceModel);
        }

        final MaterialShapeDrawable maskDrawable = getMaskDrawable();
        if (maskDrawable != null) {
            maskDrawable.setShapeAppearanceModel(shapeAppearanceModel);
        }
    }

    @Nullable
    private MaterialShapeDrawable getBackgroundDrawable() {
        if (rippleDrawable != null && rippleDrawable.getNumberOfLayers() > 0) {
            return (MaterialShapeDrawable) rippleDrawable.getDrawable(0);
        }
        return null;
    }

    @Nullable
    public MaterialShapeDrawable getMaskDrawable() {
        if (rippleDrawable != null && rippleDrawable.getNumberOfLayers() > 1) {
            return (MaterialShapeDrawable) rippleDrawable.getDrawable(1);
        }
        return null;
    }

    public PorterDuff.Mode parseTintMode(int value, PorterDuff.Mode defaultMode) {
        switch (value) {
            case 3:
                return PorterDuff.Mode.SRC_OVER;
            case 5:
                return PorterDuff.Mode.SRC_IN;
            case 9:
                return PorterDuff.Mode.SRC_ATOP;
            case 14:
                return PorterDuff.Mode.MULTIPLY;
            case 15:
                return PorterDuff.Mode.SCREEN;
            case 16:
                return PorterDuff.Mode.ADD;
            default:
                return defaultMode;
        }
    }

}
