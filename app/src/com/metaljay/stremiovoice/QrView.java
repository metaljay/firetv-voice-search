package com.metaljay.stremiovoice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import io.nayuki.qrcodegen.QrCode;

/** Draws a QR code, black on white with the quiet border scanners need, as large as the view allows. */
public final class QrView extends View {

    private static final int BORDER = 4;
    private final QrCode qr;
    private final Paint paint = new Paint();

    public QrView(Context context, QrCode qr) {
        super(context);
        this.qr = qr;
        paint.setAntiAlias(false);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int side = Math.min(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) side = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int total = qr.size + BORDER * 2;
        int cell = Math.max(1, Math.min(getWidth(), getHeight()) / total); // whole pixels, so it stays sharp
        int used = cell * total;
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, used, used, paint);
        paint.setColor(Color.BLACK);
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    int left = (x + BORDER) * cell;
                    int top = (y + BORDER) * cell;
                    canvas.drawRect(left, top, left + cell, top + cell, paint);
                }
            }
        }
    }
}
