package com.example.wifiroomlocator;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException; // Required import
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.CredentialManagerCallback;

import com.google.android.gms.common.SignInButton;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private FirebaseAuth mAuth;
    private CredentialManager credentialManager;
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);

        if (mAuth.getCurrentUser() != null) {
            updateUI(mAuth.getCurrentUser());
        }

        setContentView(R.layout.activity_login);

        SignInButton signInButton = findViewById(R.id.googleSignInBtn);
        signInButton.setOnClickListener(v -> startCredentialManagerSignIn());
    }

    private void startCredentialManagerSignIn() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // Use the main thread executor for UI-related callbacks
        Executor executor = ContextCompat.getMainExecutor(this);
        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                executor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignInResponse(result.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.e(TAG, "Credential Manager Error: " + e.getMessage());
                        Toast.makeText(LoginActivity.this, "Sign In Failed", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void handleSignInResponse(Credential credential) {
        if (credential instanceof CustomCredential &&
                credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {

            try {
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
            } catch (Exception e) {
                Log.e(TAG, "Error parsing Google ID Token");
            }
        } else {
            Log.w(TAG, "Credential is not of type Google ID!");
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        saveUserToDatabase(user);
                        updateUI(user);
                    } else {
                        updateUI(null);
                    }
                });
    }

    private void saveUserToDatabase(FirebaseUser user) {
        if (user == null) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance(dbUrl).getReference("users").child(user.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // If the user does not exist, create them.
                    User newUser = new User(user.getUid(), user.getDisplayName(), user.getEmail());
                    userRef.setValue(newUser);
                }
                // If user exists, do nothing. This is the critical fix.
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check for user existence: " + error.getMessage());
            }
        });
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
        }
    }

    public void signOut() {
        mAuth.signOut();
        ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
        credentialManager.clearCredentialStateAsync(
                clearRequest,
                new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<Void, ClearCredentialException>() { // Corrected type
                    @Override
                    public void onResult(Void result) {
                        Log.d(TAG, "Credentials cleared");
                    }
                    @Override
                    public void onError(@NonNull ClearCredentialException e) { // Corrected type
                        Log.e(TAG, "Error clearing credentials");
                    }
                });
    }
}
