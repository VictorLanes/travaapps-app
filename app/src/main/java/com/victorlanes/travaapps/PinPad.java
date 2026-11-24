package com.victorlanes.travaapps;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Teclado numérico de PIN (responde no toque, sem esperar soltar o dedo). Para fundos escuros. */
public class PinPad extends LinearLayout {

    public interface Ouvinte {
        void completo(String pin);
    }

    private static final String[] LETRAS = {"", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};
    private final LinearLayout pontos;
    private final TextView esquerda;
    private int tamanho;
    private final StringBuilder valor = new StringBuilder();
    private final Ouvinte ouvinte;
    private Runnable acaoEsquerda;
    private boolean travado;

    public PinPad(Context c, int tamanho, Ouvinte ouvinte) {
        super(c);
        this.ouvinte = ouvinte;
        this.tamanho = tamanho;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        pontos = new LinearLayout(c);
        pontos.setGravity(Gravity.CENTER);
        addView(pontos, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(c, 28)));
        desenharPontos();

        GridLayout g = new GridLayout(c);
        g.setColumnCount(3);
        LayoutParams lg = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lg.topMargin = Ui.dp(c, 22);
        addView(g, lg);
        String[] teclas = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "esq", "0", "apagar"};
        TextView esq = null;
        for (String t : teclas) {
            View v;
            if (t.equals("esq")) {
                esq = texto(c, "", 14);
                v = esq;
            } else if (t.equals("apagar")) {
                v = texto(c, "⌫", 26);
            } else {
                v = tecla(c, t);
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            int s = Ui.dp(c, 74);
            lp.width = s;
            lp.height = s;
            lp.setMargins(Ui.dp(c, 13), Ui.dp(c, 7), Ui.dp(c, 13), Ui.dp(c, 7));
            g.addView(v, lp);
            final String k = t;
            tocar(v, () -> pressionar(k));
        }
        esquerda = esq;
    }

    private TextView texto(Context c, String s, int sp) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.clicavel(0x00FFFFFF, Ui.dp(c, 37), 0x33FFFFFF));
        return t;
    }

    private View tecla(Context c, String n) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x17FFFFFF);
        bg.setStroke(Ui.dp(c, 1), 0x1AFFFFFF);
        f.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), bg, null));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView num = new TextView(c);
        num.setText(n);
        num.setTextColor(0xFFFFFFFF);
        num.setTextSize(30);
        num.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        num.setGravity(Gravity.CENTER);
        num.setIncludeFontPadding(false);
        col.addView(num);
        TextView let = new TextView(c);
        let.setText(LETRAS[Integer.parseInt(n)]);
        let.setTextColor(0x99FFFFFF);
        let.setTextSize(9.5f);
        let.setLetterSpacing(0.15f);
        let.setGravity(Gravity.CENTER);
        col.addView(let);
        f.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return f;
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void tocar(View v, Runnable r) {
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.setPressed(true);
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
                r.run();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setPressed(false);
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return true;
        });
    }

    private void pressionar(String k) {
        if (travado) return;
        if (k.equals("esq")) {
            if (acaoEsquerda != null) acaoEsquerda.run();
            return;
        }
        if (k.equals("apagar")) {
            if (valor.length() > 0) valor.deleteCharAt(valor.length() - 1);
        } else if (valor.length() < tamanho) {
            valor.append(k);
        }
        pintar(0xFFFFFFFF);
        if (valor.length() == tamanho) {
            String v = valor.toString();
            postDelayed(() -> ouvinte.completo(v), 60); // deixa o último ponto aparecer
        }
    }

    public void setAcaoEsquerda(String texto, Runnable r) {
        esquerda.setText(texto);
        acaoEsquerda = r;
    }

    public void setTamanho(int t) {
        tamanho = t;
        valor.setLength(0);
        desenharPontos();
    }

    public void setTravado(boolean t) {
        travado = t;
        setAlpha(t ? 0.4f : 1f);
    }

    private void desenharPontos() {
        pontos.removeAllViews();
        int s = Ui.dp(getContext(), 13);
        for (int i = 0; i < tamanho; i++) {
            View d = new View(getContext());
            LayoutParams lp = new LayoutParams(s, s);
            lp.setMargins(Ui.dp(getContext(), 9), 0, Ui.dp(getContext(), 9), 0);
            pontos.addView(d, lp);
        }
        pintar(0xFFFFFFFF);
    }

    private void pintar(int cor) {
        for (int i = 0; i < pontos.getChildCount(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            boolean on = i < valor.length();
            g.setColor(on ? cor : 0x00000000);
            g.setStroke(Ui.dp(getContext(), 1.5f), on ? cor : 0xB3FFFFFF);
            pontos.getChildAt(i).setBackground(g);
        }
    }

    /** limpa com animação de erro */
    public void erro() {
        pintar(0xFFFB7185);
        ObjectAnimator a = ObjectAnimator.ofFloat(pontos, "translationX", 0, -22, 22, -16, 16, -8, 8, 0);
        a.setDuration(380);
        a.start();
        postDelayed(this::limpar, 380);
    }

    public void sucesso() {
        pintar(0xFF4ADE80);
    }

    public void limpar() {
        valor.setLength(0);
        pintar(0xFFFFFFFF);
    }
}
