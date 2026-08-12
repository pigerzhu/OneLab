package io.github.pigerzhu.onelab.hook.applications;

import java.util.LinkedHashMap;
import java.util.Map;

final class GalleryLabsChineseTranslations {
    private static final Map<String, String> RESOURCE_TRANSLATIONS = resourceMap();
    private static final Map<String, String> RESOURCE_ORIGINAL_TRANSLATIONS =
            resourceOriginalMap();
    private static final Map<String, String> LITERAL_TRANSLATIONS = Map.ofEntries(
            Map.entry("Customer services", "客户服务"),
            Map.entry("Video viewer", "视频查看器"),
            Map.entry("Image viewer", "图片查看器"),
            Map.entry("Utilities", "实用工具"),
            Map.entry("Debugging options", "调试选项"),
            Map.entry("Manager", "管理"),
            Map.entry("AI features", "AI 功能"),
            Map.entry("Remote Gallery", "远程图库"),
            Map.entry("Year View", "年份视图"),
            Map.entry("PhotoStrip", "照片条"),
            Map.entry("Viewer options", "查看器选项"),
            Map.entry("Developer options", "开发者选项"),
            Map.entry("Debug log options", "调试日志选项"),
            Map.entry("Convert HEIF images when sharing", "共享时转换 HEIF 图片"),
            Map.entry("Open in other window", "在其他窗口中打开"),
            Map.entry("Go to Studio", "前往工作室"),
            Map.entry("Mirror screen mode in viewer", "查看器镜像屏幕模式"),
            Map.entry("Use AI zoom", "使用 AI 缩放"),
            Map.entry("Show full address in details", "在详细信息中显示完整地址"),
            Map.entry("Collections: optional categories", "收藏集：可选类别"),
            Map.entry("Private album", "私密相册"),
            Map.entry("Album database backup and restore", "相册数据库备份与还原"),
            Map.entry("File browser", "文件浏览器"),
            Map.entry("Restore files in Private album", "还原私密相册中的文件"),
            Map.entry("Recover not-scanned trash items", "恢复未扫描的回收站项目"),
            Map.entry("Scan missing image and video to show in Gallery", "扫描缺失的图片和视频并显示在图库中"),
            Map.entry("Clear settings changed in Labs", "清除实验室中更改的设置"),
            Map.entry("Safe mode", "安全模式"),
            Map.entry("Search", "搜索"),
            Map.entry("Use Android embedded image decoder for image decoding", "使用 Android 内置图片解码器解码图片"),
            Map.entry("Show additional embedded information from jpeg", "显示 JPEG 中嵌入的其他信息"),
            Map.entry("Album entry-locks with lock-screen credentials. Note that it shall not provide secure service or access control of multimedia contents.", "使用锁屏凭据锁定相册入口。请注意，此功能不提供多媒体内容的安全服务或访问控制"),
            Map.entry("Show \"Recent\" and \"Favorites\" albums on album tab", "在相册选项卡中显示“最近”和“收藏”相册"),
            Map.entry("Making a PDF file with images: \"Create > Save as PDF\" after selecting images", "选择图片后，通过“创建 > 另存为 PDF”生成 PDF 文件"),
            Map.entry("Print multiple pictures on pictures tab", "在图片选项卡中打印多张图片"),
            Map.entry("Open new viewer in other window from image/video viewer", "从图片或视频查看器在其他窗口中打开新查看器"),
            Map.entry("Tag in search first page is moved to recommendation in ONE UI 6.x", "在 One UI 6.x 中，将搜索首页的标签移至推荐区域"),
            Map.entry("Enhance image while using pinch zoom for small images", "对小图片使用双指缩放时增强图像"),
            Map.entry("Support private storage to keep image and video in hidden area", "使用私密存储将图片和视频保存在隐藏区域"),
            Map.entry("Backup or restore album database", "备份或还原相册数据库"),
            Map.entry("You can browse files in \"/Android/media/com.sec.android.gallery3d/\", and preview zip-files", "浏览 /Android/media/com.sec.android.gallery3d/ 中的文件并预览 ZIP 文件"),
            Map.entry("Once enabled, decoding in the picture list is disabled to find and remove contents causing system crash", "启用后将停止解码图片列表，以查找并移除导致系统崩溃的内容"),
            Map.entry("Pinch zoom with motion photo video", "动态照片视频双指缩放"),
            Map.entry("Support pinch zoom gestures on motion photo video viewer", "在动态照片视频查看器中支持双指缩放手势"),
            Map.entry("Next video starts automatically right after video playback ends with the OSD turned off", "关闭屏幕显示信息后，当前视频播放结束会自动播放下一个视频"),
            Map.entry("Use first frame as video thumbnail. If disabled, legacy policy, extracting a frame at 15 sec from video for representative thumbnail, is applied", "使用视频第一帧作为缩略图。关闭后将采用旧策略，截取视频第 15 秒画面作为代表缩略图")
    );

    private GalleryLabsChineseTranslations() {
    }

    static Map<String, String> resourceTranslations() {
        return RESOURCE_TRANSLATIONS;
    }

    static String literalTranslation(String original) {
        String translation = LITERAL_TRANSLATIONS.get(original);
        if (translation == null) {
            translation = RESOURCE_ORIGINAL_TRANSLATIONS.get(original);
        }
        if (translation != null) {
            return translation;
        }
        if (!original.startsWith("[")) {
            return null;
        }
        int separator = original.indexOf("] ");
        if (separator < 0) {
            return null;
        }
        String value = original.substring(separator + 2);
        translation = LITERAL_TRANSLATIONS.get(value);
        if (translation == null) {
            translation = RESOURCE_ORIGINAL_TRANSLATIONS.get(value);
        }
        return translation == null
                ? null
                : original.substring(0, separator + 2) + translation;
    }

    private static Map<String, String> resourceMap() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("gallery_labs_category", "实验室");
        values.put("gallery_labs_configurations", "配置实验室");
        values.put("gallery_labs_developer_title", "开发者实验室");
        values.put("gallery_labs_search_title", "搜索实验室");
        values.put("gallery_labs_summary", "仅供内部测试。实验室中的功能并非正式功能，可能随时移除");
        values.put("gallery_labs_title", "图库实验室");
        values.put("labs_description_hamburger_menu_in_viewer_bottom", "关闭后，查看器底部菜单（⋮）将移至顶部菜单。此功能自 One UI 6.0 起已停用");
        values.put("labs_dev_managing_options", "管理选项");
        values.put("labs_notice_album_bnr", "注意：此菜单仅供开发者使用。测试人员可以将相册数据库备份或还原为 JSON 文件。还原会清空并更新相册数据库，请先备份。\n\n*（S）表示系统备份文件");
        values.put("labs_notice_restart_application", "注意：实验室中的所有功能均为内部测试用途，并非正式功能。请勿随意更改下列选项，否则可能导致数据损坏或异常行为");
        values.put("labs_summary_advanced_video_preview", "支持在视频预览及投屏到电视时播放、暂停和拖动进度");
        values.put("labs_summary_appbar_expanding", "打开图库时应用栏展开状态的默认值");
        values.put("labs_summary_backup_album_db", "将相册数据库保存为 JSON 备份文件");
        values.put("labs_summary_backup_disk_cache", "归档图库缓存的低分辨率图片");
        values.put("labs_summary_backup_trash", "将回收站中的文件复制到外部存储（下载文件夹），不还原数据库");
        values.put("labs_summary_capture_system_heap_dump", "转储 Android 系统进程的堆。请注意：其中可能包含该进程可访问的敏感个人信息");
        values.put("labs_summary_day_merge_clustering", "支持在图片列表中合并只有一个项目的日期分组");
        values.put("labs_summary_dual_photo_preview", "更改实时虚化照片的近景或广角预览时不会保存文件");
        values.put("labs_summary_filmstrip", "在查看器中显示胶片条，便于快速滚动");
        values.put("labs_summary_nested_add_to_folder", "将相册添加到文件夹时显示分层文件夹结构");
        values.put("labs_summary_new_empty_album", "为空相册启用新架构");
        values.put("labs_summary_no_status_bar_in_landscape", "启用后在横屏模式下隐藏状态栏");
        values.put("labs_summary_oneui30_memories", "使用回忆替换故事选项卡");
        values.put("labs_summary_performance_log", "将耗时操作记录到日志文件");
        values.put("labs_summary_restore_album_db", "从 JSON 文件还原相册数据库");
        values.put("labs_summary_sd_card_health_state", "测试 SD 卡健康状态提示卡片");
        values.put("labs_summary_share_albums", "支持通过 Wi-Fi Direct 选择相册并共享内容");
        values.put("labs_summary_show_trash_storage", "删除内容时显示回收站已用容量");
        values.put("labs_summary_similar_photo", "支持相似照片。启用后可在图片选项卡中看到新菜单");
        values.put("labs_summary_single_take_photo", "支持一键多拍照片");
        values.put("labs_summary_stories_oneui_21", "在故事选项卡中使用瀑布流列表视图");
        values.put("labs_summary_thumbnail_preview", "在图片列表中预览视频缩略图");
        values.put("labs_summary_timeline_in_album", "按日期对相册视图中的图片分组");
        values.put("labs_summary_timeline_in_search", "按日期对搜索相册中的图片分组");
        values.put("labs_summary_timeline_in_smart_album", "按日期对视频或收藏相册中的图片分组");
        values.put("labs_summary_troubleshooting", "检查图库中无法显示的图片和视频，并修复大多数由媒体数据库错误引起的问题");
        values.put("labs_summary_your_phone_phase_3", "允许从 Microsoft 手机连接接收拖放事件");
        values.put("labs_title_advanced_video_preview", "高级视频播放器");
        values.put("labs_title_album_entry_locks", "相册进入锁定");
        values.put("labs_title_android_image_decoder", "Android 图片解码器");
        values.put("labs_title_appbar_expanding", "展开应用栏");
        values.put("labs_title_backup_disk_cache", "备份磁盘缓存文件");
        values.put("labs_title_backup_trash", "备份回收站文件");
        values.put("labs_title_capture_system_heap_dump", "捕获系统堆转储");
        values.put("labs_title_day_merge_clustering", "合并图片列表中的日期分组");
        values.put("labs_title_developer", "版本信息");
        values.put("labs_title_dual_photo_preview", "仅预览双照片");
        values.put("labs_title_filmstrip", "查看器胶片条");
        values.put("labs_title_hamburger_menu_in_viewer_bottom", "在查看器底部显示菜单（⋮）");
        values.put("labs_title_nested_add_to_folder", "添加相册时显示文件夹");
        values.put("labs_title_new_empty_album", "空相册 2.0");
        values.put("labs_title_no_status_bar_in_landscape", "横屏模式下隐藏状态栏");
        values.put("labs_title_oneui30_memories", "回忆");
        values.put("labs_title_oneui30_viewer_details", "更新查看器和详细信息");
        values.put("labs_title_performance_log", "性能日志");
        values.put("labs_title_play_next_video", "自动播放下一个视频");
        values.put("labs_title_print_multiple_pictures", "打印多张图片");
        values.put("labs_title_restore_album_db", "还原相册数据库");
        values.put("labs_title_save_as_pdf", "另存为 PDF");
        values.put("labs_title_sd_card_health_state", "SD 卡健康状态提示卡片");
        values.put("labs_title_share_albums", "通过 Wi-Fi Direct 共享相册");
        values.put("labs_title_show_cached_images", "在详细信息中显示缓存图片");
        values.put("labs_title_show_exif", "在详细信息中显示 EXIF");
        values.put("labs_title_show_trash_storage", "显示回收站存储信息");
        values.put("labs_title_show_virtual_albums", "显示虚拟相册");
        values.put("labs_title_similar_photo", "相似照片");
        values.put("labs_title_single_take_photo", "一键多拍照片");
        values.put("labs_title_slideshow_auto_repeat", "自动重复播放幻灯片");
        values.put("labs_title_slideshow_with_selected_items", "使用所选项目播放幻灯片");
        values.put("labs_title_stories_oneui_21", "故事界面 2.1");
        values.put("labs_title_tag_in_search_v2", "搜索中的标签视图 V2");
        values.put("labs_title_thumbnail_preview", "在图片列表中预览视频");
        values.put("labs_title_timeline_in_album", "相册时间线");
        values.put("labs_title_timeline_in_search", "搜索相册时间线");
        values.put("labs_title_timeline_in_smart_album", "视频或收藏相册时间线");
        values.put("labs_title_troubleshooting", "故障排除");
        values.put("labs_title_video_player_mode_filmstrip", "胶片条视频播放器模式");
        values.put("labs_title_video_thumbnail_policy_v2", "视频缩略图策略 V2");
        values.put("labs_title_your_phone_phase_3", "Microsoft 手机连接");
        values.put("labs_user_trial", "用户试用");
        return Map.copyOf(values);
    }

    private static Map<String, String> resourceOriginalMap() {
        return Map.ofEntries(
                Map.entry("Store slow operations to log file", "将耗时操作记录到日志文件"),
                Map.entry("File is not saved by changing close-up/wide of Live focus photo", "更改实时虚化照片的近景或广角预览时不会保存文件")
        );
    }
}
