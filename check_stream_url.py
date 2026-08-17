import pymysql
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()

# 查最近的 live_stream 记录
cursor.execute("SELECT id, aircraft_sn, push_url, pull_url, status FROM live_stream WHERE deleted=0 ORDER BY id DESC LIMIT 3")
cols = [d[0] for d in cursor.description]
print('=== live_stream 最近记录 ===')
for row in cursor.fetchall():
    d = dict(zip(cols, row))
    print(f"  id={d['id']}, sn={d['aircraft_sn']}, status={d['status']}")
    print(f"    push_url: {d['push_url']}")
    print(f"    pull_url: {d['pull_url']}")
    print()

conn.close()
