package com.example.flzrace;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class MedicaoService extends Service
        implements ArrancadaDetector.Listener, SimuladorMedicao.Listener {
    public static final String ACAO_INICIAR = "iniciar";
    public static final String ACAO_ENCERRAR = "encerrar";
    public static final String ACAO_CANCELAR = "cancelar";
    private static final String EXTRA_MODO = "modo";
    private static final String EXTRA_DEMO = "demo";

    private static final String CANAL = "medicao";
    private static final int ID_NOTIFICACAO = 1;

    private static final float VELOCIDADE_PARADO_KMH = 2f;
    private static final long TEMPO_PARADO_MS = 1000;
    private static final float PRECISAO_MAXIMA_M = 20f;
    private static final double DESCIDA_MAXIMA_M = 3.0;

    public static void iniciar(Context ctx, String modo, boolean demo) {
        Intent i = new Intent(ctx, MedicaoService.class)
                .setAction(ACAO_INICIAR)
                .putExtra(EXTRA_MODO, modo)
                .putExtra(EXTRA_DEMO, demo);
        ctx.startService(i);
    }

    public static void comando(Context ctx, String acao) {
        ctx.startService(new Intent(ctx, MedicaoService.class).setAction(acao));
    }

    private LocationManager locationManager;
    private ArrancadaDetector detector;
    private SimuladorMedicao simulador;
    private final CalculadoraVelocidade calculadora = new CalculadoraVelocidade();

    private Location ultimaLocalizacao;
    private long inicioParadoMs = -1;
    private double altitudeLargada = Double.NaN;
    private boolean gpsAtivo = false;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        detector = new ArrancadaDetector(this, this);
        simulador = new SimuladorMedicao(this);
        criarCanal();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String acao = intent != null ? intent.getAction() : null;
        if (ACAO_INICIAR.equals(acao)) {
            iniciarMedicao(intent.getStringExtra(EXTRA_MODO), intent.getBooleanExtra(EXTRA_DEMO, false));
        } else if (ACAO_ENCERRAR.equals(acao)) {
            boolean trajeto = Estado.MODO_AB.equals(Estado.modo);
            if (Estado.fase == Estado.FASE_VALENDO && !trajeto) concluir();
            else cancelar();
        } else if (ACAO_CANCELAR.equals(acao)) {
            cancelar();
        }
        return START_NOT_STICKY;
    }

    private void iniciarMedicao(String modo, boolean demo) {
        pararTudo();
        Estado.resetarMedicao(modo == null ? Estado.MODO_ZERO100 : modo, demo);
        ultimaLocalizacao = null;
        inicioParadoMs = -1;
        altitudeLargada = Double.NaN;

        if (temPermissaoLocalizacao()) {
            startForeground(ID_NOTIFICACAO, notificacao("Medição em andamento"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        }

        if (!detector.iniciar()) {
            Estado.status = "Este aparelho não tem acelerômetro";
            Estado.notificar();
            return;
        }

        boolean trajeto = Estado.MODO_AB.equals(Estado.modo);
        if (trajeto && Estado.rotaAtual == null) {
            Estado.status = "ROTA NÃO ENCONTRADA";
            Estado.notificar();
            return;
        }

        if (demo) {
            Estado.fase = Estado.FASE_PRONTO;
            Estado.status = "SACUDA PARA LARGAR";
            if (trajeto) Estado.distanciaAteAlvoM = Estado.rotaAtual.distanciaM;
            detector.armar(true);
        } else if (trajeto) {
            Estado.fase = Estado.FASE_AGUARDANDO_PARADA;
            Estado.status = "VÁ ATÉ O PONTO A";
            iniciarGps();
        } else {
            Estado.fase = Estado.FASE_AGUARDANDO_PARADA;
            Estado.status = "AGUARDANDO PARADA";
            iniciarGps();
        }
        Estado.notificar();
    }

    private void iniciarGps() {
        if (!temPermissaoLocalizacao()) {
            Estado.status = "SEM PERMISSÃO DE GPS";
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f,
                    ouvinteGps, Looper.getMainLooper());
            gpsAtivo = true;
        } catch (SecurityException | IllegalArgumentException e) {
            Estado.status = "GPS INDISPONÍVEL";
        }
    }

    private final LocationListener ouvinteGps = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location local) {
            processarLocalizacao(local);
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Estado.status = "LIGUE O GPS";
            Estado.notificar();
        }
    };

    private void processarLocalizacao(Location local) {
        long tempoMs = local.getElapsedRealtimeNanos() / 1_000_000L;
        float kmh = local.hasSpeed() ? local.getSpeed() * 3.6f : 0f;

        Estado.precisaoM = local.hasAccuracy() ? local.getAccuracy() : -1f;
        Estado.velocidadeKmh = kmh;

        boolean falsa = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? local.isMock() : local.isFromMockProvider();
        if (falsa) Estado.invalidar("Localização falsa detectada");

        if (Estado.MODO_AB.equals(Estado.modo)) {
            processarTrajeto(local, tempoMs, kmh);
            ultimaLocalizacao = local;
            Estado.notificar();
            return;
        }

        switch (Estado.fase) {
            case Estado.FASE_AGUARDANDO_PARADA:
                if (kmh < VELOCIDADE_PARADO_KMH) {
                    if (inicioParadoMs < 0) {
                        inicioParadoMs = tempoMs;
                    } else if (tempoMs - inicioParadoMs >= TEMPO_PARADO_MS) {
                        Estado.fase = Estado.FASE_PRONTO;
                        Estado.status = "PRONTO! ACELERE";
                        detector.armar(false);
                        vibrar(80);
                    }
                } else {
                    inicioParadoMs = -1;
                }
                break;

            case Estado.FASE_VALENDO:
                if (Estado.precisaoM > PRECISAO_MAXIMA_M) Estado.invalidar("Sinal de GPS impreciso");
                double delta = ultimaLocalizacao != null ? ultimaLocalizacao.distanceTo(local) : 0;
                calculadora.adicionar(tempoMs, kmh, delta);
                publicarCalculos(tempoMs);
                verificarDescida(local);
                verificarFim();
                break;

            default:
                break;
        }

        ultimaLocalizacao = local;
        Estado.notificar();
    }

    private void processarTrajeto(Location local, long tempoMs, float kmh) {
        Nuvem.Rota rota = Estado.rotaAtual;
        double distA = distancia(local, rota.aLat, rota.aLon);
        double distB = distancia(local, rota.bLat, rota.bLon);

        switch (Estado.fase) {
            case Estado.FASE_AGUARDANDO_PARADA:
                Estado.distanciaAteAlvoM = distA;
                if (distA <= rota.raioM) {
                    Estado.fase = Estado.FASE_PRONTO;
                    Estado.status = "NO PONTO A · PODE IR";
                    vibrar(80);
                }
                break;

            case Estado.FASE_PRONTO:
                Estado.distanciaAteAlvoM = distB;
                if (distA > rota.raioM) {
                    long inicio = tempoMs;
                    if (ultimaLocalizacao != null) {
                        double antes = distancia(ultimaLocalizacao, rota.aLat, rota.aLon);
                        long tAntes = ultimaLocalizacao.getElapsedRealtimeNanos() / 1_000_000L;
                        if (distA > antes) {
                            double fracao = (rota.raioM - antes) / (distA - antes);
                            inicio = tAntes + Math.round(Math.max(0, fracao) * (tempoMs - tAntes));
                        }
                    }
                    Estado.fase = Estado.FASE_VALENDO;
                    Estado.status = "VALENDO! RUMO AO B";
                    Estado.inicioArrancadaMs = inicio;
                    calculadora.iniciar(inicio);
                    calculadora.adicionar(tempoMs, kmh, Math.max(0, distA - rota.raioM));
                    publicarCalculos(tempoMs);
                    vibrar(150);
                }
                break;

            case Estado.FASE_VALENDO:
                if (Estado.precisaoM > PRECISAO_MAXIMA_M) Estado.invalidar("Sinal de GPS impreciso");
                double delta = ultimaLocalizacao != null ? ultimaLocalizacao.distanceTo(local) : 0;
                calculadora.adicionar(tempoMs, kmh, delta);
                publicarCalculos(tempoMs);
                Estado.distanciaAteAlvoM = distB;

                if (distB <= rota.raioM) {
                    long fim = tempoMs;
                    if (ultimaLocalizacao != null) {
                        double antes = distancia(ultimaLocalizacao, rota.bLat, rota.bLon);
                        long tAntes = ultimaLocalizacao.getElapsedRealtimeNanos() / 1_000_000L;
                        if (antes > distB) {
                            double fracao = (antes - rota.raioM) / (antes - distB);
                            fim = tAntes + Math.round(Math.max(0, fracao) * (tempoMs - tAntes));
                        }
                    }
                    Estado.tempoAB = (fim - Estado.inicioArrancadaMs) / 1000f;
                    concluir();
                }
                break;

            default:
                break;
        }
    }

    private static double distancia(Location local, double lat, double lon) {
        float[] resultado = new float[1];
        Location.distanceBetween(local.getLatitude(), local.getLongitude(), lat, lon, resultado);
        return resultado[0];
    }

    private void verificarDescida(Location local) {
        if (Double.isNaN(altitudeLargada) || !local.hasAltitude()) return;
        if (altitudeLargada - local.getAltitude() > DESCIDA_MAXIMA_M) {
            Estado.invalidar("Arrancada em descida");
        }
    }

    @Override
    public void onArrancada(long tempoMs) {
        if (Estado.fase != Estado.FASE_PRONTO) return;
        Estado.fase = Estado.FASE_VALENDO;
        Estado.status = "VALENDO!";
        Estado.inicioArrancadaMs = tempoMs;
        calculadora.iniciar(tempoMs);
        if (ultimaLocalizacao != null && ultimaLocalizacao.hasAltitude()) {
            altitudeLargada = ultimaLocalizacao.getAltitude();
        }
        if (Estado.demo) {
            if (Estado.MODO_AB.equals(Estado.modo)) {
                Estado.status = "VALENDO! RUMO AO B";
                simulador.iniciar(tempoMs, SimuladorMedicao.TRAJETO_VEL_FINAL, SimuladorMedicao.TRAJETO_TAU_S);
            } else {
                simulador.iniciar(tempoMs);
            }
        }
        vibrar(150);
        Estado.notificar();
    }

    @Override
    public void onAceleracao(float g) {
        Estado.aceleracaoG = g;
        Estado.notificar();
    }

    @Override
    public void onVelocidadeSimulada(long tempoMs, float kmh, double deltaDistanciaM) {
        if (Estado.fase != Estado.FASE_VALENDO) return;
        Estado.velocidadeKmh = kmh;
        calculadora.adicionar(tempoMs, kmh, deltaDistanciaM);
        publicarCalculos(tempoMs);

        if (Estado.MODO_AB.equals(Estado.modo) && Estado.rotaAtual != null) {
            double restante = Estado.rotaAtual.distanciaM - calculadora.getDistanciaM();
            Estado.distanciaAteAlvoM = Math.max(0, restante);
            if (restante <= 0) {
                double metrosPorSegundo = kmh / 3.6;
                long fim = metrosPorSegundo > 0
                        ? tempoMs - Math.round(-restante / metrosPorSegundo * 1000)
                        : tempoMs;
                Estado.tempoAB = (fim - Estado.inicioArrancadaMs) / 1000f;
                concluir();
                return;
            }
        }

        verificarFim();
        Estado.notificar();
    }

    private void publicarCalculos(long agoraMs) {
        Estado.maxKmh = calculadora.getMaxKmh();
        Estado.distanciaM = calculadora.getDistanciaM();
        Estado.mediaKmh = calculadora.getMediaKmh(agoraMs);
        Estado.tempo0a100 = calculadora.getTempo0a100();
    }

    private void verificarFim() {
        if (Estado.MODO_ZERO100.equals(Estado.modo) && calculadora.getTempo0a100() > 0) {
            concluir();
        }
    }

    private void concluir() {
        if (Estado.fase != Estado.FASE_VALENDO) return;
        long agora = SystemClock.elapsedRealtime();
        publicarCalculos(agora);
        if (Estado.MODO_ZERO100.equals(Estado.modo) && Estado.tempo0a100 > 0) {
            Estado.tempoFinalMs = (long) (Estado.tempo0a100 * 1000);
        } else if (Estado.MODO_AB.equals(Estado.modo) && Estado.tempoAB > 0) {
            Estado.tempoFinalMs = (long) (Estado.tempoAB * 1000);
        } else {
            Estado.tempoFinalMs = agora - Estado.inicioArrancadaMs;
        }
        Estado.amostras = calculadora.getAmostras();
        Estado.fase = Estado.FASE_CONCLUIDO;
        Estado.status = "CONCLUÍDO";
        pararTudo();
        Estado.registrarResultado(this);
        vibrar(300);
        Estado.notificar();
        encerrarServico();
    }

    private void cancelar() {
        pararTudo();
        Estado.fase = Estado.FASE_PARADA;
        Estado.status = "";
        Estado.notificar();
        encerrarServico();
    }

    private void pararTudo() {
        detector.parar();
        simulador.parar();
        if (gpsAtivo) {
            locationManager.removeUpdates(ouvinteGps);
            gpsAtivo = false;
        }
    }

    private void encerrarServico() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        pararTudo();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean temPermissaoLocalizacao() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void criarCanal() {
        NotificationChannel canal = new NotificationChannel(CANAL, "Medição",
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(canal);
    }

    private Notification notificacao(String texto) {
        Intent abrir = new Intent(this, MedicaoActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, abrir,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_acelerar)
                .setContentTitle("FLZ RACE")
                .setContentText(texto)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void vibrar(long ms) {
        Vibrator vibrador = getSystemService(Vibrator.class);
        if (vibrador != null && vibrador.hasVibrator()) {
            vibrador.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
