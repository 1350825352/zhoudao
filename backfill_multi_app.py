# -*- coding: utf-8 -*-
"""回填本次任务的双结果记录: hit/app 累计拼接 + 补下载副标注图 + annotated_local_path 多路径"""
import pymysql, json, os, urllib.request, ssl

BASE = 'https://openapi.zhimouyun.com'
P2 = r'h:\traeproject\zhoudao\zhimou-image-detect'
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE

c = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', db='sikong_cloud_api', charset='utf8mb4', autocommit=False)
cur = c.cursor()
cur.execute("SELECT id, app_id, app_name, hit_count, targets_json, annotated_local_path FROM zhimou_image_detection WHERE zhimou_task_id='1587858651477310800' AND targets_json IS NOT NULL AND targets_json != ''")
rows = cur.fetchall()
fixed = 0
for rid, old_ids, old_names, old_hit, tj, main_rel in rows:
    apps = json.loads(tj)
    if len(apps) <= 1:
        continue
    # 1) 回填累计值
    total_hit = sum(len(a.get('objects') or []) for a in apps)
    ids = list(dict.fromkeys(a['app_id'] for a in apps if a.get('app_id')))
    names = list(dict.fromkeys(a['app_name'] for a in apps if a.get('app_name')))
    # 2) 副标注图补下载(主图=现有DB路径, 保持不变)
    other_rels = []
    if main_rel:
        anno_dir = os.path.dirname(os.path.join(P2, main_rel.replace('/', os.sep)))
        base_name = os.path.splitext(os.path.basename(main_rel))[0]
        for a in apps:
            img, a_id = a.get('image'), a.get('app_id')
            if not img or not a_id or not a.get('objects'):
                continue
            rel = os.path.dirname(main_rel).replace(os.sep, '/') + '/' + base_name + '_' + a_id + '.jpg'
            disk = os.path.join(P2, rel.replace('/', os.sep))
            if os.path.exists(disk):
                other_rels.append(rel); continue
            url = img if img.lower().startswith('http') else (BASE + img if img.startswith('/') else BASE + '/' + img)
            try:
                req = urllib.request.Request(url, headers={'User-Agent': 'sikong'})
                data = urllib.request.urlopen(req, timeout=30, context=ctx).read()
                if data:
                    with open(disk, 'wb') as f: f.write(data)
                    other_rels.append(rel)
                    print(f"id={rid} 副图下载成功 {a_id} size={len(data)}")
                else:
                    print(f"id={rid} 副图下载空 {a_id}")
            except Exception as ex:
                print(f"id={rid} 副图下载失败 {a_id}: {ex}")
        new_anno = ','.join([main_rel] + other_rels)
    else:
        new_anno = None
    cur.execute("UPDATE zhimou_image_detection SET hit_count=%s, app_id=%s, app_name=%s, annotated_local_path=%s WHERE id=%s",
                (total_hit, ','.join(ids), ','.join(names), new_anno, rid))
    fixed += 1
    print(f"id={rid} 回填: hit {old_hit}->{total_hit}, app {old_names} -> {','.join(names)}, 副图{len(other_rels)}张")
c.commit()
print(f"\n回填完成: {fixed} 条记录")
c.close()
