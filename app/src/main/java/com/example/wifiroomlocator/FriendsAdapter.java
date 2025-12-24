package com.example.wifiroomlocator;

// Android UI and Layout imports
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

// AndroidX RecyclerView imports
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

// Java Utility imports
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {
    private List<User> friendList;

    public FriendsAdapter(List<User> friendList) { this.friendList = friendList; }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        User friend = friendList.get(position);
        holder.name.setText(friend.name);
        holder.loc.setText("LOC: " + friend.currentLocation.toUpperCase());
    }

    @Override
    public int getItemCount() { return friendList.size(); }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView name, loc;
        public FriendViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.friendName);
            loc = v.findViewById(R.id.friendLoc);
        }
    }
}
