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
}
