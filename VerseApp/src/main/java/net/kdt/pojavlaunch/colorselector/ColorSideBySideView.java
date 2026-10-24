package net.kdt.pojavlaunch.colorselector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorSideBySideView extends View {
    private final Paint mPaint;
    private int mColor;
    private int mAlphaColor;
    private float mWidth;
    private float mHeight;
    private float mHalfHeight;
    public ColorSideBySideView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPaint = new Paint();
    }

    public void setColor(int color) {
        mColor = ColorSelector.setAlpha(color, 0xff);
        mAlphaColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawARGB(0xff, 0x33, 0x33, 0x33);
        mPaint.setColor(mColor);
        canvas.drawRect(0,0,mWidth, mHalfHeight, mPaint);
        mPaint.setColor(mAlphaColor);
        canvas.drawRect(0,mHalfHeight,mWidth,mHeight, mPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int old_w, int old_h) {
        mHalfHeight = h/2f;
        mWidth = w;
        mHeight = h;
    }
}
