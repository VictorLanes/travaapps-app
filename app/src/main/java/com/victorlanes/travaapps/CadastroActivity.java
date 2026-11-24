package com.victorlanes.travaapps;

import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.google.mlkit.vision.face.Face;

import java.util.ArrayList;
import java.util.List;

/**
 * Cadastro do rosto: guarda 9 "assinaturas" (de frente, um pouco para a esquerda e um pouco para a direita).
 * Só a assinatura numérica é salva — nenhuma foto do seu rosto fica guardada.
 */
public class CadastroActivity extends AppCompatActivity {

    private static final int POR_POSE = 3;
    private Camara camara;
    private View anel;
    private TextView status, detalhe;
    private ProgressBar barra;
    private final List<float[]> frente = new ArrayList<>(), esquerda = new ArrayList<>(), direita = new ArrayList<>();
    private long ultimo;
    private volatile boolean pronto;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF3730A3, 0xFF1E1B4B, 0xFF0B0A1A}));
        setContentView(raiz);
        LinearLayout col = Ui.coluna(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(30), dp(24), dp(24));
        raiz.addView(col, new FrameLayout.LayoutParams(-1, -1));
        Ui.aplicarInsets(col);

        TextView t = Ui.texto(this, "Cadastrar rosto", 24, 0xFFFFFFFF, true);
        col.addView(t);
        TextView s = Ui.texto(this, "Fique num lugar bem iluminado, sem óculos escuros ou boné.", 14, 0xB3FFFFFF, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(6), 0, 0);
        col.addView(s);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        FrameLayout caixa = new FrameLayout(this);
        int lado = dp(250);
        PreviewView pv = new PreviewView(this);
        pv.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        pv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        pv.setClipToOutline(true);
        caixa.addView(pv, new FrameLayout.LayoutParams(lado - dp(18), lado - dp(18), Gravity.CENTER));
        anel = new View(this);
        cor(0x80FFFFFF);
        caixa.addView(anel, new FrameLayout.LayoutParams(lado, lado, Gravity.CENTER));
        col.addView(caixa, new LinearLayout.LayoutParams(lado, lado));

        status = Ui.texto(this, "Olhe de frente para a câmera", 19, 0xFFFFFFFF, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(22), 0, 0);
        col.addView(status, new LinearLayout.LayoutParams(-1, -2));
        detalhe = Ui.texto(this, "", 14, 0xB3FFFFFF, false);
        detalhe.setGravity(Gravity.CENTER);
        col.addView(detalhe, new LinearLayout.LayoutParams(-1, -2));
        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(POR_POSE * 3);
        barra.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFA5B4FC));
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(dp(220), dp(8));
        lb.topMargin = dp(16);
        col.addView(barra, lb);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        Button cancelar = Ui.botao(this, "Cancelar", false);
        cancelar.setOnClickListener(v -> finish());
        col.addView(cancelar, new LinearLayout.LayoutParams(-1, -2));

        camara = new Camara();
        camara.iniciar(this, pv, this::quadro);
    }

    private void quadro(Bitmap q, Face f, int n) {
        if (q == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Não foi possível abrir a câmera", Toast.LENGTH_LONG).show();
                finish();
            });
            return;
        }
        if (pronto) return;
        if (f == null || n != 1) {
            ui(n > 1 ? "Só uma pessoa na frente da câmera" : "Encaixe o rosto no círculo", "", 0x80FFFFFF);
            return;
        }
        if (f.getBoundingBox().width() < q.getWidth() * 0.28f) {
            ui("Aproxime um pouco o celular", "", 0x80FFFFFF);
            return;
        }
        long agora = SystemClock.uptimeMillis();
        if (agora - ultimo < 250) return;
        float y = f.getHeadEulerAngleY();
        List<float[]> alvo;
        if (frente.size() < POR_POSE) alvo = Math.abs(y) < 8 ? frente : null;
        else if (esquerda.size() < POR_POSE) alvo = y > 12 && y < 35 ? esquerda : null;
        else alvo = y < -12 && y > -35 ? direita : null;
        String pedido = frente.size() < POR_POSE ? "Olhe de frente para a câmera"
                : esquerda.size() < POR_POSE ? "Vire o rosto devagar para um lado" : "Agora vire devagar para o outro lado";
        if (alvo == null) {
            ui(pedido, "", 0xFFA5B4FC);
            return;
        }
        float[] e;
        try {
            e = FaceEngine.get(this).assinatura(FaceEngine.alinhar(q, f));
        } catch (Exception ex) {
            return;
        }
        ultimo = agora;
        alvo.add(e);
        int total = frente.size() + esquerda.size() + direita.size();
        runOnUiThread(() -> barra.setProgress(total));
        if (total < POR_POSE * 3) {
            ui(pedido, "Capturado " + total + " de " + POR_POSE * 3, 0xFF4ADE80);
            return;
        }
        pronto = true;
        List<float[]> todos = new ArrayList<>();
        todos.addAll(frente);
        todos.addAll(esquerda);
        todos.addAll(direita);
        // confere se todas as capturas são da mesma pessoa
        float min = 1;
        for (int i = 0; i < todos.size(); i++)
            for (int j = i + 1; j < todos.size(); j++) min = Math.min(min, FaceEngine.semelhanca(todos.get(i), todos.get(j)));
        if (min < 0.35f) {
            runOnUiThread(() -> {
                Toast.makeText(this, "As capturas ficaram diferentes entre si. Vamos tentar de novo com mais luz.", Toast.LENGTH_LONG).show();
                frente.clear();
                esquerda.clear();
                direita.clear();
                barra.setProgress(0);
                pronto = false;
            });
            return;
        }
        Prefs.salvarRostos(this, todos);
        runOnUiThread(() -> {
            cor(0xFF4ADE80);
            status.setText("Rosto cadastrado ✓");
            detalhe.setText("");
            Toast.makeText(this, "Rosto cadastrado com sucesso", Toast.LENGTH_SHORT).show();
            status.postDelayed(this::finish, 700);
        });
    }

    private void ui(String s, String d, int c) {
        runOnUiThread(() -> {
            if (pronto) return;
            status.setText(s);
            detalhe.setText(d);
            cor(c);
        });
    }

    private void cor(int c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(dp(4), c);
        anel.setBackground(g);
    }

    @Override
    protected void onDestroy() {
        camara.parar();
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
