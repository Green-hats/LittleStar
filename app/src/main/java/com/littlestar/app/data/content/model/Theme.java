package com.littlestar.app.data.content.model;

import java.util.ArrayList;
import java.util.List;

/** Gson 模型：主题。字段名与 JSON 键一致，不用 record。 */
public final class Theme {
    public String id;
    public int order;
    public String nameZh;
    public String nameEn;
    public String glyph;
    public String illustration;
    public List<Word> words = new ArrayList<>();
}
