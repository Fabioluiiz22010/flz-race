package com.example.flzrace;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Estado {
    public interface Ouvinte {
        void onEstadoMudou();
    }

    public static final Locale LOCALE = new Locale("pt", "BR");

    public static final String MODO_ZERO100 = "zero100";
    public static final String MODO_LIVRE = "livre";
    public static final String MODO_AB = "ab";

    public static final int FASE_PARADA = 0;
    public static final int FASE_AGUARDANDO_PARADA = 1;
    public static final int FASE_PRONTO = 2;
    public static final int FASE_VALENDO = 3;
    public static final int FASE_CONCLUIDO = 4;

    public static String modo = MODO_ZERO100;
    public static boolean demo = false;
    public static int fase = FASE_PARADA;
    public static String status = "";

    public static float velocidadeKmh = 0f;
    public static float maxKmh = 0f;
    public static float mediaKmh = 0f;
    public static double distanciaM = 0;
    public static float aceleracaoG = 0f;
    public static float precisaoM = -1f;
    public static float tempo0a100 = -1f;

    public static long inicioArrancadaMs = -1;
    public static long tempoFinalMs = 0;

    public static boolean medicaoValida = true;
    public static String motivoInvalida = "";

    public static Nuvem.Rota rotaAtual = null;
    public static double distanciaAteAlvoM = -1;
    public static float tempoAB = -1f;

    public static List<float[]> amostras = new ArrayList<>();
    public static boolean novoRecorde = false;
    public static float recordeAnterior = -1f;

    private static final List<Ouvinte> ouvintes = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORICO = 30;

    private Estado() {
    }

    public static void observar(Ouvinte o) {
        ouvintes.add(o);
    }

    public static void remover(Ouvinte o) {
        ouvintes.remove(o);
    }

    public static void notificar() {
        for (Ouvinte o : ouvintes) o.onEstadoMudou();
    }

    public static void resetarMedicao(String novoModo, boolean modoDemo) {
        modo = novoModo;
        demo = modoDemo;
        fase = FASE_PARADA;
        status = "";
        velocidadeKmh = 0f;
        maxKmh = 0f;
        mediaKmh = 0f;
        distanciaM = 0;
        aceleracaoG = 0f;
        precisaoM = -1f;
        tempo0a100 = -1f;
        inicioArrancadaMs = -1;
        tempoFinalMs = 0;
        medicaoValida = true;
        motivoInvalida = "";
        amostras = new ArrayList<>();
        novoRecorde = false;
        recordeAnterior = -1f;
        distanciaAteAlvoM = -1;
        tempoAB = -1f;
    }

    public static String formatarTempo(double segundos) {
        if (segundos < 60) return String.format(LOCALE, "%.2f s", segundos);
        int minutos = (int) (segundos / 60);
        return String.format(LOCALE, "%d:%04.1f", minutos, segundos - minutos * 60);
    }

    public static String formatarDistancia(double metros) {
        return metros >= 1000
                ? String.format(LOCALE, "%.1f km", metros / 1000)
                : String.format(LOCALE, "%.0f m", metros);
    }

    public static void invalidar(String motivo) {
        if (!medicaoValida) return;
        medicaoValida = false;
        motivoInvalida = motivo;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("flzrace", Context.MODE_PRIVATE);
    }

    public static String nomePiloto(Context ctx) {
        return prefs(ctx).getString("piloto", "PILOTO");
    }

    public static String modeloCarro(Context ctx) {
        return prefs(ctx).getString("carro", "");
    }

    public static void salvarPerfil(Context ctx, String piloto, String carro) {
        prefs(ctx).edit().putString("piloto", piloto).putString("carro", carro).apply();
    }

    public static boolean cadastrado(Context ctx) {
        return prefs(ctx).contains("piloto") && aceitouAviso(ctx);
    }

    public static void limparDadosLocais(Context ctx) {
        prefs(ctx).edit().clear().apply();
        FotoCarro.apagar(ctx);
    }

    public static void restaurarRecordes(Context ctx, float melhor0a100, float melhorMax) {
        SharedPreferences.Editor editor = prefs(ctx).edit();
        if (melhor0a100 > 0) editor.putFloat("recorde0a100", melhor0a100);
        if (melhorMax > 0) editor.putFloat("recordeMax", melhorMax);
        editor.apply();
    }

    public static boolean aceitouAviso(Context ctx) {
        return prefs(ctx).getBoolean("aceitouAviso", false);
    }

    public static void marcarAvisoAceito(Context ctx) {
        prefs(ctx).edit().putBoolean("aceitouAviso", true).apply();
    }

    public static boolean modoDemoLigado(Context ctx) {
        return prefs(ctx).getBoolean("modoDemo", false);
    }

    public static void salvarModoDemo(Context ctx, boolean ligado) {
        prefs(ctx).edit().putBoolean("modoDemo", ligado).apply();
    }

    public static float recorde0a100(Context ctx) {
        return prefs(ctx).getFloat("recorde0a100", -1f);
    }

    public static float recordeMax(Context ctx) {
        return prefs(ctx).getFloat("recordeMax", -1f);
    }

    public static void registrarResultado(Context ctx) {
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor editor = p.edit();
        boolean contaRecorde = !demo && medicaoValida;
        boolean zero100 = MODO_ZERO100.equals(modo);

        boolean livre = MODO_LIVRE.equals(modo);
        if (zero100) recordeAnterior = p.getFloat("recorde0a100", -1f);
        else if (livre) recordeAnterior = p.getFloat("recordeMax", -1f);

        if (contaRecorde && tempo0a100 > 0) {
            float atual = p.getFloat("recorde0a100", -1f);
            if (atual < 0 || tempo0a100 < atual) {
                editor.putFloat("recorde0a100", tempo0a100);
                if (zero100) novoRecorde = true;
            }
        }
        if (contaRecorde && maxKmh > 0) {
            float atual = p.getFloat("recordeMax", -1f);
            if (maxKmh > atual) {
                editor.putFloat("recordeMax", maxKmh);
                if (livre) novoRecorde = true;
            }
        }

        try {
            JSONObject item = new JSONObject();
            item.put("modo", modo);
            item.put("data", System.currentTimeMillis());
            item.put("tempo0a100", tempo0a100);
            item.put("maxKmh", maxKmh);
            item.put("mediaKmh", mediaKmh);
            item.put("distanciaM", distanciaM);
            item.put("demo", demo);
            item.put("valida", medicaoValida);
            if (MODO_AB.equals(modo) && rotaAtual != null) {
                item.put("tempoAB", tempoAB);
                item.put("rota", rotaAtual.nome);
            }

            JSONArray antigo = new JSONArray(p.getString("historico", "[]"));
            JSONArray novo = new JSONArray();
            novo.put(item);
            for (int i = 0; i < antigo.length() && novo.length() < MAX_HISTORICO; i++) {
                novo.put(antigo.getJSONObject(i));
            }
            editor.putString("historico", novo.toString());
        } catch (Exception ignored) {
        }
        editor.apply();
    }

    public static JSONArray historico(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString("historico", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
