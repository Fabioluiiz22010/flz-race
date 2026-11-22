package com.example.flzrace;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class BoasVindasActivity extends AppCompatActivity {
    private static final String EXTRA_EDITAR = "editar";
    private static final int LADO_AVATAR = 400;

    public static Intent criarIntentEdicao(Context ctx) {
        return new Intent(ctx, BoasVindasActivity.class).putExtra(EXTRA_EDITAR, true);
    }

    private boolean editando;
    private View grupoLogin, grupoCadastro, progresso;
    private ShapeableImageView imgFotoCarro;
    private View icoCamera;
    private TextInputLayout campoNome, campoCarro;
    private TextInputEditText edtNome, edtCarro;
    private MaterialCheckBox chkAviso;

    private CredentialManager credentialManager;
    private String nomeGoogle;

    private final ActivityResultLauncher<Intent> abrirCamera = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == RESULT_OK) atualizarFoto();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_boas_vindas);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.boasVindas), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        editando = getIntent().getBooleanExtra(EXTRA_EDITAR, false);
        credentialManager = CredentialManager.create(this);

        grupoLogin = findViewById(R.id.grupoLogin);
        grupoCadastro = findViewById(R.id.grupoCadastro);
        progresso = findViewById(R.id.progressoEntrada);
        imgFotoCarro = findViewById(R.id.imgFotoCarro);
        icoCamera = findViewById(R.id.icoCamera);
        campoNome = findViewById(R.id.campoNome);
        campoCarro = findViewById(R.id.campoCarro);
        edtNome = findViewById(R.id.edtNome);
        edtCarro = findViewById(R.id.edtCarro);
        chkAviso = findViewById(R.id.chkAviso);

        findViewById(R.id.btnGoogle).setOnClickListener(v -> entrarComGoogle());
        findViewById(R.id.btnFoto).setOnClickListener(v ->
                abrirCamera.launch(new Intent(this, CameraActivity.class)));
        findViewById(R.id.btnEntrar).setOnClickListener(v -> concluirCadastro());

        if (editando) {
            ((TextView) findViewById(R.id.txtSlogan)).setText("Editar perfil do piloto");
            ((TextView) findViewById(R.id.btnEntrar)).setText("SALVAR");
            mostrarCadastro();
        } else if (Nuvem.logado()) {
            carregarPerfilDaNuvem();
        } else {
            mostrarLogin();
        }
    }

    private void mostrarLogin() {
        grupoLogin.setVisibility(View.VISIBLE);
        grupoCadastro.setVisibility(View.GONE);
        progresso.setVisibility(View.GONE);
    }

    private void mostrarCarregando() {
        grupoLogin.setVisibility(View.GONE);
        grupoCadastro.setVisibility(View.GONE);
        progresso.setVisibility(View.VISIBLE);
    }

    private void mostrarCadastro() {
        grupoLogin.setVisibility(View.GONE);
        progresso.setVisibility(View.GONE);
        grupoCadastro.setVisibility(View.VISIBLE);

        String nome = Estado.cadastrado(this) || editando ? Estado.nomePiloto(this) : nomeGoogle;
        if (nome != null && texto(edtNome).isEmpty()) edtNome.setText(nome.toUpperCase(Estado.LOCALE));
        if (texto(edtCarro).isEmpty()) edtCarro.setText(Estado.modeloCarro(this));
        if (Estado.aceitouAviso(this)) chkAviso.setChecked(true);
        atualizarFoto();
    }

    private void entrarComGoogle() {
        mostrarCarregando();

        GetSignInWithGoogleOption opcao =
                new GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build();
        GetCredentialRequest pedido = new GetCredentialRequest.Builder()
                .addCredentialOption(opcao)
                .build();

        credentialManager.getCredentialAsync(this, pedido, new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse resposta) {
                        tratarCredencial(resposta.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        mostrarLogin();
                        if (!(e instanceof GetCredentialCancellationException)) {
                            Toast.makeText(BoasVindasActivity.this,
                                    "Não foi possível entrar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void tratarCredencial(Credential credencial) {
        if (!(credencial instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credencial.getType())) {
            mostrarLogin();
            Toast.makeText(this, "Tipo de login não reconhecido", Toast.LENGTH_LONG).show();
            return;
        }
        GoogleIdTokenCredential google;
        try {
            google = GoogleIdTokenCredential.createFrom(credencial.getData());
        } catch (Exception e) {
            mostrarLogin();
            Toast.makeText(this, "Resposta do Google inválida", Toast.LENGTH_LONG).show();
            return;
        }
        nomeGoogle = google.getDisplayName();

        Nuvem.entrarComGoogle(google.getIdToken(), (usuario, erro) -> {
            if (usuario == null) {
                mostrarLogin();
                Toast.makeText(this, "Falha no login: " + erro, Toast.LENGTH_LONG).show();
            } else {
                carregarPerfilDaNuvem();
            }
        });
    }

    private void carregarPerfilDaNuvem() {
        mostrarCarregando();
        Nuvem.carregarPerfil((perfil, erro) -> {
            if (erro != null) {
                Toast.makeText(this, "Não foi possível buscar seu perfil: " + erro, Toast.LENGTH_LONG).show();
                mostrarCadastro();
                return;
            }
            if (perfil != null && perfil.nome != null && perfil.aceitouAviso) {
                Estado.salvarPerfil(this, perfil.nome, perfil.carro != null ? perfil.carro : "");
                Estado.marcarAvisoAceito(this);
                if (!FotoCarro.existe(this)) FotoCarro.salvarDeBase64(this, perfil.foto);
                Estado.restaurarRecordes(this, (float) perfil.melhor0a100, (float) perfil.melhorMax);
                irParaInicio();
            } else {
                mostrarCadastro();
            }
        });
    }

    private void atualizarFoto() {
        Bitmap foto = FotoCarro.carregar(this, LADO_AVATAR);
        if (foto != null) {
            imgFotoCarro.setImageBitmap(foto);
            icoCamera.setVisibility(View.GONE);
        } else {
            imgFotoCarro.setImageDrawable(null);
            icoCamera.setVisibility(View.VISIBLE);
        }
    }

    private void concluirCadastro() {
        String nome = texto(edtNome).toUpperCase(Estado.LOCALE);
        String carro = texto(edtCarro);
        campoNome.setError(null);
        campoCarro.setError(null);

        if (nome.length() < 2) {
            campoNome.setError("Informe seu nome de piloto");
            edtNome.requestFocus();
            return;
        }
        if (carro.length() < 2) {
            campoCarro.setError("Informe o modelo do carro");
            edtCarro.requestFocus();
            return;
        }
        if (!chkAviso.isChecked()) {
            Toast.makeText(this, "Aceite o aviso de segurança para continuar", Toast.LENGTH_LONG).show();
            View caixa = findViewById(R.id.caixaAviso);
            caixa.animate().translationX(20).setDuration(60)
                    .withEndAction(() -> caixa.animate().translationX(0).setDuration(60));
            return;
        }

        Estado.salvarPerfil(this, nome, carro);
        Estado.marcarAvisoAceito(this);
        Nuvem.salvarPerfil(this);

        if (editando) finish();
        else irParaInicio();
    }

    private void irParaInicio() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
