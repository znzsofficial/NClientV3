package com.maxwai.nclientv3.components.widgets;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.TagFilterActivity;
import com.maxwai.nclientv3.adapters.TagsAdapter;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.async.ScrapeTags;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagV2;

public class TagTypePage extends Fragment {
    private TagType type;
    private RecyclerView recyclerView;
    private TagFilterActivity activity;
    private String query;
    private TagsAdapter adapter;

    public TagTypePage() {
    }

    private static int getTag(int page) {
        return switch (page) {
            case 0 -> TagType.UNKNOWN.getId();//tags with status
            case 1 -> TagType.TAG.getId();
            case 2 -> TagType.ARTIST.getId();
            case 3 -> TagType.CHARACTER.getId();
            case 4 -> TagType.PARODY.getId();
            case 5 -> TagType.GROUP.getId();
            case 6 -> TagType.CATEGORY.getId();//online blacklisted tags
            default -> -1;
        };
    }

    public static TagTypePage newInstance(int page) {
        TagTypePage fragment = new TagTypePage();
        Bundle args = new Bundle();
        args.putInt("TAGTYPE", getTag(page));
        fragment.setArguments(args);
        return fragment;
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        activity = (TagFilterActivity) getActivity();
        type = TagType.values[getArguments().getInt("TAGTYPE")];
        View rootView = inflater.inflate(R.layout.fragment_tag_filter, container, false);
        recyclerView = rootView.findViewById(R.id.recycler);
        Global.applyFastScroller(recyclerView);
        loadTags();
        return rootView;
    }

    public void loadTags() {
        recyclerView.setLayoutManager(new CustomGridLayoutManager(activity, getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 2));
        if (type.equals(TagType.UNKNOWN)) adapter = new TagsAdapter(activity, query, false);
        else if (type.equals(TagType.CATEGORY)) adapter = new TagsAdapter(activity, query, true);
        else adapter = new TagsAdapter(activity, query, type);
        recyclerView.setAdapter(adapter);
    }

    public void refilter(String newText) {
        if (activity != null) activity.runOnUiThread(() -> adapter.getFilter().filter(newText));
    }

    public void reset() {
        if (type.equals(TagType.UNKNOWN)) TagV2.resetAllStatus();
        else if (!type.equals(TagType.CATEGORY)) {
            ScrapeTags.startWork(activity);
        }
        Activity activity = getActivity();
        if (activity == null || adapter == null) return;
        activity.runOnUiThread(adapter::notifyDataSetChanged);

    }

    public void changeSize() {
        refilter(query);
    }
}

