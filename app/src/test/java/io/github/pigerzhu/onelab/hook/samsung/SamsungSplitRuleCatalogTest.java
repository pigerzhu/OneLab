package io.github.pigerzhu.onelab.hook.samsung;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public final class SamsungSplitRuleCatalogTest {
    @Test
    public void xhsRulesPreserveCurrentEasyGoRoutesAndLiveFullscreenException() {
        SamsungSplitRuleCatalog.RuleSet rules = findRuleSet("com.xingin.xhs");

        assertNotNull(rules);
        assertEquals("com.xingin.xhs", rules.packageName);
        assertNull(rules.settingKey);
        assertTrue(rules.enabled.get());
        assertEquals(10, rules.pairs.length);

        Set<String> pairs = new HashSet<>();
        for (SamsungSplitRuleCatalog.ActivityPair pair : rules.pairs) {
            pairs.add(pair.source + " -> " + pair.target);
        }

        assertTrue(pairs.contains("com.xingin.xhs.index.v2.IndexActivityV2 -> *"));
        assertTrue(pairs.contains("com.xingin.alioth.search.GlobalSearchActivity -> *"));
        assertTrue(pairs.contains(
                "com.xingin.matrix.v2.profile.newpage.NewOtherUserActivity -> *"));
        assertTrue(pairs.contains("com.xingin.matrix.setting.SettingActivityV2 -> *"));
        assertTrue(pairs.contains("com.xingin.matrix.topic.TopicActivity -> *"));
        assertTrue(pairs.contains("com.xingin.reactnative.ui.XhsReactActivity -> *"));
        assertTrue(pairs.contains(
                "com.xingin.reactnative.ui.XhsReactTranslucentActivity -> *"));
        assertTrue(pairs.contains(
                "com.xingin.xywebview.activity.WebViewActivityV2 -> *"));
        assertTrue(pairs.contains(
                "com.xingin.alpha.audience.v2.AlphaAudienceActivityV2 -> "
                        + "com.xingin.alpha.audience.multiscreen.LiveMultiScreenShellActivity"));
        assertTrue(pairs.contains(
                "com.xingin.alpha.audience.v2.AlphaAudienceActivityV2 -> "
                        + "com.xingin.commercial.goodsdetail.v2.activity.GoodsDetailActivityV2"));

        assertFalse(pairs.stream().anyMatch(pair -> pair.contains("xhs.v2.setting")));
        assertFalse(pairs.stream().anyMatch(pair -> pair.contains("matrix.v2.topic")));
        assertFalse(pairs.stream().anyMatch(pair -> pair.contains(
                "com.xingin.alpha.audience.AlphaAudienceActivity ->")));
        assertFalse(pairs.stream().anyMatch(pair -> pair.endsWith(
                "com.xingin.commercial.goodsdetail.GoodsDetailActivity")));
        assertEquals(Set.of("com.xingin.alpha.audience.v2.AlphaAudienceActivityV2"),
                rules.fullscreenActivities);
    }

    @Test
    public void weiboRuleKeepsSamsungPairsAndForcesKnownMediaViewersFullscreen() {
        SamsungSplitRuleCatalog.RuleSet rules = findRuleSet("com.sina.weibo");

        assertNotNull(rules);
        assertEquals("onelab_weibo_image_fullscreen", rules.settingKey);
        assertEquals("onelab_split_image_fullscreen", rules.masterSettingKey);
        assertFalse(rules.enabled.get());
        assertEquals(0, rules.pairs.length);
        assertFalse(rules.managesRepository());
        assertTrue(rules.isEnabledBy(true, true));
        assertFalse(rules.isEnabledBy(false, true));
        assertFalse(rules.isEnabledBy(true, false));
        assertEquals(Set.of(
                        "com.sina.weibo.preview.MediaPreviewActivity",
                        "com.sina.weibo.photoalbum.media.stream.vertical."
                                + "VerticalFlowImageViewerActivity",
                        "com.sina.weibo.photoalbum.imageviewer.ImageViewer",
                        "com.sina.weibo.story.multiv2.core.MediaCoreV2Activity"),
                rules.fullscreenActivities);
        assertEquals(Set.of(
                        "com.sina.weibo.preview.MediaPreviewActivity",
                        "com.sina.weibo.photoalbum.media.stream.vertical."
                                + "VerticalFlowImageViewerActivity",
                        "com.sina.weibo.photoalbum.imageviewer.ImageViewer"),
                rules.followDeviceOrientationActivities);
    }

    @Test
    public void weiboOrientationPolicyRejectsOnlyImagePortraitRequestsWhenEnabled() {
        SamsungSplitRuleCatalog.RuleSet rules = findRuleSet("com.sina.weibo");
        assertNotNull(rules);

        rules.enabled.set(true);
        try {
            assertTrue(rules.shouldIgnorePortraitRequest(
                    "com.sina.weibo.preview.MediaPreviewActivity", 1));
            assertFalse(rules.shouldIgnorePortraitRequest(
                    "com.sina.weibo.preview.MediaPreviewActivity", -1));
            assertFalse(rules.shouldIgnorePortraitRequest(
                    "com.sina.weibo.preview.MediaPreviewActivity", 6));
            assertFalse(rules.shouldIgnorePortraitRequest(
                    "com.sina.weibo.story.multiv2.core.MediaCoreV2Activity", 1));
        } finally {
            rules.enabled.set(false);
        }

        assertFalse(rules.shouldIgnorePortraitRequest(
                "com.sina.weibo.preview.MediaPreviewActivity", 1));
    }

    @Test
    public void weiboLaunchPolicyNeutralizesOnlyImageOrientationWhenEnabled() {
        SamsungSplitRuleCatalog.RuleSet rules = findRuleSet("com.sina.weibo");
        assertNotNull(rules);

        rules.enabled.set(true);
        try {
            assertTrue(rules.shouldFollowDeviceOrientation(
                    "com.sina.weibo.preview.MediaPreviewActivity"));
            assertFalse(rules.shouldFollowDeviceOrientation(
                    "com.sina.weibo.story.multiv2.core.MediaCoreV2Activity"));
        } finally {
            rules.enabled.set(false);
        }

        assertFalse(rules.shouldFollowDeviceOrientation(
                "com.sina.weibo.preview.MediaPreviewActivity"));
    }

    private static SamsungSplitRuleCatalog.RuleSet findRuleSet(String packageName) {
        for (SamsungSplitRuleCatalog.RuleSet ruleSet : SamsungSplitRuleCatalog.RULE_SETS) {
            if (packageName.equals(ruleSet.packageName)) {
                return ruleSet;
            }
        }
        return null;
    }
}
