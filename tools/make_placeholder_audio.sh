#!/bin/bash
# 开发期占位音频：macOS `say`(婷婷 zh_CN) + ffmpeg 转 ogg。
# 正式版必须换成真人录音。用法：bash tools/make_placeholder_audio.sh
set -euo pipefail
cd "$(dirname "$0")/.."
AUDIO_DIR="app/src/main/assets/audio"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

gen() { # gen <输出相对路径> <朗读文本>
    local out="$AUDIO_DIR/$1"
    local text="$2"
    mkdir -p "$(dirname "$out")"
    say -v Tingting -r 150 -o "$TMP/x.aiff" "$text"
    ffmpeg -y -loglevel error -i "$TMP/x.aiff" -ac 2 -ar 44100 -strict -2 -c:a vorbis -q:a 3 "$out"
    echo "  -> $1"
}

echo "== 字音（36 字）=="
gen "words/mao.ogg" "猫"    & gen "words/gou.ogg" "狗"   & gen "words/niao.ogg" "鸟"  &
wait; gen "words/yu.ogg" "鱼" & gen "words/ma.ogg" "马"    & gen "words/niu.ogg" "牛"   &
wait; gen "words/guo.ogg" "果" & gen "words/gua.ogg" "瓜"  & gen "words/tao.ogg" "桃"   &
wait; gen "words/li_2.ogg" "梨" & gen "words/zao.ogg" "枣" & gen "words/li_3.ogg" "李"  &
wait; gen "words/hong.ogg" "红" & gen "words/huang.ogg" "黄" & gen "words/lan.ogg" "蓝" &
wait; gen "words/lv.ogg" "绿"  & gen "words/bai.ogg" "白"  & gen "words/hei.ogg" "黑"   &
wait; gen "words/yi.ogg" "一"  & gen "words/er.ogg" "二"   & gen "words/san.ogg" "三"   &
wait; gen "words/si.ogg" "四"  & gen "words/wu.ogg" "五"   & gen "words/liu.ogg" "六"   &
wait; gen "words/ba.ogg" "爸"  & gen "words/ma_5.ogg" "妈" & gen "words/ge.ogg" "哥"    &
wait; gen "words/jie.ogg" "姐" & gen "words/di.ogg" "弟"   & gen "words/mei.ogg" "妹"   &
wait; gen "words/yue.ogg" "月" & gen "words/shan.ogg" "山" & gen "words/shui.ogg" "水"  &
wait; gen "words/huo.ogg" "火" & gen "words/mu.ogg" "木"   & gen "words/xing.ogg" "星"  &
wait

echo "== 儿歌（读词占位，无旋律）=="
gen "culture/xiaoxingxing.ogg" "一闪一闪亮晶晶，满天都是小星星。挂在天上放光明，好像许多小眼睛。一闪一闪亮晶晶，满天都是小星星。" &
gen "culture/liangzhilaohu.ogg" "两只老虎，两只老虎，跑得快，跑得快。一只没有耳朵，一只没有尾巴，真奇怪，真奇怪。" &
gen "culture/baluobo.ogg" "拔萝卜，拔萝卜，哎哟哎哟拔萝卜。哎哟哎哟拔不动，老婆婆快快来，快来帮我们拔萝卜。" &
wait
gen "culture/zhaopengyou.ogg" "找呀找呀找朋友，找到一个好朋友。敬个礼呀握握手，你是我的好朋友。" &
gen "culture/jingyesi.ogg" "静夜思，李白。床前明月光，疑是地上霜。举头望明月，低头思故乡。" &
gen "culture/yonge.ogg" "咏鹅，骆宾王。鹅，鹅，鹅，曲项向天歌。白毛浮绿水，红掌拨清波。" &
wait
gen "culture/chunxiao.ogg" "春晓，孟浩然。春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知多少。" &
gen "culture/changebenyue.ogg" "嫦娥奔月。很久很久以前，天上有十个太阳，把大地烤得滚烫。英雄后羿射下九个太阳，救了大家。王母娘娘送给他一颗仙丹。后羿的妻子嫦娥，为了保护仙丹不被坏人抢走，一口吞下仙丹，身体轻飘飘地飞了起来，一直飞到月亮上。从此，嫦娥住在月亮的广寒宫里，只有一只小玉兔陪着她。每年八月十五，后羿就在院子里摆上嫦娥最爱吃的点心，望着月亮想念她。人们也在这天赏月、吃月饼，盼望家人团圆。" &
gen "culture/shouzhudaitu.ogg" "守株待兔。从前，有个农夫在田里干活。忽然，一只兔子飞快地跑过来，一头撞在树桩上，撞晕了。农夫捡起兔子，高高兴兴地回家，美美地吃了一顿。他想，要是每天都有兔子自己撞上来，那该多好呀。第二天，他不再种地，天天坐在树桩旁边等兔子。等了一天又一天，兔子再也没有来过，田里的庄稼全都枯死了。这个故事告诉我们，不能把一次好运气当成天天都有的事，要靠自己的劳动才有收获。" &
wait
gen "culture/guitusaipao.ogg" "龟兔赛跑。一天，乌龟和兔子比赛跑步。兔子跑得飞快，一会儿就把乌龟甩得远远的。兔子回头一看，乌龟还在慢慢地爬呢。兔子想，我先睡一觉也来得及，就躺在大树下睡着了。乌龟不着急，一步一步，不停地往前爬。等兔子醒来一看，乌龟已经爬过终点线了。这个故事告诉我们，骄傲会落后，坚持才能赢。" &
gen "culture/huashetianzu.ogg" "画蛇添足。从前，有几个人一起画画比赛，谁先画好一条蛇，谁就能喝到一壶酒。有个人画得最快，他拿起酒壶，得意地说，我还能给蛇添上几只脚呢。可是他还没画完脚，另一个人已经画好了，一把抢过酒壶说，蛇本来就没有脚，你画的不是蛇。这个人只好眼睁睁看着别人把酒喝掉了。这个故事告诉我们，做了多余的事，反而会把事情弄坏。" &
gen "prompts/great.ogg" "太棒了！" &
wait

echo "== 完成 =="
find "$AUDIO_DIR" -name '*.ogg' | wc -l
