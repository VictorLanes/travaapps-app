package com.victorlanes.travaapps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.google.mlkit.vision.face.Face;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Tela de bloqueio: reconhece o rosto cadastrado (com piscada para provar que não é foto) ou PIN.
 * Depois de N erros seguidos, guarda uma foto da câmera frontal de quem estava tentando.
 */
public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_PACOTE = "pacote";
    public static final String EXTRA_PROPRIO = "proprio"; // abrir o próprio Trava Apps
    private static final long PAUSA_ERROS = 30_000;

    private String pacote;
    private boolean proprio;
    private Camara camara;
    private PreviewView previa;
    private View anel, caixaRosto;
    private TextView status, detalhe, trocar, titulo;
    private ImageView icone;
    private PinPad pin;
    private boolean modoPin;
    private boolean temCamera;
    private volatile boolean concluido;

    private volatile Bitmap ultimoQuadro;
    private List<float[]> cadastrados;
    private float limiar;
    private boolean exigirPiscar;
    private int acertos, errosSeguidos, erros;
    private boolean viuOlhosAbertos, piscou;
    private long pausaAte, ultimoAviso;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        lerIntent(getIntent());
        montar();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                desistir();
            }
        });
        cadastrados = Prefs.rostos(this);
        limiar = Prefs.limiar(this);
        exigirPiscar = Prefs.exigirPiscar(this);
        temCamera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (temCamera) {
            camara = new Camara();
            camara.iniciar(this, previa, this::quadro);
        }
        modoPin(!temCamera || cadastrados.isEmpty());
        if (!temCamera) detalhe.setText("Sem permissão da câmera: use o PIN");
        else if (cadastrados.isEmpty()) detalhe.setText("Nenhum rosto cadastrado: use o PIN");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        lerIntent(intent);
        atualizarCabecalho();
    }

    private void lerIntent(Intent i) {
        pacote = i.getStringExtra(EXTRA_PACOTE);
        proprio = i.getBooleanExtra(EXTRA_PROPRIO, false);
        if (pacote == null) pacote = getPackageName();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppLockService.descobrir();
    }

    // ================================================================== tela

    private void montar() {
        FrameLayout raiz = new FrameLayout(this);
        GradientDrawable fundo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF3730A3, 0xFF1E1B4B, 0xFF0B0A1A});
        raiz.setBackground(fundo);
        setContentView(raiz);

        LinearLayout col = Ui.coluna(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(28), dp(24), dp(20));
        raiz.addView(col, new FrameLayout.LayoutParams(-1, -1));
        Ui.aplicarInsets(col);

        icone = new ImageView(this);
        col.addView(icone, new LinearLayout.LayoutParams(dp(52), dp(52)));
        titulo = Ui.texto(this, "", 20, 0xFFFFFFFF, true);
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, dp(10), 0, 0);
        col.addView(titulo);
        TextView sub = Ui.texto(this, "🔒 Protegido pelo Trava Apps", 13, 0xB3FFFFFF, false);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub);
        atualizarCabecalho();

        View esp1 = new View(this);
        col.addView(esp1, new LinearLayout.LayoutParams(1, 0, 1f));

        // círculo com a câmera
        caixaRosto = new FrameLayout(this);
        int lado = dp(210);
        previa = new PreviewView(this);
        previa.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previa.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previa.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        previa.setClipToOutline(true);
        previa.setBackground(Ui.fundo(0x22FFFFFF, lado / 2f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(lado - dp(16), lado - dp(16), Gravity.CENTER);
        ((FrameLayout) caixaRosto).addView(previa, lp);
        anel = new View(this);
        corAnel(0x80FFFFFF);
        ((FrameLayout) caixaRosto).addView(anel, new FrameLayout.LayoutParams(lado, lado, Gravity.CENTER));
        col.addView(caixaRosto, new LinearLayout.LayoutParams(lado, lado));

        status = Ui.texto(this, "Olhe para a câmera", 18, 0xFFFFFFFF, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(18), 0, 0);
        col.addView(status, new LinearLayout.LayoutParams(-1, -2));
        detalhe = Ui.texto(this, "", 13.5f, 0xB3FFFFFF, false);
        detalhe.setGravity(Gravity.CENTER);
        detalhe.setPadding(0, dp(4), 0, 0);
        col.addView(detalhe, new LinearLayout.LayoutParams(-1, -2));

        pin = new PinPad(this, Prefs.tamanhoPin(this), this::conferirPin);
        LinearLayout.LayoutParams lpp = new LinearLayout.LayoutParams(-2, -2);
        lpp.topMargin = dp(8);
        col.addView(pin, lpp);

        View esp2 = new View(this);
        col.addView(esp2, new LinearLayout.LayoutParams(1, 0, 1f));

        trocar = Ui.texto(this, "", 15, 0xFFFFFFFF, true);
        trocar.setGravity(Gravity.CENTER);
        trocar.setPadding(dp(20), dp(12), dp(20), dp(12));
        trocar.setBackground(Ui.clicavel(0x1FFFFFFF, dp(24), 0x33FFFFFF));
        trocar.setOnClickListener(v -> modoPin(!modoPin));
        col.addView(trocar, new LinearLayout.LayoutParams(-2, -2));
    }

    private void atualizarCabecalho() {
        if (titulo == null) return;
        if (proprio || pacote.equals(getPackageName())) {
            titulo.setText("Trava Apps");
            icone.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
            return;
        }
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pacote, 0);
            titulo.setText(pm.getApplicationLabel(ai));
            Drawable d = pm.getApplicationIcon(ai);
            icone.setImageDrawable(d);
        } catch (PackageManager.NameNotFoundException e) {
            titulo.setText(pacote);
        }
    }

    private void corAnel(int cor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(dp(4), cor);
        anel.setBackground(g);
    }

    private void modoPin(boolean on) {
        modoPin = on;
        pin.setVisibility(on ? View.VISIBLE : View.GONE);
        caixaRosto.setVisibility(on ? View.GONE : View.VISIBLE);
        boolean podeRosto = temCamera && !cadastrados.isEmpty();
        trocar.setVisibility(podeRosto ? View.VISIBLE : View.GONE);
        trocar.setText(on ? "🙂  Usar o rosto" : "🔢  Usar o PIN");
        if (on) {
            status.setText("Digite seu PIN");
            if (!emPausa()) detalhe.setText(erros > 0 ? textoErros() : "");
        } else {
            status.setText("Olhe para a câmera");
            detalhe.setText(exigirPiscar ? "e pisque os olhos" : "");
            acertos = 0;
            piscou = false;
            viuOlhosAbertos = false;
        }
    }

    // ================================================================== rosto

    private void quadro(Bitmap q, Face f, int n) {
        if (q == null) {
            runOnUiThread(() -> {
                temCamera = false;
                modoPin(true);
                detalhe.setText("Não foi possível abrir a câmera: use o PIN");
            });
            return;
        }
        ultimoQuadro = q;
        if (concluido || modoPin || emPausa() || cadastrados.isEmpty()) return;
        if (f == null) {
            acertos = 0;
            avisar("Olhe para a câmera", exigirPiscar ? "e pisque os olhos" : "", 0x80FFFFFF);
            return;
        }
        float sim;
        try {
            sim = FaceEngine.melhor(FaceEngine.get(this).assinatura(FaceEngine.alinhar(q, f)), cadastrados);
        } catch (Exception e) {
            return;
        }
        // piscada: olhos abertos e depois fechados
        Float pe = f.getLeftEyeOpenProbability(), pd = f.getRightEyeOpenProbability();
        if (pe != null && pd != null) {
            if (pe > 0.6f && pd > 0.6f) viuOlhosAbertos = true;
            else if (viuOlhosAbertos && pe < 0.25f && pd < 0.25f) piscou = true;
        }
        if (sim >= limiar) {
            errosSeguidos = 0;
            acertos++;
            if (acertos >= 2) {
                if (exigirPiscar && !piscou) avisar("Agora pisque os olhos 😉", "para confirmar que é você", 0xFFA5B4FC);
                else runOnUiThread(this::liberar);
            } else avisar("Reconhecendo…", "", 0xFFA5B4FC);
        } else {
            acertos = 0;
            errosSeguidos++;
            avisar("Reconhecendo…", "", 0x80FFFFFF);
            if (errosSeguidos >= 10) { // ~1 a 2 segundos de um rosto que não bate
                errosSeguidos = 0;
                piscou = false;
                runOnUiThread(() -> falhou("Rosto não reconhecido"));
            }
        }
    }

    private void avisar(String s, String d, int cor) {
        long agora = SystemClock.uptimeMillis();
        if (agora < ultimoAviso) return; // mantém a mensagem de erro visível por um instante
        runOnUiThread(() -> {
            if (modoPin || concluido || emPausa()) return;
            status.setText(s);
            detalhe.setText(erros > 0 && d.isEmpty() ? textoErros() : d);
            corAnel(cor);
        });
    }

    // ================================================================== PIN

    private void conferirPin(String v) {
        if (emPausa()) return;
        if (Prefs.conferePin(this, v)) {
            pin.sucesso();
            liberar();
        } else {
            pin.erro();
            falhou("PIN incorreto");
        }
    }

    // ================================================================== resultado

    private boolean emPausa() {
        return SystemClock.uptimeMillis() < pausaAte;
    }

    private String textoErros() {
        return erros + (erros == 1 ? " tentativa errada" : " tentativas erradas");
    }

    private void falhou(String msg) {
        erros++;
        ultimoAviso = SystemClock.uptimeMillis() + 1200;
        getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.REJECT);
        corAnel(0xFFFB7185);
        status.setText(msg);
        int limite = Prefs.tentativas(this);
        detalhe.setText(textoErros() + " de " + limite);
        if (erros % limite == 0) {
            if (Prefs.fotoIntruso(this)) guardarIntruso();
            pausaAte = SystemClock.uptimeMillis() + PAUSA_ERROS;
            pin.setTravado(true);
            status.setText("Muitas tentativas erradas");
            contagem();
        }
    }

    private void contagem() {
        long falta = pausaAte - SystemClock.uptimeMillis();
        if (falta <= 0) {
            pin.setTravado(false);
            corAnel(0x80FFFFFF);
            status.setText(modoPin ? "Digite seu PIN" : "Olhe para a câmera");
            detalhe.setText(textoErros());
            return;
        }
        detalhe.setText("Tente de novo em " + ((falta + 999) / 1000) + " s");
        status.postDelayed(this::contagem, 500);
    }

    private void guardarIntruso() {
        final Bitmap q = ultimoQuadro;
        if (q == null) return;
        final String pkg = pacote;
        new Thread(() -> {
            File f = new File(Prefs.pastaIntrusos(this), System.currentTimeMillis() + "__" + pkg + ".jpg");
            try (FileOutputStream out = new FileOutputStream(f)) {
                q.compress(Bitmap.CompressFormat.JPEG, 88, out);
                Prefs.novosIntrusos(this, Prefs.novosIntrusos(this) + 1);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void liberar() {
        if (concluido) return;
        concluido = true;
        corAnel(0xFF4ADE80);
        status.setText("Desbloqueado");
        detalhe.setText("");
        if (proprio) MainActivity.autenticado();
        else AppLockService.liberar(pacote);
        status.postDelayed(() -> {
            finish();
            overridePendingTransition(0, android.R.anim.fade_out);
        }, 120);
    }

    private void desistir() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (camara != null) camara.parar();
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /** usado pelo MainActivity para exigir o desbloqueio do próprio app */
    public static Intent paraProprioApp(android.content.Context c) {
        Intent i = new Intent(c, LockActivity.class);
        i.putExtra(EXTRA_PROPRIO, true);
        return i;
    }
}
