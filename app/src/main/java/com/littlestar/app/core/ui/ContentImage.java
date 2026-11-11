package com.littlestar.app.core.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.littlestar.app.AppExecutors;
import com.littlestar.app.R;

import java.util.Objects;

/** 每个展示位独立跟踪异步请求，避免列表复用或换曲后显示旧封面。 */
public final class ContentImage {
    private final ImageView image;
    private final AppExecutors executors;
    @Nullable
    private final View fallback;
    private String name;
    private boolean bound;
    private int generation;

    public ContentImage(ImageView image, AppExecutors executors) {
        this(image, executors, null);
    }

    public ContentImage(ImageView image, AppExecutors executors, @Nullable View fallback) {
        this.image = image;
        this.executors = executors;
        this.fallback = fallback;
    }

    public void bind(@Nullable String cover) {
        if (bound && Objects.equals(name, cover)) {
            return;
        }
        bound = true;
        name = cover;
        int request = ++generation;
        image.setImageDrawable(null);
        if (fallback != null) {
            fallback.setVisibility(View.VISIBLE);
        }
        boolean valid = cover != null && !cover.isEmpty() && !"ill_placeholder".equals(cover);
        image.setVisibility(valid ? View.VISIBLE : View.GONE);
        if (!valid) {
            return;
        }
        Resources resources = image.getResources();
        String packageName = image.getContext().getPackageName();
        int size = Math.max(image.getWidth(), image.getHeight());
        int targetSize = size > 0 ? size : resources.getDimensionPixelSize(R.dimen.ls_player_cover);
        executors.diskIO().execute(() -> {
            Bitmap decoded = null;
            try {
                int id = resources.getIdentifier(cover, "drawable", packageName);
                if (id != 0) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeResource(resources, id, options);
                    options.inSampleSize = 1;
                    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > targetSize * 2) {
                        options.inSampleSize *= 2;
                    }
                    options.inJustDecodeBounds = false;
                    decoded = BitmapFactory.decodeResource(resources, id, options);
                }
            } catch (Resources.NotFoundException error) {
                Log.w("ContentImage", "Unable to load image: " + cover, error);
            }
            Bitmap bitmap = decoded;
            executors.mainThread().execute(() -> {
                if (request == generation) {
                    image.setImageBitmap(bitmap);
                    image.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
                    if (fallback != null) {
                        fallback.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
                    }
                }
            });
        });
    }
}
