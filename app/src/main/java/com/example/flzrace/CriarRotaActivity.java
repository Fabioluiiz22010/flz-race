package com.example.flzrace;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.DashPathEffect;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.Arrays;

public class CriarRotaActivity extends AppCompatActivity {
    private static final String USER_AGENT =
            "FLZRACE/1.0 (Android; projeto academico UniSALESIANO; github.com/Fabioluiiz22010)";
    private static final double ZOOM_INICIAL = 17.0;

    private static final GeoPoint PADRAO = new GeoPoint(-21.2089, -50.4328);

    private MapView mapa;
    private TextView txtDica, txtDistancia;
    private TextInputLayout campoNome;
    private TextInputEditText edtNome;
    private MaterialButton btnPublicar;

    private GeoPoint pontoA, pontoB;
    private double raioM = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(USER_AGENT);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_criar_rota);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.criarRota), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        mapa = findViewById(R.id.mapa);
        txtDica = findViewById(R.id.txtDicaMapa);
        txtDistancia = findViewById(R.id.txtDistanciaRota);
        campoNome = findViewById(R.id.campoNomeRota);
        edtNome = findViewById(R.id.edtNomeRota);
        btnPublicar = findViewById(R.id.btnPublicarRota);

        configurarMapa();

        MaterialButtonToggleGroup grupoRaio = findViewById(R.id.grupoRaio);
        grupoRaio.addOnButtonCheckedListener((grupo, id, marcado) -> {
            if (!marcado) return;
            raioM = id == R.id.raio15 ? 15 : id == R.id.raio50 ? 50 : 25;
            redesenhar();
        });

        findViewById(R.id.btnVoltarCriar).setOnClickListener(v -> finish());
        findViewById(R.id.btnLimparRota).setOnClickListener(v -> {
            pontoA = null;
            pontoB = null;
            redesenhar();
        });
        btnPublicar.setOnClickListener(v -> publicar());
    }

    private XYTileSource criarMapaCarto() {
        String chave = getString(R.string.carto_api_key).trim();

        return new XYTileSource("FLZRACE-CARTO-DARK-K1", 0, 20, 256, ".png",
                new String[]{"https://basemaps.cartocdn.com/rastertiles/dark_all/"},
                "© OpenStreetMap contributors, © CARTO") {
            @Override
            public String getTileURLString(long indice) {
                String url = getBaseUrl() + MapTileIndex.getZoom(indice) + "/"
                        + MapTileIndex.getX(indice) + "/" + MapTileIndex.getY(indice) + mImageFilenameEnding;
                return chave.isEmpty() ? url : url + "?key=" + chave;
            }
        };
    }

    private void configurarMapa() {
        mapa.setTileSource(criarMapaCarto());
        mapa.setMultiTouchControls(true);
        mapa.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        IMapController controle = mapa.getController();
        controle.setZoom(ZOOM_INICIAL);
        controle.setCenter(posicaoInicial());

        mapa.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                tocarNoMapa(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));
    }

    private void tocarNoMapa(GeoPoint p) {
        if (pontoA == null) {
            pontoA = p;
        } else if (pontoB == null) {
            pontoB = p;
        } else {
            pontoA = p;
            pontoB = null;
        }
        redesenhar();
    }

    private void redesenhar() {
        mapa.getOverlays().removeIf(o -> o instanceof Marker || o instanceof Polygon || o instanceof Polyline);

        if (pontoA != null) desenharPonto(pontoA, R.color.flz_ciano, R.drawable.marcador_a, "Largada (A)");
        if (pontoB != null) desenharPonto(pontoB, R.color.flz_laranja, R.drawable.marcador_b, "Chegada (B)");

        if (pontoA != null && pontoB != null) {
            Polyline linha = new Polyline(mapa);
            linha.setPoints(Arrays.asList(pontoA, pontoB));
            linha.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.flz_ciano));
            linha.getOutlinePaint().setStrokeWidth(6f);
            linha.getOutlinePaint().setPathEffect(new DashPathEffect(new float[]{24f, 16f}, 0));
            mapa.getOverlays().add(1, linha);

            double distancia = pontoA.distanceToAsDouble(pontoB);
            txtDistancia.setText("Distância em linha reta: " + Estado.formatarDistancia(distancia));
            txtDica.setText("Pronto! Toque de novo para recomeçar");
        } else {
            txtDistancia.setText("Distância: marque A e B no mapa");
            txtDica.setText(pontoA == null
                    ? "Toque no mapa para marcar a LARGADA (A)"
                    : "Agora toque para marcar a CHEGADA (B)");
        }
        mapa.invalidate();
    }

    private void desenharPonto(GeoPoint ponto, int cor, int icone, String titulo) {
        int corInt = ContextCompat.getColor(this, cor);

        Polygon circulo = new Polygon(mapa);
        circulo.setPoints(Polygon.pointsAsCircle(ponto, raioM));
        circulo.getFillPaint().setColor((corInt & 0x00FFFFFF) | 0x33000000);
        circulo.getOutlinePaint().setColor(corInt);
        circulo.getOutlinePaint().setStrokeWidth(3f);
        circulo.setOnClickListener((p, m, g) -> false);
        mapa.getOverlays().add(1, circulo);

        Marker marcador = new Marker(mapa);
        marcador.setPosition(ponto);
        marcador.setIcon(ContextCompat.getDrawable(this, icone));
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marcador.setTitle(titulo);
        marcador.setOnMarkerClickListener((m, map) -> true);
        mapa.getOverlays().add(marcador);
    }

    private void publicar() {
        String nome = edtNome.getText() == null ? "" : edtNome.getText().toString().trim();
        campoNome.setError(null);
        if (pontoA == null || pontoB == null) {
            Toast.makeText(this, "Marque os pontos A e B no mapa", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nome.length() < 3) {
            campoNome.setError("Dê um nome para a rota");
            edtNome.requestFocus();
            return;
        }
        double distancia = pontoA.distanceToAsDouble(pontoB);
        if (distancia < raioM * 2 + 20) {
            Toast.makeText(this, "A e B estão perto demais para esse raio de chegada",
                    Toast.LENGTH_LONG).show();
            return;
        }

        btnPublicar.setEnabled(false);
        btnPublicar.setText("PUBLICANDO…");
        Nuvem.criarRota(this, nome, pontoA.getLatitude(), pontoA.getLongitude(),
                pontoB.getLatitude(), pontoB.getLongitude(), raioM, distancia, (id, erro) -> {
                    if (id == null) {
                        btnPublicar.setEnabled(true);
                        btnPublicar.setText("PUBLICAR ROTA");
                        Toast.makeText(this, "Não foi possível publicar: " + erro, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, "Rota publicada!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
    }

    private GeoPoint posicaoInicial() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return PADRAO;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location melhor = null;
        for (String provedor : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provedor);
                if (l != null && (melhor == null || l.getTime() > melhor.getTime())) melhor = l;
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        return melhor != null ? new GeoPoint(melhor.getLatitude(), melhor.getLongitude()) : PADRAO;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapa.onResume();
    }

    @Override
    protected void onPause() {
        mapa.onPause();
        super.onPause();
    }
}
