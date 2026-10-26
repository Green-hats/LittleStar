package com.littlestar.app.data.content.model;

import java.util.ArrayList;
import java.util.List;

/** Gson 模型：文化条目。 */
public final class CultureItem {
    public String id;
    public String categoryId;
    public String titleZh;
    public String subtitle;
    public String cover;
    public String glyph;
    public String audio;
    public int durationSec;
    public List<DateRange> featured = new ArrayList<>();
    public List<LyricLine> lyrics = new ArrayList<>();

    /** featured 日期区间，由内容维护者按当年农历填写，代码不计算农历。 */
    public static final class DateRange {
        public String from;
        public String to;
    }
}
