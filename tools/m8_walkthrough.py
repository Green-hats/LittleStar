#!/usr/bin/env python3
"""M8 走查驱动：adb 点按 + 截图。用法: python3 tools/m8_walkthrough.py <variant>"""
import json
import re
import subprocess
import sys
import time

PKG = "com.littlestar.app.debug"
HOME = f"{PKG}/com.littlestar.app.feature.home.HomeActivity"
OUT = "docs/screenshots/M8"


def sh(cmd, check=False):
    return subprocess.run(["adb", "shell"] + cmd, capture_output=True, text=True)


def shot(name):
    subprocess.run(f"adb exec-out screencap -p > {OUT}/{name}.png", shell=True)
    print("shot:", name)


def ui():
    sh(["uiautomator", "dump", "/sdcard/ui.xml"])
    subprocess.run(["adb", "pull", "/sdcard/ui.xml", "/tmp/ui.xml"], capture_output=True)
    return open("/tmp/ui.xml", encoding="utf-8").read()


def find_text(s, text, ymin=0, ymax=9999):
    for m in re.finditer(r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
        if m.group(1) == text and ymin <= int(m.group(3)) <= ymax:
            return (int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2
    return None


def tap_text(text, ymin=0, ymax=9999, tries=5):
    for _ in range(tries):
        p = find_text(ui(), text, ymin, ymax)
        if p:
            sh(["input", "tap", str(p[0]), str(p[1])])
            time.sleep(1.0)
            return True
        time.sleep(0.8)
    print("MISS:", text)
    return False


def tap(x, y):
    sh(["input", "tap", str(x), str(y)])
    time.sleep(1.0)


def back():
    sh(["input", "keyevent", "KEYCODE_BACK"])
    time.sleep(0.8)


def focus():
    r = subprocess.run("adb shell dumpsys window | grep -i mCurrentFocus",
                       shell=True, capture_output=True, text=True)
    return r.stdout


def current_quiz_answer():
    """读 progress.json 找当前未完成会话的当前题答案汉字。"""
    r = sh(["run-as", PKG, "cat", "files/progress.json"])
    d = json.loads(r.stdout)
    open_s = [x for x in d["sessions"] if x.get("completedAt") is None]
    if not open_s:
        return None
    s = open_s[-1]
    n = len(s.get("answers", []))
    ids = s["quizWordIds"]
    if n >= len(ids):
        return None
    words = json.load(open("app/src/main/assets/content/words.json"))
    idx = {w["id"]: w["hanzi"] for t in words["themes"] for w in t["words"]}
    return idx[ids[n]]


def answer_quiz_current():
    ans = current_quiz_answer()
    if ans:
        tap_text(ans, ymin=1000)


def gate():
    """过家长验证。"""
    s = ui()
    m = re.search(r'text="(\d+) (\+|×) (\d+) = \?"', s)
    if not m:
        return False
    a, op, b = int(m.group(1)), m.group(2), int(m.group(3))
    ans = a + b if op == "+" else a * b
    tap(500, 1120)
    sh(["input", "text", str(ans)])
    tap(540, 1296)
    time.sleep(1.5)
    return True


def walkthrough(variant):
    if variant == "dark":
        sh(["cmd", "uimode", "night", "yes"])
    elif variant == "font13":
        sh(["settings", "put", "system", "font_scale", "1.3"])
    elif variant == "small":
        sh(["wm", "size", "1080x1920"])
    sh(["am", "force-stop", PKG])
    sh(["am", "start", "-n", HOME])
    time.sleep(4)
    # 首页字态
    s = ui()
    if find_text(s, "今天 · 文化"):
        p = find_text(s, "字", ymax=700)
        if p:
            tap(*p)
    shot(f"home_word_{variant}")
    # 词表
    tap_text("Cards · Quiz")
    time.sleep(0.8)
    tap_text("动物")
    time.sleep(1.2)
    shot(f"words_list_{variant}")
    tap_text("猫", ymax=1000)
    time.sleep(1.5)
    shot(f"flashcard_{variant}")
    back()
    back()
    back()
    # 会话：START → 卡 → 测验 → 奖励
    tap_text("START") or tap_text("复习一下")
    time.sleep(2)
    for _ in range(8):
        if "quiz" in focus():
            break
        tap_text("NEXT") or tap_text("完成")
    time.sleep(1)
    answer_quiz_current()
    time.sleep(0.8)
    shot(f"quiz_{variant}")
    for _ in range(12):
        if "reward" in focus():
            break
        s = ui()
        if find_text(s, "答对啦！") or find_text(s, "再听一次，答案是「"):
            tap_text("NEXT") or tap_text("完成")
        else:
            answer_quiz_current()
    time.sleep(1.5)
    shot(f"reward_{variant}")
    tap_text("完成")
    time.sleep(1)
    # 文化
    tap_text("Songs · Stories · Poems")
    time.sleep(1)
    tap_text("故事")
    time.sleep(1.2)
    shot(f"culture_stories_{variant}")
    tap_text("嫦娥奔月")
    time.sleep(2.5)
    shot(f"player_{variant}")
    back()
    back()
    back()
    # 家长区
    sh(["am", "start", "-n", HOME])
    time.sleep(2)
    sh(["input", "swipe", "540", "1800", "540", "800", "300"])
    time.sleep(0.8)
    tap_text("Parent Centre")
    time.sleep(1)
    gate()
    time.sleep(1.5)
    shot(f"parent_dashboard_{variant}")


if __name__ == "__main__":
    walkthrough(sys.argv[1] if len(sys.argv) > 1 else "light")


def reset_display():
    sh(["cmd", "uimode", "night", "no"])
    sh(["settings", "put", "system", "font_scale", "1.0"])
    sh(["wm", "size", "reset"])
