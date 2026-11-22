package com.example.flzrace;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public final class FotoCarro {
    private static final String ARQUIVO = "carro.jpg";
    private static final String ARQUIVO_PENDENTE = "carro_pendente.jpg";
    private static final String ARQUIVO_CAPTURA = "captura.jpg";
    private static final int LADO_MAXIMO = 1024;
    private static final int LADO_MINIATURA = 256;
    private static final int QUALIDADE = 85;

    private FotoCarro() {
    }

    public static File arquivo(Context ctx) {
        return new File(ctx.getFilesDir(), ARQUIVO);
    }

    public static File pendente(Context ctx) {
        return new File(ctx.getFilesDir(), ARQUIVO_PENDENTE);
    }

    public static File captura(Context ctx) {
        return new File(ctx.getCacheDir(), ARQUIVO_CAPTURA);
    }

    public static boolean existe(Context ctx) {
        return arquivo(ctx).exists();
    }

    public static boolean processarCaptura(Context ctx) {
        File origem = captura(ctx);
        Bitmap original = decodificar(origem, LADO_MAXIMO);
        if (original == null) return false;

        float escala = Math.min(1f, LADO_MAXIMO / (float) Math.max(original.getWidth(), original.getHeight()));
        Matrix matriz = new Matrix();
        matriz.postScale(escala, escala);
        matriz.postRotate(rotacaoExif(origem));
        Bitmap pronta = Bitmap.createBitmap(original, 0, 0,
                original.getWidth(), original.getHeight(), matriz, true);

        try (FileOutputStream saida = new FileOutputStream(pendente(ctx))) {
            pronta.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, saida);
        } catch (Exception e) {
            return false;
        } finally {
            origem.delete();
        }
        return true;
    }

    public static boolean confirmar(Context ctx) {
        File destino = arquivo(ctx);
        if (destino.exists() && !destino.delete()) return false;
        return pendente(ctx).renameTo(destino);
    }

    public static void descartarPendente(Context ctx) {
        pendente(ctx).delete();
    }

    public static Bitmap carregar(Context ctx, int ladoMaximo) {
        File f = arquivo(ctx);
        return f.exists() ? decodificar(f, ladoMaximo) : null;
    }

    public static Bitmap carregarPendente(Context ctx, int ladoMaximo) {
        File f = pendente(ctx);
        return f.exists() ? decodificar(f, ladoMaximo) : null;
    }

    public static String miniaturaBase64(Context ctx) {
        return miniaturaBase64(ctx, LADO_MINIATURA);
    }

    public static String miniaturaBase64(Context ctx, int lado) {
        Bitmap foto = carregar(ctx, lado * 2);
        if (foto == null) return null;
        float escala = Math.min(1f, lado / (float) Math.max(foto.getWidth(), foto.getHeight()));
        Bitmap pequena = Bitmap.createScaledBitmap(foto,
                Math.max(1, Math.round(foto.getWidth() * escala)),
                Math.max(1, Math.round(foto.getHeight() * escala)), true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        pequena.compress(Bitmap.CompressFormat.JPEG, 75, bytes);
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
    }

    public static Bitmap deBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void salvarDeBase64(Context ctx, String base64) {
        if (base64 == null || base64.isEmpty()) return;
        try (FileOutputStream saida = new FileOutputStream(arquivo(ctx))) {
            saida.write(Base64.decode(base64, Base64.NO_WRAP));
        } catch (Exception ignored) {
        }
    }

    public static void apagar(Context ctx) {
        arquivo(ctx).delete();
        descartarPendente(ctx);
    }

    private static Bitmap decodificar(File f, int ladoMaximo) {
        BitmapFactory.Options opcoes = new BitmapFactory.Options();
        opcoes.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), opcoes);
        int maior = Math.max(opcoes.outWidth, opcoes.outHeight);
        if (maior <= 0) return null;

        int amostra = 1;
        while (maior / (amostra * 2) >= ladoMaximo) amostra *= 2;
        opcoes.inJustDecodeBounds = false;
        opcoes.inSampleSize = amostra;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opcoes);
    }

    private static int rotacaoExif(File f) {
        try {
            ExifInterface exif = new ExifInterface(f.getAbsolutePath());
            int orientacao = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientacao) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}
