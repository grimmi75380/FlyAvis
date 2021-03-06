package com.flyavis.android.ui.planning;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.airbnb.epoxy.EpoxyTouchHelper;
import com.flyavis.android.R;
import com.flyavis.android.data.Resource;
import com.flyavis.android.data.database.Plan;
import com.flyavis.android.databinding.EditPlanDialogBinding;
import com.flyavis.android.databinding.PlanBottomSheetBinding;
import com.flyavis.android.databinding.PlanningFragmentBinding;
import com.flyavis.android.util.FlyAvisUtils;
import com.google.android.gms.common.api.Status;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import dagger.android.support.DaggerFragment;
import timber.log.Timber;

import static android.animation.ValueAnimator.ofObject;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static com.flyavis.android.Constants.AUTOCOMPLETE_REQUEST_CODE;

public class PlanningFragment extends DaggerFragment implements PlanningEpoxyController.PlanningCallbacks {

    @Inject
    ViewModelProvider.Factory factory;
    @Inject
    Context context;
    private PlanningViewModel mViewModel;
    private PlanningFragmentBinding binding;
    private PlanningEpoxyController controller;
    private int day;
    private Time nextSpotTime;
    private Date firstDate;
    private Date newDate;
    private SimpleDateFormat sdFormat;
    private int totalDays;
    private int myTripId;
    private List<Plan> planList;
    private LiveData<Resource<List<Plan>>> observable;

    public static PlanningFragment newInstance() {
        return new PlanningFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil//出錯的話重新命名此layout
                .inflate(inflater, R.layout.planning_fragment, container, false);
        return binding.getRoot();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this, factory).get(PlanningViewModel.class);

        //取得上一個畫面傳的參數
        myTripId = PlanningFragmentArgs
                .fromBundle(Objects.requireNonNull(getArguments())).getMyTripId();
        String dateRange = PlanningFragmentArgs
                .fromBundle(Objects.requireNonNull(getArguments())).getDateRange();
        //date handle
        String[] split = dateRange.split(" ~ ");
        firstDate = FlyAvisUtils
                .StringToDate(split[0], "yyyy-MM-dd", Locale.US);
        sdFormat = new SimpleDateFormat("yyyy.MM.dd E", Locale.TAIWAN);
        sdFormat.format(firstDate);
        totalDays = FlyAvisUtils.calculateDays(dateRange) + 1;
        //init some values
        day = 1;
        nextSpotTime = Time.valueOf("08:00:00");
        mViewModel.setQueryParameter(myTripId, day);
        initEpoxyRecyclerView();
        initTab();
    }

    private void initEpoxyRecyclerView() {
        controller = new PlanningEpoxyController(this);
        binding.planningRecyclerView.setController(controller);
        observable = mViewModel.getPlanningData();
        newDate = firstDate;
        observable.observe
                (getViewLifecycleOwner(), listResource -> {
                    planList = listResource.data;
                    controller.setData(planList, sdFormat.format(newDate));
                    if (planList != null && planList.size() != 0) {
                        nextSpotTime = planList.get(planList.size() - 1).getSpotEndTime();
                    }
                    Timber.d("plan observed");
                });

        //drag support
        EpoxyTouchHelper.initDragging(controller)
                .withRecyclerView(binding.planningRecyclerView)
                .forVerticalList()
                .withTarget(PlanningModelGroup.class)
                .andCallbacks(new EpoxyTouchHelper.DragCallbacks<PlanningModelGroup>() {
                    @ColorInt
                    final int selectedBackgroundColor = Color.argb(200, 200, 200, 200);
                    ValueAnimator backgroundAnimator = null;

                    @Override
                    public void onModelMoved(int fromPosition, int toPosition,
                                             PlanningModelGroup modelBeingMoved, View itemView) {
                        Timber.d("position:" + fromPosition + ">" + toPosition);
                        Timber.d(String.valueOf("plan size:" + planList.size()));
                        //save original time
                        List<Time> unModStartTime = new ArrayList<>();
                        List<Time> unModEndTime = new ArrayList<>();

                        for (Plan plan : planList) {
                            unModStartTime.add(plan.getSpotStartTime());
                            unModEndTime.add(plan.getSpotEndTime());
                        }

                        planList.add(fromPosition - 1 + (toPosition - fromPosition)
                                , planList.remove(fromPosition - 1));

                        for (int i = 0; i < planList.size(); i++) {
                            Plan plan = planList.get(i);
                            //set new time
                            plan.setSpotStartTime(unModStartTime.get(i));
                            plan.setSpotEndTime(unModEndTime.get(i));
                            //set new order
                            plan.setSpotOrder(i);
                            planList.set(i, plan);
                        }


                    }

                    @Override
                    public void onDragStarted(PlanningModelGroup model, View itemView, int adapterPosition) {
                        //anim
                        backgroundAnimator = ofObject(new ArgbEvaluator(), Color.WHITE, selectedBackgroundColor);
                        backgroundAnimator.addUpdateListener(
                                animator -> itemView.setBackgroundColor((int) animator.getAnimatedValue())
                        );
                        backgroundAnimator.start();
                        itemView
                                .animate()
                                .scaleX(1.05f)
                                .scaleY(1.05f);
                    }

                    @Override
                    public void onDragReleased(PlanningModelGroup model, View itemView) {
                        //anim
                        if (backgroundAnimator != null) {
                            backgroundAnimator.cancel();
                        }
                        backgroundAnimator =
                                ofObject(new ArgbEvaluator()
                                        , ((ColorDrawable) itemView.getBackground()).getColor(),
                                        Color.WHITE);
                        backgroundAnimator.addUpdateListener(
                                animator -> itemView.setBackgroundColor((int) animator.getAnimatedValue())
                        );
                        backgroundAnimator.start();
                        itemView
                                .animate()
                                .scaleX(1f)
                                .scaleY(1f);
                    }

                    @Override
                    public void clearView(PlanningModelGroup model, View itemView) {
                        onDragReleased(model, itemView);
                        mViewModel.updatePlans(planList);
                    }
                });
    }

    private void initTab() {
        TabLayout tabLayout = binding.tabLayout;
        for (int i = 1; i <= totalDays; i++) {
            tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.day) + i));
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                observable.removeObservers(getViewLifecycleOwner());
                day = tab.getPosition() + 1;
                mViewModel.setQueryParameter(myTripId, day);
                observable = mViewModel.getPlanningData();
                observable.observe
                        (getViewLifecycleOwner(), listResource -> {
                            planList = listResource.data;
                            mViewModel.setQueryParameter(myTripId, day);
                            long l = firstDate.getTime() + (day - 1) * 24 * 60 * 60 * 1000;
                            newDate = new Date(l);
                            controller.setData(planList, sdFormat.format(newDate));

                            if (planList != null && planList.size() > 0) {
                                nextSpotTime = planList.get(planList.size() - 1).getSpotEndTime();
                            } else {
                                nextSpotTime = Time.valueOf("08:00:00");
                            }
                            Timber.d("Tab: plan observed");
                        });

            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    @Override
    public void onEditButtonClick() {
        EditPlanDialogBinding editPlanDialogBinding = DataBindingUtil
                .inflate(LayoutInflater.from(getContext())
                        , R.layout.edit_plan_dialog, null, false);
        editPlanDialogBinding.setStartTime("08:00");
        if (planList.size() > 0) {
            editPlanDialogBinding.setStartTime(planList.get(0).getSpotStartTime().toString());
        }
        final long[] pickedTime = new long[1];
        editPlanDialogBinding.setOnEditTextClick(view -> {
            new TimePickerDialog(getContext(), (timePicker, i, i1) -> {
                pickedTime[0] = (i1 * 1000 * 60) + (i * 1000 * 60 * 60);
                editPlanDialogBinding.setStartTime(String.format(Locale.US, "%02d:%02d", i, i1));
            }, 0, 0, true)
                    .show();
        });
        new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity()))
                .setTitle(getString(R.string.edit))
                .setView(editPlanDialogBinding.getRoot())
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {

                    long diff = pickedTime[0] - 1000 * 60 * 60 * 8 // 時差-8
                            - planList.get(0).getSpotStartTime().getTime();
                    Timber.d(new Time(planList.get(0).getSpotStartTime().getTime()).toString());
                    Timber.d(String.valueOf(planList.get(0).getSpotStartTime().getTime()));
                    Timber.d(new Time(pickedTime[0]).toString());
                    Timber.d(String.valueOf(pickedTime[0]));
                    Timber.d(new Time(diff).toString());
                    Timber.d(String.valueOf(diff));
                    for (int i = 0; i < planList.size(); i++) {
                        timeCalculate(i, diff);
                    }

                    mViewModel.updatePlans(planList);

                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    @Override
    public void onAddNewSpotViewClick() {
        new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity()))
                .setTitle(getString(R.string.add_a_new_spot))
                .setItems(R.array.add_new_spot, (dialogInterface, i) -> {
                    switch (i) {
                        case 0:
                            placeApiSearch();
                            break;
                        case 1:
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    @Override
    public void onSpotViewClick(Plan plan) {
        String url = "https://www.google.com/maps/search/?api=1"
                + "&query=" + plan.getSpotName()
                + "&query_place_id=" + plan.getPlaceId();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    @Override
    public void onMoreButtonClick(Plan plan) {
        //show bottomSheet
        BottomSheetDialog bottomSheetDialog
                = new BottomSheetDialog(Objects.requireNonNull(getContext()));
        PlanBottomSheetBinding bottomSheetBinding
                = DataBindingUtil.inflate(LayoutInflater.from(getContext())
                , R.layout.plan_bottom_sheet, null, false);
        bottomSheetDialog.setContentView(bottomSheetBinding.getRoot());
        bottomSheetDialog.show();

        bottomSheetBinding.setHelperClickListener(view -> {
            PlanningFragmentDirections.ActionPlanningFragmentToPlanHelperFragment action
                    = PlanningFragmentDirections.actionPlanningFragmentToPlanHelperFragment
                    (plan.getPlanId());
            Navigation.findNavController(Objects.requireNonNull(this.getView())).navigate(action);
            bottomSheetDialog.dismiss();
        });

        bottomSheetBinding.setEditStayTimeClickListener(view -> {
            new TimePickerDialog(getContext(), (timePicker, i, i1) -> {
                long pickedTime = (i1 * 1000 * 60) + (i * 1000 * 60 * 60);
                Time modTime = new Time(plan.getSpotStartTime().getTime() + pickedTime);
                long diffBefore = plan.getSpotEndTime().getTime() - plan.getSpotStartTime().getTime();
                if (pickedTime != diffBefore) {
                    if (planList.size() > plan.getSpotOrder() + 1) {
                        for (int j = plan.getSpotOrder() + 1; j < planList.size(); j++) {
                            timeCalculate(j, pickedTime - diffBefore);
                        }
                    }
                    planList.get(plan.getSpotOrder()).setSpotEndTime(modTime);
                    mViewModel.updatePlans(planList);
                }
                bottomSheetDialog.dismiss();
            }, 0, 0, true).show();

        });

        bottomSheetBinding.setDeleteClickListener(view -> {
                    mViewModel.deletePlan(plan);
            //reset start time
            if (planList.size() == 1) {
                nextSpotTime = Time.valueOf("08:00:00");
            }
                    bottomSheetDialog.dismiss();
                }
        );
    }

    private void timeCalculate(int planId, long pickedTime) {
        Time newStartTime = new
                Time(planList.get(planId).getSpotStartTime().getTime() + pickedTime);
        Time newEndTime = new
                Time(planList.get(planId).getSpotEndTime().getTime() + pickedTime);
        planList.get(planId).setSpotStartTime(newStartTime);
        planList.get(planId).setSpotEndTime(newEndTime);
    }

    //start google map navigation
    @Override
    public void onTrafficTimeClick(Plan plan, Plan nextPlan) {
        String url = "https://www.google.com/maps/dir/?api=1"
                + "&destination=" + nextPlan.getSpotName()
                + "&destination_place_id=" + nextPlan.getPlaceId()
                + "&origin=" + plan.getSpotName()
                + "&origin_place_id=" + plan.getPlaceId()
                + "&travelmode=driving";
//                +"&dir_action=navigate";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    @Override
    public void onTrafficEditButtonClick(Plan plan) {

    }

    private void navigateView(NavDirections action) {
        Navigation.findNavController(Objects.requireNonNull(this.getView())).navigate(action);
    }

    private void placeApiSearch() {
        if (!Places.isInitialized()) {
            Places.initialize(context, getString(R.string.google_maps_key));
        }
        // Set the fields to specify which types of place data to return.
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME);

        // Start the autocomplete intent.
        Intent intent = new Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY, fields)
                .build(context);
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                Timber.i("Place: " + place.getName() + ", " + place.getId());

                Plan plan = new Plan();
                plan.setPlanDay(day);
                plan.setPlaceId(place.getId());
                plan.setSpotName(place.getName());
                plan.setTripId(myTripId);

                if (planList.size() == 0) {
                    plan.setSpotStartTime(nextSpotTime);
                    long l = nextSpotTime.getTime() + (60 * 60 * 1000);
                    Time time = new Time(l);
                    plan.setSpotEndTime(time);
                } else {
                    long l1 = nextSpotTime.getTime() + (60 * 60 * 1000);
                    Time startTime = new Time(l1);
                    plan.setSpotStartTime(startTime);
                    long l2 = nextSpotTime.getTime() + (60 * 60 * 1000) + (60 * 60 * 1000);
                    Time endTime = new Time(l2);
                    plan.setSpotEndTime(endTime);
                }

                if (planList != null) {
                    int size = planList.size();
                    plan.setSpotOrder(size);
                }
                plan.setTrafficTimeDuration(60);
                plan.setSpotTrafficFee(0);
                plan.setSpotCost(0);
                mViewModel.insetNewSpot(plan);

            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                // TODO: Handle the error.
                Status status = Autocomplete.getStatusFromIntent(data);
                Timber.i(status.getStatusMessage());
            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
    }
}
