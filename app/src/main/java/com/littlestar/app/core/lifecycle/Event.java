package com.littlestar.app.core.lifecycle;

import androidx.annotation.Nullable;

/**
 * 一次性事件包装：旋转后不会重复消费（防止重复跳页 / 重复播音效）。
 */
public final class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** 只取一次；已取过返回 null。 */
    @Nullable
    public T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    @Nullable
    public T peek() {
        return content;
    }
}
