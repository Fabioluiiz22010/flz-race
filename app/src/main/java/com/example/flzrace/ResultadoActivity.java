package com.example.flzrace;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ResultadoActivity extends AppCompatActivity {
    private boolean trajeto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_resultado);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resultado), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        boolean zero100 = Estado.MODO_ZERO100.equals(Estado.modo);
        trajeto = Estado.MODO_AB.equals(Estado.modo);
        preencherCabecalho(zero100);
        preencherDados();
        preencherGrafico(zero100);
        preencherValidade();
        configurarBotoes(zero100);
    }

    private void preencherCabecalho(boolean zero100) {
        TextView txtTitulo = findViewById(R.id.txtTituloResultado);
        TextView txtPrincipal = findViewById(R.id.txtPrincipal);
        TextView txtComparacao = findViewById(R.id.txtComparacao);
        findViewById(R.id.txtNovoRecorde).setVisibility(Estado.novoRecorde ? View.VISIBLE : View.GONE);

        if (trajeto) {
            Nuvem.Rota rota = Estado.rotaAtual;
            txtTitulo.setText("RESULTADO · TRAJETO A→B");
            txtPrincipal.setText(Estado.tempoAB > 0 ? Estado.formatarTempo(Estado.tempoAB) : "—");
            txtComparacao.setText(rota != null
                    ? rota.nome + " · " + Estado.formatarDistancia(rota.distanciaM)
                    : "Trajeto A→B");
            return;
        }

        txtTitulo.setText(zero100 ? "RESULTADO · 0 A 100 KM/H" : "RESULTADO · VELOCIDADE LIVRE");

        float anterior = Estado.recordeAnterior;
        boolean contaRecorde = !Estado.demo && Estado.medicaoValida;
        String comparacao = "";

        if (zero100) {
            if (Estado.tempo0a100 > 0) {
                txtPrincipal.setText(String.format(Estado.LOCALE, "%.2f s", Estado.tempo0a100));
                if (contaRecorde && anterior < 0) {
                    comparacao = "Seu primeiro recorde no 0 a 100!";
                } else if (Estado.novoRecorde) {
                    comparacao = String.format(Estado.LOCALE,
                            "%.2f s mais rápido que seu recorde anterior", anterior - Estado.tempo0a100);
                } else if (anterior > 0) {
                    comparacao = String.format(Estado.LOCALE, "Seu recorde: %.2f s", anterior);
                }
            } else {
                txtPrincipal.setText("—");
                comparacao = "Não chegou a 100 km/h";
            }
        } else {
            txtPrincipal.setText(String.format(Estado.LOCALE, "%.0f km/h", Estado.maxKmh));
            txtPrincipal.setTextColor(ContextCompat.getColor(this, R.color.flz_laranja));
            txtPrincipal.setShadowLayer(24, 0, 0, ContextCompat.getColor(this, R.color.flz_laranja));
            if (contaRecorde && anterior < 0) {
                comparacao = "Sua primeira velocidade máxima registrada!";
            } else if (Estado.novoRecorde) {
                comparacao = String.format(Estado.LOCALE,
                        "%.0f km/h acima do seu recorde anterior", Estado.maxKmh - anterior);
            } else if (anterior > 0) {
                comparacao = String.format(Estado.LOCALE, "Seu recorde: %.0f km/h", anterior);
            }
        }
        txtComparacao.setText(comparacao);
        txtComparacao.setVisibility(comparacao.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void preencherDados() {
        ((TextView) findViewById(R.id.txtResMax)).setText(
                String.format(Estado.LOCALE, "%.0f km/h", Estado.maxKmh));
        ((TextView) findViewById(R.id.txtResDistancia)).setText(
                String.format(Estado.LOCALE, "%.0f m", Estado.distanciaM));
        ((TextView) findViewById(R.id.txtResMedia)).setText(
                String.format(Estado.LOCALE, "%.0f km/h", Estado.mediaKmh));
        ((TextView) findViewById(R.id.txtResTempo)).setText(
                String.format(Estado.LOCALE, "%.2f s", tempoTotal()));
    }

    private float tempoTotal() {
        List<float[]> amostras = Estado.amostras;
        if (amostras == null || amostras.isEmpty()) return Estado.tempoFinalMs / 1000f;
        return amostras.get(amostras.size() - 1)[0];
    }

    private void preencherGrafico(boolean zero100) {
        GraficoVelocidadeView grafico = findViewById(R.id.grafico);
        grafico.setDados(Estado.amostras, Estado.tempo0a100, zero100);
    }

    private void preencherValidade() {
        View faixa = findViewById(R.id.faixaValidade);
        ImageView img = findViewById(R.id.imgValidade);
        TextView txt = findViewById(R.id.txtValidade);
        int cor;

        if (Estado.demo) {
            cor = R.color.flz_roxo;
            img.setImageResource(R.drawable.ic_demo);
            faixa.setBackgroundResource(R.drawable.bg_demo);
            txt.setText("Demonstração: não vale recorde nem ranking");
        } else if (!Estado.medicaoValida) {
            cor = R.color.flz_laranja;
            img.setImageResource(R.drawable.ic_alerta);
            faixa.setBackgroundResource(R.drawable.bg_aviso);
            txt.setText("Medição inválida: " + Estado.motivoInvalida);
        } else {
            cor = R.color.flz_ciano;
            img.setImageResource(R.drawable.ic_check);
            txt.setText("Medição válida: GPS real e terreno plano");
        }
        int corInt = ContextCompat.getColor(this, cor);
        ImageViewCompat.setImageTintList(img, ColorStateList.valueOf(corInt));
        txt.setTextColor(corInt);
    }

    private void configurarBotoes(boolean zero100) {
        MaterialButton btnEnviar = findViewById(R.id.btnEnviarRanking);
        boolean temMarca = trajeto ? Estado.tempoAB > 0 && Estado.rotaAtual != null
                : zero100 ? Estado.tempo0a100 > 0 : Estado.maxKmh > 0;
        boolean podeEnviar = !Estado.demo && Estado.medicaoValida && temMarca;
        btnEnviar.setVisibility(podeEnviar ? View.VISIBLE : View.GONE);
        btnEnviar.setOnClickListener(v -> enviarParaRanking(btnEnviar, zero100));

        String modo = Estado.modo;
        boolean demo = Estado.demo;
        findViewById(R.id.btnNovaMedicao).setOnClickListener(v -> {
            startActivity(MedicaoActivity.criarIntent(this, modo, demo));
            finish();
        });
        findViewById(R.id.btnConcluir).setOnClickListener(v -> finish());
    }

    private void enviarParaRanking(MaterialButton botao, boolean zero100) {
        String categoria;
        double valor;
        if (trajeto) {
            categoria = Estado.rotaAtual.categoriaRanking();
            valor = Estado.tempoAB;
        } else if (zero100) {
            categoria = Nuvem.RANKING_0A100;
            valor = Estado.tempo0a100;
        } else {
            categoria = Nuvem.RANKING_VMAX;
            valor = Estado.maxKmh;
        }

        botao.setEnabled(false);
        botao.setText("ENVIANDO…");
        Nuvem.enviarResultado(this, categoria, valor, (r, erro) -> {
            if (r == null) {
                botao.setEnabled(true);
                botao.setText("ENVIAR PARA O RANKING");
                Toast.makeText(this, "Não foi possível enviar: " + erro, Toast.LENGTH_LONG).show();
                return;
            }
            String marca = Nuvem.RANKING_VMAX.equals(categoria)
                    ? String.format(Estado.LOCALE, "%.0f km/h", r.valorNoRanking)
                    : Estado.formatarTempo(r.valorNoRanking);
            String mensagem = r.melhorou
                    ? "Enviado! Você está em #" + r.posicao + " no ranking"
                    : "Seu melhor no ranking continua " + marca + " (#" + r.posicao + ")";
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();

            botao.setEnabled(true);
            botao.setText("VER RANKING · #" + r.posicao);
            botao.setOnClickListener(v -> {
                startActivity(MainActivity.criarIntentAba(this, R.id.nav_ranking)
                        .putExtra(MainActivity.EXTRA_CATEGORIA, categoria));
                finish();
            });
        });
    }
}
