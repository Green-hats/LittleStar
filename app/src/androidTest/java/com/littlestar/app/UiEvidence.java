package com.littlestar.app;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** 保存设备回归的原始截图，供人工检查新界面；不修改截图。 */
final class UiEvidence {
    private UiEvidence() {}

    static void capture(String name) throws IOException {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try {
            // 等待无障碍事件和窗口事务稳定，避免截图混入上一个主题的系统栏。
            InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(150, 3_000);
        } catch (java.util.concurrent.TimeoutException error) {
            throw new IOException("Window did not settle for screenshot", error);
        }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (bitmap == null) {
            throw new IOException("Screenshot unavailable");
        }
        Context context = ApplicationProvider.getApplicationContext();
        File directory = context.getExternalFilesDir("team-qa");
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
            bitmap.recycle();
            throw new IOException("Screenshot directory unavailable");
        }
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException("Screenshot encoding failed");
            }
        } finally {
            bitmap.recycle();
        }
    }
}
