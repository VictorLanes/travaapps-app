package com.victorlanes.travaapps;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Serviço de acessibilidade: percebe qual app foi aberto e, se ele estiver na lista,
 * cobre a tela na hora e abre a tela de bloqueio.
 */
public class AppLockService extends AccessibilityService {

    private static AppLockService inst;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** app liberado -> quando o usuário saiu dele (0 = ainda está nele) */
    private static final Map<String, Long> liberados = new HashMap<>();
    private static String atual = "";

    private final Set<String> ignorar = new HashSet<>();
    private View cobertura;
    private BroadcastReceiver telaDesligada;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        ignorar.add(getPackageName());
        ignorar.add("com.android.systemui");
        ignorar.add("android");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) for (InputMethodInfo i : imm.getInputMethodList()) ignorar.add(i.getPackageName());
        telaDesligada = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                synchronized (liberados) {
                    liberados.clear(); // tela desligou: tudo volta a pedir desbloqueio
                }
            }
        };
        registerReceiver(telaDesligada, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (ignorar.contains(pkg)) return;
        long agora = System.currentTimeMillis();
        synchronized (liberados) {
            if (!pkg.equals(atual)) {
                Long l = liberados.get(atual);
                if (l != null && l == 0) liberados.put(atual, agora); // saiu do app liberado
                atual = pkg;
            }
            if (!Prefs.bloqueado(this, pkg)) return;
            Long saiu = liberados.get(pkg);
            long tol = Prefs.tolerancia(this) * 60_000L;
            if (saiu != null && (saiu == 0 || (tol > 0 && agora - saiu < tol))) {
                liberados.put(pkg, 0L);
                return;
            }
            liberados.remove(pkg);
        }
        bloquear(pkg);
    }

    private void bloquear(String pkg) {
        cobrir();
        Intent i = new Intent(this, LockActivity.class);
        i.putExtra(LockActivity.EXTRA_PACOTE, pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    /** cobre a tela imediatamente (antes da tela de bloqueio abrir) para não aparecer nada do app */
    private void cobrir() {
        if (cobertura != null) return;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        View v = new View(this);
        v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF3730A3, 0xFF1E1B4B, 0xFF0B0A1A}));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            wm.addView(v, lp);
            cobertura = v;
            MAIN.postDelayed(AppLockService::descobrir, 2500); // segurança: nunca fica preso
        } catch (Exception ignored) {
        }
    }

    public static void descobrir() {
        MAIN.post(() -> {
            AppLockService s = inst;
            if (s == null || s.cobertura == null) return;
            try {
                ((WindowManager) s.getSystemService(WINDOW_SERVICE)).removeView(s.cobertura);
            } catch (Exception ignored) {
            }
            s.cobertura = null;
        });
    }

    /** chamado pela tela de bloqueio depois de reconhecer o rosto ou o PIN */
    public static void liberar(String pkg) {
        synchronized (liberados) {
            liberados.put(pkg, 0L);
            atual = pkg;
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (telaDesligada != null) unregisterReceiver(telaDesligada);
        inst = null;
        super.onDestroy();
    }

    public static boolean ativo(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        ComponentName eu = new ComponentName(c, AppLockService.class);
        TextUtils.SimpleStringSplitter sp = new TextUtils.SimpleStringSplitter(':');
        sp.setString(s);
        for (String x : sp) {
            ComponentName cn = ComponentName.unflattenFromString(x);
            if (eu.equals(cn)) return true;
        }
        return false;
    }
}
