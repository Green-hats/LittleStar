package com.littlestar.app.data.content.model;

import java.util.ArrayList;
import java.util.List;

/** Gson 模型：字。 */
public final class Word {
    public String id;
    public String hanzi;
    public String pinyin;
    public String english;
    public String image;
    public String audio;
    public List<String> distractors = new ArrayList<>();
}
