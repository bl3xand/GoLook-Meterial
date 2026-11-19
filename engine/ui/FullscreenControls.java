package local.materialui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import java.util.ArrayList;

/** One consistent fullscreen dock; capability actions retain their native listeners. */
public final class FullscreenControls {
    private final Activity activity;
    private final ArrayList<View> sources = new ArrayList<View>();
    private final ArrayList<ViewGroup> parents = new ArrayList<ViewGroup>();
    private final ArrayList<ViewGroup.LayoutParams> params = new ArrayList<ViewGroup.LayoutParams>();
    private final ArrayList<Integer> positions = new ArrayList<Integer>();
    private final ArrayList<View> available = new ArrayList<View>();
    private FrameLayout proxies;

    public FullscreenControls(Activity activity) { this.activity = activity; }

    private int id(String name, String type) {
        return activity.getResources().getIdentifier(name, type, activity.getPackageName());
    }
    private View find(String name) { return activity.findViewById(id(name, "id")); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private void source(String name, String label, ViewGroup land) {
        View view = find(name);
        if (view == null) return;
        if (!sources.contains(view)) {
            ViewGroup parent = (ViewGroup) view.getParent();
            sources.add(view);
            parents.add(parent);
            params.add(view.getLayoutParams());
            positions.add(parent.indexOfChild(view));
            parent.removeView(view);
            if (proxies == null) {
                proxies = new FrameLayout(activity);
                proxies.setClipChildren(true);
                proxies.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                proxies.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                land.addView(proxies, new RelativeLayout.LayoutParams(0, 0));
            }
            proxies.addView(view, new FrameLayout.LayoutParams(dp(48), dp(48)));
        }
        view.setContentDescription(activity.getText(id(label, "string")));
        if (view.getVisibility() == View.VISIBLE) available.add(view);
    }

    public void apply() {
        View landView = find("layoutFragLand");
        if (!(landView instanceof ViewGroup)) return;
        ViewGroup land = (ViewGroup) landView;
        View railView = land.findViewById(id("layoutCommandBar", "id"));
        if (!(railView instanceof RelativeLayout)) return;
        RelativeLayout rail = (RelativeLayout) railView;
        if (rail.findViewById(id("material_ui_fullscreen_separator", "id")) == null) return;
        RelativeLayout.LayoutParams railParams = (RelativeLayout.LayoutParams) rail.getLayoutParams();
        railParams.width = dp(64);
        railParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        railParams.removeRule(RelativeLayout.CENTER_IN_PARENT);
        railParams.removeRule(RelativeLayout.CENTER_HORIZONTAL);
        railParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        railParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        railParams.addRule(RelativeLayout.CENTER_VERTICAL);
        railParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        railParams.setMargins(0, 0, dp(24), 0);
        railParams.setMarginStart(0);
        railParams.setMarginEnd(dp(24));
        rail.setLayoutParams(railParams);
        available.clear();
        source("imgCameraToggleLand", "material_ui_camera_switch", land);
        if (find("imgLicensePlateLand") != null)
            source("imgLicensePlateLand", "license_plate_recognition", land);
        else source("imgLicensePlate", "license_plate_recognition", land);
        ArrayList<View> actions = new ArrayList<View>();
        for (String name : new String[]{"lyBrowseFile", "lyCommand", "lySettings"}) {
            View action = rail.findViewById(id(name, "id"));
            if (action != null && action.getVisibility() == View.VISIBLE) actions.add(action);
        }
        for (View action : available) {
            if (action.getParent() != rail) {
                ((ViewGroup) action.getParent()).removeView(action);
                rail.addView(action);
            }
            action.setBackground(activity.getDrawable(id("material_ui_video_icon_ripple", "drawable")));
            if (android.os.Build.VERSION.SDK_INT >= 26) action.setTooltipText(action.getContentDescription());
            actions.add(action);
        }
        int usableHeight = land.getHeight() > 0 ? land.getHeight() : dp(activity.getResources().getConfiguration().screenHeightDp);
        boolean compact = usableHeight < dp(actions.size() * 56 + 108);
        railParams.width = dp(compact ? 120 : 64);
        rail.setLayoutParams(railParams);
        for (int i = 0; i < actions.size(); i++) {
            int above = compact ? i - 2 : i - 1;
            int previous = compact && i % 2 == 1 ? actions.get(i - 1).getId() : 0;
            position(actions.get(i), previous == 0 && above >= 0 ? actions.get(above).getId() : 0,
                    previous, previous, !compact);
        }
        View divider = rail.findViewById(id("material_ui_fullscreen_separator", "id"));
        RelativeLayout.LayoutParams dividerParams = new RelativeLayout.LayoutParams(dp(compact ? 104 : 32), dp(1));
        if (!actions.isEmpty())
            dividerParams.addRule(RelativeLayout.BELOW, actions.get(actions.size() - 1).getId());
        dividerParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        dividerParams.topMargin = dp(8);
        divider.setLayoutParams(dividerParams);
        View exit = find("imgFullScreenLand");
        if (exit != null) {
            if (exit.getParent() != rail) {
                ViewGroup parent = (ViewGroup) exit.getParent();
                parent.removeView(exit);
                if (parent.getChildCount() == 0 && parent.getId() == id("layoutFullScreenLand", "id"))
                    parent.setVisibility(View.GONE);
                rail.addView(exit);
            }
            RelativeLayout.LayoutParams exitParams = new RelativeLayout.LayoutParams(dp(48), dp(48));
            if (!actions.isEmpty()) exitParams.addRule(RelativeLayout.BELOW, divider.getId());
            else exitParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            exitParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            exitParams.topMargin = actions.isEmpty() ? 0 : dp(8);
            exit.setLayoutParams(exitParams);
            exit.setBackground(activity.getDrawable(id("material_ui_video_icon_ripple", "drawable")));
            divider.setVisibility(actions.isEmpty() ? View.GONE : exit.getVisibility());
        }
    }

    public void restore() {
        for (int i = sources.size() - 1; i >= 0; i--) {
            View view = sources.get(i);
            ((ViewGroup) view.getParent()).removeView(view);
            ViewGroup parent = parents.get(i);
            parent.addView(view, Math.min(positions.get(i), parent.getChildCount()), params.get(i));
        }
        sources.clear(); parents.clear(); params.clear(); positions.clear(); available.clear();
    }

    private void position(View view, int below, int endOf, int alignTop, boolean center) {
        RelativeLayout.LayoutParams layout = new RelativeLayout.LayoutParams(dp(48), dp(48));
        if (center) layout.addRule(RelativeLayout.CENTER_HORIZONTAL);
        else if (endOf == 0) layout.addRule(RelativeLayout.ALIGN_PARENT_START);
        if (below != 0) { layout.addRule(RelativeLayout.BELOW, below); layout.topMargin = dp(8); }
        if (endOf != 0) {
            layout.addRule(RelativeLayout.END_OF, endOf);
            layout.addRule(RelativeLayout.ALIGN_TOP, alignTop);
            layout.setMarginStart(dp(8));
        }
        view.setLayoutParams(layout);
    }

}
