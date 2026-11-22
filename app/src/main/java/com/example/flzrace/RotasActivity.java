package com.example.flzrace;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class RotasActivity extends AppCompatActivity {
    private LinearLayout listaRotas;
    private TextView txtVazio, txtInfo;
    private View progresso;
    private boolean demo;
    private Location minhaPosicao;

    private final ActivityResultLauncher<Intent> criarRota = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == RESULT_OK) carregar();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rotas);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rotas), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        listaRotas = findViewById(R.id.listaRotas);
        txtVazio = findViewById(R.id.txtRotasVazio);
        txtInfo = findViewById(R.id.txtRotasInfo);
        progresso = findViewById(R.id.progressoRotas);

        demo = Estado.modoDemoLigado(this);
        findViewById(R.id.faixaDemoRotas).setVisibility(demo ? View.VISIBLE : View.GONE);

        findViewById(R.id.btnVoltarRotas).setOnClickListener(v -> finish());
        findViewById(R.id.btnCriarRota).setOnClickListener(v ->
                criarRota.launch(new Intent(this, CriarRotaActivity.class)));

        minhaPosicao = ultimaPosicaoConhecida();
        carregar();
    }

    private void carregar() {
        listaRotas.removeAllViews();
        txtVazio.setVisibility(View.GONE);
        progresso.setVisibility(View.VISIBLE);

        Nuvem.carregarRotas((rotas, erro) -> {
            progresso.setVisibility(View.GONE);
            if (erro != null) {
                mostrarVazio("Não foi possível carregar as rotas: " + erro);
            } else if (rotas.isEmpty()) {
                mostrarVazio("Nenhuma rota ainda. Crie a primeira!");
            } else {
                preencher(rotas);
            }
        });
    }

    private void mostrarVazio(String texto) {
        txtVazio.setText(texto);
        txtVazio.setVisibility(View.VISIBLE);
    }

    private void preencher(List<Nuvem.Rota> rotas) {
        if (minhaPosicao != null) {
            rotas.sort((r1, r2) -> Double.compare(distanciaAteA(r1), distanciaAteA(r2)));
            txtInfo.setText("Mais próximas primeiro. Toque numa rota para correr nela.");
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Nuvem.Rota rota : rotas) {
            View item = inflater.inflate(R.layout.item_rota, listaRotas, false);
            ((TextView) item.findViewById(R.id.txtNomeRota)).setText(rota.nome);

            StringBuilder info = new StringBuilder(Estado.formatarDistancia(rota.distanciaM));
            if (minhaPosicao != null) {
                info.append(" · a ").append(Estado.formatarDistancia(distanciaAteA(rota)));
            }
            if (rota.criadorNome != null) info.append(" · por ").append(rota.criadorNome);
            ((TextView) item.findViewById(R.id.txtInfoRota)).setText(info.toString());

            item.setOnClickListener(v -> correr(rota));
            listaRotas.addView(item);
        }
    }

    private void correr(Nuvem.Rota rota) {
        Estado.rotaAtual = rota;
        startActivity(MedicaoActivity.criarIntent(this, Estado.MODO_AB, demo));
    }

    private double distanciaAteA(Nuvem.Rota rota) {
        float[] resultado = new float[1];
        Location.distanceBetween(minhaPosicao.getLatitude(), minhaPosicao.getLongitude(),
                rota.aLat, rota.aLon, resultado);
        return resultado[0];
    }

    private Location ultimaPosicaoConhecida() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return null;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location melhor = null;
        for (String provedor : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provedor);
                if (l != null && (melhor == null || l.getTime() > melhor.getTime())) melhor = l;
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        return melhor;
    }
}
