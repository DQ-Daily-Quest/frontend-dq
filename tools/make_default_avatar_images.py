"""
기본 캐릭터 그림(img_avatar_*) 20장을 블렌더 캡처 두 장에서 다시 만든다 (프로필 얼굴, 2026-11-24).

    python tools/make_default_avatar_images.py <캡처 폴더> <결과 폴더>

- <캡처 폴더>/4.png: 여자 캐릭터(숲속 아틀리에), <캡처 폴더>/5.png: 남자 캐릭터(오트밀 카페). 블렌더 화면 캡처 1917x1078, 배경 회색 (63,63,63).
- 얼굴 부분을 잘라 회색 배경을 지우고 크림색 바탕(252,248,237)에 올려 420px 큰 그림 2장을 만든다.
- 그 그림으로 흰 테두리(6px) 2장, 분홍 테두리(#ED94AB, 9px) 16장을 기존과 같은 크기로 만든다.
- 결과를 app/src/main/res/drawable-xxhdpi/에 덮어쓰면 된다. Pillow가 필요하다.
"""
import sys, os
from PIL import Image, ImageDraw
from collections import deque

IMG = sys.argv[1]          # 캡처 폴더
OUT = sys.argv[2]          # 결과 폴더
BG = (63, 63, 63)
CREAM = (252, 248, 237)

def cutout(src, box):
    im = Image.open(src).convert('RGB').crop(box)
    w, h = im.size
    px = im.load()
    def d(c, e): return max(abs(c[0]-e[0]), abs(c[1]-e[1]), abs(c[2]-e[2]))
    # 1) 가장자리에서 이어진 회색 배경 찾기
    bg = [[False]*w for _ in range(h)]
    q = deque()
    for x in range(w):
        for y in (0, h-1): q.append((x, y))
    for y in range(h):
        for x in (0, w-1): q.append((x, y))
    while q:
        x, y = q.popleft()
        if bg[y][x] or d(px[x, y], BG) > 8: continue
        bg[y][x] = True
        for nx, ny in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
            if 0 <= nx < w and 0 <= ny < h and not bg[ny][nx]: q.append((nx, ny))
    # 2) 배경과 맞닿은 테두리(2px) 찾기
    edge = [[False]*w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            if bg[y][x]: continue
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    nx, ny = x+dx, y+dy
                    if 0 <= nx < w and 0 <= ny < h and bg[ny][nx]: edge[y][x] = True
    out = Image.new('RGB', (w, h), CREAM)
    op = out.load()
    for y in range(h):
        for x in range(w):
            if bg[y][x]: continue
            c = px[x, y]
            if not edge[y][x]:
                op[x, y] = c; continue
            # 3) 테두리: 안쪽 색(F)과 배경(B)이 섞인 비율 a를 구해 크림 바탕으로 다시 섞는다
            acc = [0, 0, 0]; n = 0
            for dy in range(-4, 5):
                for dx in range(-4, 5):
                    nx, ny = x+dx, y+dy
                    if 0 <= nx < w and 0 <= ny < h and not bg[ny][nx] and not edge[ny][nx]:
                        p = px[nx, ny]; acc[0] += p[0]; acc[1] += p[1]; acc[2] += p[2]; n += 1
            if n == 0:
                F = c
            else:
                F = (acc[0]/n, acc[1]/n, acc[2]/n)
            fb = [F[i]-BG[i] for i in range(3)]; cb = [c[i]-BG[i] for i in range(3)]
            den = sum(v*v for v in fb)
            a = 1.0 if den < 1 else max(0.0, min(1.0, sum(fb[i]*cb[i] for i in range(3)) / den))
            op[x, y] = tuple(int(round(c[i] + (1-a)*(CREAM[i]-BG[i]))) if a > 0.02 else CREAM[i] for i in range(3))
    return out

boxes = {
    'girl': ('4.png', (585, 128, 995, 538)),
    'boy':  ('5.png', (580, 160, 990, 570)),
}
for name, (f, box) in boxes.items():
    im = cutout(os.path.join(IMG, f), box).resize((420, 420), Image.LANCZOS)
    im.save(os.path.join(OUT, f'img_avatar_{name}.png'))
    print('saved', name)

# ---- 테두리 동그라미 그림 (기존과 같은 크기·테두리) ----
PINK = (237, 148, 171, 255); WHITE = (255, 255, 255, 255)
GIRL = ['48_girl', 'story_1', 'story_2', 'story_34_1', 'story_34_2', 'story_34_3', 'story_34_4', 'story_4', 'story_48_2', 'story_lg_1', 'story_sm_1', 'story_sm_2']
BOY = ['48_boy', 'story_3', 'story_42_boy', 'story_48_1', 'story_lg_2', 'story_md_1']
SIZE = {'48_girl': 144, '48_boy': 144, 'story_1': 132, 'story_2': 132, 'story_3': 132, 'story_4': 132, 'story_34_1': 102, 'story_34_2': 102,
        'story_34_3': 102, 'story_34_4': 102, 'story_42_boy': 126, 'story_48_1': 144, 'story_48_2': 144, 'story_lg_1': 156, 'story_lg_2': 156,
        'story_md_1': 120, 'story_sm_1': 90, 'story_sm_2': 90}
K = 4  # 4배로 그린 뒤 줄여 동그라미 가장자리를 부드럽게
def ring(face, size, ring_px, color):
    S = size * K; r = ring_px * K
    out = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    disc = Image.new('L', (S, S), 0); ImageDraw.Draw(disc).ellipse((0, 0, S-1, S-1), fill=255)
    out.paste(Image.new('RGBA', (S, S), color), (0, 0), disc)
    inner = S - 2*r
    f = face.convert('RGBA').resize((inner, inner), Image.LANCZOS)
    m = Image.new('L', (inner, inner), 0); ImageDraw.Draw(m).ellipse((0, 0, inner-1, inner-1), fill=255)
    out.paste(f, (r, r), m)
    return out.resize((size, size), Image.LANCZOS)
faces = {'girl': Image.open(os.path.join(OUT, 'img_avatar_girl.png')), 'boy': Image.open(os.path.join(OUT, 'img_avatar_boy.png'))}
for names, who in ((GIRL, 'girl'), (BOY, 'boy')):
    for n in names:
        im = ring(faces[who], SIZE[n], 6 if n.startswith('48_') else 9, WHITE if n.startswith('48_') else PINK)
        im.save(os.path.join(OUT, f'img_avatar_{n}.png'), optimize=True)
print('ok', len(GIRL) + len(BOY))
