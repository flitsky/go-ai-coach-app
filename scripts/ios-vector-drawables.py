#!/usr/bin/env python3
"""안드로이드 벡터 드로어블(XML)을 iOS용 Compose `ImageVector` 코드로 옮긴다(백로그 #294 — 이식 계획 S9).

iOS(Compose Multiplatform)는 안드로이드 XML 벡터를 읽지 못한다 — 그래서 같은 경로 데이터를 그대로 담은 코틀린을 만든다.
이 앱의 벡터는 보상 아이콘 다섯이고, 쓰는 속성은 path의 채움 · 선 색 · 선 굵기 · 선 끝 모양뿐이다(모르는 속성은 멈춘다).

쓰는 법: python3 scripts/ios-vector-drawables.py   (저장소 뿌리에서) — 결과 파일은 커밋한다.
"""
import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app-android/src/main/res/drawable")
OUT = os.path.join(ROOT, "app-android/src/iosMain/kotlin/com/worksoc/goaicoach/ui/designsystem/IosVectorDrawables.kt")
NAMES = ["reward_ad_skip", "reward_eval", "reward_move_review", "reward_top_moves", "reward_undo"]
A = "{http://schemas.android.com/apk/res/android}"
KNOWN = {"pathData", "fillColor", "strokeColor", "strokeWidth", "strokeLineCap"}


def color(value):
    v = value.lstrip("#")
    if len(v) == 6:
        v = "FF" + v
    if len(v) != 8:
        sys.exit(f"unsupported color {value}")
    return f"Color(0x{v.upper()})"


def camel(name):
    return "".join(part.capitalize() for part in name.split("_"))


def vector_code(name):
    root = ET.parse(os.path.join(SRC, name + ".xml")).getroot()
    width = root.get(A + "width").removesuffix("dp")
    height = root.get(A + "height").removesuffix("dp")
    vw = root.get(A + "viewportWidth")
    vh = root.get(A + "viewportHeight")
    lines = [
        f"internal val {camel(name)}: ImageVector by lazy {{",
        f"    ImageVector.Builder(name = \"{name}\", defaultWidth = {width}.dp, defaultHeight = {height}.dp, viewportWidth = {vw}f, viewportHeight = {vh}f)",
    ]
    for child in root:
        if child.tag != "path":
            sys.exit(f"{name}: unsupported element <{child.tag}>")
        attrs = {k.replace(A, ""): v for k, v in child.attrib.items()}
        unknown = set(attrs) - KNOWN
        if unknown:
            sys.exit(f"{name}: unsupported attributes {unknown}")
        args = []
        if "fillColor" in attrs:
            args.append(f"fill = SolidColor({color(attrs['fillColor'])})")
        if "strokeColor" in attrs:
            args.append(f"stroke = SolidColor({color(attrs['strokeColor'])})")
        if "strokeWidth" in attrs:
            args.append(f"strokeLineWidth = {attrs['strokeWidth']}f")
        if "strokeLineCap" in attrs:
            args.append(f"strokeLineCap = StrokeCap.{attrs['strokeLineCap'].capitalize()}")
        args.append(f"pathData = addPathNodes(\"{attrs['pathData']}\")")
        lines.append("        .addPath(" + ", ".join(args) + ")")
    lines.append("        .build()")
    lines.append("}")
    return "\n".join(lines)


body = "\n\n".join(vector_code(n) for n in NAMES)
text = f'''package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// 만든 파일 — 고치지 말 것. `scripts/ios-vector-drawables.py`가 `app-android/src/main/res/drawable/reward_*.xml`에서 만든다(백로그 #294 — S9).

{body}
'''
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write(text)
print("wrote", os.path.relpath(OUT, ROOT))
