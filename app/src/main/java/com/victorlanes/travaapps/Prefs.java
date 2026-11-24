package com.victorlanes.travaapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Configurações, PIN, rostos cadastrados, apps bloqueados e fotos de intrusos. */
public final class Prefs {

    private static SharedPreferences sp;

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("trava", Context.MODE_PRIVATE);
        return sp;
    }

    // ------------------------------------------------------------------ apps bloqueados

    private static Set<String> cacheBloqueados;

    public static synchronized Set<String> bloqueados(Context c) {
        if (cacheBloqueados == null) cacheBloqueados = new HashSet<>(sp(c).getStringSet("bloqueados", new HashSet<>()));
        return new HashSet<>(cacheBloqueados);
    }

    public static synchronized boolean bloqueado(Context c, String pkg) {
        if (cacheBloqueados == null) bloqueados(c);
        return cacheBloqueados.contains(pkg);
    }

    public static synchronized void bloquear(Context c, String pkg, boolean on) {
        Set<String> s = bloqueados(c);
        if (on) s.add(pkg);
        else s.remove(pkg);
        cacheBloqueados = s;
        sp(c).edit().putStringSet("bloqueados", new HashSet<>(s)).apply();
    }

    // ------------------------------------------------------------------ PIN

    public static boolean temPin(Context c) {
        return sp(c).getString("pinHash", null) != null;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((salt + ":" + pin).getBytes("UTF-8"));
            for (int i = 0; i < 20000; i++) h = md.digest(h); // deixa o teste de PINs por força bruta mais lento
            return Base64.encodeToString(h, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void definirPin(Context c, String pin) {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        String salt = Base64.encodeToString(s, Base64.NO_WRAP);
        sp(c).edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin)).putInt("pinLen", pin.length()).apply();
    }

    public static boolean conferePin(Context c, String pin) {
        String salt = sp(c).getString("pinSalt", ""), h = sp(c).getString("pinHash", null);
        return h != null && MessageDigest.isEqual(h.getBytes(), hash(salt, pin).getBytes());
    }

    public static int tamanhoPin(Context c) {
        return sp(c).getInt("pinLen", 4);
    }

    // ------------------------------------------------------------------ rosto

    public static void salvarRostos(Context c, List<float[]> embs) {
        JSONArray a = new JSONArray();
        try {
            for (float[] e : embs) {
                JSONArray v = new JSONArray();
                for (float x : e) v.put((double) x);
                a.put(v);
            }
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("rostos", a.toString()).apply();
        cacheRostos = null;
    }

    private static List<float[]> cacheRostos;

    public static synchronized List<float[]> rostos(Context c) {
        if (cacheRostos != null) return cacheRostos;
        List<float[]> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("rostos", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray v = a.getJSONArray(i);
                float[] e = new float[v.length()];
                for (int j = 0; j < e.length; j++) e[j] = (float) v.getDouble(j);
                l.add(e);
            }
        } catch (JSONException ignored) {
        }
        cacheRostos = l;
        return l;
    }

    public static boolean temRosto(Context c) {
        return !rostos(c).isEmpty();
    }

    public static void apagarRosto(Context c) {
        sp(c).edit().remove("rostos").apply();
        cacheRostos = null;
    }

    // ------------------------------------------------------------------ ajustes

    public static boolean exigirPiscar(Context c) {
        return sp(c).getBoolean("piscar", true);
    }

    public static void exigirPiscar(Context c, boolean v) {
        sp(c).edit().putBoolean("piscar", v).apply();
    }

    /** "flexivel" | "normal" | "alto" */
    public static String rigor(Context c) {
        return sp(c).getString("rigor", "normal");
    }

    public static void rigor(Context c, String v) {
        sp(c).edit().putString("rigor", v).apply();
    }

    /** semelhança mínima (cosseno) para aceitar o rosto */
    public static float limiar(Context c) {
        switch (rigor(c)) {
            case "flexivel": return 0.55f;
            case "alto": return 0.70f;
            default: return 0.62f;
        }
    }

    public static int tentativas(Context c) {
        return sp(c).getInt("tentativas", 5);
    }

    public static void tentativas(Context c, int v) {
        sp(c).edit().putInt("tentativas", v).apply();
    }

    /** minutos de tolerância depois de sair de um app desbloqueado (0 = bloqueia ao sair) */
    public static int tolerancia(Context c) {
        return sp(c).getInt("tolerancia", 0);
    }

    public static void tolerancia(Context c, int v) {
        sp(c).edit().putInt("tolerancia", v).apply();
    }

    public static boolean fotoIntruso(Context c) {
        return sp(c).getBoolean("fotoIntruso", true);
    }

    public static void fotoIntruso(Context c, boolean v) {
        sp(c).edit().putBoolean("fotoIntruso", v).apply();
    }

    // ------------------------------------------------------------------ intrusos

    public static File pastaIntrusos(Context c) {
        File d = new File(c.getFilesDir(), "intrusos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** arquivos ordenados do mais novo para o mais antigo. Nome: <millis>__<pacote>.jpg */
    public static List<File> intrusos(Context c) {
        File[] fs = pastaIntrusos(c).listFiles((dir, n) -> n.endsWith(".jpg"));
        List<File> l = fs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(fs));
        Collections.sort(l, (a, b) -> b.getName().compareTo(a.getName()));
        return l;
    }

    public static int novosIntrusos(Context c) {
        return sp(c).getInt("novosIntrusos", 0);
    }

    public static void novosIntrusos(Context c, int v) {
        sp(c).edit().putInt("novosIntrusos", v).apply();
    }
}
