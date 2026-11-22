package com.example.flzrace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class GraficoVelocidadeView extends View {
    private final Paint eixo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curva = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linhaAlvo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marcador = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legenda = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendaAlvo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path caminho = new Path();
    private final Path preenchimento = new Path();
    private final float densidade;
    private final int ciano;

    private List<float[]> amostras = new ArrayList<>();
    private float tempoAlvo = -1f;
    private boolean mostrarAlvo = true;

    public GraficoVelocidadeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        densidade = getResources().getDisplayMetrics().density;
        ciano = ContextCompat.getColor(context, R.color.flz_ciano);
        int laranja = ContextCompat.getColor(context, R.color.flz_laranja);
        int texto2 = ContextCompat.getColor(context, R.color.flz_texto_2);

        eixo.setColor(texto2);
        eixo.setAlpha(120);
        eixo.setStrokeWidth(densidade);

        curva.setStyle(Paint.Style.STROKE);
        curva.setStrokeWidth(3 * densidade);
        curva.setStrokeJoin(Paint.Join.ROUND);
        curva.setStrokeCap(Paint.Cap.ROUND);
        curva.setColor(ciano);
        curva.setShadowLayer(6 * densidade, 0, 0, ciano);

        linhaAlvo.setStyle(Paint.Style.STROKE);
        linhaAlvo.setStrokeWidth(densidade);
        linhaAlvo.setColor(laranja);
        linhaAlvo.setPathEffect(new DashPathEffect(new float[]{4 * densidade, 4 * densidade}, 0));

        marcador.setColor(laranja);

        legenda.setColor(texto2);
        legenda.setTextSize(11 * densidade);

        legendaAlvo.set(legenda);
        legendaAlvo.setColor(laranja);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setDados(List<float[]> amostras, float tempoAlvo, boolean mostrarAlvo) {
        this.amostras = amostras != null ? amostras : new ArrayList<>();
        this.tempoAlvo = tempoAlvo;
        this.mostrarAlvo = mostrarAlvo;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float esquerda = 32 * densidade;
        float direita = getWidth() - 10 * densidade;
        float topo = 10 * densidade;
        float base = getHeight() - 22 * densidade;

        float maxTempo = 1f;
        float maxVel = mostrarAlvo ? 110f : 40f;
        for (float[] a : amostras) {
            maxTempo = Math.max(maxTempo, a[0]);
            maxVel = Math.max(maxVel, a[1] * 1.1f);
        }
        maxVel = (float) Math.ceil(maxVel / 20f) * 20f;

        float larguraUtil = direita - esquerda;
        float alturaUtil = base - topo;

        canvas.drawLine(esquerda, topo, esquerda, base, eixo);
        canvas.drawLine(esquerda, base, direita, base, eixo);
        canvas.drawText("0", esquerda - 14 * densidade, base + 4 * densidade, legenda);
        canvas.drawText("0 s", esquerda - 4 * densidade, base + 16 * densidade, legenda);
        String fim = String.format(Estado.LOCALE, "%.1f s", maxTempo);
        canvas.drawText(fim, direita - legenda.measureText(fim), base + 16 * densidade, legenda);

        if (mostrarAlvo) {
            float yAlvo = base - (100f / maxVel) * alturaUtil;
            canvas.drawLine(esquerda, yAlvo, direita, yAlvo, linhaAlvo);
            canvas.drawText("100", 2 * densidade, yAlvo + 4 * densidade, legendaAlvo);
            if (tempoAlvo > 0) {
                float xAlvo = esquerda + (tempoAlvo / maxTempo) * larguraUtil;
                canvas.drawLine(xAlvo, yAlvo, xAlvo, base, linhaAlvo);
                canvas.drawCircle(xAlvo, yAlvo, 5 * densidade, marcador);
            }
        } else {
            String topoTexto = String.format(Estado.LOCALE, "%.0f", maxVel);
            canvas.drawText(topoTexto, 2 * densidade, topo + 8 * densidade, legenda);
        }

        if (amostras.size() < 2) return;

        caminho.reset();
        preenchimento.reset();
        for (int i = 0; i < amostras.size(); i++) {
            float x = esquerda + (amostras.get(i)[0] / maxTempo) * larguraUtil;
            float y = base - (amostras.get(i)[1] / maxVel) * alturaUtil;
            if (i == 0) {
                caminho.moveTo(x, y);
                preenchimento.moveTo(x, base);
            }
            caminho.lineTo(x, y);
            preenchimento.lineTo(x, y);
        }
        float ultimoX = esquerda + (amostras.get(amostras.size() - 1)[0] / maxTempo) * larguraUtil;
        preenchimento.lineTo(ultimoX, base);
        preenchimento.close();

        area.setShader(new LinearGradient(0, topo, 0, base,
                (ciano & 0x00FFFFFF) | 0x55000000, ciano & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(preenchimento, area);
        canvas.drawPath(caminho, curva);
    }
}
