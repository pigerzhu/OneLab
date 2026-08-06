package io.github.pigerzhu.onelab.navigation;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import io.github.pigerzhu.onelab.ui.Ui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AppListPage {
    private static final String PREFS = "app_list";
    private static final String PREF_SORT = "sort_mode";
    private static final String PREF_DESCENDING = "sort_descending";

    private final MainActivity host;
    private final Ui ui;
    private List<AppEntry> cachedUserApps;

    public AppListPage(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
    }

    // Optional: return true for apps that should be pinned to the top of the list (e.g. apps that
    // already have a per-app override configured). Pinned apps keep their alphabetical order.
    public interface AppPriority {
        boolean isPinned(AppEntry app);
    }

    public interface AppStatusProvider {
        String status(AppEntry app);
    }

    public interface AppFilter {
        boolean include(AppEntry app);
    }

    public interface AppClickListener {
        void onAppClick(AppEntry app, Runnable refreshRow);
    }

    /** Optional bulk action exposed after long-pressing a user application. */
    public interface BatchAction {
        String actionText(int selectedCount);

        void onAppsSelected(List<AppEntry> apps, Runnable refreshList);
    }

    public static final class AppEntry {
        public final String packageName;
        public final String label;
        public final Drawable icon;
        public final long firstInstallTime;
        public final long lastUpdateTime;

        AppEntry(
                String packageName,
                String label,
                Drawable icon,
                long firstInstallTime,
                long lastUpdateTime
        ) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.firstInstallTime = firstInstallTime;
            this.lastUpdateTime = lastUpdateTime;
        }
    }

    public void show(
            String title,
            String subtitle,
            AppStatusProvider statusProvider,
            AppClickListener listener
    ) {
        show(title, subtitle, statusProvider, listener, null);
    }

    public void show(
            String title,
            String subtitle,
            AppStatusProvider statusProvider,
            AppClickListener listener,
            AppPriority priority
    ) {
        show(title, subtitle, statusProvider, listener, priority, null);
    }

    public void show(
            String title,
            String subtitle,
            AppStatusProvider statusProvider,
            AppClickListener listener,
            AppPriority priority,
            BatchAction batchAction
    ) {
        show(title, subtitle, statusProvider, listener, priority, batchAction, null);
    }

    public void show(
            String title,
            String subtitle,
            AppStatusProvider statusProvider,
            AppClickListener listener,
            AppPriority priority,
            BatchAction batchAction,
            AppFilter filter
    ) {
        show(title, subtitle, statusProvider, listener, priority, batchAction, filter, null);
    }

    public void show(
            String title,
            String subtitle,
            AppStatusProvider statusProvider,
            AppClickListener listener,
            AppPriority priority,
            BatchAction batchAction,
            AppFilter filter,
            Runnable helpAction
    ) {
        host.setShowingHomePage(false);
        Runnable parentBackAction = host.getNestedBackAction();
        SelectionState selection = new SelectionState();
        AppListAdapter[] adapter = new AppListAdapter[1];
        String[] query = {""};
        int[] sortMode = {appListPrefs().getInt(
                PREF_SORT, AppListToolbar.SORT_NAME)};
        boolean[] descending = {appListPrefs().getBoolean(PREF_DESCENDING, false)};
        boolean[] appScanRunning = {cachedUserApps == null};
        Runnable[] refreshVisibleApps = {() -> {
        }};

        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ui.colorSurface);
        if (host.isUsingLargeScreenLayout()) {
            page.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), 0);
        } else {
            page.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), 0);
        }

        AppListToolbar toolbar = new AppListToolbar(
                host,
                ui,
                title,
                parentBackAction,
                sortMode[0],
                descending[0],
                value -> {
                    query[0] = value;
                    refreshVisibleApps[0].run();
                },
                (mode, reversed) -> {
                    sortMode[0] = mode;
                    descending[0] = reversed;
                    appListPrefs().edit()
                            .putInt(PREF_SORT, mode)
                            .putBoolean(PREF_DESCENDING, reversed)
                            .apply();
                    refreshVisibleApps[0].run();
                },
                helpAction);
        page.addView(toolbar.view(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)));

        if (subtitle != null && !subtitle.isEmpty()) {
            page.addView(ui.text(subtitle, 15, false, ui.colorOnSurfaceVariant));
        }
        ui.addSpace(page, 10);

        RecyclerView recycler = new RecyclerView(host);
        recycler.setLayoutManager(new LinearLayoutManager(host));
        // Row layout changes and the fixed selection bar appear together. The default animator
        // animates every changed row and produces a visible jump, so keep this list immediate.
        recycler.setItemAnimator(null);
        recycler.setClipToPadding(false);
        recycler.setPadding(0, 0, 0, ui.dp(28));

        FrameLayout listHost = new FrameLayout(host);
        SwipeRefreshLayout swipeRefresh = new SwipeRefreshLayout(host);
        swipeRefresh.setColorSchemeColors(ui.colorPrimary);
        swipeRefresh.setProgressBackgroundColorSchemeColor(ui.colorSurfaceContainer);
        swipeRefresh.addView(recycler, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listHost.addView(swipeRefresh, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(listHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView loading = ui.text(host.getString(R.string.app_picker_loading), 14, false,
                ui.colorOnSurfaceVariant);
        TextView empty = ui.text(host.getString(R.string.app_picker_empty), 14, false,
                ui.colorOnSurfaceVariant);
        empty.setVisibility(View.GONE);
        listHost.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        if (cachedUserApps == null) {
            listHost.addView(loading, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        }

        TextView selectionCount = ui.text("", 14, true, ui.colorOnSurface);
        MaterialButton cancelSelection = selectionButton(host.getString(R.string.action_cancel));
        MaterialButton selectAll = selectionButton(host.getString(R.string.action_select_all));
        MaterialButton applySelection = selectionButton("");
        LinearLayout selectionBar = new LinearLayout(host);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setPadding(0, 0, 0, ui.dp(6));
        selectionBar.setBackgroundColor(ui.colorSurface);
        selectionBar.addView(selectionCount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        selectionBar.addView(cancelSelection);
        selectionBar.addView(selectAll);
        selectionBar.setVisibility(View.GONE);
        if (batchAction != null) {
            listHost.addView(selectionBar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
            applySelection.setVisibility(View.GONE);
            page.addView(applySelection, ui.matchWrap());
        }

        host.switchPage(page, 1);

        refreshVisibleApps[0] = () -> {
            if (adapter[0] == null || cachedUserApps == null) return;
            adapter[0].submitItems(
                    orderApps(filteredApps(cachedUserApps, filter),
                            priority, sortMode[0], descending[0]), query[0]);
            empty.setVisibility(adapter[0].getItemCount() == 0 ? View.VISIBLE : View.GONE);
        };

        Runnable updateSelectionUi = () -> {
            if (batchAction == null) return;
            int count = selection.selected.size();
            selectionBar.setVisibility(selection.active ? View.VISIBLE : View.GONE);
            selectionCount.setText(host.getResources().getQuantityString(
                    R.plurals.app_picker_selected, count, count));
            applySelection.setVisibility(selection.active ? View.VISIBLE : View.GONE);
            applySelection.setText(batchAction.actionText(count));
            applySelection.setEnabled(count > 0);
            swipeRefresh.setEnabled(!selection.active);
        };
        cancelSelection.setOnClickListener(v -> {
            selection.active = false;
            selection.selected.clear();
            if (adapter[0] != null) adapter[0].notifyDataSetChanged();
            updateSelectionUi.run();
        });
        selectAll.setOnClickListener(v -> {
            if (!selection.active || adapter[0] == null) return;
            List<AppEntry> visibleApps = adapter[0].visibleItems();
            boolean allVisibleSelected = !visibleApps.isEmpty();
            for (AppEntry app : visibleApps) {
                if (!selection.selected.contains(app.packageName)) {
                    allVisibleSelected = false;
                    break;
                }
            }
            if (allVisibleSelected) {
                for (AppEntry app : visibleApps) {
                    selection.selected.remove(app.packageName);
                }
            } else {
                for (AppEntry app : visibleApps) {
                    selection.selected.add(app.packageName);
                }
            }
            adapter[0].notifyDataSetChanged();
            updateSelectionUi.run();
        });
        applySelection.setOnClickListener(v -> {
            if (batchAction == null || selection.selected.isEmpty() || adapter[0] == null) return;
            List<AppEntry> selectedApps = new ArrayList<>();
            for (AppEntry app : filteredApps(cachedUserApps, filter)) {
                if (selection.selected.contains(app.packageName)) selectedApps.add(app);
            }
            selection.active = false;
            selection.selected.clear();
            adapter[0].notifyDataSetChanged();
            updateSelectionUi.run();
            batchAction.onAppsSelected(selectedApps, () -> adapter[0].notifyDataSetChanged());
        });
        swipeRefresh.setOnRefreshListener(() -> {
            if (appScanRunning[0]) {
                swipeRefresh.setRefreshing(false);
                return;
            }
            appScanRunning[0] = true;
            new Thread(() -> {
                List<AppEntry> apps = loadUserApps();
                host.runOnUiThread(() -> {
                    cachedUserApps = apps;
                    appScanRunning[0] = false;
                    selection.active = false;
                    selection.selected.clear();
                    refreshVisibleApps[0].run();
                    updateSelectionUi.run();
                    swipeRefresh.setRefreshing(false);
                });
            }, "OneLab-AppListRefresh").start();
        });
        updateSelectionUi.run();

        if (cachedUserApps != null) {
            adapter[0] = new AppListAdapter(
                    orderApps(filteredApps(cachedUserApps, filter),
                            priority, sortMode[0], descending[0]),
                    statusProvider, listener,
                    selection, updateSelectionUi, batchAction != null);
            recycler.setAdapter(adapter[0]);
            refreshVisibleApps[0].run();
            return;
        }
        new Thread(() -> {
            List<AppEntry> apps = loadUserApps();
            host.runOnUiThread(() -> {
                cachedUserApps = apps;
                appScanRunning[0] = false;
                listHost.removeView(loading);
                adapter[0] = new AppListAdapter(
                        orderApps(filteredApps(apps, filter),
                                priority, sortMode[0], descending[0]),
                        statusProvider, listener,
                        selection, updateSelectionUi, batchAction != null);
                recycler.setAdapter(adapter[0]);
                refreshVisibleApps[0].run();
            });
        }, "OneLab-AppList").start();
    }

    private List<AppEntry> filteredApps(List<AppEntry> apps, AppFilter filter) {
        if (filter == null) return new ArrayList<>(apps);
        List<AppEntry> filtered = new ArrayList<>();
        for (AppEntry app : apps) {
            if (filter.include(app)) filtered.add(app);
        }
        return filtered;
    }

    private List<AppEntry> orderApps(
            List<AppEntry> apps,
            AppPriority priority,
            int sortMode,
            boolean descending
    ) {
        List<AppEntry> ordered = new ArrayList<>(apps);
        Comparator<AppEntry> comparator = appComparator(sortMode);
        ordered.sort(descending ? comparator.reversed() : comparator);
        if (priority == null) return ordered;

        List<AppEntry> pinned = new ArrayList<>();
        List<AppEntry> rest = new ArrayList<>();
        for (AppEntry app : ordered) {
            (priority.isPinned(app) ? pinned : rest).add(app);
        }
        pinned.addAll(rest);
        return pinned;
    }

    private Comparator<AppEntry> appComparator(int sortMode) {
        Collator collator = Collator.getInstance(Locale.getDefault());
        Comparator<AppEntry> byName = (first, second) -> {
            int labelOrder = collator.compare(first.label, second.label);
            return labelOrder != 0
                    ? labelOrder
                    : first.packageName.compareToIgnoreCase(second.packageName);
        };
        if (sortMode == AppListToolbar.SORT_RECENTLY_INSTALLED) {
            return Comparator.comparingLong((AppEntry app) -> app.firstInstallTime)
                    .thenComparing(byName);
        }
        if (sortMode == AppListToolbar.SORT_RECENTLY_UPDATED) {
            return Comparator.comparingLong((AppEntry app) -> app.lastUpdateTime)
                    .thenComparing(byName);
        }
        return byName;
    }

    private SharedPreferences appListPrefs() {
        return host.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private final class AppListAdapter extends RecyclerView.Adapter<AppRowHolder> {
        private final List<AppEntry> items;
        private final AppStatusProvider statusProvider;
        private final AppClickListener listener;
        private final SelectionState selection;
        private final Runnable selectionChanged;
        private final boolean selectionEnabled;

        AppListAdapter(
                List<AppEntry> items,
                AppStatusProvider statusProvider,
                AppClickListener listener,
                SelectionState selection,
                Runnable selectionChanged,
                boolean selectionEnabled
        ) {
            this.items = new ArrayList<>(items);
            this.statusProvider = statusProvider;
            this.listener = listener;
            this.selection = selection;
            this.selectionChanged = selectionChanged;
            this.selectionEnabled = selectionEnabled;
        }

        void submitItems(List<AppEntry> orderedItems, String query) {
            String normalized = query == null
                    ? ""
                    : query.trim().toLowerCase(Locale.getDefault());
            items.clear();
            if (normalized.isEmpty()) {
                items.addAll(orderedItems);
            } else {
                for (AppEntry app : orderedItems) {
                    if (app.label.toLowerCase(Locale.getDefault()).contains(normalized)
                            || app.packageName.toLowerCase(Locale.ROOT).contains(normalized)) {
                        items.add(app);
                    }
                }
            }
            notifyDataSetChanged();
        }

        List<AppEntry> visibleItems() {
            return new ArrayList<>(items);
        }

        @Override
        public AppRowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            MaterialCardView cardView = new MaterialCardView(host);
            cardView.setRadius(ui.dp(16));
            cardView.setCardElevation(0);
            cardView.setStrokeWidth(ui.dp(1));
            cardView.setStrokeColor(MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorOutlineVariant, 0x1F000000));
            cardView.setCardBackgroundColor(ui.colorSurfaceContainer);
            cardView.setClickable(true);
            cardView.setFocusable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = ui.dp(10);
            cardView.setLayoutParams(lp);

            LinearLayout row = new LinearLayout(host);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14));
            cardView.addView(row);

            ImageView icon = new ImageView(host);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
            iconParams.setMarginEnd(ui.dp(14));
            row.addView(icon, iconParams);

            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView label = ui.text("", 16, true, ui.colorOnSurface);
            TextView pkg = ui.text("", 12, false, ui.colorOnSurfaceVariant);
            copy.addView(label);
            copy.addView(pkg);

            TextView status = ui.text("", 14, true, ui.colorPrimary);
            status.setGravity(Gravity.END);
            row.addView(status);

            CheckBox checkBox = new CheckBox(host);
            checkBox.setVisibility(View.GONE);
            row.addView(checkBox);

            return new AppRowHolder(cardView, icon, label, pkg, status, checkBox);
        }

        @Override
        public void onBindViewHolder(AppRowHolder holder, int position) {
            AppEntry app = items.get(position);
            holder.icon.setImageDrawable(app.icon);
            holder.label.setText(app.label);
            holder.pkg.setText(app.packageName);
            holder.status.setText(statusProvider.status(app));
            boolean selecting = selectionEnabled && selection.active;
            holder.status.setVisibility(selecting ? View.GONE : View.VISIBLE);
            holder.checkBox.setVisibility(selecting ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(selection.selected.contains(app.packageName));
            holder.checkBox.setOnClickListener(selecting ? v -> toggleSelection(app, holder) : null);
            holder.card.setOnClickListener(v -> {
                if (selectionEnabled && selection.active) {
                    toggleSelection(app, holder);
                    return;
                }
                listener.onAppClick(app, () -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(pos);
                }
                });
            });
            holder.card.setOnLongClickListener(v -> {
                if (!selectionEnabled || selection.active) return false;
                selection.active = true;
                selection.selected.add(app.packageName);
                notifyDataSetChanged();
                selectionChanged.run();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void toggleSelection(AppEntry app, AppRowHolder holder) {
            if (!selectionEnabled) return;
            if (selection.selected.contains(app.packageName)) {
                selection.selected.remove(app.packageName);
            } else {
                selection.selected.add(app.packageName);
            }
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) notifyItemChanged(position);
            selectionChanged.run();
        }
    }

    private static final class AppRowHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView label;
        final TextView pkg;
        final TextView status;
        final CheckBox checkBox;

        AppRowHolder(
                MaterialCardView card,
                ImageView icon,
                TextView label,
                TextView pkg,
                TextView status,
                CheckBox checkBox
        ) {
            super(card);
            this.card = card;
            this.icon = icon;
            this.label = label;
            this.pkg = pkg;
            this.status = status;
            this.checkBox = checkBox;
        }
    }

    private MaterialButton selectionButton(String text) {
        MaterialButton button = new MaterialButton(host, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setTextSize(14);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        return button;
    }

    private static final class SelectionState {
        boolean active;
        final Set<String> selected = new HashSet<>();
    }

    private List<AppEntry> loadUserApps() {
        List<AppEntry> apps = new ArrayList<>();
        PackageManager packageManager = host.getPackageManager();
        for (ApplicationInfo info : packageManager.getInstalledApplications(0)) {
            if (info == null) {
                continue;
            }
            boolean system = (info.flags
                    & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (system) {
                continue;
            }
            if (packageManager.getLaunchIntentForPackage(info.packageName) == null) {
                continue;
            }
            String label = String.valueOf(packageManager.getApplicationLabel(info));
            Drawable icon;
            try {
                icon = packageManager.getApplicationIcon(info);
            } catch (Throwable ignored) {
                icon = null;
            }
            long firstInstallTime = 0L;
            long lastUpdateTime = 0L;
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(info.packageName, 0);
                firstInstallTime = packageInfo.firstInstallTime;
                lastUpdateTime = packageInfo.lastUpdateTime;
            } catch (Throwable ignored) {
                // Keep unknown timestamps at the end of time-based sorts.
            }
            apps.add(new AppEntry(
                    info.packageName,
                    label,
                    icon,
                    firstInstallTime,
                    lastUpdateTime));
        }
        return apps;
    }
}
