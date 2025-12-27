package com.example.wifiroomlocator;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

    private List<User> friendList;
    private final String dbUrl = "https://wifiroomlocator-default-rtdb.asia-southeast1.firebasedatabase.app/";

    public FriendsAdapter(List<User> friendList) {
        this.friendList = friendList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User friend = friendList.get(position);
        holder.name.setText(friend.name);
        holder.location.setText(friend.currentLocation);

        holder.itemView.setOnLongClickListener(v -> {
            showDeleteFriendDialog(friend, position, holder);
            return true;
        });
    }

    private void showDeleteFriendDialog(User friend, int position, ViewHolder holder) {
        new AlertDialog.Builder(holder.itemView.getContext())
                .setTitle("Remove Friend")
                .setMessage("Are you sure you want to remove " + friend.name + " from your friends list?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    deleteFriend(friend, position, holder);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFriend(User friend, int position, ViewHolder holder) {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        String myFriendPath = "/users/" + myUid + "/friends/" + friend.uid;
        String theirFriendPath = "/users/" + friend.uid + "/friends/" + myUid;

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put(myFriendPath, null);
        childUpdates.put(theirFriendPath, null);

        FirebaseDatabase.getInstance(dbUrl).getReference().updateChildren(childUpdates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                friendList.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, friendList.size());
                Toast.makeText(holder.itemView.getContext(), friend.name + " removed.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(holder.itemView.getContext(), "Failed to remove friend.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return friendList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, location;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.friendName);
            location = itemView.findViewById(R.id.friendLocation);
        }
    }
}
