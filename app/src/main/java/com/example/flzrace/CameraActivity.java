package com.example.flzrace;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    private static final int LADO_PREVIA = 1280;

    private PreviewView previewView;
    private ImageView imgCapturada;
    private View moldura, grupoCaptura, grupoConfirmar;
    private ImageButton btnCapturar;

    private ImageCapture imageCapture;
    private ExecutorService executor;

    private final ActivityResultLauncher<String> pedirCamera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), concedida -> {
                if (concedida) {
                    iniciarCamera();
                } else {
                    Toast.makeText(this, "Permita a câmera para tirar a foto do carro",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.camera), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        previewView = findViewById(R.id.previewView);
        imgCapturada = findViewById(R.id.imgCapturada);
        moldura = findViewById(R.id.moldura);
        grupoCaptura = findViewById(R.id.grupoCaptura);
        grupoConfirmar = findViewById(R.id.grupoConfirmar);
        btnCapturar = findViewById(R.id.btnCapturar);

        executor = Executors.newSingleThreadExecutor();

        findViewById(R.id.btnVoltarCamera).setOnClickListener(v -> {
            FotoCarro.descartarPendente(this);
            finish();
        });
        btnCapturar.setOnClickListener(v -> tirarFoto());
        findViewById(R.id.btnTirarOutra).setOnClickListener(v -> mostrarCamera());
        findViewById(R.id.btnUsarFoto).setOnClickListener(v -> usarFoto());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarCamera();
        } else {
            pedirCamera.launch(Manifest.permission.CAMERA);
        }
    }

    private void iniciarCamera() {
        ListenableFuture<ProcessCameraProvider> futuro = ProcessCameraProvider.getInstance(this);
        futuro.addListener(() -> {
            try {
                ProcessCameraProvider provider = futuro.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Erro ao abrir a câmera: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void tirarFoto() {
        if (imageCapture == null) return;
        btnCapturar.setEnabled(false);

        ImageCapture.OutputFileOptions opcoes =
                new ImageCapture.OutputFileOptions.Builder(FotoCarro.captura(this)).build();

        imageCapture.takePicture(opcoes, executor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults resultado) {
                boolean ok = FotoCarro.processarCaptura(CameraActivity.this);
                Bitmap previa = ok ? FotoCarro.carregarPendente(CameraActivity.this, LADO_PREVIA) : null;
                runOnUiThread(() -> {
                    btnCapturar.setEnabled(true);
                    if (previa != null) {
                        mostrarConfirmacao(previa);
                    } else {
                        Toast.makeText(CameraActivity.this, "Não foi possível salvar a foto",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException erro) {
                runOnUiThread(() -> {
                    btnCapturar.setEnabled(true);
                    Toast.makeText(CameraActivity.this, "Erro ao tirar a foto: " + erro.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void mostrarConfirmacao(Bitmap foto) {
        imgCapturada.setImageBitmap(foto);
        imgCapturada.setVisibility(View.VISIBLE);
        moldura.setVisibility(View.GONE);
        grupoCaptura.setVisibility(View.GONE);
        grupoConfirmar.setVisibility(View.VISIBLE);
    }

    private void mostrarCamera() {
        FotoCarro.descartarPendente(this);
        imgCapturada.setImageDrawable(null);
        imgCapturada.setVisibility(View.GONE);
        moldura.setVisibility(View.VISIBLE);
        grupoConfirmar.setVisibility(View.GONE);
        grupoCaptura.setVisibility(View.VISIBLE);
    }

    private void usarFoto() {
        if (FotoCarro.confirmar(this)) {
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "Não foi possível salvar a foto", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
