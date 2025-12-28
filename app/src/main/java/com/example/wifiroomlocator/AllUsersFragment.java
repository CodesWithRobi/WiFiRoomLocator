package com.example.wifiroomlocator;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

public class AllUsersFragment extends Fragment {

    private static final String TAG = "AllUsersFragment";
    private EditText emailSearchEditText;
    private ImageButton sendRequestButton;
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_users, container, false);

        emailSearchEditText = view.findViewById(R.id.emailSearchEditText);
        sendRequestButton = view.findViewById(R.id.sendRequestButton);

        sendRequestButton.setOnClickListener(v -> {
            String email = emailSearchEditText.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(getContext(), "Please enter an email address", Toast.LENGTH_SHORT).show();
                return;
            }
            findUserByEmail(email);
        });

        return view;
    }

    private void findUserByEmail(String email) {
        Query query = FirebaseDatabase.getInstance(dbUrl).getReference("users").orderByChild("email").equalTo(email);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    return;
                }
                if (snapshot.exists()) {
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        User user = userSnapshot.getValue(User.class);
                        if (user != null) {
                            sendFriendRequest(user.uid);
                            return;
                        }
                    }
                } else {
                    Toast.makeText(getContext(), "User not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) {
                    return;
                }
                Log.e(TAG, "Failed to search for user: " + error.getMessage());
            }
        });
    }

    private void sendFriendRequest(String recipientUid) {
        String senderUid = FirebaseAuth.getInstance().getUid();
        if (senderUid == null || senderUid.equals(recipientUid)) {
            return;
        }

        // Create friend request
        FirebaseDatabase.getInstance(dbUrl).getReference("friend_requests").child(recipientUid).child(senderUid).setValue(true);

        // Update UI to show pending status
        sendRequestButton.setImageResource(R.drawable.ic_clock);
        sendRequestButton.setEnabled(false);
        emailSearchEditText.setEnabled(false);

        new Handler().postDelayed(() -> {
            if (isAdded()) {
                sendRequestButton.setImageResource(R.drawable.ic_add);
                sendRequestButton.setEnabled(true);
                emailSearchEditText.setEnabled(true);
                emailSearchEditText.setText("");
            }
        }, 3000); // Revert after 3 seconds
    }
}
