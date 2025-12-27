package com.example.wifiroomlocator;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FriendRequestsFragment extends Fragment {

    private static final String TAG = "FriendRequestsFragment";
    private RecyclerView recyclerView;
    private FriendRequestsAdapter adapter;
    private List<User> requesters = new ArrayList<>();
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friend_requests, container, false);
        recyclerView = view.findViewById(R.id.friendRequestsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FriendRequestsAdapter(requesters);
        recyclerView.setAdapter(adapter);
        fetchFriendRequests();
        return view;
    }

    private void fetchFriendRequests() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance(dbUrl).getReference("friendRequests").child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        requesters.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String senderUid = ds.getKey();
                            if (senderUid != null) {
                                fetchUserData(senderUid);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch friend requests: " + error.getMessage());
                    }
                });
    }

    private void fetchUserData(String uid) {
        FirebaseDatabase.getInstance(dbUrl).getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            requesters.add(user);
                            adapter.notifyDataSetChanged();
                        } else {
                            // This can happen if the user who sent the request has deleted their account.
                            // We should remove the orphaned request.
                            String myUid = FirebaseAuth.getInstance().getUid();
                            if (myUid != null) {
                                FirebaseDatabase.getInstance(dbUrl).getReference("friendRequests").child(myUid).child(uid).removeValue();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch user data for friend request: " + error.getMessage());
                    }
                });
    }
}
