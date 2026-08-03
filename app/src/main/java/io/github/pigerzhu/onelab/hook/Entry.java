package io.github.pigerzhu.onelab.hook;

import io.github.pigerzhu.onelab.hook.applications.BiliFoldGateHook;
import io.github.pigerzhu.onelab.hook.applications.BaiduLargeScreenHook;
import io.github.pigerzhu.onelab.hook.applications.GalleryLabsHook;
import io.github.pigerzhu.onelab.hook.applications.LarkSplitRatioHook;
import io.github.pigerzhu.onelab.hook.applications.QqFoldLayoutHook;
import io.github.pigerzhu.onelab.hook.applications.TongchengSplitRulesHook;
import io.github.pigerzhu.onelab.hook.applications.XhsFoldVideoHook;
import io.github.pigerzhu.onelab.hook.applications.XiaomiShopFoldHook;
import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.samsung.ActivityEmbeddingRatioHook;
import io.github.pigerzhu.onelab.hook.samsung.SamsungSplitRatioHook;
import io.github.pigerzhu.onelab.hook.samsung.SamsungSplitRulesHook;
import io.github.pigerzhu.onelab.hook.system.AspectRatioHook;
import io.github.pigerzhu.onelab.hook.system.CaptivePortalHook;
import io.github.pigerzhu.onelab.hook.system.GosPermissionHook;
import io.github.pigerzhu.onelab.hook.system.RefreshRateHook;
import io.github.pigerzhu.onelab.hook.system.SdhmsThermalHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Entry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (HookConstants.CAPTIVE_PORTAL_PACKAGE.equals(lpparam.packageName)) {
            CaptivePortalHook.install(lpparam);
        } else if (HookConstants.GALLERY_PACKAGE.equals(lpparam.packageName)) {
            GalleryLabsHook.install(lpparam);
        } else if (HookConstants.BILIBILI_PACKAGE.equals(lpparam.packageName)) {
            BiliFoldGateHook.install(lpparam);
        } else if (HookConstants.BAIDU_PACKAGE.equals(lpparam.packageName)) {
            BaiduLargeScreenHook.install(lpparam);
        } else if (HookConstants.QQ_PACKAGE.equals(lpparam.packageName)) {
            QqFoldLayoutHook.install(lpparam);
        } else if (HookConstants.XHS_PACKAGE.equals(lpparam.packageName)) {
            XhsFoldVideoHook.install(lpparam);
        } else if (HookConstants.TONGCHENG_PACKAGE.equals(lpparam.packageName)) {
            TongchengSplitRulesHook.install(lpparam);
            ActivityEmbeddingRatioHook.install(lpparam);
        } else if (HookConstants.XIAOMI_SHOP_PACKAGE.equals(lpparam.packageName)) {
            XiaomiShopFoldHook.install(lpparam);
        } else if (HookConstants.FEISHU_PACKAGE.equals(lpparam.packageName)) {
            LarkSplitRatioHook.install(lpparam);
            ActivityEmbeddingRatioHook.install(lpparam);
        } else if (HookConstants.GOS_PACKAGE.equals(lpparam.packageName)) {
            GosPermissionHook.install(lpparam);
        } else if (HookConstants.SDHMS_PACKAGE.equals(lpparam.packageName)) {
            SdhmsThermalHook.install(lpparam);
        } else if (HookConstants.isActivityEmbeddingCandidate(lpparam.packageName)) {
            ActivityEmbeddingRatioHook.install(lpparam);
        } else if (HookConstants.isSystemServerPackage(lpparam.packageName)) {
            AspectRatioHook.install(lpparam);
            SamsungSplitRatioHook.install(lpparam);
            SamsungSplitRulesHook.install(lpparam);
            RefreshRateHook.install(lpparam);
        } else {
            ActivityEmbeddingRatioHook.installIfConfigured(lpparam);
        }
    }
}
