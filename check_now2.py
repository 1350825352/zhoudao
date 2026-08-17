import pymysql
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api', charset='utf8mb4')
cur = conn.cursor()
# 最新的zhimou_task
cur.execute("SELECT id, live_stream_id, zhimou_task_id, status, create_time FROM zhimou_task WHERE create_time >= '2026-08-14 22:06:00' ORDER BY id DESC LIMIT 10")
rows = cur.fetchall()
print('22:06后的zhimou_task: %d条' % len(rows))
for r in rows:
    print('  id=%s live=%s task_id=%s status=%s time=%s' % r)
# 最新live_stream
cur.execute("SELECT id, status, zhimou_status, zhimou_task_id, zhimou_last_error FROM live_stream WHERE aircraft_sn='1581F8HHX25CL00A06BL' AND deleted=0 ORDER BY id DESC LIMIT 3")
print()
print('live_stream:')
for r in cur.fetchall():
    print('  id=%s status=%s zm_st=%s zm_id=%s err=%s' % (r[0],r[1],r[2],r[3],(r[4][:60] if r[4] else '')))
conn.close()
