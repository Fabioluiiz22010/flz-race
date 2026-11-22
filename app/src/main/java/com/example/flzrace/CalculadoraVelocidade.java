package com.example.flzrace;

import java.util.ArrayList;
import java.util.List;

public class CalculadoraVelocidade {
    public static final float ALVO_KMH = 100f;

    private long inicioMs = -1;
    private long ultimoTempoMs = -1;
    private float ultimaVelocidade = 0f;
    private float maxKmh = 0f;
    private double distanciaM = 0;
    private float tempo0a100 = -1f;
    private final List<float[]> amostras = new ArrayList<>();

    public void iniciar(long inicioMs) {
        this.inicioMs = inicioMs;
        ultimoTempoMs = inicioMs;
        ultimaVelocidade = 0f;
        maxKmh = 0f;
        distanciaM = 0;
        tempo0a100 = -1f;
        amostras.clear();
        amostras.add(new float[]{0f, 0f});
    }

    public boolean iniciado() {
        return inicioMs >= 0;
    }

    public void adicionar(long tempoMs, float kmh, double deltaDistanciaM) {
        if (inicioMs < 0 || tempoMs < ultimoTempoMs) return;

        if (kmh > maxKmh) maxKmh = kmh;
        distanciaM += deltaDistanciaM;

        if (tempo0a100 < 0 && ultimaVelocidade < ALVO_KMH && kmh >= ALVO_KMH) {
            double fracao = (ALVO_KMH - ultimaVelocidade) / (kmh - ultimaVelocidade);
            double instante = ultimoTempoMs + fracao * (tempoMs - ultimoTempoMs);
            tempo0a100 = (float) ((instante - inicioMs) / 1000.0);
        }

        ultimoTempoMs = tempoMs;
        ultimaVelocidade = kmh;
        amostras.add(new float[]{(tempoMs - inicioMs) / 1000f, kmh});
    }

    public float getMaxKmh() {
        return maxKmh;
    }

    public double getDistanciaM() {
        return distanciaM;
    }

    public float getTempo0a100() {
        return tempo0a100;
    }

    public float getMediaKmh(long agoraMs) {
        double segundos = (agoraMs - inicioMs) / 1000.0;
        if (inicioMs < 0 || segundos <= 0) return 0f;
        return (float) (distanciaM / segundos * 3.6);
    }

    public List<float[]> getAmostras() {
        return new ArrayList<>(amostras);
    }
}
