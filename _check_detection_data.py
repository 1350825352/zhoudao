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

print("=" * 70)
print("检查 zhimou_detection_result 表结构")
print("=" * 70)

cursor.execute("DESCRIBE zhimou_detection_result")
print(f"{'字段名':<30} {'类型':<20} {'是否为空'}")
print("-" * 60)
for row in cursor.fetchall():
    print(f"{row[0]:<30} {row[1]:<20} {row[2]}")

print("\n" + "=" * 70)
print("检查 zhimou_detection_result 表")
print("=" * 70)

cursor.execute("SELECT id, aircraft_sn, event_time, wayline_uuid FROM zhimou_detection_result WHERE deleted = 0 ORDER BY event_time DESC LIMIT 20")
rows = cursor.fetchall()

print(f"\n最近20条记录:")
print(f"{'ID':<6} {'aircraft_sn':<20} {'event_time':<25} {'wayline_uuid':<25}")
print("-" * 80)
for row in rows:
    print(f"{row[0]:<6} {row[1]:<20} {str(row[2]):<25} {str(row[3]):<25}")

print("\n" + "=" * 70)
print("检查 device_realtime_status 表的字段")
print("=" * 70)

cursor.execute("DESCRIBE device_realtime_status")
print(f"{'字段名':<30} {'类型':<20} {'是否为空'}")
print("-" * 60)
for row in cursor.fetchall():
    print(f"{row[0]:<30} {row[1]:<20} {row[2]}")

print("\n" + "=" * 70)
print("检查 device_realtime_status 表最近10条记录")
print("=" * 70)

cursor.execute("SELECT id, device_sn, update_time, current_task_id, wayline_uuid FROM device_realtime_status ORDER BY update_time DESC LIMIT 10")
rows = cursor.fetchall()

print(f"\n最近10条记录:")
print(f"{'ID':<6} {'device_sn':<20} {'update_time':<25} {'current_task_id':<30} {'wayline_uuid':<30}")
print("-" * 110)
for row in rows:
    print(f"{row[0]:<6} {row[1]:<20} {str(row[2]):<25} {str(row[3]):<30} {str(row[4]):<30}")

print("\n" + "=" * 70)
print("检查 flight_task 表")
print("=" * 70)

cursor.execute("SELECT COUNT(*) FROM flight_task WHERE deleted = 0")
count = cursor.fetchone()[0]
print(f"flight_task 表有效记录数: {count}")

if count > 0:
    cursor.execute("SELECT id, aircraft_sn, task_uuid, wayline_uuid, create_time FROM flight_task WHERE deleted = 0 ORDER BY create_time DESC LIMIT 10")
    rows = cursor.fetchall()
    print(f"\n最近10条记录:")
    print(f"{'ID':<6} {'aircraft_sn':<20} {'task_uuid':<36} {'wayline_uuid':<36} {'create_time':<25}")
    print("-" * 130)
    for row in rows:
        print(f"{row[0]:<6} {row[1]:<20} {str(row[2]):<36} {str(row[3]):<36} {str(row[4]):<25}")

conn.close()
