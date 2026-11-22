package com.example.flzrace;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class TelaRanking {
    private final AppCompatActivity activity;
    private final MaterialButton aba0a100, abaVmax, abaRotas;
    private final TextView txtSub, txtVazio;
    private final View progresso;
    private final LinearLayout lista;
    private final TextView txtVocePosicao, txtVoceNome, txtVoceInfo, txtVoceValor;

    private String categoria = Nuvem.RANKING_0A100;
    private Nuvem.Rota rotaSelecionada;
    private int requisicao = 0;

    public TelaRanking(AppCompatActivity activity, View raiz) {
        this.activity = activity;
        aba0a100 = raiz.findViewById(R.id.aba0a100);
        abaVmax = raiz.findViewById(R.id.abaVmax);
        abaRotas = raiz.findViewById(R.id.abaRotas);
        txtSub = raiz.findViewById(R.id.txtRankingSub);
        txtVazio = raiz.findViewById(R.id.txtRankingVazio);
        progresso = raiz.findViewById(R.id.progressoRanking);
        lista = raiz.findViewById(R.id.listaRanking);
        txtVocePosicao = raiz.findViewById(R.id.txtVocePosicao);
        txtVoceNome = raiz.findViewById(R.id.txtVoceNome);
        txtVoceInfo = raiz.findViewById(R.id.txtVoceInfo);
        txtVoceValor = raiz.findViewById(R.id.txtVoceValor);

        aba0a100.setOnClickListener(v -> selecionar(Nuvem.RANKING_0A100));
        abaVmax.setOnClickListener(v -> selecionar(Nuvem.RANKING_VMAX));
        abaRotas.setOnClickListener(v -> {
            if (rotaSelecionada == null || Nuvem.ehRota(categoria)) escolherRota();
            else selecionar(rotaSelecionada.categoriaRanking());
        });
        raiz.findViewById(R.id.btnAtualizarRanking).setOnClickListener(v -> carregar());
        pintarAbas();
    }

    public void selecionar(String novaCategoria) {
        definirCategoria(novaCategoria);
        carregar();
    }

    public void definirCategoria(String novaCategoria) {
        categoria = novaCategoria;
        Nuvem.Rota atual = Estado.rotaAtual;
        if (Nuvem.ehRota(categoria) && atual != null && categoria.equals(atual.categoriaRanking())) {
            rotaSelecionada = atual;
        }
        pintarAbas();
    }

    private void escolherRota() {
        Toast.makeText(activity, "Carregando rotas…", Toast.LENGTH_SHORT).show();
        Nuvem.carregarRotas((rotas, erro) -> {
            if (rotas == null || rotas.isEmpty()) {
                Toast.makeText(activity, erro != null ? "Erro: " + erro
                        : "Nenhuma rota criada ainda. Crie uma em Acelerar → Trajeto A→B.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            String[] nomes = new String[rotas.size()];
            for (int i = 0; i < rotas.size(); i++) {
                Nuvem.Rota r = rotas.get(i);
                nomes[i] = r.nome + " · " + Estado.formatarDistancia(r.distanciaM);
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Ranking de qual rota?")
                    .setItems(nomes, (d, i) -> {
                        rotaSelecionada = rotas.get(i);
                        selecionar(rotaSelecionada.categoriaRanking());
                    })
                    .show();
        });
    }

    public void carregar() {
        int minha = ++requisicao;
        if (Nuvem.ehRota(categoria)) {
            txtSub.setText(rotaSelecionada != null
                    ? "Rota: " + rotaSelecionada.nome + " · "
                    + Estado.formatarDistancia(rotaSelecionada.distanciaM) + " · toque em ROTAS para trocar"
                    : "Menor tempo na rota");
        } else {
            txtSub.setText(Nuvem.RANKING_0A100.equals(categoria)
                    ? "Menor tempo de 0 a 100 km/h · todos os pilotos"
                    : "Maior velocidade registrada · todos os pilotos");
        }

        lista.removeAllViews();
        txtVazio.setVisibility(View.GONE);
        progresso.setVisibility(View.VISIBLE);

        Nuvem.carregarRanking(categoria, (linhas, erro) -> {
            if (minha != requisicao) return;
            progresso.setVisibility(View.GONE);
            if (erro != null) {
                mostrarVazio("Não foi possível carregar o ranking: " + erro);
            } else if (linhas.isEmpty()) {
                mostrarVazio("Ninguém neste ranking ainda. Seja o primeiro!");
            } else {
                preencherLista(linhas);
            }
        });
        carregarMinhaPosicao(minha);
    }

    private void mostrarVazio(String texto) {
        txtVazio.setText(texto);
        txtVazio.setVisibility(View.VISIBLE);
    }

    private void preencherLista(List<Nuvem.LinhaRanking> linhas) {
        String meuUid = Nuvem.logado() ? Nuvem.usuario().getUid() : "";
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (Nuvem.LinhaRanking l : linhas) {
            View v = inflater.inflate(R.layout.item_ranking, lista, false);
            TextView txtPos = v.findViewById(R.id.txtPosicao);
            txtPos.setText(String.valueOf(l.posicao));
            txtPos.setTextColor(cor(corDaPosicao(l.posicao)));
            ((TextView) v.findViewById(R.id.txtNomeRanking)).setText(
                    meuUid.equals(l.uid) ? l.nome + " (você)" : l.nome);
            ((TextView) v.findViewById(R.id.txtCarroRanking)).setText(l.carro);
            ((TextView) v.findViewById(R.id.txtValorRanking)).setText(formatar(l.valor));
            mostrarFoto(v.findViewById(R.id.imgFotoRanking), l.foto);

            if (l.posicao == 1) v.setBackgroundResource(R.drawable.bg_primeiro);
            else if (meuUid.equals(l.uid)) v.setBackgroundResource(R.drawable.bg_aviso);
            lista.addView(v);
        }
    }

    private void carregarMinhaPosicao(int minha) {
        txtVocePosicao.setText("—");
        txtVoceValor.setText("");
        txtVoceNome.setText("Você · " + Estado.nomePiloto(activity));
        txtVoceInfo.setText("Carregando…");

        Nuvem.minhaPosicao(categoria, (linha, erro) -> {
            if (minha != requisicao) return;
            if (linha == null) {
                txtVoceInfo.setText(erro != null ? "Sem conexão" : "Você ainda não está neste ranking");
                return;
            }
            txtVocePosicao.setText("#" + linha.posicao);
            txtVoceInfo.setText(linha.carro);
            txtVoceValor.setText(formatar(linha.valor));
        });
    }

    private String formatar(double valor) {
        return Nuvem.RANKING_VMAX.equals(categoria)
                ? String.format(Estado.LOCALE, "%.0f km/h", valor)
                : Estado.formatarTempo(valor);
    }

    private void mostrarFoto(ShapeableImageView img, String base64) {
        Bitmap foto = FotoCarro.deBase64(base64);
        if (foto != null) {
            ImageViewCompat.setImageTintList(img, null);
            img.setImageBitmap(foto);
        } else {
            img.setImageResource(R.drawable.ic_camera);
            ImageViewCompat.setImageTintList(img, ColorStateList.valueOf(cor(R.color.flz_ciano)));
            int p = Math.round(10 * activity.getResources().getDisplayMetrics().density);
            img.setContentPadding(p, p, p, p);
        }
    }

    private int corDaPosicao(int posicao) {
        if (posicao == 1) return R.color.flz_ciano;
        if (posicao == 2) return R.color.flz_laranja;
        if (posicao == 3) return R.color.flz_texto;
        return R.color.flz_texto_2;
    }

    private void pintarAbas() {
        pintarAba(aba0a100, Nuvem.RANKING_0A100.equals(categoria));
        pintarAba(abaVmax, Nuvem.RANKING_VMAX.equals(categoria));
        pintarAba(abaRotas, Nuvem.ehRota(categoria));
    }

    private void pintarAba(MaterialButton aba, boolean ativa) {
        aba.setBackgroundTintList(ColorStateList.valueOf(ativa
                ? cor(R.color.flz_ciano) : android.graphics.Color.TRANSPARENT));
        aba.setTextColor(cor(ativa ? R.color.flz_fundo : R.color.flz_texto_2));
        aba.setStrokeColor(ColorStateList.valueOf(cor(ativa ? R.color.flz_ciano : R.color.flz_ciano_borda)));
    }

    private int cor(int id) {
        return ContextCompat.getColor(activity, id);
    }
}
