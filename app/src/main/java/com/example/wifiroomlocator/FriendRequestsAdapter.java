package com.example.wifiroomlocator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendRequestsAdapter extends RecyclerView.Adapter<FriendRequestsAdapter.ViewHolder> {

    private List<User> requesters;
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    public FriendRequestsAdapter(List<User> requesters) {
        this.requesters = requesters;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User requester = requesters.get(position);
        holder.name.setText(requester.name);
        holder.email.setText(requester.email);

        holder.acceptButton.setOnClickListener(v -> {
            acceptRequest(requester, holder);
        });
        holder.declineButton.setOnClickListener(v -> {
            declineRequest(requester, holder);
        });
    }

    private void acceptRequest(User requester, ViewHolder holder) {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        // Create paths for the atomic update
        String userAFriendPath = "/users/" + requester.uid + "/friends/" + myUid;
        String userBFriendPath = "/users/" + myUid + "/friends/" + requester.uid;
        String requestPath = "/friendRequests/" + myUid + "/" + requester.uid;

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put(userAFriendPath, true);
        childUpdates.put(userBFriendPath, true);
        childUpdates.put(requestPath, null); // Use null to delete the request data

        FirebaseDatabase.getInstance(dbUrl).getReference().updateChildren(childUpdates).addOnCompleteListener(task -> {
            if(task.isSuccessful()) {
                // The Fragment's listener will automatically update the UI.
                Toast.makeText(holder.itemView.getContext(), "Friend request accepted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(holder.itemView.getContext(), "Failed to accept request.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void declineRequest(User requester, ViewHolder holder) {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance(dbUrl).getReference("friendRequests").child(myUid).child(requester.uid).removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // The Fragment's listener will automatically update the UI.
                        Toast.makeText(holder.itemView.getContext(), "Friend request declined", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(holder.itemView.getContext(), "Failed to decline request.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public int getItemCount() {
        return requesters.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, email;
        Button acceptButton, declineButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.requesterName);
            email = itemView.findViewById(R.id.requesterEmail);
            acceptButton = itemView.findViewById(R.id.acceptButton);
            declineButton = itemView.findViewById(R.id.declineButton);
        }
    }
}
