"""격자 자체로 판을 펴고 잘라 낸다(백로그 #263) — 비스듬히·작게 찍힌 실물 판용.

`perspective.py`는 사진 전체에서 가장 센 두 방향으로 소실점을 잡는다. 판 옆면·다다미·벽의 선이 그보다 세면 엉뚱한 방향을 펴고(갤러리 w07·w08),
편 뒤에도 칸이 납작하거나(가로세로 비율은 안 맞춘다) 판이 사진의 일부라 격자 찾기가 엉뚱한 곳을 잡는다(w09·w18·w13·w33).

여기서는 **고르게 늘어선 선 무리 둘**을 찾는다 — 바둑판 격자는 같은 간격의 선이 열 몇 줄씩 두 방향으로 있다. 다다미 이음매·판 옆면 모서리는 그렇지 않다.

1. 선분(LSD, 짧은 돌 테두리 호는 버림).
2. 소실점 후보: 가장 긴 선분 [TOP_SEGMENTS]개의 쌍마다 교점 → 각도 [SUPPORT_DEG]° 안으로 지나는 선분 길이 합. 가장 센 것부터 지지 선분을 빼며 [MAX_VPS]개.
   무작위가 없다 — 앱(Kotlin)이 같은 답을 낸다.
3. 소실점 두 개씩: 둘이 지나는 소실선을 무한원으로 보내면(아핀) 두 무리가 각각 평행해진다. 무리마다 선 위치(법선 방향 거리)의 길이 가중
   히스토그램 → 자기상관의 첫 봉우리 = 칸 간격, 그 높이 = **고른 정도**. 두 무리 다 고른 쌍을 고른다.
4. 칸 간격·방향으로 격자 좌표 → 사진 좌표 변환을 바로 만든다(칸이 정사각, 줄이 축과 나란하게). 선이 실제로 있는 줄 범위 + 여유
   [MARGIN_CELLS]칸을 틀로 잘라 낸다 — 앱의 `PerspectiveFrame`(사진 속 사각형 → 직사각형)과 같은 꼴이다.
"""

import math

import cv2
import numpy as np

WORKING_MAX_DIM = 1000
MIN_SEGMENT = 0.012  # 긴 변 대비 — 돌 사이로 끊긴 격자선 한 칸 토막도 남게
TOP_SEGMENTS = 80
PAIR_MAX_DEG = 25.0  # 소실점 가설을 만드는 두 선분의 각도 차 상한
SUPPORT_DEG = 1.0
MAX_VPS = 4
MIN_CELL, MAX_CELL = 0.008, 0.12  # 칸 간격(정규화 — 긴 변 1)
MARGIN_CELLS = 3.0
WEIGHT_CAP = 2.0
OUT_LONG_SIDE = 900


def _segments(gray):
    lsd = cv2.createLineSegmentDetector(cv2.LSD_REFINE_STD)
    lines = lsd.detect(gray)[0]
    if lines is None:
        return np.zeros((0, 4))
    seg = lines.reshape(-1, 4).astype(np.float64)
    length = np.hypot(seg[:, 2] - seg[:, 0], seg[:, 3] - seg[:, 1])
    keep = length >= MIN_SEGMENT * max(gray.shape)
    return seg[keep]


def _norm(seg, w, h):
    s = max(w, h)
    out = seg.copy()
    out[:, [0, 2]] = (seg[:, [0, 2]] - w / 2) / s
    out[:, [1, 3]] = (seg[:, [1, 3]] - h / 2) / s
    return out


def _lines(seg):
    p = np.c_[seg[:, 0:2], np.ones(len(seg))]
    q = np.c_[seg[:, 2:4], np.ones(len(seg))]
    ln = np.cross(p, q)
    return ln / np.linalg.norm(ln[:, :2], axis=1, keepdims=True)


def _support(vp, seg, dirs, mids):
    if abs(vp[2]) < 1e-12:
        to = np.tile(vp[:2], (len(seg), 1))
    else:
        to = vp[:2] / vp[2] - mids
    to = to / (np.linalg.norm(to, axis=1, keepdims=True) + 1e-12)
    return np.abs(np.sum(to * dirs, axis=1)) >= math.cos(math.radians(SUPPORT_DEG))


def _vanishing_points(seg):
    """[MAX_VPS]개의 소실점과 각자의 지지 선분 표시. 무작위 없이 — 긴 선분 쌍을 모두 본다."""
    length = np.hypot(seg[:, 2] - seg[:, 0], seg[:, 3] - seg[:, 1])
    dirs = (seg[:, 2:4] - seg[:, 0:2]) / length[:, None]
    mids = (seg[:, 0:2] + seg[:, 2:4]) / 2
    lines = _lines(seg)
    ang = np.degrees(np.arctan2(dirs[:, 1], dirs[:, 0])) % 180
    free = np.ones(len(seg), bool)
    out = []
    for _ in range(MAX_VPS):
        idx = np.where(free)[0]
        if len(idx) < 4:
            break
        top = idx[np.argsort(-length[idx])][:TOP_SEGMENTS]
        best, best_w, best_vp = None, 0.0, None
        for a in range(len(top)):
            for b in range(a + 1, len(top)):
                i, j = top[a], top[b]
                d = abs((ang[i] - ang[j] + 90) % 180 - 90)
                if d > PAIR_MAX_DEG:
                    continue
                vp = np.cross(lines[i], lines[j])
                n = np.linalg.norm(vp)
                if n < 1e-12:
                    continue
                vp = vp / n
                m = _support(vp, seg, dirs, mids) & free
                wsum = length[m].sum()
                if wsum > best_w:
                    best, best_w, best_vp = m, wsum, vp
        if best is None or best.sum() < 4:
            break
        a = lines[best] * length[best, None]
        vp = np.linalg.svd(a)[2][-1]
        vp = vp / np.linalg.norm(vp)
        m = _support(vp, seg, dirs, mids) & free
        out.append((vp, m))
        free &= ~m
    return out


def _period(pos, wts):
    """선 위치들의 고른 간격 — (간격, 고른 정도 0~1). 위치 히스토그램(간격의 1/40 눈금)의 자기상관 첫 봉우리."""
    if len(pos) < 6:
        return None
    lo, hi = pos.min(), pos.max()
    if hi - lo < 4 * MIN_CELL:
        return None
    binw = MIN_CELL / 8
    nb = int((hi - lo) / binw) + 3
    hist = np.zeros(nb)
    np.add.at(hist, ((pos - lo) / binw).astype(int) + 1, wts)
    hist = np.convolve(hist, np.array([1, 2, 3, 2, 1], float) / 9, mode="same")
    hist -= hist.mean()
    ac = np.correlate(hist, hist, mode="full")[nb - 1:]
    if ac[0] <= 0:
        return None
    ac = ac / ac[0]
    lmin, lmax = int(MIN_CELL / binw), min(int(MAX_CELL / binw), nb // 4)
    if lmax <= lmin + 2:
        return None
    peaks = [k for k in range(lmin + 1, lmax) if ac[k] > ac[k - 1] and ac[k] >= ac[k + 1] and ac[k] > 0]
    if not peaks:
        return None
    top = max(ac[k] for k in peaks)
    k = next(k for k in peaks if ac[k] >= 0.6 * top)
    # 배수 봉우리로 다듬기
    est = float(k)
    for mult in (2, 3, 4):
        c = int(round(est * mult))
        if c + 2 >= len(ac) or c > lmax * 4:
            break
        win = range(c - 2, c + 3)
        p = max(win, key=lambda t: ac[t])
        if ac[p] < 0.3 * top:
            break
        est = p / mult
    return est * binw, float(ac[k])


def _family_lines(seg, mask, haff):
    """한 무리의 선분을 아핀으로 편 뒤: 단위 방향 · 법선 · 선마다 (법선 거리, 길이)."""
    s = seg[mask]
    p = cv2.perspectiveTransform(s[:, 0:2].reshape(-1, 1, 2), haff).reshape(-1, 2)
    q = cv2.perspectiveTransform(s[:, 2:4].reshape(-1, 1, 2), haff).reshape(-1, 2)
    d = q - p
    ln = np.linalg.norm(d, axis=1)
    ok = ln > 1e-9
    p, q, d, ln = p[ok], q[ok], d[ok], ln[ok]
    u = d / ln[:, None]
    # 방향의 부호를 맞춘 가중 평균
    ref = u[np.argmax(ln)]
    u = u * np.sign(u @ ref)[:, None]
    direction = (u * ln[:, None]).sum(0)
    direction /= np.linalg.norm(direction)
    normal = np.array([-direction[1], direction[0]])
    pos = ((p + q) / 2) @ normal
    # 무게 = 길이, 단 중앙값의 [WEIGHT_CAP]배까지 — 판 테두리·다다미 이음매·벽처럼 긴 선 몇 개가 고른 격자선 열 몇 줄을 덮지 않게
    # (w08·w09: 긴 선 두세 개의 봉우리가 격자선 봉우리의 열 배였다).
    wts = np.minimum(ln, WEIGHT_CAP * np.median(ln))
    return direction, normal, pos, wts


def _horizon_affine(v1, v2, corners_n):
    hz = np.cross(v1, v2)
    if np.linalg.norm(hz[:2]) < 1e-9 * abs(hz[2]) or abs(hz[2]) < 1e-12:
        if abs(hz[2]) < 1e-12:
            return None  # 소실선이 사진 한가운데를 지난다
        return np.eye(3)
    hz = hz / hz[2]
    side = corners_n @ hz[:2] + 1.0
    if np.any(side <= 0.15):  # 소실선이 사진에 걸치거나 아주 가깝다 — 펴면 무한히 늘어난다
        return None
    return np.array([[1, 0, 0], [0, 1, 0], [hz[0], hz[1], 1.0]])


def _run(pos, wts, period):
    """선들이 실제로 있는 줄 번호 범위(가장 긴 덩어리) — 위상 · 첫 줄 · 끝 줄 · 줄마다의 무게."""
    phase_angles = 2 * np.pi * pos / period
    phase = math.atan2(np.sum(wts * np.sin(phase_angles)), np.sum(wts * np.cos(phase_angles))) / (2 * np.pi) * period
    k = np.round((pos - phase) / period).astype(int)
    off = np.abs((pos - phase) / period - k)
    good = off < 0.2
    if good.sum() < 4:
        return None
    ks = k[good]
    kw = {}
    for kk, ww in zip(ks, wts[good]):
        kw[kk] = kw.get(kk, 0.0) + ww
    strong = sorted(kw)
    # 기준은 「흔한 줄」의 무게(상위 4분의 1 경계) — 가장 무거운 줄을 기준으로 하면 판 테두리 한 줄에 격자선이 다 묻혔다
    ref = float(np.percentile(list(kw.values()), 75))
    keep = [kk for kk in strong if kw[kk] >= 0.25 * ref]
    # 한두 줄 빈 곳은 잇고 그보다 긴 틈에서 덩어리를 나눈다 — 가장 무거운 덩어리
    blocks, cur = [], [keep[0]]
    for kk in keep[1:]:
        if kk - cur[-1] <= 3:
            cur.append(kk)
        else:
            blocks.append(cur)
            cur = [kk]
    blocks.append(cur)
    block = max(blocks, key=lambda b: sum(kw[x] for x in b))
    return phase, block[0], block[-1], kw


def _shortfall(lines):
    """표준 판 크기(9·13·19) 중 [lines] 이상인 가장 작은 것까지 모자란 줄 수(19를 넘으면 0)."""
    for size in (9, 13, 19):
        if lines <= size:
            return size - lines
    return 0


def find_board_frame(img_bgr):
    """사진 속 사각형(TL TR BR BL, 원본 픽셀) → 직사각형(너비, 높이) 틀과 잰 값들. 못 찾으면 None."""
    h, w = img_bgr.shape[:2]
    scale = min(1.0, WORKING_MAX_DIM / max(h, w))
    small = cv2.resize(img_bgr, (round(w * scale), round(h * scale)), interpolation=cv2.INTER_AREA) if scale < 1 else img_bgr
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    sh, sw = gray.shape
    seg = _segments(gray)
    if len(seg) < 12:
        return None
    segn = _norm(seg, sw, sh)
    vps = _vanishing_points(segn)
    s = max(sw, sh)
    corners_n = np.array([[-sw / 2, -sh / 2], [sw / 2, -sh / 2], [sw / 2, sh / 2], [-sw / 2, sh / 2]]) / s
    best = None
    for a in range(len(vps)):
        for b in range(a + 1, len(vps)):
            (va, ma), (vb, mb) = vps[a], vps[b]
            haff = _horizon_affine(va, vb, corners_n)
            if haff is None:
                continue
            fa = _family_lines(segn, ma, haff)
            fb = _family_lines(segn, mb, haff)
            # 두 무리가 너무 비슷한 방향이면 같은 무리를 둘로 나눈 것이다
            if abs(fa[0] @ fb[0]) > math.cos(math.radians(30)):
                continue
            pa = _period(fa[2], fa[3])
            pb = _period(fb[2], fb[3])
            if pa is None or pb is None:
                continue
            ra = _run(fa[2], fa[3], pa[0])
            rb = _run(fb[2], fb[3], pb[0])
            if ra is None or rb is None:
                continue
            na, nb_ = ra[2] - ra[1] + 1, rb[2] - rb[1] + 1
            if na < 5 or nb_ < 5:
                continue
            score = math.sqrt(pa[1] * pb[1]) * math.sqrt(min(na, 21) * min(nb_, 21))
            if best is None or score > best["score"]:
                best = dict(score=score, haff=haff, fa=fa, fb=fb, pa=pa, pb=pb, ra=ra, rb=rb, na=na, nb=nb_)
    if best is None:
        return None
    # 격자 좌표(k = 무리 A의 줄, j = 무리 B의 줄) → 편 좌표: [nA; nB] x = [phaseA + k pA, phaseB + j pB]
    nA, nB = best["fa"][1], best["fb"][1]
    pA, pB = best["pa"][0], best["pb"][0]
    phA, k0, k1, _ = best["ra"]
    phB, j0, j1, _ = best["rb"]
    ninv = np.linalg.inv(np.array([nA, nB]))

    def grid_to_img(k, j):
        x = ninv @ np.array([phA + k * pA, phB + j * pB])
        pt = np.linalg.inv(best["haff"]) @ np.array([x[0], x[1], 1.0])
        pt = pt[:2] / pt[2]
        return np.array([pt[0] * s + sw / 2, pt[1] * s + sh / 2]) / scale

    # 찾은 줄 수가 표준 판(9·13·19)보다 적으면 그만큼 여유를 더 둔다 — 가장자리 줄이 돌에 덮이거나 흐려 덜 셌을 수 있다(w28: 19줄 판을 14줄로).
    # 어느 쪽이 모자란지 모르므로 양쪽 다. 여유가 넓어도 격자 찾기가 그 안에서 판을 찾는다.
    mk, mj = MARGIN_CELLS + _shortfall(best["na"]), MARGIN_CELLS + _shortfall(best["nb"])
    quad = np.array([grid_to_img(k0 - mk, j0 - mj), grid_to_img(k1 + mk, j0 - mj), grid_to_img(k1 + mk, j1 + mj), grid_to_img(k0 - mk, j1 + mj)])
    inner = np.array([grid_to_img(k0, j0), grid_to_img(k1, j0), grid_to_img(k1, j1), grid_to_img(k0, j1)])
    if not np.all(np.isfinite(quad)):
        return None
    order = np.arange(4)
    # 거울상이면 B 쪽을 뒤집는다(시계 방향 TL TR BR BL)
    area = sum(quad[i, 0] * quad[(i + 1) % 4, 1] - quad[(i + 1) % 4, 0] * quad[i, 1] for i in range(4))
    if area < 0:
        order = order[[1, 0, 3, 2]]
    q = quad[order]
    # 사진의 위쪽이 틀의 위쪽이 되게 — TL→TR이 가장 오른쪽을 향하는 순서로 돌린다
    best_rot = max(range(4), key=lambda r: (q[(r + 1) % 4] - q[r])[0] / (np.linalg.norm(q[(r + 1) % 4] - q[r]) + 1e-9))
    order = np.roll(order, -best_rot)
    quad, inner = quad[order], inner[order]
    # TL→TR 방향이 무리 A(k)를 따라가는가 B(j)를 따라가는가
    along_k = order[0] in (0, 3) and order[1] in (1, 2) or order[0] in (1, 2) and order[1] in (0, 3)
    spans = (k1 - k0 + 2 * mk, j1 - j0 + 2 * mj)
    lines = (best["na"], best["nb"])
    if not along_k:
        spans, lines = spans[::-1], lines[::-1]
    cols, rows = spans
    cell = OUT_LONG_SIDE / max(cols, rows)
    size = (int(round(cols * cell)), int(round(rows * cell)))
    hmat = cv2.getPerspectiveTransform(np.float32(quad), np.float32([[0, 0], [size[0], 0], [size[0], size[1]], [0, size[1]]]))
    inner_frame = cv2.perspectiveTransform(inner.reshape(-1, 1, 2), hmat.astype(np.float64)).reshape(-1, 2)
    info = dict(score=best["score"], lines=lines, regularity=(best["pa"][1], best["pb"][1]), inner=inner_frame, vps=len(vps))
    return quad, size, info
