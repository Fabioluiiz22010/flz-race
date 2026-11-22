package com.example.flzrace;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MedicaoActivity extends AppCompatActivity implements Estado.Ouvinte {
    private static final String EXTRA_MODO = "modo";
    private static final String EXTRA_DEMO = "demo";
    private static final long INTERVALO_RELOGIO_MS = 50;

    public static Intent criarIntent(Context ctx, String modo, boolean demo) {
        return new Intent(ctx, MedicaoActivity.class)
                .putExtra(EXTRA_MODO, modo)
                .putExtra(EXTRA_DEMO, demo);
    }

    private VelocimetroView velocimetro;
    private TextView txtModo, txtGps, txtSeloDemo, txtStatus, txtVelocidade, txtTempo;
    private TextView txtMax, txtAceleracao, txtDistancia, txtRotuloDistancia;
    private boolean trajeto;
    private View faixaDemo;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resultadoMostrado = false;

    private final Runnable relogio = new Runnable() {
        @Override
        public void run() {
            atualizarTempo();
            handler.postDelayed(this, INTERVALO_RELOGIO_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_medicao);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.medicao), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        ligarViews();

        Intent intent = getIntent();
        if (savedInstanceState == null && intent.hasExtra(EXTRA_MODO)) {
            String modo = intent.getStringExtra(EXTRA_MODO);
            boolean demo = intent.getBooleanExtra(EXTRA_DEMO, false);
            Estado.resetarMedicao(modo, demo);
            MedicaoService.iniciar(this, modo, demo);
        }

        boolean zero100 = Estado.MODO_ZERO100.equals(Estado.modo);
        trajeto = Estado.MODO_AB.equals(Estado.modo);
        if (trajeto) {
            String nomeRota = Estado.rotaAtual != null ? Estado.rotaAtual.nome : "";
            txtModo.setText(("A→B · " + nomeRota).toUpperCase(Estado.LOCALE));
            txtRotuloDistancia.setText("Até o alvo");

            findViewById(R.id.btnEncerrar).setVisibility(View.GONE);
        } else {
            txtModo.setText(zero100 ? "0 A 100 KM/H" : "VELOCIDADE LIVRE");
        }
        velocimetro.setMaximo(zero100 ? 100f : trajeto ? 160f : 200f);
        txtSeloDemo.setVisibility(Estado.demo ? View.VISIBLE : View.GONE);
        txtGps.setVisibility(Estado.demo ? View.GONE : View.VISIBLE);
        faixaDemo.setVisibility(Estado.demo ? View.VISIBLE : View.GONE);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> cancelar());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> cancelar());
        findViewById(R.id.btnEncerrar).setOnClickListener(v -> {
            if (Estado.fase == Estado.FASE_VALENDO) {
                MedicaoService.comando(this, MedicaoService.ACAO_ENCERRAR);
            } else {
                cancelar();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancelar();
            }
        });
    }

    private void ligarViews() {
        velocimetro = findViewById(R.id.velocimetro);
        txtModo = findViewById(R.id.txtModo);
        txtGps = findViewById(R.id.txtGps);
        txtSeloDemo = findViewById(R.id.txtSeloDemo);
        txtStatus = findViewById(R.id.txtStatus);
        txtVelocidade = findViewById(R.id.txtVelocidade);
        txtTempo = findViewById(R.id.txtTempo);
        txtMax = findViewById(R.id.txtMax);
        txtAceleracao = findViewById(R.id.txtAceleracao);
        txtDistancia = findViewById(R.id.txtDistancia);
        txtRotuloDistancia = findViewById(R.id.txtRotuloDistancia);
        faixaDemo = findViewById(R.id.faixaDemo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Estado.observar(this);
        onEstadoMudou();
        handler.post(relogio);
    }

    @Override
    protected void onPause() {
        Estado.remover(this);
        handler.removeCallbacks(relogio);
        super.onPause();
    }

    private void cancelar() {
        if (Estado.fase != Estado.FASE_CONCLUIDO) {
            MedicaoService.comando(this, MedicaoService.ACAO_CANCELAR);
        }
        finish();
    }

    @Override
    public void onEstadoMudou() {
        velocimetro.setVelocidade(Estado.velocidadeKmh);
        txtVelocidade.setText(String.format(Estado.LOCALE, "%.0f", Estado.velocidadeKmh));
        txtMax.setText(String.format(Estado.LOCALE, "%.0f", Estado.maxKmh));
        txtAceleracao.setText(String.format(Estado.LOCALE, "%.2f g", Estado.aceleracaoG));
        if (trajeto) {
            txtDistancia.setText(Estado.distanciaAteAlvoM >= 0
                    ? Estado.formatarDistancia(Estado.distanciaAteAlvoM) : "—");
        } else {
            txtDistancia.setText(String.format(Estado.LOCALE, "%.0f m", Estado.distanciaM));
        }
        txtGps.setText(Estado.precisaoM > 0
                ? String.format(Estado.LOCALE, "%.0f m", Estado.precisaoM) : "—");

        atualizarStatus();
        atualizarTempo();

        if (Estado.fase == Estado.FASE_CONCLUIDO && !resultadoMostrado) {
            resultadoMostrado = true;
            mostrarResultado();
        }
    }

    private void atualizarStatus() {
        int cor;
        switch (Estado.fase) {
            case Estado.FASE_VALENDO:
                cor = R.color.flz_laranja;
                break;
            case Estado.FASE_PRONTO:
            case Estado.FASE_CONCLUIDO:
                cor = Estado.demo ? R.color.flz_roxo : R.color.flz_ciano;
                break;
            default:
                cor = R.color.flz_texto_2;
                break;
        }
        txtStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, cor)));
        txtStatus.setText(Estado.status.isEmpty() ? "PREPARANDO" : Estado.status);
    }

    private void atualizarTempo() {
        long ms;
        if (Estado.fase == Estado.FASE_VALENDO && Estado.inicioArrancadaMs > 0) {
            ms = SystemClock.elapsedRealtime() - Estado.inicioArrancadaMs;
        } else if (Estado.fase == Estado.FASE_CONCLUIDO) {
            ms = Estado.tempoFinalMs;
        } else {
            ms = 0;
        }
        float segundos = ms / 1000f;
        txtTempo.setText(segundos >= 60
                ? Estado.formatarTempo(segundos)
                : String.format(Estado.LOCALE, "%05.2f s", segundos));
    }

    private void mostrarResultado() {
        startActivity(new Intent(this, ResultadoActivity.class));
        finish();
    }
}
