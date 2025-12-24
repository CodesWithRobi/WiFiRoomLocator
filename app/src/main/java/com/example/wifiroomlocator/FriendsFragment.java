package com.example.wifiroomlocator;// Android Core & Fragment imports
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

// UI Component imports
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Firebase imports
import com.example.wifiroomlocator.FriendsAdapter;
import com.example.wifiroomlocator.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

// Java Collection imports
import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends Fragment {
    private RecyclerView recyclerView;
    private FriendsAdapter adapter;
    private List<User> friendList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);
        recyclerView = view.findViewById(R.id.friendsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new FriendsAdapter(friendList);
        recyclerView.setAdapter(adapter);

        fetchFriends();
        return view;
    }

    private void fetchFriends() {
        FirebaseDatabase.getInstance().getReference("users")
                .addValueEventListener(new ValueEventListener() { // Listen for real-time changes
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        friendList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            User user = ds.getValue(User.class);
                            // Filter out yourself
                            if (user != null && !user.uid.equals(FirebaseAuth.getInstance().getUid())) {
                                friendList.add(user);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
}
