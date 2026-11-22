package com.example.flzrace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class GradeView extends View {
    private static final float PASSO_DP = 28f;

    private final Paint linha = new Paint();
    private final float passo;

    public GradeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        passo = PASSO_DP * getResources().getDisplayMetrics().density;
        linha.setColor(ContextCompat.getColor(context, R.color.flz_ciano_grade));
        linha.setStrokeWidth(1f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int largura = getWidth();
        int altura = getHeight();
        for (float x = 0; x <= largura; x += passo) canvas.drawLine(x, 0, x, altura, linha);
        for (float y = 0; y <= altura; y += passo) canvas.drawLine(0, y, largura, y, linha);
    }
}
