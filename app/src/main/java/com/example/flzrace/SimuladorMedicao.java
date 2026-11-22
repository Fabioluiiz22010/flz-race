package com.example.flzrace;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Random;

public class SimuladorMedicao {
    public interface Listener {
        void onVelocidadeSimulada(long tempoMs, float kmh, double deltaDistanciaM);
    }

    private static final long PASSO_MS = 100;
    private static final float RUIDO_KMH = 0.6f;

    public static final float ARRANCADA_VEL_FINAL = 140f;
    public static final float ARRANCADA_TAU_S = 5.46f;

    public static final float TRAJETO_VEL_FINAL = 90f;
    public static final float TRAJETO_TAU_S = 4f;

    private float velocidadeFinal = ARRANCADA_VEL_FINAL;
    private float constanteTempoS = ARRANCADA_TAU_S;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random aleatorio = new Random();
    private final Listener listener;

    private long inicioMs;
    private long ultimoMs;
    private float ultimaVelocidade;
    private boolean rodando = false;

    public SimuladorMedicao(Listener listener) {
        this.listener = listener;
    }

    public void iniciar(long inicioMs) {
        iniciar(inicioMs, ARRANCADA_VEL_FINAL, ARRANCADA_TAU_S);
    }

    public void iniciar(long inicioMs, float velocidadeFinal, float constanteTempoS) {
        this.velocidadeFinal = velocidadeFinal;
        this.constanteTempoS = constanteTempoS;
        this.inicioMs = inicioMs;
        ultimoMs = inicioMs;
        ultimaVelocidade = 0f;
        rodando = true;
        handler.post(passo);
    }

    public void parar() {
        rodando = false;
        handler.removeCallbacks(passo);
    }

    private final Runnable passo = new Runnable() {
        @Override
        public void run() {
            if (!rodando) return;
            long agora = SystemClock.elapsedRealtime();
            float t = (agora - inicioMs) / 1000f;

            float v = velocidadeFinal * (1f - (float) Math.exp(-t / constanteTempoS));
            if (v > 1f) v += (aleatorio.nextFloat() * 2f - 1f) * RUIDO_KMH;
            v = Math.max(0f, v);

            double segundos = (agora - ultimoMs) / 1000.0;
            double deltaM = (ultimaVelocidade + v) / 2.0 / 3.6 * segundos;

            ultimoMs = agora;
            ultimaVelocidade = v;
            listener.onVelocidadeSimulada(agora, v, deltaM);

            if (rodando) handler.postDelayed(this, PASSO_MS);
        }
    };
}
