package io.github.pigerzhu.onelab.hook.samsung;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.pigerzhu.onelab.contract.SettingsKeys;

/** Verified EasyGo declarations translated to Samsung split-activity semantics. */
final class SamsungSplitRuleCatalog {
    static final String ANY_ACTIVITY = "*";

    static final RuleSet[] RULE_SETS = {
            new RuleSet(
                    SettingsKeys.KEY_ENABLE_CTRIP_SPLIT_RULES,
                    "ctrip.android.view",
                    new ActivityPair[]{
                            pair("ctrip.android.publicproduct.home.business.activity.CtripHomeActivity",
                                    "ctrip.android.view.myctrip.views.MyCtripHomeActivity"),
                            pair("ctrip.android.publicproduct.home.business.activity.CtripHomeActivity",
                                    ANY_ACTIVITY),
                            pair("ctrip.business.planthome.CtripPlantHomeActivityForFold",
                                    ANY_ACTIVITY),
                            pair("ctrip.android.hotel.list.flutter.map.HotelListMixMapFoldActivity",
                                    ANY_ACTIVITY),
                            pair("ctrip.android.flutter.containers.TripFlutterActivityForMultiEngine",
                                    ANY_ACTIVITY),
                            pair("ctrip.android.reactnative.preloadv2.CRNBaseActivityV2ForFold",
                                    ANY_ACTIVITY),
                            pair("ctrip.android.reactnative.preloadv2.CRNTransparentActivityV2ForFold",
                                    ANY_ACTIVITY),
                            pair("ctrip.android.view.h5v2.view.H5ContainerForFold",
                                    ANY_ACTIVITY)
                    },
                    setOf(
                            "ctrip.android.login.view.commonlogin.CtripLoginActivity",
                            "ctrip.android.reactnative.preloadv2.CRNBaseActivityV2ForFoldFullScreen",
                            "ctrip.android.reactnative.preloadv2.CRNBaseActivityV2ForFoldFullScreenV2",
                            "ctrip.android.reactnative.preloadv2.CRNTransparentActivityV2ForFoldFullScreen",
                            "ctrip.android.basebusiness.permission.CTPermissionHelper$HuaweiPermissionFoldCompactActivity",
                            "com.sina.weibo.sdk.share.ShareTransActivity",
                            "com.tencent.connect.common.AssistActivity")),
            new RuleSet(
                    SettingsKeys.KEY_ENABLE_UMETRIP_SPLIT_RULES,
                    "com.umetrip.android.msky.app",
                    new ActivityPair[]{
                            pair("com.umetrip.android.msky.homepage.activity.UmeHomeActivity",
                                    "com.umetrip.android.msky.homepage.activity.HomeFoldScreenActivity"),
                            pair("com.umetrip.android.msky.homepage.activity.UmeHomeActivity",
                                    ANY_ACTIVITY)
                    },
                    setOf(
                            "com.umetrip.advert.activity.AdActivity",
                            "com.umetrip.android.msky.journey.myjourney.RouteMapActivity",
                            "com.umetrip.android.msky.journey.myjourney.UmeItineraryImageActivity",
                            "com.ume.android.lib.common.video.PlayerDetailActivity",
                            "com.umetrip.android.msky.player.activity.LocalPlayerActivity")),
            new RuleSet(
                    SettingsKeys.KEY_ENABLE_MEITUAN_SPLIT_RULES,
                    "com.sankuai.meituan",
                    new ActivityPair[]{
                            pair("com.meituan.android.pt.homepage.activity.MainActivity",
                                    "com.sankuai.waimai.business.page.homepage.TakeoutActivity"),
                            pair("com.meituan.android.pt.homepage.activity.MainActivity",
                                    ANY_ACTIVITY)
                    },
                    setOf(
                            "com.meituan.android.upgrade.UpgradeDialogActivity",
                            "com.sankuai.waimai.business.restaurant.base.WebImagePreviewActivity",
                            "com.sankuai.titans.widget.media.MediaActivity",
                            "com.dianping.bizcomponent.preview.ui.BizImagePreviewActivity",
                            "com.sankuai.waimai.platform.machpro.container.WMMPActivity")),
            new RuleSet(
                    SettingsKeys.KEY_ENABLE_ZHUANZHUAN_SPLIT_RULES,
                    "com.wuba.zhuanzhuan",
                    new ActivityPair[]{
                            pair("com.wuba.zhuanzhuan.activity.MainActivity",
                                    "com.zhuanzhuan.HomeCategoryActivityWithoutBack"),
                            pair("com.wuba.zhuanzhuan.activity.MainActivity",
                                    ANY_ACTIVITY),
                            pair("com.zhuanzhuan.search.v3.SearchActivity",
                                    ANY_ACTIVITY),
                            pair("com.zhuanzhuan.searchresult.NativeSearchResultActivityV5",
                                    ANY_ACTIVITY),
                            pair("com.zhuanzhuan.module.webview.page.WebContainerActivity",
                                    ANY_ACTIVITY),
                            pair("com.zhuanzhuan.minigoodsdetail.MiniGoodsDetailActivity",
                                    ANY_ACTIVITY)
                    },
                    setOf(
                            "com.zhuanzhuan.login.page.LoginActivity",
                            "com.zhuanzhuan.module.webview.ability.app.browser.PreviewContainerActivity")),
            new RuleSet(
                    null,
                    "com.xingin.xhs",
                    new ActivityPair[]{
                            pair("com.xingin.xhs.index.v2.IndexActivityV2", ANY_ACTIVITY),
                            pair("com.xingin.alioth.search.GlobalSearchActivity", ANY_ACTIVITY),
                            pair("com.xingin.matrix.v2.profile.newpage.NewOtherUserActivity",
                                    ANY_ACTIVITY),
                            pair("com.xingin.matrix.setting.SettingActivityV2", ANY_ACTIVITY),
                            pair("com.xingin.matrix.topic.TopicActivity", ANY_ACTIVITY),
                            pair("com.xingin.reactnative.ui.XhsReactActivity", ANY_ACTIVITY),
                            pair("com.xingin.reactnative.ui.XhsReactTranslucentActivity",
                                    ANY_ACTIVITY),
                            pair("com.xingin.xywebview.activity.WebViewActivityV2", ANY_ACTIVITY),
                            pair("com.xingin.alpha.audience.v2.AlphaAudienceActivityV2",
                                    "com.xingin.alpha.audience.multiscreen."
                                            + "LiveMultiScreenShellActivity"),
                            pair("com.xingin.alpha.audience.v2.AlphaAudienceActivityV2",
                                    "com.xingin.commercial.goodsdetail.v2.activity."
                                            + "GoodsDetailActivityV2")
                    },
                    setOf("com.xingin.alpha.audience.v2.AlphaAudienceActivityV2"))
    };

    private SamsungSplitRuleCatalog() {
    }

    private static ActivityPair pair(String source, String target) {
        return new ActivityPair(source, target);
    }

    private static Set<String> setOf(String... activities) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(activities)));
    }

    static final class ActivityPair {
        final String source;
        final String target;

        ActivityPair(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }

    static final class RuleSet {
        final String settingKey;
        final String packageName;
        final ActivityPair[] pairs;
        final Set<String> fullscreenActivities;
        final AtomicBoolean enabled;

        RuleSet(
                String settingKey,
                String packageName,
                ActivityPair[] pairs,
                Set<String> fullscreenActivities) {
            this.settingKey = settingKey;
            this.packageName = packageName;
            this.pairs = pairs;
            this.fullscreenActivities = fullscreenActivities;
            enabled = new AtomicBoolean(settingKey == null);
        }
    }
}
