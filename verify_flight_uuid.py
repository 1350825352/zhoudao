# -*- coding: utf-8 -*-
"""v4: 已确认 FLIGHT_UUID=task_uuid, 现在查 detail/media 拿 drone_sn 和文件名对照"""
import urllib.request, urllib.error, urllib.parse, json, time

DJI_OPENAPI_BASE = 'https://es-flight-api-cn.djigate.com'
USER_TOKEN = 'eyJhbGciOiJIUzUxMiIsImNyaXQiOlsidHlwIiwiYWxnIiwia2lkIl0sImtpZCI6IjhiZmRiZmRkLWM4OGYtNGE5Yi04NzI3LWQ0ZGYzYWE5OTJlOSIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50IjoiIiwiZXhwIjoyMDY3NjcyNDA3LCJuYmYiOjE3NTIxMzk2MDcsIm9yZ2FuaXphdGlvbl91dWlkIjoiODU2NmM3ZGUtMDVhYy00MWVlLWIyNjAtMTYxNDg0OGVkMDM3IiwicHJvamVjdF91dWlkIjoiIiwic3ViIjoiZmgyIiwidXNlcl9pZCI6IjEyNTcyMTIyODY5ODM3NTc4MjQifQ.brYj6iCfUyLl8O5XAmjKQF7yI1IAm-ehjKGRm9wiesK87ZmYS3s3KmPM-8WyhNaOVhEYORkdqkIC9yRt87UbUw'
ORG_UUID     = '8566c7de-05ac-41ee-b260-1614848ed037'
PROJECT_UUID = '826ccf49-8430-4037-a505-cd43b868d4d6'

TASK_UUID = 'dbc84414-a612-4637-9964-ea4e8c3ca2a6'  # = MinIO FLIGHT_UUID!

def api(path, q=None):
    u = DJI_OPENAPI_BASE + path
    if q: u += '?' + urllib.parse.urlencode(q, doseq=True)
    req = urllib.request.Request(u, headers={
        'X-User-Token': USER_TOKEN, 'X-Organization-Uuid': ORG_UUID,
        'X-Project-Uuid': PROJECT_UUID, 'X-Language': 'zh',
        'X-Request-Id': 'v4-'+str(int(time.time()*1000)),
    })
    try: return json.loads(urllib.request.urlopen(req, timeout=45).read())
    except urllib.error.HTTPError as e:
        return {'__E': 'HTTP'+str(e.code), '__B': e.read().decode('utf-8','replace')}
    except Exception as e: return {'__E':'EXC','__B':str(e)}

print('='*70)
print('阶段3 - MinIO路径与DJI司空2 API 完整关联验证')
print(f'  MinIO FLIGHT_UUID (media目录名) = {TASK_UUID}')
print('='*70)

print('\n【A】/openapi/v2.0/flight-task/detail  拿 drone_sn')
for ws in [PROJECT_UUID, ORG_UUID]:
    # 文档: workspace_id=project_uuid (字符串必填) + task_uuid
    r = api('/openapi/v2.0/flight-task/detail', {'workspace_id': ws, 'task_uuid': TASK_UUID})
    if r.get('code') == 0:
        d = r['data']
        print(f'  workspace={ws[:12]}... ✅')
        pieces = d.get('flight_pieces',[]) or []
        print(f'  top-level keys: {list(d.keys())}')
        print(f'  folder_id      : {d.get("folder_id")}')
        print(f'  flight_task_id : {d.get("flight_task_id")}')
        print(f'  flight_pieces数: {len(pieces)}')
        for i,p in enumerate(pieces):
            print(f'\n  [flight_piece #{i}]')
            for k in ['flight_piece_id','drone_sn','task_name','status',
                      'take_off_airport_sn','completed_at','begin_at',
                      'home_point_sn','home_point_name','happen_time','track_id']:
                if k in p: print(f'    {k:<28}= {p.get(k)}')
            di = p.get('drone_info')
            if isinstance(di, dict):
                for k,v in di.items():
                    print(f'    drone_info.{k:<22}= {v}')
            ti = p.get('track_information')
            if isinstance(ti, dict):
                ddi = ti.get('drone_info')
                if isinstance(ddi, dict):
                    for k,v in ddi.items():
                        print(f'    track_info.drone_info.{k:<16}= {v}')
        break
    else:
        print(f'  workspace={ws[:12]}... 失败 code={r.get("code")} msg={r.get("message")} {r.get("__B","")[:200]}')

print('\n【B】/openapi/v2.0/flight-task/{uuid}/media  文件名对照 MinIO 中 DJI_..._V.jpeg')
r = api(f'/openapi/v2.0/flight-task/{TASK_UUID}/media')
if r.get('code')==0:
    data = r.get('data') or {}
    lst = data.get('list',[]) if isinstance(data,dict) else (data or [])
    print(f'  media 总数: {len(lst)}')
    for i,m in enumerate(lst):
        name = m.get('name','')
        is_img = 'jpeg' in name.lower() or 'jpg' in name.lower()
        star = '⭐' if '_V' in name else ('📷' if is_img else '📁')
        size = m.get('size','')
        ftype = m.get('file_type','')
        orig = m.get('original_url','')
        shot = m.get('shoot_time','') or m.get('shooting_time','')
        print(f'  {star} [{i+1:2}] {name:<60} type={ftype:<6} size={size} shot={shot}')
        if orig and is_img:
            print(f'       original_url: {orig[:90]}...')
    print(f'\n  MinIO样例文件名对照:')
    sample_minio = ['DJI_20260826130711_0001_V.jpeg','DJI_20260826130717_0002_V.jpeg','DJI_20260826130723_0003_V.jpeg']
    names = [(m.get('name') or '') for m in lst]
    for s in sample_minio:
        hit = any(s[:20] in n for n in names) or any(s in n for n in names)
        # 精确匹配
        exact = s in names
        print(f'    {s}: 精确匹配={exact}  前缀包含={hit}  实际API中同格式数量={sum(1 for n in names if "DJI_" in n and "_V" in n)}')
else:
    print(f'  失败 code={r.get("code")} msg={r.get("message")}')

print('\n【C】/openapi/v2.0/flight-task/{uuid}/track  确认 drone_sn + track里的信息')
r = api(f'/openapi/v2.0/flight-task/{TASK_UUID}/track')
if r.get('code')==0:
    d = r['data']
    print(f'  drone_sn       : {d.get("drone_sn")}')
    print(f'  sn             : {d.get("sn")}')
    if d.get('wayline_uuid'): print(f'  wayline_uuid   : {d.get("wayline_uuid")}')
    if d.get('workspace_id'): print(f'  workspace_id   : {d.get("workspace_id")}')
    other = {k:v for k,v in d.items() if k not in ('drone_sn','sn','wayline_uuid','workspace_id','points') and not isinstance(v,(list,dict))}
    print(f'  other scalar keys: {other}')
else:
    print(f'  失败 code={r.get("code")} msg={r.get("message")} {r.get("__B","")[:200]}')

print('\n【D】完整关联表 (MinIO 路径 ↔ DJI API 数据)')
print('='*70)
print(f'  MinIO 完整路径:')
print(f'    flydata/fh_sync/<ORG_UUID>/<PROJECT_UUID>/media/<TASK_UUID>/DJI_..._V.jpeg')
print(f'      ↓               ↓              ↓               ↓')
print(f'  DJI API 含义: organization      project          flight_task_id (task uuid)')
print(f'')
print(f'  获取 drone_sn (飞机SN) 方式:')
print(f'    GET /openapi/v2.0/flight-task/detail')
print(f'        ?workspace_id={PROJECT_UUID[:8]}...(PROJECT_UUID)')
print(f'        &task_uuid   ={TASK_UUID[:8]}...(=MinIO FLIGHT_UUID)')
print(f'    → 返回 flight_pieces[].drone_sn  或  /track 返回 drone_sn')
print(f'')
print(f'  获取文件名 / media 对照:')
print(f'    GET /openapi/v2.0/flight-task/{TASK_UUID[:8]}.../media')
print(f'    → 对比 MinIO 中 DJI_XXXXXX_XXXX_V.jpeg  名称完全一致可做校验')
print('='*70)
