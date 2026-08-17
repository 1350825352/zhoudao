import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='root', database='sikong_cloud_api', charset='utf8mb4')
cur = conn.cursor(pymysql.cursors.DictCursor)

print("=== [1] device表: 关键SN的deviceType (飞机=3, 机场=1) ===")
cur.execute("SELECT sn, device_type, device_name, project_uuid FROM device WHERE deleted=0 AND sn IN ('1581F6Q8D23BM00ALJFB','1581F8HGX252F00A018X','8UUXN2A00A00M9','TEST-AIRCRAFT-FIX-001','7CTDM2100BFTR1')")
for r in cur.fetchall():
    print("  SN=%s deviceType=%s name=%s project=%s" % (r['sn'], r['device_type'], r.get('device_name'), (r.get('project_uuid') or '')[:16]))

print("\n=== [2] live_stream 活跃码流(删除状态=0, status=0/1) TOP25 ===")
cur.execute("SELECT id, aircraft_sn, gateway_sn, status, zhimou_status, zhimou_task_id, zhimou_app_id, push_url FROM live_stream WHERE deleted=0 AND status IN (0,1) ORDER BY id DESC LIMIT 25")
rows = cur.fetchall()
print("共找到 %d 条活跃码流:" % len(rows))
for r in rows:
    aid = r.get('aircraft_sn') or ''
    gw = r.get('gateway_sn') or ''
    pid = (r.get('zhimou_task_id') or '')[:18]
    url = (r.get('push_url') or '')[:70]
    print("  id=%-4s aircraft=%-22s gw=%-20s status=%s zhStatus=%s taskId=%s appId=%s push=%s" % (
        r['id'], aid[:22], gw[:20], r['status'], r.get('zhimou_status'), pid, (r.get('zhimou_app_id') or '')[:12], url))

print("\n=== [3] zhimou_task 最新10条记录 ===")
cur.execute("SELECT id, live_stream_id, aircraft_sn, zhimou_task_id, status, status_message, input_url, last_error, create_time FROM zhimou_task ORDER BY id DESC LIMIT 10")
for r in cur.fetchall():
    zid = (r.get('zhimou_task_id') or '')[:18]
    msg = (r.get('status_message') or r.get('last_error') or '')[:40]
    url = (r.get('input_url') or '')[:55]
    print("  ztId=%-3s liveId=%-4s aircraft=%-22s zTaskId=%s status=%s err=%s url=%s" % (
        r['id'], r.get('live_stream_id'), (r.get('aircraft_sn') or '')[:22], zid, r['status'], msg, url))

conn.close()
print("\nDone.")
