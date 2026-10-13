"""바둑판 **외곽 사각형** 찾기 — OpenCV(백로그 #263 단계 3). 앱의 `OpenCvBoardOutline.kt`가 이것을 함수 단위로 옮긴다.

목표: 바깥 격자선 사각형에 **딱 붙는**(0.3칸 안) 사각형. JVM 실험(2026-10-13)에서 그만큼 붙으면 원근·축소 사진의 판 찾기가 풀렸고,
1.5칸 넘게 넓으면 판 옆 글자·UI가 섞여 오히려 나빠졌다.

1. 긴 변 800px로 줄이고 회색 + CLAHE(한쪽으로 쏠린 조명·그림자를 고르게).
2. 사각형 후보: 가장자리(Canny)·어두운 구조(적응 임계)의 윤곽 → 볼록 사각형 근사, 큰 윤곽의 볼록 껍질 → 사각형 근사.
3. 후보마다 펴 보고 **격자다움**을 잰다 — 안쪽에 고른 간격의 선이 두 방향으로 있는가 × 가장자리가 바로 바깥 격자선인가(딱 붙었나).
"""

import cv2
import numpy as np

WORKING_MAX_DIM = 800
PROBE = 400  # 격자다움을 잴 때 펴는 크기(px)


def _order(quad: np.ndarray) -> np.ndarray:
    """네 점을 TL TR BR BL 순으로."""
    pts = quad.reshape(4, 2).astype(np.float64)
    s = pts.sum(axis=1)
    d = pts[:, 1] - pts[:, 0]
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)], pts[np.argmax(s)], pts[np.argmax(d)]])


def _prepare(img_bgr):
    h, w = img_bgr.shape[:2]
    scale = min(1.0, WORKING_MAX_DIM / max(h, w))
    small = cv2.resize(img_bgr, (round(w * scale), round(h * scale)), interpolation=cv2.INTER_AREA) if scale < 1 else img_bgr
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    return gray, scale


def _quads_from(binary, min_area):
    contours, _ = cv2.findContours(binary, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    out = []
    for c in contours:
        if cv2.contourArea(c) < min_area:
            continue
        for shape in (c, cv2.convexHull(c)):
            peri = cv2.arcLength(shape, True)
            for eps in (0.01, 0.02, 0.04):
                approx = cv2.approxPolyDP(shape, eps * peri, True)
                if len(approx) == 4 and cv2.isContourConvex(approx) and cv2.contourArea(approx) >= min_area:
                    out.append(_order(approx))
                    break
    return out


def _dedup(quads, tol):
    kept = []
    for q in quads:
        if all(np.max(np.linalg.norm(q - k, axis=1)) > tol for k in kept):
            kept.append(q)
    return kept


def _line_map(gray):
    """가는 어두운 선 — 적응 임계로 어두운 구조를 뽑는다(돌도 섞이지만 투영 주기에는 선이 이긴다)."""
    return cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV, 15, 7)


def _periodicity(profile: np.ndarray) -> float:
    p = profile - profile.mean()
    if not np.any(p):
        return 0.0
    ac = np.correlate(p, p, mode="full")[len(p) - 1 :]
    ac /= ac[0]
    lo, hi = len(p) // 24, len(p) // 6  # 칸 간격: 판 폭의 1/24(19줄) ~ 1/6(9줄 안쪽 여유)
    return float(max(0.0, ac[lo:hi].max()))


def grid_score(gray, quad) -> float:
    """사각형을 펴서 격자다움을 잰다: (두 방향 주기의 세기) × (가장자리 띠에 선이 몰려 있나 — 딱 붙었나)."""
    dst = np.float32([[0, 0], [PROBE, 0], [PROBE, PROBE], [0, PROBE]])
    m = cv2.getPerspectiveTransform(np.float32(quad), dst)
    flat = cv2.warpPerspective(gray, m, (PROBE, PROBE), flags=cv2.INTER_LINEAR, borderValue=128)
    lines = _line_map(flat).astype(np.float32) / 255.0
    rows, cols = lines.mean(axis=1), lines.mean(axis=0)
    periodic = min(_periodicity(rows), _periodicity(cols))
    band = max(2, PROBE // 100)
    edge = np.mean([rows[:band].max(), rows[-band:].max(), cols[:band].max(), cols[-band:].max()])
    inner = (rows[band:-band].mean() + cols[band:-band].mean()) / 2
    tight = edge / max(1e-6, inner)
    return periodic * min(tight, 4.0)


def candidates(img_bgr, limit: int = 6):
    """외곽 사각형 후보들(원본 픽셀, TL TR BR BL)을 격자다움 순으로. 각 원소는 (사각형, 점수)."""
    gray, scale = _prepare(img_bgr)
    h, w = gray.shape
    min_area = 0.08 * w * h
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    med = float(np.median(blur))
    edges = cv2.Canny(blur, max(10, 0.66 * med), min(255, 1.33 * med))
    edges = cv2.dilate(edges, np.ones((3, 3), np.uint8))
    dark = _line_map(blur)
    dark = cv2.morphologyEx(dark, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    quads = _quads_from(edges, min_area) + _quads_from(dark, min_area)
    quads = _dedup(quads, tol=0.01 * max(w, h))
    scored = sorted(((q, grid_score(gray, q)) for q in quads), key=lambda t: -t[1])[:limit]
    return [(q / scale, s) for q, s in scored]
