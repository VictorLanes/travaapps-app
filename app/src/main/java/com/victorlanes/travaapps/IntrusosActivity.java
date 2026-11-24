package com.victorlanes.travaapps;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Galeria das fotos tiradas de quem errou o desbloqueio. */
public class IntrusosActivity extends AppCompatActivity {

    private Ui.Cores k;
    private List<File> fotos;
    private GridView grade;
    private TextView vazio, resumo;
    private final Map<String, Bitmap> miniaturas = new HashMap<>();
    private final Map<String, String> nomes = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        k = Ui.cores(this);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        Prefs.novosIntrusos(this, 0);

        LinearLayout raiz = Ui.coluna(this);
        raiz.setBackgroundColor(k.fundo);
        setContentView(raiz);
        Ui.aplicarInsets(raiz);

        LinearLayout topo = Ui.linha(this);
        topo.setPadding(dp(8), dp(8), dp(16), dp(4));
        TextView voltar = Ui.texto(this, "‹", 32, k.texto, false);
        voltar.setPadding(dp(12), 0, dp(12), dp(4));
        voltar.setOnClickListener(v -> finish());
        topo.addView(voltar);
        LinearLayout tt = Ui.coluna(this);
        tt.addView(Ui.texto(this, "Intrusos", 24, k.texto, true));
        resumo = Ui.texto(this, "", 13, k.sub, false);
        tt.addView(resumo);
        topo.addView(tt, Ui.lp(0, -2, 1f));
        TextView limpar = Ui.texto(this, "Apagar tudo", 14, 0xFFE11D48, true);
        limpar.setPadding(dp(12), dp(10), dp(4), dp(10));
        limpar.setOnClickListener(v -> apagarTudo());
        topo.addView(limpar);
        raiz.addView(topo);

        FrameLayout corpo = new FrameLayout(this);
        raiz.addView(corpo, Ui.lp(-1, 0, 1f));
        grade = new GridView(this);
        grade.setNumColumns(2);
        grade.setHorizontalSpacing(dp(10));
        grade.setVerticalSpacing(dp(10));
        grade.setPadding(dp(16), dp(8), dp(16), dp(16));
        grade.setClipToPadding(false);
        corpo.addView(grade);
        vazio = Ui.texto(this, "📸\n\nNenhum intruso registrado.\nQuando alguém errar o desbloqueio várias vezes, a foto aparece aqui.", 15, k.sub, false);
        vazio.setGravity(Gravity.CENTER);
        vazio.setPadding(dp(32), 0, dp(32), 0);
        corpo.addView(vazio, new FrameLayout.LayoutParams(-1, -1));
        carregar();
    }

    private void carregar() {
        fotos = Prefs.intrusos(this);
        vazio.setVisibility(fotos.isEmpty() ? View.VISIBLE : View.GONE);
        resumo.setText(fotos.size() + (fotos.size() == 1 ? " foto" : " fotos"));
        final int larg = (getResources().getDisplayMetrics().widthPixels - dp(42)) / 2;
        grade.setAdapter(new BaseAdapter() {
            public int getCount() {
                return fotos.size();
            }

            public Object getItem(int p) {
                return fotos.get(p);
            }

            public long getItemId(int p) {
                return p;
            }

            public View getView(int p, View v, ViewGroup g) {
                LinearLayout card;
                if (v == null) {
                    card = Ui.coluna(IntrusosActivity.this);
                    card.setLayoutParams(new AbsListView.LayoutParams(larg, -2));
                    card.setBackground(Ui.fundo(k.superficie, dp(16)));
                    card.setClipToOutline(true);
                    ImageView iv = new ImageView(IntrusosActivity.this);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundColor(k.cartao);
                    card.addView(iv, Ui.lp(-1, (int) (larg * 1.25f)));
                    TextView t = Ui.texto(IntrusosActivity.this, "", 13, k.texto, true);
                    t.setPadding(dp(10), dp(8), dp(10), 0);
                    card.addView(t);
                    TextView d = Ui.texto(IntrusosActivity.this, "", 12, k.sub, false);
                    d.setPadding(dp(10), 0, dp(10), dp(10));
                    card.addView(d);
                } else card = (LinearLayout) v;
                File f = fotos.get(p);
                ((TextView) card.getChildAt(1)).setText(nomeApp(f));
                ((TextView) card.getChildAt(2)).setText(quando(f));
                miniatura((ImageView) card.getChildAt(0), f, larg);
                return card;
            }
        });
        grade.setOnItemClickListener((parent, view, pos, id) -> abrir(fotos.get(pos)));
    }

    private void miniatura(ImageView iv, File f, int larg) {
        Bitmap c = miniaturas.get(f.getName());
        iv.setTag(f.getName());
        iv.setImageBitmap(c);
        if (c != null) return;
        Ui.FUNDO.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b == null) return;
            miniaturas.put(f.getName(), b);
            Ui.MAIN.post(() -> {
                if (f.getName().equals(iv.getTag())) iv.setImageBitmap(b);
            });
        });
    }

    private long momento(File f) {
        try {
            return Long.parseLong(f.getName().split("__")[0]);
        } catch (Exception e) {
            return f.lastModified();
        }
    }

    private String quando(File f) {
        return new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", new Locale("pt", "BR")).format(new Date(momento(f)));
    }

    private String nomeApp(File f) {
        String n = f.getName();
        String pkg = n.contains("__") ? n.substring(n.indexOf("__") + 2, n.length() - 4) : "";
        String c = nomes.get(pkg);
        if (c != null) return c;
        String r;
        if (pkg.equals(getPackageName())) r = "Trava Apps";
        else {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                r = String.valueOf(pm.getApplicationLabel(ai));
            } catch (Exception e) {
                r = pkg.isEmpty() ? "App" : pkg;
            }
        }
        nomes.put(pkg, r);
        return r;
    }

    private void abrir(File f) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(0xFF000000);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
        raiz.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout rod = Ui.coluna(this);
        rod.setBackgroundColor(0xAA000000);
        rod.setPadding(dp(20), dp(14), dp(20), dp(20));
        rod.addView(Ui.texto(this, "Tentou abrir: " + nomeApp(f), 17, 0xFFFFFFFF, true));
        rod.addView(Ui.texto(this, quando(f), 14, 0xCCFFFFFF, false));
        LinearLayout bt = Ui.linha(this);
        bt.setPadding(0, dp(12), 0, 0);
        android.widget.Button salvar = Ui.botao(this, "Salvar na galeria", true);
        salvar.setOnClickListener(v -> salvarNaGaleria(f));
        bt.addView(salvar, Ui.lp(0, -2, 1f));
        android.widget.Button apagar = Ui.botao(this, "Apagar", false);
        apagar.setTextColor(0xFFE11D48);
        apagar.setOnClickListener(v -> {
            f.delete();
            miniaturas.remove(f.getName());
            d.dismiss();
            carregar();
        });
        LinearLayout.LayoutParams la = Ui.lp(0, -2, 1f);
        la.leftMargin = dp(10);
        bt.addView(apagar, la);
        rod.addView(bt);
        TextView fechar = Ui.texto(this, "Fechar", 15, 0xFFFFFFFF, true);
        fechar.setGravity(Gravity.CENTER);
        fechar.setPadding(0, dp(14), 0, 0);
        fechar.setOnClickListener(v -> d.dismiss());
        rod.addView(fechar, Ui.lp(-1, -2));
        raiz.addView(rod, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        Ui.aplicarInsets(rod);
        d.setContentView(raiz);
        d.show();
    }

    private void salvarNaGaleria(File f) {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Disponível no Android 10 ou mais novo", Toast.LENGTH_SHORT).show();
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "intruso_" + momento(f) + ".jpg");
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TravaApps");
        Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (u == null) return;
        try (InputStream in = new FileInputStream(f); OutputStream out = getContentResolver().openOutputStream(u)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            Toast.makeText(this, "Salva em Imagens/TravaApps", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível salvar", Toast.LENGTH_SHORT).show();
        }
    }

    private void apagarTudo() {
        if (fotos.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Apagar todas as fotos?")
                .setMessage("As " + fotos.size() + " fotos de intrusos serão apagadas. Isso não pode ser desfeito.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar", (dd, w) -> {
                    for (File f : fotos) f.delete();
                    miniaturas.clear();
                    carregar();
                })
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
