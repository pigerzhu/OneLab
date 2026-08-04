package io.github.pigerzhu.onelab;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;

import java.util.ArrayDeque;

import io.github.pigerzhu.onelab.feature.applications.BaiduLargeScreenScreen;
import io.github.pigerzhu.onelab.feature.applications.BiliFoldGateScreen;
import io.github.pigerzhu.onelab.feature.applications.CtripSplitRulesScreen;
import io.github.pigerzhu.onelab.feature.applications.MeituanSplitRulesScreen;
import io.github.pigerzhu.onelab.feature.applications.QqFoldLayoutScreen;
import io.github.pigerzhu.onelab.feature.applications.TongchengSplitRulesScreen;
import io.github.pigerzhu.onelab.feature.applications.UmetripSplitRulesScreen;
import io.github.pigerzhu.onelab.feature.applications.XhsFoldVideoScreen;
import io.github.pigerzhu.onelab.feature.applications.XiaomiShopFoldScreen;
import io.github.pigerzhu.onelab.feature.applications.ZhuanzhuanSplitRulesScreen;
import io.github.pigerzhu.onelab.feature.connectivity.NetworkScreen;
import io.github.pigerzhu.onelab.feature.diagnostics.DiagnosticsScreen;
import io.github.pigerzhu.onelab.feature.experiment.GalleryLabsScreen;
import io.github.pigerzhu.onelab.feature.performance.GameHeatScreen;
import io.github.pigerzhu.onelab.feature.performance.PassThroughChargingScreen;
import io.github.pigerzhu.onelab.feature.performance.ProcessingSpeedScreen;
import io.github.pigerzhu.onelab.feature.performance.ThermalScreen;
import io.github.pigerzhu.onelab.feature.window.AspectRatioScreen;
import io.github.pigerzhu.onelab.feature.window.CoverEdgeScreen;
import io.github.pigerzhu.onelab.feature.window.CoverScreen;
import io.github.pigerzhu.onelab.feature.window.RefreshRateScreen;
import io.github.pigerzhu.onelab.feature.window.SplitViewRatioScreen;
import io.github.pigerzhu.onelab.feature.window.WindowManagementScreen;
import io.github.pigerzhu.onelab.navigation.AppListPage;
import io.github.pigerzhu.onelab.navigation.FoldSidebar;
import io.github.pigerzhu.onelab.navigation.PageTransitionController;
import io.github.pigerzhu.onelab.navigation.PredictiveBackController;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.AppTheme;
import io.github.pigerzhu.onelab.ui.ChoiceGroup;
import io.github.pigerzhu.onelab.ui.Ui;

public class MainActivity extends Activity {
    private static final String STATE_APPEARANCE_PAGE = "appearance_page";
    private static final String STATE_SIDEBAR_EXPANDED = "sidebar_expanded";

    private Ui ui;
    private NetworkScreen networkScreen;
    private GalleryLabsScreen galleryLabsScreen;
    private BaiduLargeScreenScreen baiduLargeScreenScreen;
    private BiliFoldGateScreen biliFoldGateScreen;
    private CtripSplitRulesScreen ctripSplitRulesScreen;
    private UmetripSplitRulesScreen umetripSplitRulesScreen;
    private MeituanSplitRulesScreen meituanSplitRulesScreen;
    private ZhuanzhuanSplitRulesScreen zhuanzhuanSplitRulesScreen;
    private TongchengSplitRulesScreen tongchengSplitRulesScreen;
    private XiaomiShopFoldScreen xiaomiShopFoldScreen;
    private QqFoldLayoutScreen qqFoldLayoutScreen;
    private XhsFoldVideoScreen xhsFoldVideoScreen;
    private WindowManagementScreen windowManagementScreen;
    private ProcessingSpeedScreen processingSpeedScreen;
    private PassThroughChargingScreen passThroughChargingScreen;
    private CoverScreen coverScreen;
    private CoverEdgeScreen coverEdgeScreen;
    private ThermalScreen thermalScreen;
    private GameHeatScreen gameHeatScreen;
    private AspectRatioScreen aspectRatioScreen;
    private RefreshRateScreen refreshRateScreen;
    private SplitViewRatioScreen splitViewRatioScreen;
    private DiagnosticsScreen diagnosticsScreen;
    boolean showingHomePage = true;
    private Runnable nestedBackAction;
    private long lastBackPressMs;
    private FrameLayout pageHost;
    private View currentPageView;
    private final ArrayDeque<View> backPageStack = new ArrayDeque<>();
    private final PageTransitionController pageTransitions = new PageTransitionController();
    private boolean largeScreenLayout;
    private int selectedTopLevel = -1;
    private FoldSidebar foldSidebar;
    private boolean showingAppearancePage;
    private boolean predictiveParentPreviewEnabled = true;
    private PredictiveBackController predictiveBackController;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppTheme.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        SettingsStore settings = new SettingsStore(this);
        networkScreen = new NetworkScreen(this, ui, settings);
        galleryLabsScreen = new GalleryLabsScreen(this, ui, settings);
        baiduLargeScreenScreen = new BaiduLargeScreenScreen(this, ui, settings);
        biliFoldGateScreen = new BiliFoldGateScreen(this, ui, settings);
        ctripSplitRulesScreen = new CtripSplitRulesScreen(this, ui, settings);
        umetripSplitRulesScreen = new UmetripSplitRulesScreen(this, ui, settings);
        meituanSplitRulesScreen = new MeituanSplitRulesScreen(this, ui, settings);
        zhuanzhuanSplitRulesScreen =
                new ZhuanzhuanSplitRulesScreen(this, ui, settings);
        tongchengSplitRulesScreen = new TongchengSplitRulesScreen(this, ui, settings);
        xiaomiShopFoldScreen = new XiaomiShopFoldScreen(this, ui, settings);
        qqFoldLayoutScreen = new QqFoldLayoutScreen(this, ui, settings);
        xhsFoldVideoScreen = new XhsFoldVideoScreen(this, ui, settings);
        windowManagementScreen = new WindowManagementScreen(this, ui, settings);
        processingSpeedScreen = new ProcessingSpeedScreen(this, ui, settings);
        passThroughChargingScreen = new PassThroughChargingScreen(this, ui);
        thermalScreen = new ThermalScreen(this, ui, settings);
        gameHeatScreen = new GameHeatScreen(this, ui, settings);
        coverScreen = new CoverScreen(this, ui);
        coverEdgeScreen = new CoverEdgeScreen(this, ui);
        AppListPage appList = new AppListPage(this, ui);
        aspectRatioScreen = new AspectRatioScreen(this, ui, settings, appList);
        refreshRateScreen = new RefreshRateScreen(this, ui, settings, appList);
        splitViewRatioScreen = new SplitViewRatioScreen(this, ui, settings, appList);
        diagnosticsScreen = new DiagnosticsScreen(this, ui);
        predictiveBackController = PredictiveBackController.register(
                this,
                () -> currentPageView,
                () -> predictiveParentPreviewEnabled ? backPageStack.peek() : null,
                pageTransitions::isRunning,
                this::interruptPageTransitionForBack,
                this::handleBackNavigation
        );
        largeScreenLayout = isLargeScreen(getResources().getConfiguration());
        buildNavigationShell();
        if (savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_APPEARANCE_PAGE, false)) {
            if (foldSidebar != null) {
                foldSidebar.setExpandedImmediately(
                        savedInstanceState.getBoolean(STATE_SIDEBAR_EXPANDED, false));
            }
            showAppearanceSettingsPage(true);
        } else if (largeScreenLayout) {
            showLargeScreenPrompt();
        } else {
            showHomePage();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_APPEARANCE_PAGE, showingAppearancePage);
        if (foldSidebar != null) {
            outState.putBoolean(STATE_SIDEBAR_EXPANDED, foldSidebar.isExpanded());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean useLargeLayout = isLargeScreen(newConfig);
        if (useLargeLayout != largeScreenLayout) {
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        pageTransitions.interrupt();
        if (predictiveBackController != null) predictiveBackController.unregister();
        if (coverScreen != null) coverScreen.onDestroy();
        if (coverEdgeScreen != null) coverEdgeScreen.onDestroy();
        if (processingSpeedScreen != null) processingSpeedScreen.onDestroy();
        if (passThroughChargingScreen != null) passThroughChargingScreen.onDestroy();
        if (diagnosticsScreen != null) diagnosticsScreen.onDestroy();
        super.onDestroy();
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (pageTransitions.isRunning()) return;
        if (nestedBackAction != null) {
            Runnable action = nestedBackAction;
            nestedBackAction = null;
            action.run();
            return;
        }
        if (!showingHomePage) {
            if (largeScreenLayout) {
                showLargeScreenPrompt();
            } else {
                showHomePage(true);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressMs < 1600L) {
            finish();
            return;
        }
        lastBackPressMs = now;
        Toast.makeText(this, "再返回一次退出", Toast.LENGTH_SHORT).show();
    }

    private void showHomePage() {
        showHomePage(false);
    }

    private void showHomePage(boolean animateBack) {
        if (largeScreenLayout) {
            showLargeScreenPrompt();
            return;
        }
        showingAppearancePage = false;
        showingHomePage = true;
        selectedTopLevel = -1;
        nestedBackAction = null;
        LinearLayout root = beginPage(animateBack ? -1 : 0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(header, ui.matchWrap());

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heading.addView(ui.text("OneLab", 34, true, ui.colorOnSurface));
        heading.addView(ui.text(
                "One UI tweaks and behavior fixes", 16, false, ui.colorOnSurfaceVariant));

        ImageButton appearanceButton = new ImageButton(this);
        appearanceButton.setImageResource(R.drawable.ic_settings);
        appearanceButton.setImageTintList(ColorStateList.valueOf(ui.colorOnSurface));
        appearanceButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        appearanceButton.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        appearanceButton.setContentDescription("外观设置");
        appearanceButton.setTooltipText("外观设置");
        appearanceButton.setOnClickListener(v -> showAppearanceSettingsPage());
        LinearLayout.LayoutParams appearanceParams = new LinearLayout.LayoutParams(
                ui.dp(48), ui.dp(48));
        appearanceParams.setMarginStart(ui.dp(12));
        header.addView(appearanceButton, appearanceParams);

        ui.addSpace(root, 20);
        root.addView(ui.homeButton(R.drawable.ic_home_connectivity, Ui.HOME_NETWORK,
                "网络与连接", "认证页保活与连接实验", v -> showNetworkPage()));
        root.addView(ui.homeButton(R.drawable.ic_home_performance, Ui.HOME_PERFORMANCE,
                "性能与温控", "处理速度、SIOP 稳帧", v -> showPerformancePage()));
        root.addView(ui.homeButton(R.drawable.ic_home_system, Ui.HOME_SYSTEM,
                "系统界面", "窗口与系统 UI 相关隐藏开关", v -> showSystemUiPage()));
        root.addView(ui.homeButton(R.drawable.ic_home_apps, Ui.HOME_APPS,
                "应用程序", "应用功能扩展", v -> showSamsungAppsPage()));
        root.addView(ui.homeButton(R.drawable.ic_home_experiments, Ui.HOME_EXPERIMENTS,
                "实验功能", "", v -> showExperimentsPage()));
    }

    private void showAppearanceSettingsPage() {
        showAppearanceSettingsPage(false);
    }

    private void showAppearanceSettingsPage(boolean preserveSidebarState) {
        showingAppearancePage = true;
        selectedTopLevel = -1;
        if (foldSidebar != null) {
            foldSidebar.setSelectedSection(-1);
            if (!preserveSidebarState) {
                foldSidebar.collapse();
            }
        }
        showingHomePage = false;
        nestedBackAction = null;
        LinearLayout root = beginSubPage(
                "外观设置", "选择 OneLab 的显示主题。", topLevelEnterDirection());

        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);
        body.addView(ui.text("主题", 20, true, ui.colorOnSurface));
        ui.addSpace(body, 14);

        ChoiceGroup themeGroup = new ChoiceGroup(this, ui);
        body.addView(themeGroup, ui.matchWrap());
        themeGroup.addOption("跟随系统", "使用手机当前的深色模式设置", AppTheme.MODE_SYSTEM);
        themeGroup.addOption("浅色", "始终使用浅色界面", AppTheme.MODE_LIGHT);
        themeGroup.addOption("深色", "始终使用深色界面", AppTheme.MODE_DARK);
        themeGroup.setValue(AppTheme.getMode(this));
        themeGroup.setOnChoiceChangedListener(mode -> {
            if (mode != AppTheme.getMode(this)) {
                AppTheme.setMode(this, mode);
                recreate();
            }
        });
        root.addView(card);
        root.addView(diagnosticsScreen.card());
    }

    private void showNetworkPage() {
        markTopLevel(Ui.HOME_NETWORK);
        nestedBackAction = null;
        LinearLayout root = beginSubPage(
                "网络与连接", "和网络登录、连接相关的功能。", topLevelEnterDirection());
        root.addView(networkScreen.card());
    }

    private void showPerformancePage() {
        showPerformancePage(false);
    }

    private void showPerformancePage(boolean animateBack) {
        markTopLevel(Ui.HOME_PERFORMANCE);
        nestedBackAction = null;
        LinearLayout root = beginSubPage(
                "性能与温控", "处理速度和隐藏温控。",
                animateBack ? -1 : topLevelEnterDirection());
        root.addView(thermalScreen.sdhmsThermalMasterCard());
        root.addView(passThroughChargingScreen.card());
        root.addView(thermalScreen.sdhmsHiddenThermalCard());
        root.addView(processingSpeedScreen.card());
    }

    private void showSystemUiPage() {
        showSystemUiPage(false);
    }

    public void showSystemUiPage(boolean animateBack) {
        markTopLevel(Ui.HOME_SYSTEM);
        nestedBackAction = null;
        LinearLayout root = beginSubPage(
                "系统界面", "系统窗口和 SystemUI 相关功能。",
                animateBack ? -1 : topLevelEnterDirection());
        root.addView(windowManagementScreen.persistFreeformBoundsCard());
        root.addView(coverScreen.outerSystemCard());
        root.addView(refreshRateScreen.entryCard());
        root.addView(aspectRatioScreen.entryCard());
        root.addView(splitViewRatioScreen.entryCard());
        root.addView(coverScreen.card());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        coverScreen.onActivityResult(requestCode, resultCode, data);
    }

    private void showSamsungAppsPage() {
        markTopLevel(Ui.HOME_APPS);
        nestedBackAction = null;
        LinearLayout root = beginSubPage(
                "应用程序", "应用功能扩展。", topLevelEnterDirection());
        root.addView(biliFoldGateScreen.card());
        root.addView(xhsFoldVideoScreen.card());
        root.addView(qqFoldLayoutScreen.card());
        root.addView(xiaomiShopFoldScreen.card());
        root.addView(baiduLargeScreenScreen.card());
        root.addView(ctripSplitRulesScreen.card());
        root.addView(umetripSplitRulesScreen.card());
        root.addView(meituanSplitRulesScreen.card());
        root.addView(zhuanzhuanSplitRulesScreen.card());
        root.addView(tongchengSplitRulesScreen.card());
        root.addView(galleryLabsScreen.card());
    }

    private void showExperimentsPage() {
        showExperimentsPage(false);
    }

    public void showExperimentsPage(boolean animateBack) {
        markTopLevel(Ui.HOME_EXPERIMENTS);
        nestedBackAction = null;
        LinearLayout root = beginSubPage(
                "实验功能", "仅测试用途，不能保证有效",
                animateBack ? -1 : topLevelEnterDirection());
        root.addView(gameHeatScreen.entryCard());
        root.addView(thermalScreen.entryCard());
        root.addView(coverEdgeScreen.entryCard());
    }

    private LinearLayout beginPage(int animationDirection) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ui.colorSurface);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(28));
        scrollView.addView(root, ui.matchWrap());
        switchPage(scrollView, animationDirection);
        return root;
    }

    private void buildNavigationShell() {
        pageHost = new FrameLayout(this);
        pageHost.setBackgroundColor(ui.colorSurface);
        currentPageView = null;

        if (!largeScreenLayout) {
            foldSidebar = null;
            pageHost.setClipChildren(true);
            pageHost.setClipToPadding(true);
            pageHost.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getInsets(
                        android.view.WindowInsets.Type.statusBars()).top + ui.dp(8);
                if (view.getPaddingTop() != top) {
                    view.setPadding(0, top, 0, 0);
                }
                return insets;
            });
            setContentView(pageHost);
            pageHost.requestApplyInsets();
            return;
        }

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setBackgroundColor(ui.colorSurface);
        foldSidebar = new FoldSidebar(this, ui, selectedTopLevel, new FoldSidebar.Listener() {
            @Override
            public void onSectionSelected(int section) {
                selectedTopLevel = section;
                foldSidebar.setSelectedSection(section);
                foldSidebar.collapse();
                showTopLevel(section);
            }

            @Override
            public void onAppearanceSelected() {
                showAppearanceSettingsPage();
            }
        });
        shell.addView(foldSidebar.view());

        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        hostParams.setMargins(ui.dp(6), ui.dp(32), ui.dp(12), ui.dp(12));
        shell.addView(pageHost, hostParams);
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getInsets(
                    android.view.WindowInsets.Type.statusBars()).top + ui.dp(8);
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) pageHost.getLayoutParams();
            if (params.topMargin != top) {
                params.topMargin = top;
                pageHost.setLayoutParams(params);
            }
            return insets;
        });
        shell.requestApplyInsets();
        setContentView(shell);
    }

    private void showLargeScreenPrompt() {
        showingAppearancePage = false;
        showingHomePage = true;
        nestedBackAction = null;
        selectedTopLevel = -1;
        if (foldSidebar != null) {
            foldSidebar.setSelectedSection(-1);
            foldSidebar.expand();
        }

        LinearLayout prompt = new LinearLayout(this);
        prompt.setGravity(Gravity.CENTER);
        prompt.setOrientation(LinearLayout.VERTICAL);
        prompt.setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32));
        prompt.addView(ui.text("选择一个功能", 26, true, ui.colorOnSurface));
        ui.addSpace(prompt, 8);
        prompt.addView(ui.text(
                "从左侧菜单选择要查看的内容", 15, false, ui.colorOnSurfaceVariant));
        switchPage(prompt, 0);
    }

    private void showTopLevel(int section) {
        switch (section) {
            case Ui.HOME_NETWORK:
                showNetworkPage();
                break;
            case Ui.HOME_PERFORMANCE:
                showPerformancePage();
                break;
            case Ui.HOME_SYSTEM:
                showSystemUiPage();
                break;
            case Ui.HOME_APPS:
                showSamsungAppsPage();
                break;
            case Ui.HOME_EXPERIMENTS:
                showExperimentsPage();
                break;
            default:
                showLargeScreenPrompt();
                break;
        }
    }

    private void markTopLevel(int section) {
        showingAppearancePage = false;
        selectedTopLevel = section;
        if (foldSidebar != null) {
            foldSidebar.setSelectedSection(section);
        }
    }

    private static boolean isLargeScreen(Configuration configuration) {
        return configuration.screenWidthDp >= 600;
    }

    public boolean isUsingLargeScreenLayout() {
        return largeScreenLayout;
    }

    private int topLevelEnterDirection() {
        return largeScreenLayout ? 0 : 1;
    }

    private LinearLayout beginSubPage(String title, String subtitle) {
        return beginSubPage(title, subtitle, 1);
    }

    public LinearLayout beginSubPage(String title, String subtitle, int animationDirection) {
        showingHomePage = false;
        LinearLayout root = beginPage(animationDirection);
        root.addView(ui.text(title, 32, true, ui.colorOnSurface));
        root.addView(ui.text(subtitle, 15, false, ui.colorOnSurfaceVariant));
        ui.addSpace(root, 20);
        return root;
    }

    public void switchPage(View nextPage, int direction) {
        View previousPage = currentPageView;
        predictiveParentPreviewEnabled = true;

        if (direction == 0) {
            backPageStack.clear();
            pageHost.removeAllViews();
            currentPageView = nextPage;
            pageHost.addView(nextPage, pageParams());
            nextPage.setTranslationX(0f);
            nextPage.setScaleX(1f);
            nextPage.setScaleY(1f);
            nextPage.setAlpha(1f);
            return;
        }

        if (direction < 0 && !backPageStack.isEmpty()) {
            View restoredPage = backPageStack.pop();
            currentPageView = restoredPage;
            animateDetailOut(previousPage, restoredPage);
            return;
        }

        currentPageView = nextPage;
        pageHost.addView(nextPage, pageParams());

        if (previousPage == null) {
            nextPage.setTranslationX(0f);
            nextPage.setAlpha(1f);
            return;
        }
        if (direction > 0) {
            backPageStack.push(previousPage);
            animateDetailIn(previousPage, nextPage);
        } else {
            animateDetailOut(previousPage, nextPage);
        }
    }

    public void setPredictiveParentPreviewEnabled(boolean enabled) {
        predictiveParentPreviewEnabled = enabled;
    }

    public Runnable getNestedBackAction() {
        return nestedBackAction;
    }

    public void setNestedBackAction(Runnable action) {
        nestedBackAction = action;
    }

    public void setShowingHomePage(boolean showing) {
        showingHomePage = showing;
    }

    private FrameLayout.LayoutParams pageParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void animateDetailIn(View previousPage, View nextPage) {
        int width = getResources().getDisplayMetrics().widthPixels;
        pageTransitions.animateIn(previousPage, nextPage, width);
    }

    private void animateDetailOut(View previousPage, View nextPage) {
        int width = getResources().getDisplayMetrics().widthPixels;
        if (nextPage.getParent() != pageHost) {
            nextPage.setTranslationX(-width * 0.16f);
            nextPage.setAlpha(0.92f);
        }
        pageTransitions.animateOut(pageHost, previousPage, nextPage, width);
    }

    private void interruptPageTransitionForBack() {
        pageTransitions.interrupt();
    }
}
