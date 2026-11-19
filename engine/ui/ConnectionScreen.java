package local.materialui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

/** Shared native connection instructions; onboarding has one direct network action. */
public final class ConnectionScreen implements View.OnClickListener {
    private final Activity activity;
    private final View chooseNetwork;

    private ConnectionScreen(Activity activity, boolean guide) {
        this.activity = activity;
        activity.setContentView(id(guide ? "activity_connect_guide" : "activity_connect_help", "layout"));
        ((TextView) activity.findViewById(id("title_content", "id"))).setText(
                id(guide ? "connect_wifi" : "connect_help", "string"));
        activity.findViewById(id("backLayout", "id")).setOnClickListener(this);
        chooseNetwork = activity.findViewById(id("btn_test", "id"));
        chooseNetwork.setVisibility(guide ? View.VISIBLE : View.GONE);
        chooseNetwork.setOnClickListener(this);
        activity.findViewById(id("material_ui_help_retry", "id")).setVisibility(guide ? View.GONE : View.VISIBLE);
    }

    private int id(String name, String type) {
        return activity.getResources().getIdentifier(name, type, activity.getPackageName());
    }

    public static void show(Activity activity, boolean guide) {
        new ConnectionScreen(activity, guide);
    }

    @Override public void onClick(View view) {
        if (view == chooseNetwork) {
            Intent intent = new Intent();
            intent.setClassName(activity, activity.getPackageName() + ".activitys.devmanager.DeviceScanActivity");
            activity.startActivity(intent);
        } else {
            activity.finish();
        }
    }
}
