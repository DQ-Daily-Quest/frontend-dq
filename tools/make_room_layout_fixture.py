"""
에셋 팩의 배치 예시(exampleLayout 54개)로 "정답 위치"를 계산해 단위 테스트와 디버그 "DQ 방 테스트" 화면 자료를 만든다. (STEP 10-2, 10-4)

계산은 에셋 팩이 미리보기 이미지(preview/room_example_1080.png)를 만들 때 쓴 방식(source/package.py 117~139줄)과 같다.
  - 그리는 순서: 러그(floor_decor) 먼저, 그다음 차지한 칸의 가운데 (cx + cy)가 작은 것(방 안쪽)부터
  - 바닥 = 방 기준점 + cx × X축 + cy × Y축
  - 기준점 = 바닥 + 보정값, 그림 왼쪽 위 = 기준점 − 그림 안 기준점
매니페스트의 원래 값(소수 끝까지)을 써서, 앱 JSON의 반올림(소수 4자리)이 결과를 틀어지게 하지 않는지도 함께 확인한다.

    python tools/make_room_layout_fixture.py <압축 푼 팩 폴더>/assets_manifest.json app/src/debug/assets/decor/example_layout.json
"""
import json
import sys


def main(src, out):
    with open(src, encoding='utf-8') as f:
        m = json.load(f)
    room = m['room']
    byid = {a['assetId']: a for a in m['furniture']}

    def sprite(p):
        return next(s for s in byid[p['assetId']]['sprites'] if s['rotationDeg'] == p['rotationDeg'])

    def center(p):
        s = sprite(p)
        return p['gridX'] + s['gridWidth'] / 2, p['gridY'] + s['gridDepth'] / 2

    def order(p):
        return (0 if byid[p['assetId']]['placementLayer'] == 'floor_decor' else 1, sum(center(p)))

    layout = m['exampleLayout']
    drawn = sorted(layout, key=order)
    items = []
    for p in layout:
        s = sprite(p)
        cx, cy = center(p)
        ground = [room['gridOriginPx'][k] + cx * room['gridAxisXPx'][k] + cy * room['gridAxisYPx'][k] for k in (0, 1)]
        anchor = [ground[k] + s['gridAnchorOffsetPx'][k] for k in (0, 1)]
        top_left = [anchor[k] - s['anchorPx'][k] for k in (0, 1)]
        items.append({
            'id': p['instanceId'],
            'code': p['assetId'],
            'gridX': p['gridX'],
            'gridY': p['gridY'],
            'rotation': p['rotationDeg'],
            'expectedLeftPx': round(top_left[0], 6),
            'expectedTopPx': round(top_left[1], 6),
            'expectedWidthPx': s['sizePx'][0],
            'expectedHeightPx': s['sizePx'][1],
        })
    fixture = {
        'source': m['packId'] + ' exampleLayout',
        'items': items,
        'expectedDrawOrder': [p['instanceId'] for p in drawn],
    }
    with open(out, 'w', encoding='utf-8', newline='\n') as o:
        json.dump(fixture, o, ensure_ascii=False, indent=1)
        o.write('\n')
    print(f'{len(items)}개 → {out}')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('사용법: python tools/make_room_layout_fixture.py <assets_manifest.json> <출력 json>')
    main(sys.argv[1], sys.argv[2])
