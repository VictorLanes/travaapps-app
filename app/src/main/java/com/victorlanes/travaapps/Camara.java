package com.victorlanes.travaapps;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Size;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Câmera frontal (CameraX) + detecção de rosto (ML Kit). Entrega cada quadro já na posição certa. */
public final class Camara {

    public interface Ouvinte {
        /** chamado fora da thread principal. rosto = maior rosto do quadro (ou null) */
        void quadro(Bitmap quadro, Face rosto, int quantidade);
    }

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final FaceDetector detector;
    private ProcessCameraProvider provider;
    private volatile boolean ativa = true;

    public Camara() {
        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.2f)
                .build());
    }

    public void iniciar(AppCompatActivity act, PreviewView pv, Ouvinte ouvinte) {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(act);
        f.addListener(() -> {
            try {
                provider = f.get();
                Preview preview = new Preview.Builder().build();
                if (pv != null) preview.setSurfaceProvider(pv.getSurfaceProvider());
                ResolutionSelector rs = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build();
                ImageAnalysis analise = new ImageAnalysis.Builder()
                        .setResolutionSelector(rs)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analise.setAnalyzer(exec, img -> processar(img, ouvinte));
                provider.unbindAll();
                provider.bindToLifecycle(act, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analise);
            } catch (Exception e) {
                ouvinte.quadro(null, null, -1);
            }
        }, ContextCompat.getMainExecutor(act));
    }

    private void processar(ImageProxy img, Ouvinte ouvinte) {
        try {
            if (!ativa) return;
            Bitmap b = img.toBitmap();
            int rot = img.getImageInfo().getRotationDegrees();
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            List<Face> faces = Tasks.await(detector.process(InputImage.fromBitmap(b, 0)));
            Face maior = null;
            for (Face f : faces) {
                if (maior == null || f.getBoundingBox().width() > maior.getBoundingBox().width()) maior = f;
            }
            ouvinte.quadro(b, maior, faces.size());
        } catch (Exception ignored) {
        } finally {
            img.close();
        }
    }

    public void parar() {
        ativa = false;
        if (provider != null) provider.unbindAll();
        exec.shutdown();
        try {
            detector.close();
        } catch (Exception ignored) {
        }
    }
}
