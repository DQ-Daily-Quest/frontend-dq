"""
에셋 팩의 assets_manifest.json에서 앱에 필요한 배치 정보만 골라 app/src/main/assets/decor/room16_catalog.json을 만든다.
(STEP 10-1, docs/step10-room-decor.md)

에셋 팩이 새로 오면 다시 실행한다.
    python tools/make_room_catalog.py <압축 푼 팩 폴더>/assets_manifest.json app/src/main/assets/decor/room16_catalog.json
그다음 ./gradlew testDebugUnitTest --tests "com.dailyquest.app.data.decor.*" 로 형식을 확인한다.

가구 이름·가격은 서버가 주므로 넣지 않는다. 값은 소수 4자리로 줄인다(1080px 기준 0.0001px 차이라 화면에 영향 없음).
"""
import json
import sys


def rnd(v):
    return round(float(v), 4)


def main(src, out):
    with open(src, encoding='utf-8') as f:
        m = json.load(f)
    r = m['room']
    catalog = {
        'schemaVersion': 1,
        'sourcePackId': m['packId'],
        'room': {
            'code': r['assetId'],
            'widthPx': r['sizePx'][0],
            'heightPx': r['sizePx'][1],
            'columns': r['columns'],
            'rows': r['rows'],
            'gridOriginPx': [rnd(x) for x in r['gridOriginPx']],
            'gridAxisXPx': [rnd(x) for x in r['gridAxisXPx']],
            'gridAxisYPx': [rnd(x) for x in r['gridAxisYPx']],
        },
        'furniture': [],
    }
    for f in m['furniture']:
        catalog['furniture'].append({
            'code': f['assetId'],
            'gridWidth': f['gridWidth'],
            'gridDepth': f['gridDepth'],
            'layer': f['placementLayer'],
            'blocksPlacement': f['blocksPlacement'],
            'sprites': [{
                'rotation': s['rotationDeg'],
                'widthPx': s['sizePx'][0],
                'heightPx': s['sizePx'][1],
                'anchorPx': [rnd(x) for x in s['anchorPx']],
                'gridAnchorOffsetPx': [rnd(x) for x in s['gridAnchorOffsetPx']],
                'gridWidth': s['gridWidth'],
                'gridDepth': s['gridDepth'],
            } for s in f['sprites']],
        })
    with open(out, 'w', encoding='utf-8', newline='\n') as o:
        json.dump(catalog, o, ensure_ascii=False, separators=(',', ':'))
        o.write('\n')
    print(f"방 1개 + 가구 {len(catalog['furniture'])}개 → {out}")


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('사용법: python tools/make_room_catalog.py <assets_manifest.json> <출력 json>')
    main(sys.argv[1], sys.argv[2])
