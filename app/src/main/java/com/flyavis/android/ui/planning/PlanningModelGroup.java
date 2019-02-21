package com.flyavis.android.ui.planning;

import com.airbnb.epoxy.EpoxyModel;
import com.airbnb.epoxy.EpoxyModelGroup;
import com.flyavis.android.R;
import com.flyavis.android.SpotItemBindingModel_;
import com.flyavis.android.TrafficTimeBindingModel_;
import com.flyavis.android.data.database.Plan;

import java.util.ArrayList;
import java.util.List;

//把Model做群組
public class PlanningModelGroup extends EpoxyModelGroup {
    public final Plan plan;

    PlanningModelGroup(Plan plan, PlanningEpoxyController.PlanningCallbacks callbacks) {
        super(R.layout.planning_item, buildModels(plan, callbacks));
        this.plan = plan;
        id(plan.getPlanId());
    }

    private static List<EpoxyModel<?>> buildModels
            (Plan plan, PlanningEpoxyController.PlanningCallbacks callbacks) {
        ArrayList<EpoxyModel<?>> models = new ArrayList<>();
        models.add(
                new SpotItemBindingModel_()
                        .id("spotItem")
                        .spotName(plan.getSpotName())
                        .arriveTime("13:30")
                        .leaveTime("14:30")
                        .spotNotice("專題好難")
                        .stayTime("1小時")
                        .clickListener(view -> {

                        })
                        .moreButtonClickListener(view -> {
                            callbacks.onMoreButtonClick(plan.getPlanId());
                        })
        );
        models.add(
                new TrafficTimeBindingModel_()
                        .id("trafficTime")
                        .trafficTime("∞小時")
                        .clickListener(view -> {

                        })
        );
        return models;
    }


}
