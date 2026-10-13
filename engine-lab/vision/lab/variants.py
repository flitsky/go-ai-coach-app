"""앱 테스트의 변형(`BoardPhotoVariants.kt`)과 같은 여섯 가지 — 회전 ±7° · 사다리꼴 12% · 조명 기울기 · 축척 0.6x · 1.8x.

리샘플링은 OpenCV(쌍선형)라 앱의 변형과 픽셀은 조금 다르지만 기하는 같다. 사진 밖은 책상 같은 중간 회색(0x8A).
"""

import math

import cv2
import numpy as np

BACKDROP = (0x8A, 0x8A, 0x8A)


def _warp(img, corners, w, h, src, dst):
    m = cv2.getPerspectiveTransform(np.float32(src), np.float32(dst))
    out = cv2.warpPerspective(img, m, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=BACKDROP)
    moved = cv2.perspectiveTransform(corners.reshape(-1, 1, 2).astype(np.float64), m).reshape(-1, 2)
    return out, moved


def _rect(w, h):
    return [(0, 0), (w, 0), (w, h), (0, h)]


def rotated(img, corners, degrees):
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    h0, w0 = img.shape[:2]
    w = math.ceil(abs(w0 * c) + abs(h0 * s))
    h = math.ceil(abs(w0 * s) + abs(h0 * c))
    src = _rect(w0, h0)
    dst = [((x - w0 / 2) * c - (y - h0 / 2) * s + w / 2, (x - w0 / 2) * s + (y - h0 / 2) * c + h / 2) for x, y in src]
    return _warp(img, corners, w, h, src, dst)


def keystone(img, corners, shrink):
    h, w = img.shape[:2]
    inset = w * shrink / 2
    return _warp(img, corners, w, h, _rect(w, h), [(inset, 0), (w - inset, 0), (w, h), (0, h)])


def scaled(img, corners, factor):
    h0, w0 = img.shape[:2]
    w, h = max(1, round(w0 * factor)), max(1, round(h0 * factor))
    return _warp(img, corners, w, h, _rect(w0, h0), _rect(w, h))


def lighting(img, corners):
    h, w = img.shape[:2]
    f = 0.6 + 0.5 * np.arange(w) / max(1, w - 1)
    out = np.clip(img.astype(np.float32) * f[None, :, None], 0, 255).round().astype(np.uint8)
    return out, corners.copy()


def all_variants(img, corners):
    return [
        ("회전 7°", *rotated(img, corners, 7.0)),
        ("회전 -7°", *rotated(img, corners, -7.0)),
        ("사다리꼴 12%", *keystone(img, corners, 0.12)),
        ("조명 기울기", *lighting(img, corners)),
        ("축척 0.6x", *scaled(img, corners, 0.6)),
        ("축척 1.8x", *scaled(img, corners, 1.8)),
    ]
