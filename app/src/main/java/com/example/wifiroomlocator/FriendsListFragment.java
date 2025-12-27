package com.example.wifiroomlocator;

import android.os.Bundle;
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

public class FriendsListFragment extends Fragment {
    private RecyclerView recyclerView;
    private FriendsAdapter adapter;
    private List<User> friendList = new ArrayList<>();
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends_list, container, false);
        recyclerView = view.findViewById(R.id.friendsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new FriendsAdapter(friendList);
        recyclerView.setAdapter(adapter);

        fetchFriends();
        return view;
    }

    private void fetchFriends() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance(dbUrl).getReference("users").child(myUid).child("friends")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        friendList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String friendUid = ds.getKey();
                            if (friendUid != null) {
                                fetchUserData(friendUid);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void fetchUserData(String uid) {
        FirebaseDatabase.getInstance(dbUrl).getReference("users").child(uid)
                .addValueEventListener(new ValueEventListener() { // Changed to addValueEventListener for real-time updates
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            // Avoid duplicates
                            boolean exists = false;
                            for(User u : friendList) {
                                if (u.uid.equals(user.uid)) {
                                    exists = true;
                                    // Update existing user
                                    u.currentLocation = user.currentLocation;
                                    break;
                                }
                            }
                            if (!exists) {
                                friendList.add(user);
                            }
                            adapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }
}
