package com.example.myapplicationnew;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.Task;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 1001;

    private GoogleSignInClient googleSignInClient;
    private FirebaseAuth mAuth;
    private CallbackManager callbackManager;

    private EditText etEmail, etPassword;
    private Button btnEmailSignIn;
    private LoginButton btnFacebookSignIn;
    private Button btnGoogleSignIn;
    private TextView tvRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Ініціалізація Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Ініціалізація елементів інтерфейсу
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnEmailSignIn = findViewById(R.id.btnEmailSignIn);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnFacebookSignIn = findViewById(R.id.btnFacebookSignIn);
        tvRegister = findViewById(R.id.tvRegister);

        // Налаштування Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Ініціалізація CallbackManager для Facebook
        callbackManager = CallbackManager.Factory.create();

        // Налаштування кнопки Facebook
        btnFacebookSignIn.setPermissions("email", "public_profile");
        btnFacebookSignIn.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d("FacebookAuth", "Вхід успішний, токен: " + loginResult.getAccessToken().getToken());
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Log.d("FacebookAuth", "Вхід через Facebook скасовано");
                Toast.makeText(LoginActivity.this, "Вхід через Facebook скасовано", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(FacebookException error) {
                Log.e("FacebookAuth", "Помилка входу через Facebook: ", error);
                Toast.makeText(LoginActivity.this, "Помилка входу через Facebook: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // Обробник кліку для входу через Google
        btnGoogleSignIn.setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        // Обробник кліку для входу через email/пароль
        btnEmailSignIn.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Будь ласка, заповніть усі поля", Toast.LENGTH_SHORT).show();
                return;
            }

            signInWithEmail(email, password);
        });

        // Перехід до екрану реєстрації
        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void signInWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String name = mAuth.getCurrentUser().getDisplayName();
                        goToMainActivity(name != null ? name : "Користувач");
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Невідома помилка";
                        Toast.makeText(this, "Помилка аутентифікації: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String name = mAuth.getCurrentUser().getDisplayName();
                        goToMainActivity(name != null ? name : "Користувач Facebook");
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Невідома помилка";
                        Toast.makeText(LoginActivity.this, "Аутентифікація через Facebook не вдалася: " + errorMessage, Toast.LENGTH_LONG).show();
                        Log.e("FacebookAuth", "Помилка: ", task.getException());
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Передача результату до Facebook SDK
        callbackManager.onActivityResult(requestCode, resultCode, data);

        // Обробка результату Google Sign-In
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                Toast.makeText(this, "Помилка входу через Google", Toast.LENGTH_SHORT).show();
                Log.e("GoogleAuth", "Помилка Google Sign-In: ", e);
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        goToMainActivity(acct.getDisplayName());
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Невідома помилка";
                        Toast.makeText(this, "Помилка аутентифікації Google: " + errorMessage, Toast.LENGTH_SHORT).show();
                        Log.e("GoogleAuth", "Помилка: ", task.getException());
                    }
                });
    }

    private void goToMainActivity(String fullName) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("userFullName", fullName);
        startActivity(intent);
        finish();
    }
}