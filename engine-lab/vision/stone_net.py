"""신경망 돌 판정 — 교점 조각(빈 점·흑·백) 분류기를 학습하고 앱 자산으로 내보낸다(백로그 #263 단계 5).

재료: 앱 JVM 테스트 `StonePatchExportTest`가 쓴 `runs/patches/`(교점 조각 — 앱이 판정할 때와 **같은 함수** `StonePatches`로 잘랐다).

    ./gradlew :app-android:testDebugUnitTest --tests '*StonePatchExportTest*' -PexportStonePatches   # 조각 내보내기
    engine-lab/vision/.venv/bin/python -I engine-lab/vision/stone_net.py            # 묶음 교차 검증만(규칙과 견주기)
    engine-lab/vision/.venv/bin/python -I engine-lab/vision/stone_net.py --export   # + 전부로 학습해 앱 자산·대조 벡터를 쓴다
    engine-lab/vision/.venv/bin/python -I engine-lab/vision/stone_net.py --export-only   # 교차 검증 없이 내보내기만

검증은 **묶음(같은 대국) 단위**로 나눈다 — 같은 대국을 다른 각도로 찍은 사진이 학습과 검증에 함께 들어가면 점수가 부풀려진다.
검증 점수는 검증 묶음의 「정답 모서리」·「앱이 찾은 모서리」 판만 센다(흔든 모서리·변형은 학습에만).

앱 쪽 실행은 순수 Kotlin(`StoneNet`)이다 — 맥 JVM 테스트가 앱과 같은 계산을 한다(LiteRT는 JVM에서 못 돈다 — 함정 91과 같은 사정).
내보내는 파일: `app-android/src/main/resources/vision/stone-net.bin`(가중치 — 앱에 실린다) ·
`app-android/src/test/resources/vision/stone-net-parity.json`(대조 벡터).

2026-10-14 첫 결과(묶음 5겹, `--warm`): 정답 모서리 신경망 97.3% · 규칙 97.8%, 앱 모서리 98.6% · 99.5%. 규칙의 흔들리는 교점만 신경망에 맡겨도
99.41% 대 99.48% — 대국 23묶음이라 그림 양식마다 한 장뿐이어서 처음 보는 양식(기준 이미지 04 · 기보 w48)에서 약했다. 사용자 사진을 더해 다시 잰다.

2026-10-15 사용자 사진 39장(S22 Ultra · 찍은 날 19묶음)을 더해 다시 학습(넓게 · 돌리기 없이): 처음 보는 날의 사용자 사진 정답 모서리 신경망 99.92% · 규칙 93.79%
(판 전체 34/39 대 3/39). 그림 기보는 여전히 규칙이 낫다(기준 이미지 97.7% 대 100%) — 그래서 앱은 **판마다 고른다**(규칙이 흔들리는 판만 신경망, 함정 97):
정답 모서리 99.37%(판 전체 55/63) · 앱 모서리 99.68%(30/35). 「확인 필요」는 확률 0.9 미만(`StoneNet.DoubtfulBelow`).
"""

import json
import struct
import sys
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

REPO = Path(__file__).resolve().parents[2]
PATCHES = REPO / "engine-lab/vision/runs/patches"
# 앱에 실린다(자바 리소스 — 앱과 JVM 테스트가 같은 파일을 같은 길로 읽는다, `StoneNet.bundled`). 규칙이 흔들리는 판만 이것으로 읽는다.
ASSET = REPO / "app-android/src/main/resources/vision/stone-net.bin"
PARITY = REPO / "app-android/src/test/resources/vision/stone-net-parity.json"
SIZE = 24
SCALE = 64.0
FOLDS = 5
EPOCHS = 14
SEED = 263
# 실험 설정(명령줄로 바꾼다): 채널 폭 · 학습 횟수 · 따뜻한 조명·대비 변형 · 돌리기(끄면 좌우 뒤집기만)
# 2026-10-15 사용자 사진 39장을 더해 넷을 견줬다 — 넓게 · 돌리기 없이가 사용자 사진(처음 보는 날)에서 가장 좋았다(99.92% · 판 전체 34/39).
CONFIG = {"width": (16, 32, 32, 64), "epochs": EPOCHS, "warm": True, "rotate": False}
LABELS = ".BW"


class StoneNet(nn.Module):
    """3×3 합성곱 셋(각각 ReLU · 2×2 최대 풀링) → 완전 연결 둘. 앱의 `StoneNet.kt`가 같은 계산을 한다."""

    def __init__(self, c1=8, c2=16, c3=16, hidden=32):
        super().__init__()
        self.c = (c1, c2, c3, hidden)
        self.conv1 = nn.Conv2d(3, c1, 3, padding=1)
        self.conv2 = nn.Conv2d(c1, c2, 3, padding=1)
        self.conv3 = nn.Conv2d(c2, c3, 3, padding=1)
        self.fc1 = nn.Linear(c3 * (SIZE // 8) ** 2, hidden)
        self.fc2 = nn.Linear(hidden, 3)

    def forward(self, x):
        x = F.max_pool2d(F.relu(self.conv1(x)), 2)
        x = F.max_pool2d(F.relu(self.conv2(x)), 2)
        x = F.max_pool2d(F.relu(self.conv3(x)), 2)
        return self.fc2(F.relu(self.fc1(x.flatten(1))))


def load():
    manifest = json.loads((PATCHES / "manifest.json").read_text(encoding="utf-8"))
    per = 3 * SIZE * SIZE
    rec = 5 + 12 + per
    xs, bgs, ys, rules, groups, variants, boards, shaky = [], [], [], [], [], [], [], []
    for b, m in enumerate(manifest):
        raw = (PATCHES / m["file"]).read_bytes()
        assert raw[:4] == b"STP1"
        count, size = struct.unpack_from("<ii", raw, 4)
        assert size == SIZE
        body = np.frombuffer(raw, np.uint8, offset=12).reshape(count, rec)
        ys.append(body[:, 0].astype(np.int64))
        rules.append(body[:, 1].astype(np.int64))
        shaky.append((body[:, 4] & 0x80) != 0)
        bgs.append(np.frombuffer(body[:, 5:17].tobytes(), "<f4").reshape(count, 3))
        xs.append(body[:, 17:].reshape(count, 3, SIZE, SIZE))
        groups += [m["group"]] * count
        variants += [m["variant"]] * count
        boards += [b] * count
    x = np.concatenate(xs)
    bg = np.concatenate(bgs)
    xn = ((x.astype(np.float32) - bg[:, :, None, None]) / SCALE).astype(np.float32)
    y, rule, grp, var, brd, shk = (np.concatenate(ys), np.concatenate(rules), np.array(groups), np.array(variants), np.array(boards),
                                   np.concatenate(shaky))
    known = y != 3  # 정답 「모름」(손에 가린 자리)은 학습·채점에서 뺀다
    return manifest, xn[known], y[known], rule[known], grp[known], var[known], brd[known], shk[known]


def augment(x, rng):
    """돌리기·뒤집기(돌과 격자는 대칭) · 채널별 밝기 배율과 치우침(조명) · 잡음.

    `rotate`를 끄면 좌우 뒤집기만 한다 — 비스듬히 찍은 실물 사진은 돌이 먼 쪽(위)으로 솟아 보여서, 위아래가 정보다(함정 94).
    """
    if CONFIG["rotate"]:
        x = torch.rot90(x, int(rng.integers(4)), dims=(2, 3))
    if rng.random() < 0.5:
        x = torch.flip(x, dims=(3,))
    n = x.shape[0]
    gain = rng.uniform(0.8, 1.2, (n, 3, 1, 1)).astype(np.float32)
    bias = rng.uniform(-0.1, 0.1, (n, 3, 1, 1)).astype(np.float32)
    if CONFIG["warm"]:
        # 따뜻한 조명: 파랑을 깎고 빨강을 올린다 · 대비를 줄이거나 늘린다(그늘·역광)
        warm = rng.uniform(0.0, 0.35, (n, 1, 1, 1)).astype(np.float32)
        gain = gain * np.concatenate([1 + warm * 0.3, np.ones_like(warm), 1 - warm], axis=1)
        gain = gain * rng.uniform(0.6, 1.3, (n, 1, 1, 1)).astype(np.float32)
    noise = torch.from_numpy(rng.normal(0, 0.03, x.shape).astype(np.float32))
    return x * torch.from_numpy(gain) + torch.from_numpy(bias) + noise


DEVICE = torch.device("mps" if torch.backends.mps.is_available() else "cpu")


def train(x, y, seed, epochs=None):
    epochs = epochs or CONFIG["epochs"]
    torch.manual_seed(seed)
    rng = np.random.default_rng(seed)
    model = StoneNet(*CONFIG["width"]).to(DEVICE)
    opt = torch.optim.Adam(model.parameters(), lr=3e-3)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, epochs)
    counts = np.bincount(y, minlength=3).astype(np.float32)
    weight = torch.from_numpy((counts.sum() / (3 * counts)) ** 0.5).to(DEVICE)
    xt, yt = torch.from_numpy(x), torch.from_numpy(y)
    for _ in range(epochs):
        model.train()
        order = torch.from_numpy(rng.permutation(len(y)))
        for i in range(0, len(y), 256):
            idx = order[i:i + 256]
            loss = F.cross_entropy(model(augment(xt[idx], rng).to(DEVICE)), yt[idx].to(DEVICE), weight=weight)
            opt.zero_grad()
            loss.backward()
            opt.step()
        sched.step()
    model.eval()
    return model.to("cpu")


def predict(model, x):
    """판정과 그 확률(소프트맥스 최댓값) — 확률은 앱의 「확인 필요」 문턱을 정하는 데 쓴다."""
    with torch.no_grad():
        p = torch.cat([F.softmax(model(torch.from_numpy(x[i:i + 4096])), 1) for i in range(0, len(x), 4096)])
    best = p.max(1)
    return best.indices.numpy(), best.values.numpy()


def cross_validate(manifest, x, y, rules, groups, variants, boards):
    names = sorted(set(groups))
    # 묶음을 이름순으로 돌려 담는다(무작위 없이 — 같은 입력이면 같은 나눔)
    fold_of = {g: i % FOLDS for i, g in enumerate(names)}
    folds = np.array([fold_of[g] for g in groups])
    real = np.isin(variants, ["truth", "auto"])
    rows = []
    cv_pred = np.full(len(y), -1)
    cv_prob = np.zeros(len(y), np.float32)
    for f in range(FOLDS):
        tr, te = folds != f, (folds == f) & real
        t0 = time.time()
        model = train(x[tr], y[tr], SEED + f)
        pred, prob = predict(model, x[te])
        cv_pred[te] = pred
        cv_prob[te] = prob
        for b in np.unique(boards[te]):
            sel = boards[te] == b
            m = manifest[b]
            yy = y[te][sel]
            rows.append(dict(key=m["key"], set=m["set"], variant=m["variant"], n=int(m["n"]), net=int((pred[sel] == yy).sum()),
                             rules=int((rules[te][sel] == yy).sum()), total=int(sel.sum())))
        print(f"  묶음 {f + 1}/{FOLDS}: 학습 {tr.sum()}조각 · {time.time() - t0:.0f}초", flush=True)
    return rows, cv_pred, cv_prob


def report(rows):
    lines = ["| 사진 | 모서리 | 신경망 | 규칙 | 교점 |", "| --- | --- | --- | --- | --- |"]
    for r in sorted(rows, key=lambda r: (r["key"], r["variant"])):
        lines.append(f"| {r['key']} | {r['variant']} | {100 * r['net'] / r['total']:.1f}% | {100 * r['rules'] / r['total']:.1f}% | {r['total']} |")
    # 묶음별(기준 이미지 · 인터넷 · 사용자 사진)로도 — 그림 양식과 실물 사진은 성적이 따로 논다
    for s, v in [(None, "truth"), (None, "auto")] + [(s, v) for s in sorted({r["set"] for r in rows}) for v in ("truth", "auto")]:
        sel = [r for r in rows if r["variant"] == v and s in (None, r["set"])]
        if not sel:
            continue
        net = sum(r["net"] for r in sel)
        rul = sum(r["rules"] for r in sel)
        tot = sum(r["total"] for r in sel)
        full_net = sum(r["net"] == r["total"] for r in sel)
        full_rul = sum(r["rules"] == r["total"] for r in sel)
        better = sum(r["net"] > r["rules"] for r in sel)
        worse = sum(r["net"] < r["rules"] for r in sel)
        lines.append("")
        lines.append(f"**{s or '전체'} {v}**: 신경망 {100 * net / tot:.2f}% · 규칙 {100 * rul / tot:.2f}% · 판 전체가 맞은 판 신경망 {full_net}/{len(sel)} · 규칙 {full_rul}/{len(sel)} · "
                     f"신경망이 나은 판 {better} · 못한 판 {worse}")
    return "\n".join(lines)


def export(model, x, y):
    c1, c2, c3, hidden = model.c
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    with ASSET.open("wb") as fh:
        fh.write(b"STN1")
        fh.write(struct.pack("<6i", 1, SIZE, c1, c2, c3, hidden))
        for t in (model.conv1.weight, model.conv1.bias, model.conv2.weight, model.conv2.bias, model.conv3.weight, model.conv3.bias,
                  model.fc1.weight, model.fc1.bias, model.fc2.weight, model.fc2.bias):
            fh.write(t.detach().numpy().astype("<f4").tobytes())
    # 대조 벡터 — 조각 몇 개와 그 로짓. 앱의 `StoneNetParityTest`가 같은 값을 내는지 본다.
    rng = np.random.default_rng(SEED)
    picks = np.concatenate([rng.choice(np.where(y == c)[0], 4, replace=False) for c in range(3)])
    with torch.no_grad():
        logits = model(torch.from_numpy(x[picks])).numpy()
    PARITY.parent.mkdir(parents=True, exist_ok=True)
    PARITY.write_text(json.dumps({"inputs": [x[i].reshape(-1).round(6).tolist() for i in picks], "logits": logits.round(6).tolist()}) + "\n",
                      encoding="utf-8")
    print(f"→ {ASSET.relative_to(REPO)} ({ASSET.stat().st_size // 1024}KB) · {PARITY.relative_to(REPO)}")


def main(argv):
    torch.set_num_threads(8)
    if "--narrow" in argv:
        CONFIG["width"] = (8, 16, 16, 32)
    if "--no-warm" in argv:
        CONFIG["warm"] = False
    if "--rotate" in argv:
        CONFIG["rotate"] = True
    for a in argv:
        if a.startswith("--epochs="):
            CONFIG["epochs"] = int(a.split("=")[1])
    print("설정", CONFIG)
    manifest, x, y, rules, groups, variants, boards, shaky = load()
    if "--export-only" in argv:  # 교차 검증 없이 전부로 학습해 내보내기만(설정을 이미 골랐을 때)
        export(train(x, y, SEED), x, y)
        return
    print(f"조각 {len(y)}개 · 판 {len(manifest)}벌 · 묶음 {len(set(groups))}개 · 빈 점 {np.mean(y == 0):.0%} 흑 {np.mean(y == 1):.0%} 백 {np.mean(y == 2):.0%}")
    rows, cv_pred, cv_prob = cross_validate(manifest, x, y, rules, groups, variants, boards)
    text = report(rows)
    out = REPO / "engine-lab/vision/runs" / time.strftime("stone-net-%Y%m%d-%H%M%S")
    out.mkdir(parents=True, exist_ok=True)
    (out / "report.md").write_text(f"## 신경망 돌 판정 — 묶음 {FOLDS}겹 교차 검증(검증 묶음은 학습에 안 씀)\n\n{text}\n", encoding="utf-8")
    print(text)
    np.savez_compressed(out / "cv.npz", y=y, rules=rules, net=cv_pred, prob=cv_prob, variants=variants, boards=boards, shaky=shaky)
    print(f"→ {out.relative_to(REPO)}/report.md")
    if "--export" in argv:
        model = train(x, y, SEED)
        export(model, x, y)


if __name__ == "__main__":
    main(sys.argv[1:])
