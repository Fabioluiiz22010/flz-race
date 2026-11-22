package com.example.flzrace;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Nuvem {
    public interface Callback<T> {
        void onResultado(T valor, String erro);
    }

    public static final String RANKING_0A100 = "ranking_0a100";
    public static final String RANKING_VMAX = "ranking_vmax";
    private static final String USUARIOS = "usuarios";
    private static final String ROTAS = "rotas";
    private static final int LIMITE_ROTAS = 100;
    private static final int LIMITE_RANKING = 50;
    private static final int LADO_FOTO_RANKING = 96;

    public static class PerfilRemoto {
        public String nome, carro, foto;
        public boolean aceitouAviso;
        public double melhor0a100 = -1, melhorMax = -1;
    }

    public static class LinhaRanking {
        public String uid, nome, carro, foto;
        public double valor;
        public int posicao;
    }

    public static class Rota {
        public String id, nome, criadorUid, criadorNome;
        public double aLat, aLon, bLat, bLon;
        public double raioM, distanciaM;

        public String categoriaRanking() {
            return categoriaRota(id);
        }
    }

    public static class ResultadoEnvio {
        public boolean melhorou;
        public double valorNoRanking;
        public int posicao;
    }

    private Nuvem() {
    }

    public static boolean menorEhMelhor(String categoria) {
        return !RANKING_VMAX.equals(categoria);
    }

    public static String categoriaRota(String rotaId) {
        return ROTAS + "/" + rotaId + "/ranking";
    }

    public static boolean ehRota(String categoria) {
        return categoria != null && categoria.startsWith(ROTAS + "/");
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    public static FirebaseUser usuario() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    public static boolean logado() {
        return usuario() != null;
    }

    public static void entrarComGoogle(String idToken, Callback<FirebaseUser> cb) {
        AuthCredential credencial = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credencial)
                .addOnSuccessListener(r -> cb.onResultado(r.getUser(), null))
                .addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    public static void sair() {
        FirebaseAuth.getInstance().signOut();
    }

    public static void carregarPerfil(Callback<PerfilRemoto> cb) {
        FirebaseUser u = usuario();
        if (u == null) {
            cb.onResultado(null, "Você não está conectado");
            return;
        }
        db().collection(USUARIOS).document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        cb.onResultado(null, null);
                        return;
                    }
                    PerfilRemoto p = new PerfilRemoto();
                    p.nome = doc.getString("nome");
                    p.carro = doc.getString("carro");
                    p.foto = doc.getString("foto");
                    Boolean aceitou = doc.getBoolean("aceitouAviso");
                    p.aceitouAviso = aceitou != null && aceitou;
                    buscarMeusRecordes(u.getUid(), p, cb);
                })
                .addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    private static void buscarMeusRecordes(String uid, PerfilRemoto p, Callback<PerfilRemoto> cb) {
        Task<DocumentSnapshot> t0a100 = db().collection(RANKING_0A100).document(uid).get();
        Task<DocumentSnapshot> tMax = db().collection(RANKING_VMAX).document(uid).get();
        Tasks.whenAllComplete(t0a100, tMax).addOnCompleteListener(x -> {
            if (t0a100.isSuccessful() && t0a100.getResult().exists()) {
                Double v = t0a100.getResult().getDouble("valor");
                if (v != null) p.melhor0a100 = v;
            }
            if (tMax.isSuccessful() && tMax.getResult().exists()) {
                Double v = tMax.getResult().getDouble("valor");
                if (v != null) p.melhorMax = v;
            }
            cb.onResultado(p, null);
        });
    }

    public static void salvarPerfil(Context ctx) {
        FirebaseUser u = usuario();
        if (u == null) return;

        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", Estado.nomePiloto(ctx));
        dados.put("carro", Estado.modeloCarro(ctx));
        dados.put("foto", FotoCarro.miniaturaBase64(ctx));
        dados.put("aceitouAviso", true);
        dados.put("atualizadoEm", FieldValue.serverTimestamp());
        db().collection(USUARIOS).document(u.getUid()).set(dados, SetOptions.merge());

        Map<String, Object> publicos = dadosPublicos(ctx);
        for (String categoria : new String[]{RANKING_0A100, RANKING_VMAX}) {
            db().collection(categoria).document(u.getUid()).update(publicos)
                    .addOnFailureListener(e -> {
                    });
        }
    }

    private static Map<String, Object> dadosPublicos(Context ctx) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", Estado.nomePiloto(ctx));
        dados.put("carro", Estado.modeloCarro(ctx));
        dados.put("foto", FotoCarro.miniaturaBase64(ctx, LADO_FOTO_RANKING));
        return dados;
    }

    public static void enviarResultado(Context ctx, String categoria, double valor,
                                       Callback<ResultadoEnvio> cb) {
        FirebaseUser u = usuario();
        if (u == null) {
            cb.onResultado(null, "Entre com sua conta Google para enviar");
            return;
        }
        DocumentReference ref = db().collection(categoria).document(u.getUid());
        Map<String, Object> dados = dadosPublicos(ctx);
        dados.put("uid", u.getUid());
        dados.put("valor", valor);
        dados.put("data", FieldValue.serverTimestamp());

        db().runTransaction(transacao -> {
            DocumentSnapshot atual = transacao.get(ref);
            Double anterior = atual.exists() ? atual.getDouble("valor") : null;
            boolean melhorou = anterior == null
                    || (menorEhMelhor(categoria) ? valor < anterior : valor > anterior);
            if (melhorou) transacao.set(ref, dados);

            ResultadoEnvio r = new ResultadoEnvio();
            r.melhorou = melhorou;
            r.valorNoRanking = melhorou ? valor : anterior;
            return r;
        }).addOnSuccessListener(r -> posicao(categoria, r.valorNoRanking, (pos, erro) -> {
            r.posicao = pos != null ? pos : 0;
            cb.onResultado(r, null);
        })).addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    public static void posicao(String categoria, double valor, Callback<Integer> cb) {
        CollectionReference col = db().collection(categoria);
        Query melhores = menorEhMelhor(categoria)
                ? col.whereLessThan("valor", valor)
                : col.whereGreaterThan("valor", valor);
        melhores.count().get(AggregateSource.SERVER)
                .addOnSuccessListener(s -> cb.onResultado((int) s.getCount() + 1, null))
                .addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    public static void carregarRanking(String categoria, Callback<List<LinhaRanking>> cb) {
        Query.Direction direcao = menorEhMelhor(categoria)
                ? Query.Direction.ASCENDING : Query.Direction.DESCENDING;
        db().collection(categoria).orderBy("valor", direcao).limit(LIMITE_RANKING).get()
                .addOnSuccessListener(snap -> {
                    List<LinhaRanking> linhas = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        LinhaRanking l = paraLinha(d);
                        if (l == null) continue;
                        l.posicao = linhas.size() + 1;
                        linhas.add(l);
                    }
                    cb.onResultado(linhas, null);
                })
                .addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    public static void minhaPosicao(String categoria, Callback<LinhaRanking> cb) {
        FirebaseUser u = usuario();
        if (u == null) {
            cb.onResultado(null, null);
            return;
        }
        db().collection(categoria).document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    LinhaRanking l = doc.exists() ? paraLinha(doc) : null;
                    if (l == null) {
                        cb.onResultado(null, null);
                        return;
                    }
                    posicao(categoria, l.valor, (pos, erro) -> {
                        l.posicao = pos != null ? pos : 0;
                        cb.onResultado(l, erro);
                    });
                })
                .addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    public static void criarRota(Context ctx, String nome, double aLat, double aLon,
                                 double bLat, double bLon, double raioM, double distanciaM,
                                 Callback<String> cb) {
        FirebaseUser u = usuario();
        if (u == null) {
            cb.onResultado(null, "Entre com sua conta Google para criar rotas");
            return;
        }
        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", nome);
        dados.put("aLat", aLat);
        dados.put("aLon", aLon);
        dados.put("bLat", bLat);
        dados.put("bLon", bLon);
        dados.put("raioM", raioM);
        dados.put("distanciaM", distanciaM);
        dados.put("criadorUid", u.getUid());
        dados.put("criadorNome", Estado.nomePiloto(ctx));
        dados.put("criadaEm", FieldValue.serverTimestamp());
        db().collection(ROTAS).add(dados)
                .addOnSuccessListener(ref -> cb.onResultado(ref.getId(), null))
                .addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    public static void carregarRotas(Callback<List<Rota>> cb) {
        db().collection(ROTAS).limit(LIMITE_ROTAS).get()
                .addOnSuccessListener(snap -> {
                    List<Rota> rotas = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Rota r = paraRota(d);
                        if (r != null) rotas.add(r);
                    }
                    cb.onResultado(rotas, null);
                })
                .addOnFailureListener(e -> cb.onResultado(null, mensagem(e)));
    }

    private static Rota paraRota(DocumentSnapshot d) {
        Double aLat = d.getDouble("aLat"), aLon = d.getDouble("aLon");
        Double bLat = d.getDouble("bLat"), bLon = d.getDouble("bLon");
        if (aLat == null || aLon == null || bLat == null || bLon == null) return null;
        Rota r = new Rota();
        r.id = d.getId();
        r.nome = d.getString("nome");
        r.criadorUid = d.getString("criadorUid");
        r.criadorNome = d.getString("criadorNome");
        r.aLat = aLat;
        r.aLon = aLon;
        r.bLat = bLat;
        r.bLon = bLon;
        Double raio = d.getDouble("raioM");
        Double dist = d.getDouble("distanciaM");
        r.raioM = raio != null ? raio : 25;
        r.distanciaM = dist != null ? dist : 0;
        return r;
    }

    private static LinhaRanking paraLinha(DocumentSnapshot d) {
        Double valor = d.getDouble("valor");
        if (valor == null) return null;
        LinhaRanking l = new LinhaRanking();
        l.uid = d.getId();
        l.nome = d.getString("nome");
        l.carro = d.getString("carro");
        l.foto = d.getString("foto");
        l.valor = valor;
        return l;
    }

    private static String mensagem(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code codigo = ((FirebaseFirestoreException) e).getCode();
            if (codigo == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "sem permissão no Firestore (confira as regras)";
            }
            if (codigo == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "sem conexão com a internet";
            }
        }
        return e.getMessage() != null ? e.getMessage() : "erro desconhecido";
    }
}
