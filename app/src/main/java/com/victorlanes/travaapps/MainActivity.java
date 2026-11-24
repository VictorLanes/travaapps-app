package com.victorlanes.travaapps;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Tela principal: configuração, ajustes, intrusos e lista de apps para bloquear. */
public class MainActivity extends AppCompatActivity {

    private static boolean autenticado;
    private static long saiuEm;

    private Ui.Cores k;
    private LinearLayout conteudo, listaApps;
    private TextView resumoApps;
    private String busca = "";
    private final List<AppItem> apps = new ArrayList<>();
    private final Map<String, Drawable> icones = new HashMap<>();
    private ActivityResultLauncher<String> pedirCamera;
    private Runnable depoisDaCamera;

    static final class AppItem {
        String pkg;
        String nome;
    }

    /** chamado pela tela de bloqueio quando o dono desbloqueia o próprio Trava Apps */
    static void autenticado() {
        autenticado = true;
        saiuEm = System.currentTimeMillis();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        k = Ui.cores(this);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        pedirCamera = registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
            if (ok && depoisDaCamera != null) depoisDaCamera.run();
            else if (!ok) Toast.makeText(this, "Sem a câmera não dá para usar o rosto nem tirar foto de intrusos", Toast.LENGTH_LONG).show();
            depoisDaCamera = null;
            montar();
        });

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(k.fundo);
        sv.setFillViewport(true);
        conteudo = Ui.coluna(this);
        conteudo.setPadding(dp(16), dp(12), dp(16), dp(24));
        sv.addView(conteudo);
        setContentView(sv);
        Ui.aplicarInsets(sv);
        carregarApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Prefs.temPin(this)) {
            montar();
            boasVindas();
            return;
        }
        // o próprio Trava Apps também pede rosto/PIN (com 2 minutos de tolerância)
        if (!autenticado || System.currentTimeMillis() - saiuEm > 120_000) {
            autenticado = false;
            startActivity(LockActivity.paraProprioApp(this));
            overridePendingTransition(0, 0);
            return;
        }
        montar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (autenticado) saiuEm = System.currentTimeMillis();
    }

    // ================================================================== tela

    private void montar() {
        conteudo.removeAllViews();
        TextView t = Ui.texto(this, "Trava Apps", 30, k.texto, true);
        t.setPadding(dp(4), dp(8), 0, 0);
        conteudo.addView(t);
        TextView st = Ui.texto(this, "Seus apps protegidos pelo seu rosto", 14, k.sub, false);
        st.setPadding(dp(4), 0, 0, dp(4));
        conteudo.addView(st);

        int novos = Prefs.novosIntrusos(this);
        if (novos > 0) {
            LinearLayout al = Ui.linha(this);
            al.setBackground(Ui.clicavel(Ui.escuro(this) ? 0xFF4C1D2B : 0xFFFDE2E7, dp(20), 0x22000000));
            al.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView tx = Ui.texto(this, "⚠️  " + novos + (novos == 1 ? " tentativa de invasão registrada" : " tentativas de invasão registradas") + "\nToque para ver as fotos", 15,
                    Ui.escuro(this) ? 0xFFFDA4AF : 0xFF9F1239, true);
            al.addView(tx, Ui.lp(0, -2, 1f));
            al.setOnClickListener(v -> startActivity(new Intent(this, IntrusosActivity.class)));
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = dp(12);
            conteudo.addView(al, lp);
        }

        // ---- passos
        LinearLayout passos = Ui.secao(this, conteudo, "Proteção", null);
        boolean servico = AppLockService.ativo(this);
        boolean cam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        passo(passos, Prefs.temPin(this), "PIN de segurança", Prefs.temPin(this) ? "Usado quando o rosto não puder ser lido" : "Crie um PIN reserva",
                Prefs.temPin(this) ? "Alterar" : "Criar", v -> criarPin());
        passo(passos, Prefs.temRosto(this) && cam, "Rosto cadastrado",
                !cam ? "Permita o uso da câmera" : Prefs.temRosto(this) ? "Desbloqueio pelo rosto ativo" : "Cadastre seu rosto para desbloquear",
                Prefs.temRosto(this) ? "Refazer" : "Cadastrar", v -> comCamera(() -> startActivity(new Intent(this, CadastroActivity.class))));
        passo(passos, servico, "Serviço de bloqueio", servico ? "Ativo — os apps marcados pedem desbloqueio" : "Desativado — nada é bloqueado ainda",
                servico ? "Ajustes" : "Ativar", v -> ativarServico());

        // ---- intrusos
        List<java.io.File> fotos = Prefs.intrusos(this);
        LinearLayout intr = Ui.linha(this);
        intr.setBackground(Ui.clicavel(k.superficie, dp(22), 0x22000000));
        intr.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout ti = Ui.coluna(this);
        ti.addView(Ui.texto(this, "📸  Intrusos", 16, k.texto, true));
        ti.addView(Ui.texto(this, fotos.isEmpty() ? "Fotos de quem errar o desbloqueio aparecem aqui"
                : fotos.size() + (fotos.size() == 1 ? " foto registrada" : " fotos registradas"), 13, k.sub, false));
        intr.addView(ti, Ui.lp(0, -2, 1f));
        intr.addView(Ui.texto(this, "›", 26, k.sub, false));
        intr.setOnClickListener(v -> startActivity(new Intent(this, IntrusosActivity.class)));
        LinearLayout.LayoutParams lpi = Ui.lp(-1, -2);
        lpi.topMargin = dp(12);
        conteudo.addView(intr, lpi);

        montarAjustes();

        // ---- apps
        LinearLayout sec = Ui.secao(this, conteudo, "Apps", null);
        resumoApps = Ui.texto(this, "", 13, k.sub, false);
        sec.addView(resumoApps);
        EditText q = new EditText(this);
        q.setHint("🔍  Buscar app");
        q.setText(busca);
        q.setSingleLine();
        q.setTextColor(k.texto);
        q.setHintTextColor(k.sub);
        q.setBackground(Ui.fundo(k.cartao, dp(14)));
        q.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lq = Ui.lp(-1, -2);
        lq.topMargin = dp(10);
        sec.addView(q, lq);
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable e) {
                busca = e.toString();
                desenharApps();
            }
        });
        listaApps = Ui.coluna(this);
        sec.addView(listaApps);
        desenharApps();
    }

    private void passo(LinearLayout pai, boolean ok, String titulo, String sub, String botao, View.OnClickListener cl) {
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(12), 0, 0);
        TextView ic = Ui.texto(this, ok ? "✓" : "!", 16, 0xFFFFFFFF, true);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(ok ? 0xFF16A34A : 0xFFF59E0B);
        ic.setBackground(g);
        l.addView(ic, Ui.lp(dp(30), dp(30)));
        LinearLayout tx = Ui.coluna(this);
        tx.setPadding(dp(12), 0, dp(8), 0);
        tx.addView(Ui.texto(this, titulo, 15, k.texto, true));
        tx.addView(Ui.texto(this, sub, 12.5f, k.sub, false));
        l.addView(tx, Ui.lp(0, -2, 1f));
        Button b = Ui.botao(this, botao, !ok);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setTextSize(13.5f);
        b.setOnClickListener(cl);
        l.addView(b, Ui.lp(-2, dp(40)));
        pai.addView(l);
    }

    private void montarAjustes() {
        LinearLayout s = Ui.secao(this, conteudo, "Ajustes", null);
        Switch piscar = interruptor(s, "Pedir para piscar", "Impede que alguém desbloqueie com uma foto sua", Prefs.exigirPiscar(this));
        piscar.setOnCheckedChangeListener((v, on) -> Prefs.exigirPiscar(this, on));
        Switch foto = interruptor(s, "Foto do intruso", "Tira uma foto com a câmera frontal após várias tentativas erradas", Prefs.fotoIntruso(this));
        foto.setOnCheckedChangeListener((v, on) -> Prefs.fotoIntruso(this, on));

        rotulo(s, "Tirar a foto depois de");
        s.addView(new Ui.Chips(this, new String[]{"3", "5", "10"}, new String[]{"3 erros", "5 erros", "10 erros"},
                String.valueOf(Prefs.tentativas(this)), id -> Prefs.tentativas(this, Integer.parseInt(id))).view);
        rotulo(s, "Rigor do reconhecimento");
        s.addView(new Ui.Chips(this, new String[]{"flexivel", "normal", "alto"}, new String[]{"Flexível", "Normal", "Alto"},
                Prefs.rigor(this), id -> Prefs.rigor(this, id)).view);
        rotulo(s, "Pedir de novo ao voltar para um app");
        s.addView(new Ui.Chips(this, new String[]{"0", "1", "5", "15"}, new String[]{"Sempre", "Após 1 min", "Após 5 min", "Após 15 min"},
                String.valueOf(Prefs.tolerancia(this)), id -> Prefs.tolerancia(this, Integer.parseInt(id))).view);
        TextView dica = Ui.texto(this, "Ao desligar a tela, todos os apps voltam a pedir desbloqueio.", 12.5f, k.sub, false);
        dica.setPadding(0, dp(8), 0, 0);
        s.addView(dica);
    }

    private void rotulo(LinearLayout s, String t) {
        TextView r = Ui.texto(this, t, 14, k.texto, true);
        r.setPadding(0, dp(14), 0, 0);
        s.addView(r);
    }

    private Switch interruptor(LinearLayout pai, String t, String sub, boolean on) {
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(12), 0, 0);
        LinearLayout tx = Ui.coluna(this);
        tx.addView(Ui.texto(this, t, 15, k.texto, false));
        tx.addView(Ui.texto(this, sub, 12.5f, k.sub, false));
        l.addView(tx, Ui.lp(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        l.addView(sw);
        pai.addView(l);
        return sw;
    }

    // ================================================================== apps

    private void carregarApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
            Set<String> vistos = new HashSet<>();
            List<AppItem> out = new ArrayList<>();
            for (ResolveInfo r : l) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName()) || !vistos.add(p)) continue;
                AppItem a = new AppItem();
                a.pkg = p;
                a.nome = String.valueOf(r.loadLabel(pm));
                out.add(a);
            }
            Collator col = Collator.getInstance(new Locale("pt", "BR"));
            out.sort((x, y) -> col.compare(x.nome, y.nome));
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(out);
                desenharApps();
            });
        }).start();
    }

    private void desenharApps() {
        if (listaApps == null) return;
        listaApps.removeAllViews();
        Set<String> bloq = Prefs.bloqueados(this);
        resumoApps.setText(apps.isEmpty() ? "Carregando apps…" : bloq.size() + (bloq.size() == 1 ? " app bloqueado" : " apps bloqueados") + " de " + apps.size());
        String q = busca.trim().toLowerCase(Locale.ROOT);
        List<AppItem> ordem = new ArrayList<>();
        for (AppItem a : apps) if (bloq.contains(a.pkg)) ordem.add(a);
        for (AppItem a : apps) if (!bloq.contains(a.pkg)) ordem.add(a);
        for (AppItem a : ordem) {
            if (!q.isEmpty() && !a.nome.toLowerCase(Locale.ROOT).contains(q)) continue;
            LinearLayout l = Ui.linha(this);
            l.setPadding(0, dp(10), 0, dp(10));
            ImageView iv = new ImageView(this);
            l.addView(iv, Ui.lp(dp(40), dp(40)));
            icone(a.pkg, iv);
            TextView n = Ui.texto(this, a.nome, 15.5f, k.texto, false);
            n.setPadding(dp(14), 0, dp(8), 0);
            l.addView(n, Ui.lp(0, -2, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(bloq.contains(a.pkg));
            sw.setOnCheckedChangeListener((v, on) -> {
                Prefs.bloquear(this, a.pkg, on);
                Set<String> b2 = Prefs.bloqueados(this);
                resumoApps.setText(b2.size() + (b2.size() == 1 ? " app bloqueado" : " apps bloqueados") + " de " + apps.size());
                if (on && !AppLockService.ativo(this)) Toast.makeText(this, "Ative o serviço de bloqueio para valer", Toast.LENGTH_SHORT).show();
            });
            l.addView(sw);
            l.setOnClickListener(v -> sw.toggle());
            listaApps.addView(l);
        }
    }

    private void icone(String pkg, ImageView iv) {
        Drawable d = icones.get(pkg);
        if (d != null) {
            iv.setImageDrawable(d);
            return;
        }
        iv.setTag(pkg);
        Ui.FUNDO.execute(() -> {
            Drawable x;
            try {
                x = getPackageManager().getApplicationIcon(pkg);
            } catch (Exception e) {
                return;
            }
            icones.put(pkg, x);
            Ui.MAIN.post(() -> {
                if (pkg.equals(iv.getTag())) iv.setImageDrawable(x);
            });
        });
    }

    // ================================================================== ações

    private void comCamera(Runnable r) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            r.run();
            return;
        }
        depoisDaCamera = r;
        pedirCamera.launch(Manifest.permission.CAMERA);
    }

    private void boasVindas() {
        new AlertDialog.Builder(this)
                .setTitle("Bem-vindo ao Trava Apps 🔒")
                .setMessage("Vamos configurar em 3 passos:\n\n1. Criar um PIN reserva\n2. Cadastrar seu rosto\n3. Ativar o serviço de bloqueio\n\nDepois é só marcar quais apps devem ser bloqueados.")
                .setCancelable(false)
                .setPositiveButton("Começar", (d, w) -> criarPin())
                .show();
    }

    private void criarPin() {
        final Dialog dlg = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF3730A3, 0xFF1E1B4B, 0xFF0B0A1A}));
        LinearLayout col = Ui.coluna(this);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(24), dp(40), dp(24), dp(40));
        raiz.addView(col, new FrameLayout.LayoutParams(-1, -1));
        TextView titulo = Ui.texto(this, "Crie seu PIN", 22, 0xFFFFFFFF, true);
        col.addView(titulo);
        TextView sub = Ui.texto(this, "4 números · usado quando o rosto não puder ser lido", 14, 0xB3FFFFFF, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(26));
        col.addView(sub);
        final String[] primeiro = {null};
        final int[] tam = {4};
        final PinPad[] pad = new PinPad[1];
        final Runnable[] alternar = new Runnable[1];
        pad[0] = new PinPad(this, 4, v -> {
            if (primeiro[0] == null) {
                primeiro[0] = v;
                titulo.setText("Confirme o PIN");
                sub.setText("Digite de novo");
                pad[0].limpar();
                pad[0].setAcaoEsquerda("", null);
            } else if (primeiro[0].equals(v)) {
                pad[0].sucesso();
                Prefs.definirPin(this, v);
                autenticado();
                Toast.makeText(this, "PIN criado ✓", Toast.LENGTH_SHORT).show();
                dlg.dismiss();
                montar();
                if (!Prefs.temRosto(this)) comCamera(() -> startActivity(new Intent(this, CadastroActivity.class)));
            } else {
                primeiro[0] = null;
                pad[0].erro();
                titulo.setText("Os PINs não conferem");
                sub.setText("Crie o PIN de novo");
                pad[0].setAcaoEsquerda(tam[0] == 4 ? "6 dígitos" : "4 dígitos", alternar[0]);
            }
        });
        alternar[0] = () -> {
            tam[0] = tam[0] == 4 ? 6 : 4;
            pad[0].setTamanho(tam[0]);
            primeiro[0] = null;
            titulo.setText("Crie seu PIN");
            sub.setText(tam[0] + " números · usado quando o rosto não puder ser lido");
            pad[0].setAcaoEsquerda(tam[0] == 4 ? "6 dígitos" : "4 dígitos", alternar[0]);
        };
        pad[0].setAcaoEsquerda("6 dígitos", alternar[0]);
        col.addView(pad[0]);
        if (Prefs.temPin(this)) {
            TextView c = Ui.texto(this, "Cancelar", 15, 0xFFFFFFFF, true);
            c.setPadding(dp(20), dp(24), dp(20), dp(12));
            c.setOnClickListener(v -> dlg.dismiss());
            col.addView(c);
        }
        dlg.setContentView(raiz);
        dlg.setCancelable(Prefs.temPin(this));
        dlg.show();
    }

    private void ativarServico() {
        boolean restrito = Build.VERSION.SDK_INT >= 33;
        new AlertDialog.Builder(this)
                .setTitle("Ativar o serviço de bloqueio")
                .setMessage("O Trava Apps usa a Acessibilidade do Android só para saber qual app foi aberto. Ele não lê o que está na tela.\n\n"
                        + "1. Toque em \"Abrir Acessibilidade\"\n2. Vá em Apps instalados (ou Serviços baixados) → Trava Apps\n3. Ative a chave e confirme"
                        + (restrito ? "\n\nSe a opção aparecer apagada (\"configuração restrita\"): toque em \"Informações do app\", depois no menu ⋮ do canto superior direito → \"Permitir configurações restritas\", e volte ao passo 1." : ""))
                .setPositiveButton("Abrir Acessibilidade", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNeutralButton(restrito ? "Informações do app" : null, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Agora não", null)
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
