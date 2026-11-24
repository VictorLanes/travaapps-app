package com.victorlanes.travaapps;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pequenos utilitários de interface (o app monta as telas em código, sem bibliotecas externas). */
public final class Ui {

    public static final ExecutorService FUNDO = Executors.newFixedThreadPool(3);
    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ui() {
    }

    public static final class Cores {
        public int fundo, superficie, cartao, texto, sub, destaque, sobreDestaque, linha, chip, chipSel;
    }

    public static boolean escuro(Context c) {
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    public static Cores cores(Context c) {
        Cores k = new Cores();
        if (escuro(c)) {
            k.fundo = 0xFF111114;
            k.superficie = 0xFF1B1B20;
            k.cartao = 0xFF25252B;
            k.texto = 0xFFEDEDF2;
            k.sub = 0xFFA3A3AD;
            k.destaque = 0xFFA5B4FC;
            k.sobreDestaque = 0xFF15134A;
            k.linha = 0xFF33333A;
            k.chip = 0xFF2C2C33;
            k.chipSel = 0xFF3B3A75;
        } else {
            k.fundo = 0xFFF6F6F9;
            k.superficie = 0xFFFFFFFF;
            k.cartao = 0xFFECECF1;
            k.texto = 0xFF1B1B1F;
            k.sub = 0xFF6B6B74;
            k.destaque = 0xFF4F46E5;
            k.sobreDestaque = 0xFFFFFFFF;
            k.linha = 0xFFE1E1E8;
            k.chip = 0xFFECECF1;
            k.chipSel = 0xFFE0E7FF;
        }
        return k;
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable fundo(int cor, float raioPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(raioPx);
        return g;
    }

    public static RippleDrawable clicavel(int cor, float raioPx, int corToque) {
        return new RippleDrawable(ColorStateList.valueOf(corToque), fundo(cor, raioPx), fundo(0xFFFFFFFF, raioPx));
    }

    /** Aplica as margens da barra de status/navegação (Android 15 desenha atrás delas). */
    public static void aplicarInsets(View v) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener((view, ins) -> {
            int top, bot, esq, dir;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                top = s.top;
                bot = s.bottom;
                esq = s.left;
                dir = s.right;
            } else {
                top = ins.getSystemWindowInsetTop();
                bot = ins.getSystemWindowInsetBottom();
                esq = ins.getSystemWindowInsetLeft();
                dir = ins.getSystemWindowInsetRight();
            }
            view.setPadding(l + esq, t + top, r + dir, b + bot);
            return ins;
        });
        v.requestApplyInsets();
    }

    public static TextView texto(Context c, String s, float sp, int cor, boolean negrito) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        if (negrito) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static Button botao(Context c, String s, boolean principal) {
        Cores k = cores(c);
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        int raio = dp(c, 24);
        if (principal) {
            b.setTextColor(k.sobreDestaque);
            b.setBackground(clicavel(k.destaque, raio, 0x33FFFFFF));
        } else {
            b.setTextColor(k.destaque);
            b.setBackground(clicavel(k.chip, raio, 0x22000000));
        }
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        return b;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float peso) {
        return new LinearLayout.LayoutParams(w, h, peso);
    }

    public static LinearLayout coluna(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout linha(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Cartão de seção com título (estilo One UI). */
    public static LinearLayout secao(Context c, ViewGroup pai, String titulo, String dica) {
        Cores k = cores(c);
        LinearLayout card = coluna(c);
        card.setBackground(fundo(k.superficie, dp(c, 22)));
        int p = dp(c, 16);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = dp(c, 12);
        pai.addView(card, l);
        card.addView(texto(c, titulo, 16, k.texto, true));
        if (dica != null) {
            TextView d = texto(c, dica, 13, k.sub, false);
            d.setPadding(0, dp(c, 2), 0, 0);
            card.addView(d);
        }
        return card;
    }

    public interface AoEscolher {
        void escolheu(String id);
    }

    /** Grupo de "chips" de escolha única com rolagem horizontal. */
    public static final class Chips {
        public final HorizontalScrollView view;
        private final List<TextView> itens = new ArrayList<>();
        private final String[] ids;
        private String selecionado;
        private final Cores k;

        public Chips(Context c, String[] ids, String[] nomes, String sel, AoEscolher cb) {
            this.ids = ids;
            this.k = cores(c);
            view = new HorizontalScrollView(c);
            view.setHorizontalScrollBarEnabled(false);
            view.setClipToPadding(false);
            LinearLayout l = linha(c);
            l.setPadding(0, dp(c, 10), 0, dp(c, 2));
            view.addView(l);
            for (int i = 0; i < ids.length; i++) {
                final String id = ids[i];
                TextView t = texto(c, nomes[i], 14, k.texto, false);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
                LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                p.rightMargin = dp(c, 8);
                l.addView(t, p);
                itens.add(t);
                t.setOnClickListener(v -> {
                    selecionar(id);
                    if (cb != null) cb.escolheu(id);
                });
            }
            selecionar(sel);
        }

        public void selecionar(String id) {
            selecionado = id;
            Context c = view.getContext();
            for (int i = 0; i < ids.length; i++) {
                TextView t = itens.get(i);
                boolean s = ids[i].equals(id);
                t.setBackground(clicavel(s ? k.chipSel : k.chip, dp(c, 18), 0x22000000));
                t.setTextColor(s ? (escuro(c) ? 0xFFFFFFFF : 0xFF312E81) : k.texto);
                t.setTypeface(Typeface.create(s ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            }
        }

        public void habilitar(boolean on) {
            for (TextView t : itens) {
                t.setEnabled(on);
                t.setAlpha(on ? 1f : 0.45f);
            }
        }

        public String valor() {
            return selecionado;
        }
    }
}
