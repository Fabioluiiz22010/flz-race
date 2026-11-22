package com.example.flzrace;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ArrancadaDetector implements SensorEventListener {
    public interface Listener {
        void onArrancada(long tempoMs);

        void onAceleracao(float g);
    }

    private static final float GRAVIDADE = 9.81f;

    private static final float LIMIAR_CARRO = 2.0f;
    private static final long JANELA_CARRO_MS = 150;

    private static final float LIMIAR_DEMO = 0.8f;
    private static final long JANELA_DEMO_MS = 50;
    private static final long INTERVALO_UI_MS = 100;
    private static final float ALFA_FILTRO = 0.8f;

    private final SensorManager sensorManager;
    private final Listener listener;
    private Sensor sensor;
    private boolean usandoAceleracaoLinear;

    private final float[] gravidade = new float[3];
    private boolean primeiraLeitura = true;
    private boolean armado = false;
    private float limiar = LIMIAR_CARRO;
    private long janelaMs = JANELA_CARRO_MS;
    private long inicioAcimaDoLimiar = -1;
    private long ultimaAtualizacaoUi = 0;

    public ArrancadaDetector(Context context, Listener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;
    }

    public boolean iniciar() {
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        usandoAceleracaoLinear = sensor != null;
        if (sensor == null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) return false;
        primeiraLeitura = true;
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        return true;
    }

    public void parar() {
        sensorManager.unregisterListener(this);
        armado = false;
    }

    public void armar(boolean sensivel) {
        limiar = sensivel ? LIMIAR_DEMO : LIMIAR_CARRO;
        janelaMs = sensivel ? JANELA_DEMO_MS : JANELA_CARRO_MS;
        armado = true;
        inicioAcimaDoLimiar = -1;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x, y, z;
        if (usandoAceleracaoLinear) {
            x = e.values[0];
            y = e.values[1];
            z = e.values[2];
        } else {
            if (primeiraLeitura) {
                System.arraycopy(e.values, 0, gravidade, 0, 3);
                primeiraLeitura = false;
            }
            for (int i = 0; i < 3; i++) {
                gravidade[i] = ALFA_FILTRO * gravidade[i] + (1 - ALFA_FILTRO) * e.values[i];
            }
            x = e.values[0] - gravidade[0];
            y = e.values[1] - gravidade[1];
            z = e.values[2] - gravidade[2];
        }

        float aceleracao = (float) Math.sqrt(x * x + y * y + z * z);
        long agora = e.timestamp / 1_000_000L;

        if (agora - ultimaAtualizacaoUi >= INTERVALO_UI_MS) {
            ultimaAtualizacaoUi = agora;
            listener.onAceleracao(aceleracao / GRAVIDADE);
        }

        if (!armado) return;
        if (aceleracao >= limiar) {
            if (inicioAcimaDoLimiar < 0) {
                inicioAcimaDoLimiar = agora;
            } else if (agora - inicioAcimaDoLimiar >= janelaMs) {
                armado = false;
                listener.onArrancada(inicioAcimaDoLimiar);
            }
        } else {
            inicioAcimaDoLimiar = -1;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
