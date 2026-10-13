"""격자선 두 방향의 **소실점으로 원근을 걷어낸다** — OpenCV(백로그 #263 단계 3). 앱이 함수 단위로 옮긴다.

왜 외곽을 잘라내지 않는가: JVM 실험(2026-10-13)에서 외곽 사각형이 0.3칸 안으로 딱 붙어야만 이득이었고, 윤곽선 방식은 그만큼 붙지
않았다(35장 중 4장). 지금의 격자 찾기(`BoardLocator`)는 회전(±20°)·판 옆 글자·여백은 이미 견딘다 — 못 견디는 것은 **원근(사다리꼴)** 이다.
그래서 사진을 자르지 않고 **원근만** 걷어낸 사진을 격자 찾기에 넘긴다.

1. 선분: LSD(`createLineSegmentDetector`)로 찾고, 짧은 것(돌 테두리의 호)은 버린다.
2. 방향 두 무리: 각도 히스토그램(0~180°)의 두 봉우리(서로 45° 넘게 떨어진).
3. 무리마다 소실점: 선분을 늘인 직선들의 최소제곱 교점(동차좌표 — 평행하면 무한원점). RANSAC으로 글자·UI 선분을 뺀다.
4. 두 소실점이 지나는 **소실선**을 무한원으로 보내는 투시 변환 → 두 무리가 각각 평행해진다(아핀까지). 기울어짐은 격자 찾기가 견딘다.
5. 원근이 약하면(소실선이 사진에서 아주 멀면) 아무것도 하지 않는다 — 똑바로 찍은 사진을 흔들지 않게.
"""

import math

import cv2
import numpy as np

WORKING_MAX_DIM = 800
MIN_SEGMENT = 0.04  # 긴 변 대비 — 돌(지름 ~1/19)의 호보다 길어야 한다
FAMILY_WINDOW = 20.0  # 봉우리 ±도
WEAK_PERSPECTIVE = 0.06  # 소실선 세기(정규화 좌표) — 사다리꼴 12% 변형은 0.13~0.20, 똑바로 찍은 사진은 0.04 이하(2026-10-13 실측)


def _segments(gray):
    lsd = cv2.createLineSegmentDetector(cv2.LSD_REFINE_STD)
    lines = lsd.detect(gray)[0]
    if lines is None:
        return np.zeros((0, 4))
    seg = lines.reshape(-1, 4).astype(np.float64)
    length = np.hypot(seg[:, 2] - seg[:, 0], seg[:, 3] - seg[:, 1])
    return seg[length >= MIN_SEGMENT * max(gray.shape)]


def _angle(seg):
    return np.degrees(np.arctan2(seg[:, 3] - seg[:, 1], seg[:, 2] - seg[:, 0])) % 180.0


def _families(seg):
    """길이로 가중한 각도 히스토그램의 두 봉우리(45°보다 멀리 떨어진)로 무리를 나눈다."""
    ang = _angle(seg)
    w = np.hypot(seg[:, 2] - seg[:, 0], seg[:, 3] - seg[:, 1])
    hist = np.zeros(180)
    for a, wt in zip(ang, w):
        for d in range(-3, 4):  # 조금 번지게
            hist[int(round(a + d)) % 180] += wt * (4 - abs(d))
    p1 = int(np.argmax(hist))
    masked = hist.copy()
    for d in range(-45, 46):
        masked[(p1 + d) % 180] = 0
    p2 = int(np.argmax(masked))
    if masked[p2] <= 0:
        return None

    def member(peak):
        diff = np.abs((ang - peak + 90) % 180 - 90)
        return seg[diff <= FAMILY_WINDOW]

    return member(p1), member(p2)


def _homogeneous_lines(seg):
    p = np.c_[seg[:, 0:2], np.ones(len(seg))]
    q = np.c_[seg[:, 2:4], np.ones(len(seg))]
    lines = np.cross(p, q)
    return lines / np.linalg.norm(lines[:, :2], axis=1, keepdims=True)


def _vanishing_point(seg, rng, iters=300):
    """RANSAC — 두 선분의 교점을 가설로, 직선이 그 점을 각도 2° 안으로 지나면 지지. 지지 선분으로 최소제곱 다듬기."""
    if len(seg) < 3:
        return None
    lines = _homogeneous_lines(seg)
    mids = (seg[:, 0:2] + seg[:, 2:4]) / 2
    dirs = seg[:, 2:4] - seg[:, 0:2]
    dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
    weights = np.hypot(seg[:, 2] - seg[:, 0], seg[:, 3] - seg[:, 1])

    def support(vp):
        # 선분 가운데에서 소실점을 향하는 방향과 선분 방향의 각도
        if abs(vp[2]) < 1e-9:
            to = np.tile(vp[:2], (len(seg), 1))
        else:
            to = vp[:2] / vp[2] - mids
        to /= np.linalg.norm(to, axis=1, keepdims=True) + 1e-12
        cos = np.abs(np.sum(to * dirs, axis=1))
        return cos >= math.cos(math.radians(2.0))

    best, best_w = None, -1
    for _ in range(iters):
        i, j = rng.choice(len(seg), 2, replace=False)
        vp = np.cross(lines[i], lines[j])
        if np.linalg.norm(vp) < 1e-12:
            continue
        vp = vp / np.linalg.norm(vp)
        m = support(vp)
        wsum = weights[m].sum()
        if wsum > best_w:
            best, best_w = m, wsum
    if best is None or best.sum() < 3:
        return None
    # 최소제곱: 지지 직선들 l·v = 0 를 만족하는 v = 가장 작은 특잇값의 벡터
    a = lines[best] * weights[best, None]
    _, _, vt = np.linalg.svd(a)
    vp = vt[-1]
    return vp / np.linalg.norm(vp)


def rectifying_homography(img_bgr, seed=263):
    """원근을 걷어내는 3×3 투시 변환(원본 픽셀 → 새 픽셀)과 새 크기. 원근이 약하거나 못 재면 `None`."""
    h, w = img_bgr.shape[:2]
    scale = min(1.0, WORKING_MAX_DIM / max(h, w))
    small = cv2.resize(img_bgr, (round(w * scale), round(h * scale)), interpolation=cv2.INTER_AREA) if scale < 1 else img_bgr
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    seg = _segments(gray)
    fam = _families(seg) if len(seg) else None
    if fam is None:
        return None
    rng = np.random.default_rng(seed)
    vps = [_vanishing_point(f, rng) for f in fam]
    if any(v is None for v in vps):
        return None
    # 작업 좌표 → 정규화 좌표(가운데 0, 긴 변 1)에서 계산해야 수치가 안정적이다
    sh, sw = gray.shape
    t = np.array([[1 / max(sw, sh), 0, -sw / 2 / max(sw, sh)], [0, 1 / max(sw, sh), -sh / 2 / max(sw, sh)], [0, 0, 1]])
    v1, v2 = (t @ v for v in vps)
    horizon = np.cross(v1, v2)
    if abs(horizon[2]) < 1e-12:
        return None
    horizon = horizon / horizon[2]
    rectifying_homography.last_strength = math.hypot(horizon[0], horizon[1])
    if math.hypot(horizon[0], horizon[1]) < WEAK_PERSPECTIVE:
        return None  # 소실선이 아주 멀다 = 원근이 약하다
    affine = np.array([[1, 0, 0], [0, 1, 0], [horizon[0], horizon[1], 1.0]])
    s = np.diag([scale, scale, 1.0])
    full = np.linalg.inv(t) @ affine @ t @ s  # 원본 픽셀 → (원근을 걷은) 작업 픽셀
    # 새 사진이 원본과 비슷한 크기·자리에 오도록 네 모서리를 맞춘다
    corners = np.float64([[0, 0], [w, 0], [w, h], [0, h]]).reshape(-1, 1, 2)
    mapped = cv2.perspectiveTransform(corners, full).reshape(-1, 2)
    if not np.all(np.isfinite(mapped)):
        return None
    lo, hi = mapped.min(axis=0), mapped.max(axis=0)
    span = hi - lo
    if span.max() <= 0 or span.max() > 20 * max(w, h) * scale:
        return None  # 소실선이 사진을 가로지른다 — 펴면 무한히 늘어난다
    fit = max(w, h) / span.max()
    place = np.array([[fit, 0, -lo[0] * fit], [0, fit, -lo[1] * fit], [0, 0, 1]])
    hmat = place @ full
    size = (int(math.ceil(span[0] * fit)), int(math.ceil(span[1] * fit)))
    return hmat / hmat[2, 2], size


def keystone_residual(corners: np.ndarray) -> float:
    """정답 바깥 격자선 사각형이 평행사변형에서 얼마나 먼가 — 마주 보는 변 길이 비의 최댓값 - 1(0 = 원근 없음)."""
    side = [np.linalg.norm(corners[i] - corners[(i + 1) % 4]) for i in range(4)]
    return max(side[0] / side[2], side[2] / side[0], side[1] / side[3], side[3] / side[1]) - 1.0
