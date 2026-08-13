package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class GalleryLabsChineseTranslationsTest {
    @Test
    public void coversAllKnownGalleryLabsStringResources() {
        assertEquals(80, GalleryLabsChineseTranslations.resourceTranslations().size());
        GalleryLabsChineseTranslations.resourceTranslations().forEach((name, translation) -> {
            assertFalse(name.isBlank());
            assertFalse(translation.isBlank());
        });
    }

    @Test
    public void translatesHardcodedLabsCategoriesWithoutChangingUnknownText() {
        assertEquals("客户服务",
                GalleryLabsChineseTranslations.literalTranslation("Customer services"));
        assertEquals("视频查看器",
                GalleryLabsChineseTranslations.literalTranslation("Video viewer"));
        assertNotNull(GalleryLabsChineseTranslations.literalTranslation("Debugging options"));
        assertEquals("[performance_log] 将耗时操作记录到日志文件",
                GalleryLabsChineseTranslations.literalTranslation(
                        "[performance_log] Store slow operations to log file"));
        assertEquals(null, GalleryLabsChineseTranslations.literalTranslation("Albums"));
    }

    @Test
    public void translatesDeveloperLabsPreferencesAndPreservesInternalKeys() {
        assertEquals("对象捕获调试模式",
                GalleryLabsChineseTranslations.literalTranslation("Object Capture Debug Mode"));
        assertEquals("显示对象捕获轮廓以便调试",
                GalleryLabsChineseTranslations.literalTranslation(
                        "Show object capture outline for debugging"));
        assertEquals("自适应快速滚动",
                GalleryLabsChineseTranslations.literalTranslation("Adaptive fast scroll"));
        assertEquals("[recover_last_stack] 从进程终止或 Activity 重建中恢复上次的页面堆栈",
                GalleryLabsChineseTranslations.literalTranslation(
                        "[recover_last_stack] Recover last fragment stack from process kill or activity recreate."));
        assertEquals("远程图库支持",
                GalleryLabsChineseTranslations.literalTranslation("Support Remote Gallery"));
        assertEquals("图库动态照片播放器",
                GalleryLabsChineseTranslations.literalTranslation("Gallery motion photo player"));
        assertEquals("启用故事默认主题",
                GalleryLabsChineseTranslations.literalTranslation("Enable story default theme"));
    }

    @Test
    public void translatesDeveloperLabsDynamicYearInterval() {
        assertEquals("每 5 分钟选取 1 张代表图片",
                GalleryLabsChineseTranslations.literalTranslation(
                        "1 representative image every 5 minutes"));
        assertEquals(null,
                GalleryLabsChineseTranslations.literalTranslation(
                        "2 representative images every 5 minutes"));
    }

    @Test
    public void translatesDeveloperLabsDynamicOptionSummary() {
        assertEquals("选项：始终静音（默认）",
                GalleryLabsChineseTranslations.literalTranslation(
                        "Option: Always mute (default)"));
        assertEquals(null,
                GalleryLabsChineseTranslations.literalTranslation(
                        "Option: a future unknown value"));
    }
}
