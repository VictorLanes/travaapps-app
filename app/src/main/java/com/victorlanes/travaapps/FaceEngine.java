package com.victorlanes.travaapps;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * Reconhecimento facial no próprio aparelho (nada sai do celular).
 * Detecção e pontos do rosto: ML Kit. Assinatura do rosto (192 números): MobileFaceNet (TensorFlow Lite).
 * Modelo MobileFaceNet: github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing (licença MIT).
 */
public final class FaceEngine {

    public static final int TAM = 112;
    // posição padrão dos olhos, nariz e cantos da boca num rosto 112x112 alinhado (padrão ArcFace/InsightFace)
    private static final float[][] MODELO = {
            {38.2946f, 51.6963f}, {73.5318f, 51.5014f}, {56.0252f, 71.7366f}, {41.5493f, 92.3655f}, {70.7299f, 92.2041f}};

    private static FaceEngine inst;
    private final Interpreter tfl;
    private final float[][][][] entrada = new float[2][TAM][TAM][3];
    private final float[][] saida = new float[2][192];
    private final int[] px = new int[TAM * TAM];

    private FaceEngine(Context c) throws IOException {
        Interpreter.Options o = new Interpreter.Options();
        o.setNumThreads(4);
        tfl = new Interpreter(carregar(c, "MobileFaceNet.tflite"), o);
    }

    public static synchronized FaceEngine get(Context c) throws IOException {
        if (inst == null) inst = new FaceEngine(c.getApplicationContext());
        return inst;
    }

    private static MappedByteBuffer carregar(Context c, String nome) throws IOException {
        try (AssetFileDescriptor fd = c.getAssets().openFd(nome);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
            return in.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    /** Assinatura normalizada (tamanho 1) de um rosto já alinhado em 112x112. */
    public synchronized float[] assinatura(Bitmap rosto112) {
        rosto112.getPixels(px, 0, TAM, 0, 0, TAM, TAM);
        for (int y = 0; y < TAM; y++) {
            for (int x = 0; x < TAM; x++) {
                int v = px[y * TAM + x];
                float r = (((v >> 16) & 0xFF) - 127.5f) / 128f;
                float g = (((v >> 8) & 0xFF) - 127.5f) / 128f;
                float b = ((v & 0xFF) - 127.5f) / 128f;
                // o modelo tem lote fixo de 2: mandamos o mesmo rosto nas duas posições
                entrada[0][y][x][0] = r; entrada[0][y][x][1] = g; entrada[0][y][x][2] = b;
                entrada[1][y][x][0] = r; entrada[1][y][x][1] = g; entrada[1][y][x][2] = b;
            }
        }
        tfl.run(entrada, saida);
        float[] e = saida[0].clone();
        double n = 0;
        for (float f : e) n += f * f;
        n = Math.sqrt(n) + 1e-10;
        for (int i = 0; i < e.length; i++) e[i] /= (float) n;
        return e;
    }

    public static float semelhanca(float[] a, float[] b) {
        float s = 0;
        for (int i = 0; i < a.length && i < b.length; i++) s += a[i] * b[i];
        return s;
    }

    /** maior semelhança entre o rosto e os cadastrados */
    public static float melhor(float[] e, List<float[]> cadastrados) {
        float m = -1;
        for (float[] c : cadastrados) m = Math.max(m, semelhanca(e, c));
        return m;
    }

    /** Recorta e alinha o rosto (olhos, nariz e boca nas posições padrão) em 112x112. */
    public static Bitmap alinhar(Bitmap quadro, Face f) {
        PointF le = ponto(f, FaceLandmark.LEFT_EYE), re = ponto(f, FaceLandmark.RIGHT_EYE), no = ponto(f, FaceLandmark.NOSE_BASE),
                ml = ponto(f, FaceLandmark.MOUTH_LEFT), mr = ponto(f, FaceLandmark.MOUTH_RIGHT);
        Bitmap out = Bitmap.createBitmap(TAM, TAM, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        Matrix m = new Matrix();
        if (le != null && re != null && no != null && ml != null && mr != null) {
            // olhos e cantos da boca ordenados da esquerda para a direita da imagem
            PointF[] olhos = le.x <= re.x ? new PointF[]{le, re} : new PointF[]{re, le};
            PointF[] boca = ml.x <= mr.x ? new PointF[]{ml, mr} : new PointF[]{mr, ml};
            PointF[] src = {olhos[0], olhos[1], no, boca[0], boca[1]};
            semelhancaAfim(src, m);
        } else {
            // sem os pontos: recorte quadrado da caixa do rosto
            Rect r = f.getBoundingBox();
            float lado = Math.max(r.width(), r.height());
            m.setTranslate(-(r.centerX() - lado / 2f), -(r.centerY() - lado / 2f));
            m.postScale(TAM / lado, TAM / lado);
        }
        cv.drawBitmap(quadro, m, p);
        return out;
    }

    private static PointF ponto(Face f, int tipo) {
        FaceLandmark l = f.getLandmark(tipo);
        return l == null ? null : l.getPosition();
    }

    /** transformação de semelhança (escala + rotação + translação) por mínimos quadrados */
    private static void semelhancaAfim(PointF[] src, Matrix m) {
        int n = src.length;
        float smx = 0, smy = 0, dmx = 0, dmy = 0;
        for (int i = 0; i < n; i++) {
            smx += src[i].x; smy += src[i].y;
            dmx += MODELO[i][0]; dmy += MODELO[i][1];
        }
        smx /= n; smy /= n; dmx /= n; dmy /= n;
        float num1 = 0, num2 = 0, den = 0;
        for (int i = 0; i < n; i++) {
            float sx = src[i].x - smx, sy = src[i].y - smy;
            float dx = MODELO[i][0] - dmx, dy = MODELO[i][1] - dmy;
            num1 += sx * dx + sy * dy;
            num2 += sx * dy - sy * dx;
            den += sx * sx + sy * sy;
        }
        if (den == 0) den = 1;
        float a = num1 / den, b = num2 / den;
        float tx = dmx - (a * smx - b * smy);
        float ty = dmy - (b * smx + a * smy);
        m.setValues(new float[]{a, -b, tx, b, a, ty, 0, 0, 1});
    }
}
