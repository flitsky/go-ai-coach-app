"""바둑판 사진 인식 **평가 갤러리**(백로그 #263) — 사람이 눈으로 보고 맞았는지 고르는 로컬 HTML.

사진마다: 원본 위에 앱이 찾은 격자 · 앱이 읽은 판(「확인 필요」 교점은 주황 네모) · 출처와 라이선스 · 평가(맞음 / 몇 점 틀림 /
판 위치 틀림 / 판 못 찾음 / 판이 아님)와 메모. 평가는 브라우저에 저장되고 「평가 복사」로 한 번에 옮길 수 있다.

재료: `app-android/build/reports/board-photos/gallery-<폴더>.json`(앱과 같은 순서로 인식한 결과 — `BoardPhotoGalleryTest`).
⚠️ 사진은 남의 저작물(CC·퍼블릭 도메인)이다 — 이 HTML은 이 맥에서만 본다(폴더째 `.gitignore`).

    engine-lab/vision/.venv/bin/python -I engine-lab/vision/render_gallery.py test-fixtures/board-photos-web
"""

import base64
import html
import json
import sys
from pathlib import Path

import cv2
import numpy as np

REPO = Path(__file__).resolve().parents[2]
PHOTO_MAX = 560
BOARD_PX = 380

KIND = {"photo": "실물 사진", "flickr": "실물 사진", "diagram": "기보 그림", "book": "옛 책 인쇄", "own": "사용자 사진"}


def b64(img, ext):
    ok, buf = cv2.imencode(ext, img, [cv2.IMWRITE_JPEG_QUALITY, 82] if ext == ".jpg" else [])
    return base64.b64encode(buf.tobytes()).decode() if ok else ""


def overlay(img, r):
    h, w = img.shape[:2]
    s = min(1.0, PHOTO_MAX / max(h, w))
    out = cv2.resize(img, (round(w * s), round(h * s)), interpolation=cv2.INTER_AREA) if s < 1 else img.copy()
    if not r.get("located"):
        return out
    n = r["boardSize"]
    c = np.float64(r["corners"]) * s
    grid = np.float64([[0, 0], [n - 1, 0], [n - 1, n - 1], [0, n - 1]])
    hm = cv2.getPerspectiveTransform(np.float32(grid), np.float32(c))
    color = (60, 200, 60) if r.get("confident") else (0, 160, 255)
    thick = max(1, round(max(out.shape[:2]) / 400))
    for k in range(n):
        for a, b in (((k, 0), (k, n - 1)), ((0, k), (n - 1, k))):
            pts = cv2.perspectiveTransform(np.float64([[a, b]]), hm).reshape(-1, 2)
            cv2.line(out, tuple(map(int, pts[0])), tuple(map(int, pts[1])), color, thick, cv2.LINE_AA)
    return out


def board(r):
    n = r["boardSize"]
    pad = BOARD_PX // (n + 1)
    step = (BOARD_PX - 2 * pad) / (n - 1)
    img = np.full((BOARD_PX, BOARD_PX, 3), (95, 175, 222), np.uint8)  # 나무색(BGR)
    for k in range(n):
        p = round(pad + k * step)
        cv2.line(img, (pad, p), (BOARD_PX - pad, p), (40, 40, 40), 1, cv2.LINE_AA)
        cv2.line(img, (p, pad), (p, BOARD_PX - pad), (40, 40, 40), 1, cv2.LINE_AA)
    stars = {19: [3, 9, 15], 13: [3, 6, 9], 9: [2, 4, 6]}.get(n, [])
    for a in stars:
        for b in stars:
            cv2.circle(img, (round(pad + a * step), round(pad + b * step)), 3, (40, 40, 40), -1, cv2.LINE_AA)
    rad = max(3, round(step * 0.47))
    for row, line in enumerate(r["grid"]):
        for col, ch in enumerate(line):
            if ch == ".":
                continue
            ctr = (round(pad + col * step), round(pad + row * step))
            if ch == "B":
                cv2.circle(img, ctr, rad, (25, 25, 25), -1, cv2.LINE_AA)
            else:
                cv2.circle(img, ctr, rad, (245, 245, 245), -1, cv2.LINE_AA)
                cv2.circle(img, ctr, rad, (90, 90, 90), 1, cv2.LINE_AA)
    half = round(step / 2)
    for row, col in r.get("uncertain", []):
        x, y = round(pad + col * step), round(pad + row * step)
        cv2.rectangle(img, (x - half, y - half), (x + half, y + half), (0, 143, 255), 2)
    return img


def sources(folder):
    out = {}
    for f in ("sources.json", "sources-flickr.json"):
        p = folder / f
        if p.is_file():
            for row in json.loads(p.read_text(encoding="utf-8")):
                out[row["file"]] = row
    return out


CSS = """
:root{--bg:#f6f3ee;--card:#fff;--ink:#222;--muted:#666;--line:#ddd;--ok:#2e7d32;--warn:#ef6c00}
@media (prefers-color-scheme:dark){:root{--bg:#161616;--card:#222;--ink:#eee;--muted:#aaa;--line:#333}}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 -apple-system,system-ui,sans-serif}
header{position:sticky;top:0;background:var(--bg);padding:12px 16px;border-bottom:1px solid var(--line);z-index:1}
h1{font-size:18px;margin:0 0 4px}
.sum{color:var(--muted);font-size:13px}
.item{background:var(--card);margin:16px;border:1px solid var(--line);border-radius:10px;padding:12px}
.row{display:flex;flex-wrap:wrap;gap:12px;align-items:flex-start}
.row img{max-width:100%;border-radius:6px}
.meta{font-size:13px;color:var(--muted)}
.meta b{color:var(--ink)}
.tag{display:inline-block;padding:1px 8px;border-radius:10px;font-size:12px;margin-right:4px;border:1px solid var(--line)}
.tag.ok{color:var(--ok);border-color:var(--ok)}.tag.warn{color:var(--warn);border-color:var(--warn)}
.eval label{margin-right:12px;white-space:nowrap}
.eval textarea{width:100%;min-height:38px;margin-top:6px;font:inherit;background:var(--bg);color:var(--ink);border:1px solid var(--line);border-radius:6px}
button{font:inherit;padding:6px 12px;border-radius:6px;border:1px solid var(--line);background:var(--card);color:var(--ink);cursor:pointer}
"""

JS = """
const KEY='go-board-gallery-v1';
const load=()=>{try{return JSON.parse(localStorage.getItem(KEY)||'{}')}catch(e){return {}}};
const save=s=>{try{localStorage.setItem(KEY,JSON.stringify(s))}catch(e){}};
function refresh(){const s=load();let n=0;document.querySelectorAll('.item').forEach(el=>{const k=el.dataset.key,v=s[k]||{};
 el.querySelectorAll('input[type=radio]').forEach(r=>r.checked=(r.value===v.verdict));el.querySelector('textarea').value=v.memo||'';if(v.verdict)n++;});
 document.getElementById('done').textContent=n;}
document.addEventListener('change',e=>{const el=e.target.closest('.item');if(!el)return;const s=load(),k=el.dataset.key;s[k]=s[k]||{};
 if(e.target.type==='radio')s[k].verdict=e.target.value;else s[k].memo=e.target.value;save(s);refresh();});
document.addEventListener('input',e=>{if(e.target.tagName==='TEXTAREA'){const el=e.target.closest('.item'),s=load(),k=el.dataset.key;s[k]=s[k]||{};s[k].memo=e.target.value;save(s);}});
function copyAll(){const s=load();const lines=[...document.querySelectorAll('.item')].map(el=>{const k=el.dataset.key,v=s[k]||{};
 return [k,el.dataset.file,v.verdict||'-',(v.memo||'').replace(/\\s+/g,' ')].join('\\t')});
 navigator.clipboard.writeText('사진\\t파일\\t평가\\t메모\\n'+lines.join('\\n')).then(()=>alert('평가를 복사했어요 — 대화창에 붙여 넣어 주세요'));}
window.addEventListener('load',refresh);
"""

VERDICTS = [("ok", "맞음"), ("few", "몇 점 틀림"), ("grid", "판 위치·크기 틀림"), ("miss", "판 못 찾음"), ("nb", "판이 아님/평가 제외")]


def main(argv):
    folder = (REPO / argv[0]) if not Path(argv[0]).is_absolute() else Path(argv[0])
    results = json.loads((REPO / "app-android/build/reports/board-photos" / f"gallery-{folder.name}.json").read_text(encoding="utf-8"))
    src = sources(folder)
    items = []
    found = sum(1 for r in results if r.get("located"))
    confident = sum(1 for r in results if r.get("confident"))
    for r in results:
        img = cv2.imread(str(folder / r["file"]), cv2.IMREAD_COLOR)
        if img is None:
            continue
        s = src.get(r["file"], {})
        key = r["file"].split("-")[0]
        kind = KIND.get(s.get("kind", ""), s.get("kind", ""))
        tags = []
        if r.get("located"):
            tags.append(f'<span class="tag">{r["boardSize"]}줄 · 흑 {r["black"]} 백 {r["white"]}</span>')
            tags.append('<span class="tag ok">확실 → 핀 화면 건너뜀</span>' if r.get("confident") else '<span class="tag warn">불확실 → 핀 화면</span>')
            if r.get("uncertain"):
                tags.append(f'<span class="tag warn">확인 필요 {len(r["uncertain"])}곳</span>')
        else:
            tags.append('<span class="tag warn">판을 못 찾음 → 핀을 직접</span>')
        if r.get("perspective"):
            tags.append('<span class="tag">원근 폄(OpenCV)</span>')
        tags.append(f'<span class="tag">{r["ms"]}ms(맥 JVM)</span>')
        board_html = f'<img src="data:image/png;base64,{b64(board(r), ".png")}" width="{BOARD_PX}">' if r.get("located") else ""
        title = html.escape(s.get("title", r["file"]))
        page = html.escape(s.get("page") or "")
        credit = f'{html.escape(s.get("license", ""))} · {html.escape((s.get("artist") or "")[:80])}'
        radios = "".join(f'<label><input type="radio" name="v-{key}" value="{v}"> {t}</label>' for v, t in VERDICTS)
        items.append(f"""
<section class="item" data-key="{key}" data-file="{html.escape(r['file'])}">
 <div class="meta"><b>{key}</b> · {kind} · <a href="{page}" target="_blank" rel="noopener">{title}</a> · {credit}</div>
 <div style="margin:6px 0">{''.join(tags)}</div>
 <div class="row"><img src="data:image/jpeg;base64,{b64(overlay(img, r), '.jpg')}">{board_html}</div>
 <div class="eval" style="margin-top:8px">{radios}<textarea placeholder="메모 (예: 우하귀 백 2점 놓침)"></textarea></div>
</section>""")
    doc = f"""<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>바둑판 인식 평가</title><style>{CSS}</style><script>{JS}</script></head><body>
<header><h1>바둑판 인식 평가 — {html.escape(folder.name)}</h1>
<div class="sum">{len(items)}장 · 판을 찾음 {found} · 확실(핀 건너뜀) {confident} · 평가함 <span id="done">0</span> ·
초록 격자 = 확실, 주황 격자 = 불확실, 주황 네모 = 확인 필요 · <button onclick="copyAll()">평가 복사</button></div></header>
{''.join(items)}
</body></html>"""
    out = folder / "gallery.html"
    out.write_text(doc, encoding="utf-8")
    print(f"{len(items)}장 → {out} ({out.stat().st_size // 1024}KB)")


if __name__ == "__main__":
    main(sys.argv[1:])
