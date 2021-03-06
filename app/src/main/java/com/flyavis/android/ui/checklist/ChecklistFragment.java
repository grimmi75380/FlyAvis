package com.flyavis.android.ui.checklist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.flyavis.android.R;
import com.flyavis.android.databinding.ChecklistFragmentBinding;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

public class ChecklistFragment extends Fragment {

    @Inject
    ViewModelProvider.Factory factory;
    private ChecklistViewModel mViewModel;
    private ChecklistFragmentBinding binding;
    private CheckListEpoxyController controller;

    public static ChecklistFragment newInstance() {
        return new ChecklistFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil
                .inflate(inflater, R.layout.checklist_fragment, container, false);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this, factory).get(ChecklistViewModel.class);
        controller = new CheckListEpoxyController();
        binding.checkListRecyclerView.setController(controller);
        // TODO: Use the ViewModel
        controller.setData(null);
    }

}
