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
print("机场的实时状态")
print("=" * 70)

cursor.execute("""
    SELECT s.device_sn, s.wayline_uuid, s.current_task_id, s.update_time
    FROM device_realtime_status s
    WHERE s.device_sn IN ('4TADKCM001000Y', '8UUXN2A00A00M9', '7CTDM2100BFTR1', '9N9CMA70010178')
    ORDER BY s.update_time DESC
""")
rows = cursor.fetchall()

print(f"\n{'机场SN':<20} {'wayline_uuid':<40} {'current_task_id':<40} {'update_time':<25}")
print("-" * 125)
for row in rows:
    wu = row[1] if row[1] else ""
    ct = row[2] if row[2] else ""
    print(f"{row[0]:<20} {wu:<40} {ct:<40} {str(row[3]):<25}")

print("\n" + "=" * 70)
print("飞机1581F8HGX252F00A018X的实时状态")
print("=" * 70)

cursor.execute("""
    SELECT s.device_sn, s.wayline_uuid, s.current_task_id, s.update_time
    FROM device_realtime_status s
    WHERE s.device_sn = '1581F8HGX252F00A018X'
    ORDER BY s.update_time DESC
""")
rows = cursor.fetchall()

print(f"\n{'飞机SN':<20} {'wayline_uuid':<40} {'current_task_id':<40} {'update_time':<25}")
print("-" * 125)
for row in rows:
    wu = row[1] if row[1] else ""
    ct = row[2] if row[2] else ""
    print(f"{row[0]:<20} {wu:<40} {ct:<40} {str(row[3]):<25}")

conn.close()
