package com.littlestar.app.feature.quiz;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.littlestar.app.R;

record CharacterClue(String shape, @StringRes int description) {
    @Nullable
    static CharacterClue forHanzi(String hanzi) {
        return switch (hanzi) {
            case "猫" -> new CharacterClue("艹", R.string.quiz_shape_1);
            case "狗" -> new CharacterClue("口", R.string.quiz_shape_2);
            case "鸟" -> new CharacterClue("丶", R.string.quiz_shape_3);
            case "鱼" -> new CharacterClue("田", R.string.quiz_shape_4);
            case "马" -> new CharacterClue("㇉", R.string.quiz_shape_5);
            case "牛" -> new CharacterClue("丨", R.string.quiz_shape_6);
            case "果" -> new CharacterClue("田", R.string.quiz_shape_7);
            case "瓜" -> new CharacterClue("厶", R.string.quiz_shape_8);
            case "桃" -> new CharacterClue("木", R.string.quiz_shape_9);
            case "梨" -> new CharacterClue("木", R.string.quiz_shape_10);
            case "枣" -> new CharacterClue("⺀", R.string.quiz_shape_11);
            case "李" -> new CharacterClue("子", R.string.quiz_shape_12);
            case "红" -> new CharacterClue("工", R.string.quiz_shape_13);
            case "黄" -> new CharacterClue("八", R.string.quiz_shape_14);
            case "蓝" -> new CharacterClue("皿", R.string.quiz_shape_15);
            case "绿" -> new CharacterClue("纟", R.string.quiz_shape_16);
            case "白" -> new CharacterClue("丿", R.string.quiz_shape_17);
            case "黑" -> new CharacterClue("灬", R.string.quiz_shape_18);
            case "一" -> new CharacterClue("—", R.string.quiz_shape_19);
            case "二" -> new CharacterClue("═", R.string.quiz_shape_20);
            case "三" -> new CharacterClue("≡", R.string.quiz_shape_21);
            case "四" -> new CharacterClue("囗", R.string.quiz_shape_22);
            case "五" -> new CharacterClue("㇕", R.string.quiz_shape_23);
            case "六" -> new CharacterClue("八", R.string.quiz_shape_24);
            case "爸" -> new CharacterClue("父", R.string.quiz_shape_25);
            case "妈" -> new CharacterClue("马", R.string.quiz_shape_26);
            case "哥" -> new CharacterClue("口 口", R.string.quiz_shape_27);
            case "姐" -> new CharacterClue("且", R.string.quiz_shape_28);
            case "弟" -> new CharacterClue("丿", R.string.quiz_shape_29);
            case "妹" -> new CharacterClue("未", R.string.quiz_shape_30);
            case "月" -> new CharacterClue("冂", R.string.quiz_shape_31);
            case "山" -> new CharacterClue("丨", R.string.quiz_shape_32);
            case "水" -> new CharacterClue("氺", R.string.quiz_shape_33);
            case "火" -> new CharacterClue("人", R.string.quiz_shape_34);
            case "木" -> new CharacterClue("十", R.string.quiz_shape_35);
            case "星" -> new CharacterClue("日", R.string.quiz_shape_36);
            default -> null;
        };
    }
}
