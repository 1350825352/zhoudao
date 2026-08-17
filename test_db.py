import pymysql
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api', charset='utf8mb4')
cur = conn.cursor()
cur.execute('DESCRIBE live_stream')
for r in cur.fetchall():
    print(r)
print()
cur.execute('SELECT * FROM live_stream WHERE deleted=0 ORDER BY id DESC LIMIT 3')
for r in cur.fetchall():
    print(r)
cur.close()
conn.close()
