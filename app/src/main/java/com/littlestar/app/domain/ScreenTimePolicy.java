package com.littlestar.app.domain;

/**
 * 时长判断纯逻辑（§10.1）：输入已用秒数、上限、extra、连续使用分钟、是否 defer，
 * 输出 NONE / REST / LIMIT。ScreenTimeTracker 只负责计时和跳页。
 */
public final class ScreenTimePolicy {

    public enum Decision {NONE, REST, LIMIT}

    private ScreenTimePolicy() {
    }

    /**
     * @param usedSecondsToday   今天孩子区已用秒数
     * @param limitMin           每日上限（分钟）
     * @param extraMin           家长今天额外给的分钟
     * @param continuousMinutes  连续使用分钟（暂停 ≥ 5 分钟会重置）
     * @param restReminderOn     休息提醒开关
     * @param defer              会话中（Flashcard / Quiz）：到时不打断
     */
    public static Decision decide(int usedSecondsToday, int limitMin, int extraMin,
                                  long continuousMinutes, boolean restReminderOn, boolean defer) {
        if (usedSecondsToday >= (limitMin + extraMin) * 60L) {
            return defer ? Decision.NONE : Decision.LIMIT;
        }
        if (restReminderOn && continuousMinutes >= 15) {
            return defer ? Decision.NONE : Decision.REST;
        }
        return Decision.NONE;
    }
}
