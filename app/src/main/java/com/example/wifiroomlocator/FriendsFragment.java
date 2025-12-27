package com.example.wifiroomlocator;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

public class FriendsFragment extends Fragment {

    private ViewPager viewPager;
    private TabLayout tabLayout;
    private FloatingActionButton addFriendFab;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        addFriendFab = view.findViewById(R.id.addFriendFab);

        ViewPagerAdapter adapter = new ViewPagerAdapter(getChildFragmentManager());
        adapter.addFragment(new FriendsListFragment(), "Friends");
        adapter.addFragment(new FriendRequestsFragment(), "Requests");

        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);

        addFriendFab.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), FindFriendsActivity.class);
            startActivity(intent);
        });

        return view;
    }
}
