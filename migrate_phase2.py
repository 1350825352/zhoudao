import pymysql
import redis

conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456',
                       database='sikong_cloud_api', charset='utf8mb4', autocommit=True)
cur = conn.cursor()

try:
    cur.execute("ALTER TABLE zhimou_image_detection ADD COLUMN annotated_local_path VARCHAR(255) DEFAULT NULL COMMENT '知眸标注图本地相对路径'")
    print('1. annotated_local_path 已添加')
except Exception as e:
    print('1. annotated_local_path: ' + str(e)[:80])

try:
    cur.execute("ALTER TABLE zhimou_image_detection ADD COLUMN work_order_id BIGINT DEFAULT NULL COMMENT '工单ID(NULL=未处理)'")
    print('2. work_order_id 已添加')
except Exception as e:
    print('2. work_order_id: ' + str(e)[:80])

cur.execute('TRUNCATE TABLE zhimou_image_detection')
print('3. zhimou_image_detection 已清空')
cur.execute('TRUNCATE TABLE self_model_image_detection')
print('4. self_model_image_detection 已清空')

r = redis.Redis(host='localhost', port=6379, db=0)
keys = list(r.scan_iter('minio:flight:*')) + list(r.scan_iter('zhimou:img:meta:*'))
for k in keys:
    r.delete(k)
print('5. Redis 清理 %d 个 key' % len(keys))

cur.close()
conn.close()
print('DB 变更全部完成')
