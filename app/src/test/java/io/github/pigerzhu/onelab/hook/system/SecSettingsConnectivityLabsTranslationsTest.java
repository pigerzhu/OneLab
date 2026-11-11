package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SecSettingsConnectivityLabsTranslationsTest {
    @Test
    public void translatesKnownConnectivityLabsText() {
        assertEquals("连接实验室",
                SecSettingsConnectivityLabsTranslations.translate("Connectivity labs"));
        assertEquals("Wi-Fi 开发者选项",
                SecSettingsConnectivityLabsTranslations.translate("Wi-Fi developer options"));
        assertEquals("连接信息", SecSettingsConnectivityLabsTranslations.translate("Connection information"));
        assertEquals("附近 Wi-Fi 信息", SecSettingsConnectivityLabsTranslations.translate("Nearby Wi-Fi information"));
        assertEquals("Wi-Fi 与网络诊断", SecSettingsConnectivityLabsTranslations.translate("Wi-Fi and network diagnosis"));
        assertEquals("数据包捕获", SecSettingsConnectivityLabsTranslations.translate("Packet Capture"));
        assertEquals("用于调试的 Wi-Fi 数据包日志", SecSettingsConnectivityLabsTranslations.translate("Wi-Fi packet log for debugging"));
        assertEquals("Wi-Fi 连接历史记录", SecSettingsConnectivityLabsTranslations.translate("Wi-Fi connection history"));
        assertEquals("Wi-Fi 链路状态历史记录", SecSettingsConnectivityLabsTranslations.translate("Wi-Fi link status history"));
        assertEquals("更改扫描间隔", SecSettingsConnectivityLabsTranslations.translate("Change scan interval"));
        assertEquals("重启 Wi-Fi 子系统", SecSettingsConnectivityLabsTranslations.translate("Restart the Wi-Fi subsystem"));
        assertEquals("数据包捕获进行中", SecSettingsConnectivityLabsTranslations.translate("Packet Capture In Progress"));
        assertEquals("数据包捕获已关闭", SecSettingsConnectivityLabsTranslations.translate("Packet Capture Off"));
        assertEquals("未使用",
                SecSettingsConnectivityLabsTranslations.translate("Unused"));
        assertEquals("未加密",
                SecSettingsConnectivityLabsTranslations.translate("Unsecured"));
        assertEquals("家庭 Wi-Fi 检查",
                SecSettingsConnectivityLabsTranslations.translate("Home Wi-Fi inspection"));
        assertEquals("自动重新连接到运营商 Wi-Fi",
                SecSettingsConnectivityLabsTranslations.translate("Auto reconnect to carrier Wi-Fi"));
        assertEquals("在 Wi-Fi 中启用接收端 L4S",
                SecSettingsConnectivityLabsTranslations.translate("Enable receiver-side L4S in the Wi-Fi"));
        assertEquals("更快切换到移动数据",
                SecSettingsConnectivityLabsTranslations.translate("Switching to mobile data faster"));
        assertEquals("智能 Wi-Fi 切换",
                SecSettingsConnectivityLabsTranslations.translate("Intelligent Wi-Fi Handover"));
        assertEquals("使用 AI 切换到移动数据",
                SecSettingsConnectivityLabsTranslations.translate("Switching to mobile data with AI"));
        assertEquals("连接 2.4GHz 以进行物联网设置",
                SecSettingsConnectivityLabsTranslations.translate("Connect to 2.4GHz for IOT setup"));
        assertEquals("显示网络质量信息",
                SecSettingsConnectivityLabsTranslations.translate("Show network quality info"));
        assertEquals("禁用用于 D2D 的 Wi-Fi 扫描",
                SecSettingsConnectivityLabsTranslations.translate("Disable Wi-Fi Scan for D2D"));
        assertEquals("增强 Wi-Fi 兼容性",
                SecSettingsConnectivityLabsTranslations.translate("Enhanced Wi-Fi compatibility"));
    }

    @Test
    public void preservesUnknownAndMalformedText() {
        assertEquals(null, SecSettingsConnectivityLabsTranslations.translate(null));
        assertEquals(null, SecSettingsConnectivityLabsTranslations.translate(""));
        assertEquals(null, SecSettingsConnectivityLabsTranslations.translate("New Labs item"));
        assertEquals(null, SecSettingsConnectivityLabsTranslations.translate(
                "Connectivity labs will be enabled - %s"));
    }

    @Test
    public void preservesSupportedFormatArguments() {
        assertEquals("将在 3 后启用连接实验室",
                SecSettingsConnectivityLabsTranslations.translate(
                        "Connectivity labs will be enabled - 3"));
    }
}
