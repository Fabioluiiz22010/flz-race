package com.example.flzrace;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String EXTRA_ABA = "aba";
    public static final String EXTRA_CATEGORIA = "categoria";
    private static final int MAX_ITENS_INICIO = 5;
    private static final int LADO_AVATAR = 200;
    private static final int LADO_CAPA = 1024;

    private View telaInicio, telaAcelerar, telaRanking, telaPerfil;
    private BottomNavigationView navegacao;

    private TextView txtPiloto, txtRecorde0a100, txtRecordeMax, txtHistoricoVazio, txtPerfilNome;
    private LinearLayout listaHistorico;

    private TelaRanking telaRankingControle;
    private TextView txtPerfilConta;
    private ShapeableImageView imgAvatarInicio;
    private ImageView imgCapa;
    private View semFoto;
    private TextView txtPerfilCarro, txtPerfilMedicoes, txtPerfil0a100, txtPerfilMax, txtPerfilHistoricoVazio;
    private LinearLayout listaPerfilHistorico;

    private MaterialCardView cardDemo;
    private MaterialSwitch switchDemo;
    private TextView txtDemoTexto;

    private final ActivityResultLauncher<String[]> pedirPermissoes = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), resultado -> {
            });

    private final ActivityResultLauncher<Intent> abrirCamera = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == RESULT_OK) {
                    Nuvem.salvarPerfil(this);
                    atualizarInicio();
                    atualizarPerfil();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Nuvem.logado() || !Estado.cadastrado(this)) {
            startActivity(new Intent(this, BoasVindasActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        ligarViews();
        configurarNavegacao();
        configurarAcelerar();
        configurarPerfil();
        telaRankingControle = new TelaRanking(this, telaRanking);
        solicitarPermissoes();
        abrirAbaPedida(getIntent());
    }

    public static Intent criarIntentAba(Context ctx, int itemId) {
        return new Intent(ctx, MainActivity.class)
                .putExtra(EXTRA_ABA, itemId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        abrirAbaPedida(intent);
    }

    private void abrirAbaPedida(Intent intent) {
        String categoria = intent.getStringExtra(EXTRA_CATEGORIA);
        if (categoria != null) telaRankingControle.definirCategoria(categoria);

        int aba = intent.getIntExtra(EXTRA_ABA, 0);
        if (aba == 0) return;
        if (navegacao.getSelectedItemId() == aba && aba == R.id.nav_ranking) {
            telaRankingControle.carregar();
        } else {
            navegacao.setSelectedItemId(aba);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarInicio();
        atualizarPerfil();
    }

    private void ligarViews() {
        telaInicio = findViewById(R.id.telaInicio);
        telaAcelerar = findViewById(R.id.telaAcelerar);
        telaRanking = findViewById(R.id.telaRanking);
        telaPerfil = findViewById(R.id.telaPerfil);
        navegacao = findViewById(R.id.navegacao);

        txtPiloto = findViewById(R.id.txtPiloto);
        txtRecorde0a100 = findViewById(R.id.txtRecorde0a100);
        txtRecordeMax = findViewById(R.id.txtRecordeMax);
        txtHistoricoVazio = findViewById(R.id.txtHistoricoVazio);
        listaHistorico = findViewById(R.id.listaHistorico);
        txtPerfilNome = findViewById(R.id.txtPerfilNome);
        imgAvatarInicio = findViewById(R.id.imgAvatarInicio);

        imgCapa = findViewById(R.id.imgCapa);
        semFoto = findViewById(R.id.semFoto);
        txtPerfilCarro = findViewById(R.id.txtPerfilCarro);
        txtPerfilConta = findViewById(R.id.txtPerfilConta);
        txtPerfilMedicoes = findViewById(R.id.txtPerfilMedicoes);
        txtPerfil0a100 = findViewById(R.id.txtPerfil0a100);
        txtPerfilMax = findViewById(R.id.txtPerfilMax);
        txtPerfilHistoricoVazio = findViewById(R.id.txtPerfilHistoricoVazio);
        listaPerfilHistorico = findViewById(R.id.listaPerfilHistorico);

        cardDemo = findViewById(R.id.cardDemo);
        switchDemo = findViewById(R.id.switchDemo);
        txtDemoTexto = findViewById(R.id.txtDemoTexto);
    }

    private void configurarNavegacao() {
        navegacao.setOnItemSelectedListener(item -> {
            mostrarTela(item.getItemId());
            return true;
        });

        findViewById(R.id.btnIniciar).setOnClickListener(v ->
                navegacao.setSelectedItemId(R.id.nav_acelerar));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navegacao.getSelectedItemId() != R.id.nav_inicio) {
                    navegacao.setSelectedItemId(R.id.nav_inicio);
                } else {
                    finish();
                }
            }
        });
    }

    private void mostrarTela(int itemId) {
        telaInicio.setVisibility(itemId == R.id.nav_inicio ? View.VISIBLE : View.GONE);
        telaAcelerar.setVisibility(itemId == R.id.nav_acelerar ? View.VISIBLE : View.GONE);
        telaRanking.setVisibility(itemId == R.id.nav_ranking ? View.VISIBLE : View.GONE);
        telaPerfil.setVisibility(itemId == R.id.nav_perfil ? View.VISIBLE : View.GONE);
        if (itemId == R.id.nav_inicio) atualizarInicio();
        if (itemId == R.id.nav_ranking) telaRankingControle.carregar();
    }

    private void configurarAcelerar() {
        switchDemo.setChecked(Estado.modoDemoLigado(this));
        atualizarCardDemo();
        switchDemo.setOnCheckedChangeListener((botao, ligado) -> {
            Estado.salvarModoDemo(this, ligado);
            atualizarCardDemo();
        });

        findViewById(R.id.cardZero100).setOnClickListener(v -> abrirMedicao(Estado.MODO_ZERO100));
        findViewById(R.id.cardLivre).setOnClickListener(v -> abrirMedicao(Estado.MODO_LIVRE));
        findViewById(R.id.cardAB).setOnClickListener(v -> {
            if (!switchDemo.isChecked() && !temPermissao(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Permita a localização precisa para correr nas rotas",
                        Toast.LENGTH_LONG).show();
                solicitarPermissoes();
                return;
            }
            startActivity(new Intent(this, RotasActivity.class));
        });
    }

    private void atualizarCardDemo() {
        boolean ligado = switchDemo.isChecked();
        txtDemoTexto.setText(ligado
                ? "Ligado: corrida simulada para apresentar em aula, sem carro."
                : "Desligado: medições reais com GPS.");
        cardDemo.setStrokeColor(ContextCompat.getColor(this,
                ligado ? R.color.flz_roxo : R.color.flz_ciano_borda));
    }

    private void abrirMedicao(String modo) {
        boolean demo = switchDemo.isChecked();
        if (!demo && !temPermissao(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(this, "Permita a localização precisa para medir com GPS",
                    Toast.LENGTH_LONG).show();
            solicitarPermissoes();
            return;
        }
        startActivity(MedicaoActivity.criarIntent(this, modo, demo));
    }

    private void atualizarInicio() {
        String piloto = Estado.nomePiloto(this);
        txtPiloto.setText(piloto);
        txtPerfilNome.setText(piloto);

        float recorde0a100 = Estado.recorde0a100(this);
        float recordeMax = Estado.recordeMax(this);
        txtRecorde0a100.setText(recorde0a100 > 0
                ? String.format(Estado.LOCALE, "%.2f s", recorde0a100) : "—");
        txtRecordeMax.setText(recordeMax > 0
                ? String.format(Estado.LOCALE, "%.0f km/h", recordeMax) : "—");

        preencherHistorico(listaHistorico, txtHistoricoVazio, MAX_ITENS_INICIO);

        Bitmap avatar = FotoCarro.carregar(this, LADO_AVATAR);
        if (avatar != null) {
            ImageViewCompat.setImageTintList(imgAvatarInicio, null);
            imgAvatarInicio.setImageBitmap(avatar);
            imgAvatarInicio.setContentPadding(0, 0, 0, 0);
        } else {
            imgAvatarInicio.setImageResource(R.drawable.ic_camera);
            ImageViewCompat.setImageTintList(imgAvatarInicio,
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.flz_laranja)));
            int p = Math.round(14 * getResources().getDisplayMetrics().density);
            imgAvatarInicio.setContentPadding(p, p, p, p);
        }
    }

    private void preencherHistorico(LinearLayout lista, TextView vazio, int maximo) {
        lista.removeAllViews();
        JSONArray historico = Estado.historico(this);
        vazio.setVisibility(historico.length() == 0 ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < historico.length() && i < maximo; i++) {
            JSONObject item = historico.optJSONObject(i);
            if (item == null) continue;
            View linha = inflater.inflate(R.layout.item_historico, lista, false);
            preencherLinha(linha, item);
            lista.addView(linha);
        }
    }

    private void configurarPerfil() {
        imgAvatarInicio.setOnClickListener(v -> navegacao.setSelectedItemId(R.id.nav_perfil));
        findViewById(R.id.btnTrocarFoto).setOnClickListener(v ->
                abrirCamera.launch(new Intent(this, CameraActivity.class)));
        findViewById(R.id.btnEditarPerfil).setOnClickListener(v ->
                startActivity(BoasVindasActivity.criarIntentEdicao(this)));
        findViewById(R.id.btnSair).setOnClickListener(v -> confirmarSaida());
    }

    private void confirmarSaida() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sair da conta?")
                .setMessage("Seu perfil e seus recordes do ranking ficam salvos na nuvem e voltam "
                        + "quando você entrar de novo. O histórico deste aparelho será apagado.")
                .setPositiveButton("Sair", (d, w) -> {
                    Nuvem.sair();
                    Estado.limparDadosLocais(this);
                    startActivity(new Intent(this, BoasVindasActivity.class));
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void atualizarPerfil() {
        txtPerfilNome.setText(Estado.nomePiloto(this));
        txtPerfilCarro.setText(Estado.modeloCarro(this));
        String email = Nuvem.logado() ? Nuvem.usuario().getEmail() : null;
        txtPerfilConta.setText(email != null ? "Conectado como " + email : "");

        Bitmap capa = FotoCarro.carregar(this, LADO_CAPA);
        imgCapa.setImageBitmap(capa);
        semFoto.setVisibility(capa == null ? View.VISIBLE : View.GONE);

        float recorde0a100 = Estado.recorde0a100(this);
        float recordeMax = Estado.recordeMax(this);
        txtPerfilMedicoes.setText(String.valueOf(Estado.historico(this).length()));
        txtPerfil0a100.setText(recorde0a100 > 0
                ? String.format(Estado.LOCALE, "%.2f s", recorde0a100) : "—");
        txtPerfilMax.setText(recordeMax > 0
                ? String.format(Estado.LOCALE, "%.0f", recordeMax) : "—");

        preencherHistorico(listaPerfilHistorico, txtPerfilHistoricoVazio, Integer.MAX_VALUE);
    }

    private void preencherLinha(View linha, JSONObject item) {
        String modo = item.optString("modo");
        boolean zero100 = Estado.MODO_ZERO100.equals(modo);
        boolean trajeto = Estado.MODO_AB.equals(modo);
        boolean demo = item.optBoolean("demo");
        int cor = ContextCompat.getColor(this, demo ? R.color.flz_roxo
                : zero100 ? R.color.flz_ciano : trajeto ? R.color.flz_laranja : R.color.flz_texto_2);

        TextView txtTipo = linha.findViewById(R.id.txtTipo);
        TextView txtDescricao = linha.findViewById(R.id.txtDescricao);
        TextView txtValor = linha.findViewById(R.id.txtValor);
        linha.findViewById(R.id.barra).setBackgroundColor(cor);
        txtTipo.setTextColor(cor);
        txtTipo.setText(zero100 ? "0-100" : trajeto ? "A→B" : "LIVRE");

        String data = DateFormat.format("dd/MM HH:mm", item.optLong("data")).toString();
        if (trajeto && item.has("rota")) data = item.optString("rota") + " · " + data;
        txtDescricao.setText(demo ? data + " · demo" : data);

        float tempo = (float) item.optDouble("tempo0a100", -1);
        float tempoAB = (float) item.optDouble("tempoAB", -1);
        float max = (float) item.optDouble("maxKmh", 0);
        if (trajeto) {
            txtValor.setText(tempoAB > 0 ? Estado.formatarTempo(tempoAB) : "—");
        } else if (zero100 && tempo > 0) {
            txtValor.setText(String.format(Estado.LOCALE, "%.2f s", tempo));
        } else {
            txtValor.setText(String.format(Estado.LOCALE, "%.0f km/h", max));
        }
    }

    private void solicitarPermissoes() {
        List<String> faltando = new ArrayList<>();
        String[] necessarias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};
        for (String p : necessarias) {
            if (!temPermissao(p)) faltando.add(p);
        }
        if (!faltando.isEmpty()) pedirPermissoes.launch(faltando.toArray(new String[0]));
    }

    private boolean temPermissao(String permissao) {
        return ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED;
    }
}
