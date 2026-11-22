package com.example.flzrace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class VelocimetroView extends View {
    private static final float ANGULO_INICIAL = 135f;
    private static final float ANGULO_TOTAL = 270f;

    private final Paint trilho = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progresso = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pontilhado = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legenda = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendaMax = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arco = new RectF();
    private final float densidade;

    private float maximo = 100f;
    private float velocidade = 0f;

    public VelocimetroView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        densidade = getResources().getDisplayMetrics().density;
        int ciano = ContextCompat.getColor(context, R.color.flz_ciano);

        trilho.setStyle(Paint.Style.STROKE);
        trilho.setStrokeCap(Paint.Cap.BUTT);
        trilho.setStrokeWidth(14 * densidade);
        trilho.setColor(ContextCompat.getColor(context, R.color.flz_ciano_suave));

        progresso.setStyle(Paint.Style.STROKE);
        progresso.setStrokeCap(Paint.Cap.BUTT);
        progresso.setStrokeWidth(14 * densidade);
        progresso.setColor(ciano);
        progresso.setShadowLayer(10 * densidade, 0, 0, ciano);

        pontilhado.setStyle(Paint.Style.STROKE);
        pontilhado.setStrokeWidth(densidade);
        pontilhado.setColor(ContextCompat.getColor(context, R.color.flz_ciano_borda));
        pontilhado.setPathEffect(new DashPathEffect(new float[]{2 * densidade, 8 * densidade}, 0));

        legenda.setTextSize(13 * densidade);
        legenda.setColor(ContextCompat.getColor(context, R.color.flz_texto_2));
        legenda.setTextAlign(Paint.Align.CENTER);

        legendaMax.set(legenda);
        legendaMax.setColor(ContextCompat.getColor(context, R.color.flz_laranja));

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setMaximo(float maximo) {
        this.maximo = maximo;
        invalidate();
    }

    public void setVelocidade(float kmh) {
        velocidade = Math.max(0f, kmh);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float lado = Math.min(getWidth(), getHeight());
        float margem = 18 * densidade;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float raio = lado / 2f - margem;

        arco.set(cx - raio, cy - raio, cx + raio, cy + raio);
        canvas.drawArc(arco, ANGULO_INICIAL, ANGULO_TOTAL, false, trilho);

        float fracao = Math.min(1f, velocidade / maximo);
        if (fracao > 0f) canvas.drawArc(arco, ANGULO_INICIAL, ANGULO_TOTAL * fracao, false, progresso);

        canvas.drawCircle(cx, cy, raio - 22 * densidade, pontilhado);

        double angInicio = Math.toRadians(ANGULO_INICIAL);
        double angFim = Math.toRadians(ANGULO_INICIAL + ANGULO_TOTAL);
        float distLegenda = raio + 2 * densidade;
        canvas.drawText("0",
                cx + (float) Math.cos(angInicio) * distLegenda,
                cy + (float) Math.sin(angInicio) * distLegenda + 20 * densidade, legenda);
        canvas.drawText(String.format(Locale.getDefault(), "%.0f", maximo),
                cx + (float) Math.cos(angFim) * distLegenda,
                cy + (float) Math.sin(angFim) * distLegenda + 20 * densidade, legendaMax);
    }
}
