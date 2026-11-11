package io.github.pigerzhu.onelab.hook.system;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SecSettingsConnectivityLabsTranslations {
    private static final Pattern ENABLE_COUNT = Pattern.compile(
            "^Connectivity labs will be enabled - (\\d+)$");
    private static final Map<String, String> FIXED = Map.ofEntries(
            Map.entry("Wi-Fi/Network diagnosis", "Wi-Fi/网络诊断"),
            Map.entry("Wi-Fi developer options", "Wi-Fi 开发者选项"),
            Map.entry("Connectivity labs", "连接实验室"),
            Map.entry("Connectivity Labs is enabled.", "连接实验室已启用。"),
            Map.entry("Router history", "路由器历史记录"),
            Map.entry("Time history", "时间历史记录"),
            Map.entry("Reset all labs settings", "重置所有实验室设置"),
            Map.entry("Turn on Wi-Fi extender?", "要开启 Wi-Fi 扩展器吗？"),
            Map.entry("Restore all changes made in the connectivity labs to default values.",
                    "将连接实验室中的所有更改恢复为默认值。"),
            Map.entry("This feature is for internal test purposes. It does not guarantee stability.",
                    "此功能仅供内部测试，无法保证稳定性。"),
            Map.entry("Unused", "未使用"),
            Map.entry("Unsecured", "未加密"),
            Map.entry("Home Wi-Fi inspection", "家庭 Wi-Fi 检查"),
            Map.entry("Auto reconnect to carrier Wi-Fi", "自动重新连接到运营商 Wi-Fi"),
            Map.entry("Enable receiver-side L4S in the Wi-Fi", "在 Wi-Fi 中启用接收端 L4S"),
            Map.entry("Switching to mobile data faster", "更快切换到移动数据"),
            Map.entry("Intelligent Wi-Fi Handover", "智能 Wi-Fi 切换"),
            Map.entry("Switching to mobile data with AI", "使用 AI 切换到移动数据"),
            Map.entry("Connect to 2.4GHz for IOT setup", "连接 2.4GHz 以进行物联网设置"),
            Map.entry("Show network quality info", "显示网络质量信息"),
            Map.entry("Disable Wi-Fi Scan for D2D", "禁用用于 D2D 的 Wi-Fi 扫描"),
            Map.entry("Enhanced Wi-Fi compatibility", "增强 Wi-Fi 兼容性"),
            Map.entry("Show network quality info in the list of available Wi-Fi networks.",
                    "在可用 Wi-Fi 网络列表中显示网络质量信息。"),
            Map.entry("Disable Wi-Fi scanning to enhance performance in Device-to-Device connection.",
                    "禁用 Wi-Fi 扫描以提升设备到设备连接性能。"),
            Map.entry("When the mobile device detects a compatibility issue with a specific Wi-Fi router, we adjust the connection option to enhance the Wi-Fi compatibility. By turning off the switch, users can revert to using default parameters.",
                    "当设备检测到特定 Wi-Fi 路由器存在兼容性问题时，会调整连接选项以增强 Wi-Fi 兼容性。关闭此开关后，将恢复使用默认参数。"),
            Map.entry("Unused more than 6 months", "超过 6 个月未使用"),
            Map.entry("Network information", "网络信息"),
            Map.entry("Connected with another device", "已与其他设备连接"),
            Map.entry("Device connected to another device", "设备已连接到其他设备"),
            Map.entry("No connectable devices found", "未找到可连接的设备"),
            Map.entry("No devices selected", "未选择设备"),
            Map.entry("Scanning...", "正在扫描…"),
            Map.entry("Connection information", "连接信息"),
            Map.entry("Wi-Fi history", "Wi-Fi 历史记录"),
            Map.entry("others", "其他"),
            Map.entry("Nearby Wi-Fi information", "附近 Wi-Fi 信息"),
            Map.entry("Wi-Fi and network diagnosis", "Wi-Fi 与网络诊断"),
            Map.entry("Packet Capture", "数据包捕获"),
            Map.entry("Wi-Fi packet log for debugging", "用于调试的 Wi-Fi 数据包日志"),
            Map.entry("Wi-Fi connection history", "Wi-Fi 连接历史记录"),
            Map.entry("Wi-Fi link status history", "Wi-Fi 链路状态历史记录"),
            Map.entry("Change scan interval", "更改扫描间隔"),
            Map.entry("Restart the Wi-Fi subsystem", "重启 Wi-Fi 子系统"),
            Map.entry("Packet Capture In Progress", "数据包捕获进行中"),
            Map.entry("Packet Capture Off", "数据包捕获已关闭"));

    private SecSettingsConnectivityLabsTranslations() {
    }

    static String translate(String original) {
        if (original == null || original.isEmpty()) {
            return null;
        }
        String fixed = FIXED.get(original);
        if (fixed != null) {
            return fixed;
        }
        Matcher matcher = ENABLE_COUNT.matcher(original);
        return matcher.matches() ? "将在 " + matcher.group(1) + " 后启用连接实验室" : null;
    }
}
