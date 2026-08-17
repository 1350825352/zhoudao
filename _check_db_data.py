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
print("1. 检查 zhimou_detection_result 表 wayline_uuid 字段情况")
print("=" * 60)
cursor.execute("SELECT wayline_uuid, COUNT(*) as cnt FROM zhimou_detection_result WHERE deleted = 0 GROUP BY wayline_uuid")
results = cursor.fetchall()
for row in results:
    print(f"  wayline_uuid='{row[0]}': {row[1]} 条")

print("\n" + "=" * 60)
print("2. 检查 flight_task 表数据情况")
print("=" * 60)
cursor.execute("SELECT COUNT(*) FROM flight_task WHERE deleted = 0")
count = cursor.fetchone()[0]
print(f"  flight_task 表总记录数: {count}")

if count > 0:
    cursor.execute("SELECT id, aircraft_sn, wayline_uuid, wayline_name, task_status, create_time FROM flight_task WHERE deleted = 0 ORDER BY id DESC LIMIT 10")
    print("\n  最新10条记录:")
    print("  " + "-" * 120)
    print("  ID | aircraft_sn | wayline_uuid | wayline_name | task_status | create_time")
    print("  " + "-" * 120)
    for row in cursor.fetchall():
        print(f"  {row[0]:4d} | {row[1]:20s} | {str(row[2])[:30]:30s} | {str(row[3])[:20]:20s} | {row[4]:12d} | {str(row[5])}")

    cursor.execute("SELECT wayline_uuid, COUNT(*) as cnt FROM flight_task WHERE deleted = 0 GROUP BY wayline_uuid")
    print("\n  wayline_uuid 分布:")
    for row in cursor.fetchall():
        print(f"    '{row[0]}': {row[1]} 条")

print("\n" + "=" * 60)
print("3. 检查 device_realtime_status 表 current_task_id 字段情况")
print("=" * 60)
cursor.execute("SELECT COUNT(*) FROM device_realtime_status WHERE current_task_id IS NOT NULL AND current_task_id != ''")
count = cursor.fetchone()[0]
print(f"  current_task_id 不为空的记录数: {count}")

cursor.execute("SELECT DISTINCT current_task_id FROM device_realtime_status WHERE current_task_id IS NOT NULL AND current_task_id != '' LIMIT 10")
print("\n  前10个不同的 current_task_id:")
for row in cursor.fetchall():
    print(f"    {row[0]}")

print("\n" + "=" * 60)
print("4. 检查 zhimou_task 表 wayline_uuid 字段情况")
print("=" * 60)
cursor.execute("SELECT COUNT(*) FROM zhimou_task WHERE deleted = 0")
count = cursor.fetchone()[0]
print(f"  zhimou_task 表总记录数: {count}")

cursor.execute("SELECT wayline_uuid, COUNT(*) as cnt FROM zhimou_task WHERE deleted = 0 GROUP BY wayline_uuid")
print("\n  wayline_uuid 分布:")
for row in cursor.fetchall():
    print(f"    wayline_uuid='{row[0]}': {row[1]} 条")

cursor.execute("SELECT id, zhimou_task_id, aircraft_sn, wayline_uuid, status, create_time FROM zhimou_task WHERE deleted = 0 ORDER BY id DESC LIMIT 10")
print("\n  最新10条记录:")
print("  " + "-" * 150)
print("  ID | zhimou_task_id | aircraft_sn | wayline_uuid | status | create_time")
print("  " + "-" * 150)
for row in cursor.fetchall():
    print(f"  {row[0]:4d} | {str(row[1])[:15]:15s} | {row[2]:20s} | {str(row[3])[:36]:36s} | {row[4]:6d} | {str(row[5])}")

conn.close()
