package com.littlestar.app.data.store;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 状态文件读写抽象。正式实现 {@link AtomicStateFile} 包装 AtomicFile；
 * 单测里用基于临时目录的普通文件实现。
 */
public interface StateFile {

    /** 读全文；文件不存在返回 null。 */
    @Nullable
    String read();

    /** 原子写入（先写临时文件再替换）。 */
    void write(@NonNull String content);

    /** 把当前文件重命名为 原名 + suffix（保留损坏现场）。 */
    void rename(@NonNull String suffix);

    /** 备份文件（progress.bak.json）的内容；不存在返回 null。 */
    @Nullable
    String readBackup();

    /** 把当前内容复制为备份文件（每天第一次成功加载后调用）。 */
    void writeBackup(@NonNull String content);
}
