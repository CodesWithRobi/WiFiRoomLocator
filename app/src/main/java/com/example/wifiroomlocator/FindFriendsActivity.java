package com.example.wifiroomlocator;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FindFriendsActivity extends AppCompatActivity {

    private static final String TAG = "FindFriendsActivity";
    private RecyclerView recyclerView;
    private FindFriendsAdapter adapter;
    private List<User> userList = new ArrayList<>();
    private List<String> friendUids = new ArrayList<>();
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_friends);

        recyclerView = findViewById(R.id.findFriendsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FindFriendsAdapter(userList);
        recyclerView.setAdapter(adapter);

        fetchFriendUidsAndThenUsers();
    }

    private void fetchFriendUidsAndThenUsers() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance(dbUrl).getReference("users").child(myUid).child("friends")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        friendUids.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            friendUids.add(ds.getKey());
                        }
                        fetchAllUsers(myUid);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch friend UIDs: " + error.getMessage());
                        fetchAllUsers(myUid); // Fetch users anyway
                    }
                });
    }

    private void fetchAllUsers(String myUid) {
        FirebaseDatabase.getInstance(dbUrl).getReference("users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        userList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            User user = ds.getValue(User.class);
                            if (user != null && user.uid != null && !user.uid.equals(myUid) && !friendUids.contains(user.uid)) {
                                userList.add(user);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch all users: " + error.getMessage());
                    }
                });
    }
}
