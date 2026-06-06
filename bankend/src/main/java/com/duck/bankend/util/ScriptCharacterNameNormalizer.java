package com.duck.bankend.util;

import org.springframework.util.StringUtils;

/**
 * 剧本人物名归一化工具。
 * <p>
 * 用于清理 AI 输出中的状态说明，避免“伊甸（AI）”“伊甸（通过神经接口）”被当成不同人物。
 */
public class ScriptCharacterNameNormalizer {

    private ScriptCharacterNameNormalizer() {
    }

    /**
     * 返回用于展示和落库的人物名，去除括号中的状态、数量或媒介说明。
     */
    public static String displayName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        String normalized = name.trim()
                .replaceAll("[（(][^（）()]*[）)]", "")
                .replaceAll("\\s+", "");
        return StringUtils.hasText(normalized) ? normalized : name.trim();
    }

    /**
     * 返回用于去重和映射的人物 key。
     */
    public static String key(String name) {
        return displayName(name).toLowerCase();
    }
}
