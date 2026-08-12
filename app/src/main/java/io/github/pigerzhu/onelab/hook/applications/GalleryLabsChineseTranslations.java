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
            Map.entry("One UI 5.x", "One UI 5.x 功能"),
            Map.entry("One UI 4.x trial", "One UI 4.x 试用功能"),
            Map.entry("One UI 3.x trial", "One UI 3.x 试用功能"),
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
            Map.entry("Use first frame as video thumbnail. If disabled, legacy policy, extracting a frame at 15 sec from video for representative thumbnail, is applied", "使用视频第一帧作为缩略图。关闭后将采用旧策略，截取视频第 15 秒画面作为代表缩略图"),
            Map.entry("Object Capture Debug Mode", "对象捕获调试模式"),
            Map.entry("Show object capture outline for debugging", "显示对象捕获轮廓以便调试"),
            Map.entry("Suggest intelligent stuff", "智能内容建议"),
            Map.entry("Show revitalize, highlight, and portrait", "显示焕新、精彩时刻和人像建议"),
            Map.entry("Use AMAP", "使用高德地图"),
            Map.entry("File operation service v2", "文件操作服务 V2"),
            Map.entry("Add 12x on pictures tab", "在图片选项卡中添加 12 倍网格"),
            Map.entry("Support more grid configuration of real,3,4,7(month),12(month), and year", "支持实际尺寸、3 倍、4 倍、7 倍（月）、12 倍（月）和年份等更多网格配置"),
            Map.entry("New Trash(Mp trash)", "新回收站（媒体提供程序回收站）"),
            Map.entry("Use Mp trash provider", "使用媒体提供程序回收站"),
            Map.entry("Expose NonDestructive recording in Search", "在搜索中显示无损录制"),
            Map.entry("Expose nondestructive recording in Search shot mode category (for SM/SSM)", "在搜索的拍摄模式类别中显示无损录制（适用于 SM/SSM）"),
            Map.entry("Support undo merge people", "支持撤销人物合并"),
            Map.entry("Support undo after merging people", "支持在合并人物后撤销"),
            Map.entry("Debug SmartCropRect info in Visual Search", "在视觉搜索中调试智能裁剪区域信息"),
            Map.entry("Show SmartCropRect of media item in Visual search with long press", "长按时在视觉搜索中显示媒体项目的智能裁剪区域"),
            Map.entry("Surface preview on list", "在列表中使用 Surface 预览"),
            Map.entry("Surface Preview video thumbnails on list for HDR", "在列表中使用 Surface 预览 HDR 视频缩略图"),
            Map.entry("Insensitive fast scroll", "非敏感快速滚动"),
            Map.entry("Try to maintain the position at the point of the release of your finger.", "尝试保持手指松开位置对应的滚动位置"),
            Map.entry("Adaptive fast scroll", "自适应快速滚动"),
            Map.entry("Hold fast scroll and drag it to the opposite side of fast scroll, user can adjust scroll speed.", "按住快速滚动条并向另一侧拖动，可调整滚动速度"),
            Map.entry("Time slot in year view", "年份视图时间间隔"),
            Map.entry("Recover last fragment", "恢复上次页面"),
            Map.entry("Recover last fragment stack from process kill or activity recreate.", "从进程终止或 Activity 重建中恢复上次的页面堆栈"),
            Map.entry("Restore Time/Location", "还原时间和位置"),
            Map.entry("Support dateTime and location restore for modified file", "支持为修改过的文件还原日期、时间和位置"),
            Map.entry("Enhanced video thumbnail", "增强视频缩略图"),
            Map.entry("remove black video thumbnails", "移除黑色视频缩略图"),
            Map.entry("Debug face rectangle", "调试人脸区域"),
            Map.entry("Draw yellow rectangle on face", "在人脸周围绘制黄色矩形"),
            Map.entry("[PS] Enhanced Medium Cache", "[照片条] 增强中等尺寸缓存"),
            Map.entry("Make medium thumbnail with 640 for long side", "生成长边为 640 像素的中等尺寸缩略图"),
            Map.entry("[PS] High quality preview", "[照片条] 高质量预览"),
            Map.entry("use large size thumbnail for better quality", "使用大尺寸缩略图以获得更高质量"),
            Map.entry("[PS] for One UI 41", "[照片条] One UI 4.1 版本"),
            Map.entry("photoStrip DA", "照片条 DA"),
            Map.entry("Film Smooth scroll", "胶片平滑滚动"),
            Map.entry("Support Film Smooth scroll", "支持胶片平滑滚动"),
            Map.entry("Dual photo preview only", "仅预览双照片"),
            Map.entry("Cloud video preview", "云端视频预览"),
            Map.entry("Preview cloud video and video in shared album", "预览云端视频和共享相册中的视频"),
            Map.entry("Region decoding info", "区域解码信息"),
            Map.entry("Show region decoding info on canvas", "在画布上显示区域解码信息"),
            Map.entry("Debug info in more-info", "更多信息中的调试信息"),
            Map.entry("Image filter always", "始终显示图片滤镜"),
            Map.entry("Show suggested effects always", "始终显示建议效果"),
            Map.entry("Slide-up VI in viewer details", "查看器详细信息上滑动画"),
            Map.entry("Enable VI and change view recycle logic", "启用动画并更改视图回收逻辑"),
            Map.entry("Viewer2 debug text", "查看器 2 调试文本"),
            Map.entry("Play video while swipe", "滑动时播放视频"),
            Map.entry("Set video viewer mute concept", "设置视频查看器静音方式"),
            Map.entry("Always mute (default)", "始终静音（默认）"),
            Map.entry("Always un-mute", "始终取消静音"),
            Map.entry("maintain mute value before app destroy", "保持应用退出前的静音状态"),
            Map.entry("Skip alive zoom output", "跳过动态缩放输出"),
            Map.entry("Skip alive zoom output, directly use region decoder output", "跳过动态缩放输出，直接使用区域解码器输出"),
            Map.entry("Save PPP temp image", "保存 PPP 临时图片"),
            Map.entry("Save temp image before Camera post processing.", "保存相机后处理前的临时图片"),
            Map.entry("Add cleanout burst/similar pictures tab to suggestions", "在建议中添加清理连拍/相似图片选项卡"),
            Map.entry("By removing similar photos, you can increase available storage.", "移除相似照片可增加可用存储空间"),
            Map.entry("Add Remove background effect info menu", "添加移除背景效果信息菜单"),
            Map.entry("remove background effect info for reduce file size.\nYou can use list & viewer.", "移除背景效果信息以减小文件大小。\n可在列表和查看器中使用"),
            Map.entry("Search People custom relationship edit and remove", "编辑和移除搜索人物的自定义关系"),
            Map.entry("Support custom relationship name edit and remove function for testing", "支持编辑和移除自定义关系名称以供测试"),
            Map.entry("Quick search", "快速搜索"),
            Map.entry("Support quick search on Pictures tab", "支持在图片选项卡中快速搜索"),
            Map.entry("Album cover sync", "相册封面同步"),
            Map.entry("Support album cover cloud sync", "支持将相册封面同步到云端"),
            Map.entry("Original content scale on story", "故事中的原始内容比例"),
            Map.entry("Show original content scale on story when paused", "故事暂停时显示原始内容比例"),
            Map.entry("Story summary with Collage", "带拼贴画的故事摘要"),
            Map.entry("Show Collage above related stories", "在相关故事上方显示拼贴画"),
            Map.entry("Story contents reorder", "故事内容重新排序"),
            Map.entry("Support reorder in Story highlight list", "支持在故事精彩时刻列表中重新排序"),
            Map.entry("Story last page", "故事末页"),
            Map.entry("show collage and related stories on page", "在页面中显示拼贴画和相关故事"),
            Map.entry("Story irregular collage", "故事不规则拼贴画"),
            Map.entry("show irregular collage on last page", "在末页显示不规则拼贴画"),
            Map.entry("Stories irregular cover", "故事不规则封面"),
            Map.entry("mask image with irregular shape", "使用不规则形状遮罩图片"),
            Map.entry("Support Remote Gallery", "远程图库支持"),
            Map.entry("Access any album of the remote device in the same WiFi domain", "访问同一 Wi-Fi 网络中远程设备上的任意相册"),
            Map.entry("Start Remote Gallery Server", "启动远程图库服务器"),
            Map.entry("Share my pictures with other galleries in same wifi network", "与同一 Wi-Fi 网络中的其他图库共享我的图片"),
            Map.entry("Connect to Remote Gallery", "连接到远程图库"),
            Map.entry("Connect Remote gallery server to preview and download pictures.", "连接远程图库服务器以预览和下载图片"),
            Map.entry("Show widgets for selection mode expanded viewer", "在选择模式的展开查看器中显示控件"),
            Map.entry("Show filmstrip and navigation button", "显示胶片条和导航按钮"),
            Map.entry("Use address from database", "使用数据库中的地址"),
            Map.entry("Enable album auto grouping", "启用相册自动分组"),
            Map.entry("Group albums by directory tree structure.\nAlbums->View All->Menu->Auto grouping\n", "按目录树结构对相册分组。\n相册 > 查看全部 > 菜单 > 自动分组\n"),
            Map.entry("Paste clipboard in image viewer", "在图片查看器中粘贴剪贴板内容"),
            Map.entry("Enable paste clipboard option of image viewer", "启用图片查看器的剪贴板粘贴选项"),
            Map.entry("Clipped image edit", "编辑裁剪图片"),
            Map.entry("Enable edit option of clipped image popup menu", "启用裁剪图片弹出菜单中的编辑选项"),
            Map.entry("Visual search 6.1", "视觉搜索 6.1"),
            Map.entry("New visual search look", "新版视觉搜索界面"),
            Map.entry("Support Search Cluster", "支持搜索聚类"),
            Map.entry("Show search result using cluster GUI but it depends on U OS SCS and CMH", "使用聚类界面显示搜索结果，但依赖 U OS、SCS 和 CMH"),
            Map.entry("In app assist look", "应用内辅助界面"),
            Map.entry("Enables 'In app assist look' for the search bar including blur, light, gradient effect for the views.", "为搜索栏启用应用内辅助界面，包括视图的模糊、光效和渐变效果"),
            Map.entry("Enable blur map view under bottom sheet", "启用底部面板下方的地图模糊效果"),
            Map.entry("Manager for preference & cache", "偏好设置和缓存管理器"),
            Map.entry("PocFeatures", "PocFeatures（概念验证功能）"),
            Map.entry("PreferenceFeatures", "PreferenceFeatures（偏好功能）"),
            Map.entry("\"Undo delete\" in viewer", "查看器中的“撤销删除”"),
            Map.entry("Support \"undo delete\" function when deleting image or video in viewer", "支持在查看器中删除图片或视频时撤销删除"),
            Map.entry("MotionPhoto player", "动态照片播放器"),
            Map.entry("enable video player for motion photo", "为动态照片启用视频播放器"),
            Map.entry("Search picker", "搜索选择器"),
            Map.entry("enable search in picker mode", "在选择器模式中启用搜索"),
            Map.entry("Permanent album cover", "永久相册封面"),
            Map.entry("can select an album cover as an item in another album", "可将另一个相册中的项目选作相册封面"),
            Map.entry("Gallery motion photo player", "图库动态照片播放器"),
            Map.entry("Support embedded motion photo viewer", "支持内嵌动态照片查看器"),
            Map.entry("Story UI 5.0 (slideshow)", "故事界面 5.0（幻灯片）"),
            Map.entry("Story slideshow for OneUI 5.0", "适用于 One UI 5.0 的故事幻灯片"),
            Map.entry("Story recording solution", "故事录制方案"),
            Map.entry("Embedded story recording solution instead of highlight-reels", "使用内嵌故事录制方案代替精彩时刻短片"),
            Map.entry("Face cluster", "人脸聚类"),
            Map.entry("Support face cluster merge recommend", "支持人脸聚类合并建议"),
            Map.entry("Search Multiple Keyword filter", "搜索多关键词筛选器"),
            Map.entry("Enable selecting multiple keyword filter in search results", "允许在搜索结果中选择多个关键词筛选条件"),
            Map.entry("Search Result Screen V2", "搜索结果页面 V2"),
            Map.entry("Show Story on result screen", "在结果页面显示故事"),
            Map.entry("Search Hide People", "搜索中隐藏人物"),
            Map.entry("Hide unwanted people", "隐藏不需要的人物"),
            Map.entry("DoubleTapSeek", "双击快进/快退"),
            Map.entry("Support DoubleTap Seek", "支持双击快进/快退"),
            Map.entry("Shared Pinch Layout", "共享相册缩放布局"),
            Map.entry("Support a shared pinch layout", "支持共享相册的双指缩放布局"),
            Map.entry("Albums v2", "相册 V2"),
            Map.entry("New album layout including physical/logical albums and shared albums", "包含实体相册、逻辑相册和共享相册的新布局"),
            Map.entry("Show an icon on name-merged albums", "在名称合并的相册上显示图标"),
            Map.entry("Search people face score", "搜索人物人脸评分"),
            Map.entry("Support represent people thumbnail by face score", "支持按人脸评分选择人物代表缩略图"),
            Map.entry("Enable new smart crop for story highlight", "为故事精彩时刻启用新版智能裁剪"),
            Map.entry("Support for expanded slideshows with smart crop", "支持使用智能裁剪的扩展幻灯片"),
            Map.entry("Support stories filter", "支持故事滤镜"),
            Map.entry("Support for filter effects", "支持滤镜效果"),
            Map.entry("Enable story default theme", "启用故事默认主题")
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
        String yearIntervalPrefix = "1 representative image every ";
        String yearIntervalSuffix = " minutes";
        if (original.startsWith(yearIntervalPrefix) && original.endsWith(yearIntervalSuffix)) {
            String minutes = original.substring(
                    yearIntervalPrefix.length(),
                    original.length() - yearIntervalSuffix.length());
            if (!minutes.isEmpty() && minutes.chars().allMatch(Character::isDigit)) {
                return "每 " + minutes + " 分钟选取 1 张代表图片";
            }
        }
        String optionPrefix = "Option: ";
        if (original.startsWith(optionPrefix)) {
            translation = LITERAL_TRANSLATIONS.get(original.substring(optionPrefix.length()));
            if (translation != null) {
                return "选项：" + translation;
            }
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
