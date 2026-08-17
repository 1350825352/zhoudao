import pymysql

db_config = {
    'host': '192.168.2.200',
    'port': 3306,
    'user': 'szy02',
    'password': '123456',
    'database': 'sikong_cloud_api',
    'charset': 'utf8mb4'
}

conn = pymysql.connect(**db_config)
cursor = conn.cursor()

print("=" * 60)
print("检查 device_realtime_status 表中是否有 current_task_id")
print("=" * 60)
cursor.execute("SELECT COUNT(*) FROM device_realtime_status")
total = cursor.fetchone()[0]
print(f"总记录数: {total}")

cursor.execute("SELECT COUNT(*) FROM device_realtime_status WHERE current_task_id IS NOT NULL AND current_task_id != ''")
has_task = cursor.fetchone()[0]
print(f"有current_task_id的记录数: {has_task}")

if has_task > 0:
    cursor.execute("SELECT device_sn, current_task_id, update_time FROM device_realtime_status WHERE current_task_id IS NOT NULL AND current_task_id != '' ORDER BY update_time DESC LIMIT 10")
    print("\n最近10条有任务ID的记录:")
    for row in cursor.fetchall():
        print(f"  device_sn={row[0]}, current_task_id={row[1]}, update_time={row[2]}")

print("\n" + "=" * 60)
print("检查 zhimou_detection_result 表的 aircraft_sn 分布")
print("=" * 60)
cursor.execute("SELECT aircraft_sn, COUNT(*) as cnt FROM zhimou_detection_result WHERE deleted = 0 GROUP BY aircraft_sn")
for row in cursor.fetchall():
    print(f"  aircraft_sn='{row[0]}': {row[1]} 条")

conn.close()
