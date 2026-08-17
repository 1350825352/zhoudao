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
print("剩余手动飞行记录")
print("=" * 70)

cursor.execute("SELECT id, aircraft_sn, event_time FROM zhimou_detection_result WHERE deleted = 0 AND wayline_uuid = '手动飞行' ORDER BY event_time DESC")
rows = cursor.fetchall()

print(f"\n共 {len(rows)} 条手动飞行记录:")
print(f"{'ID':<6} {'aircraft_sn':<20} {'event_time':<25}")
print("-" * 55)
for row in rows:
    print(f"{row[0]:<6} {row[1]:<20} {str(row[2]):<25}")

print("\n" + "=" * 70)
print("飞机与机场绑定关系")
print("=" * 70)

cursor.execute("""
    SELECT d.sn as aircraft_sn, COALESCE(d.device_name, '') as aircraft_name, 
           d.gateway_sn as airport_sn, COALESCE(a.device_name, '') as airport_name
    FROM device d
    LEFT JOIN device a ON d.gateway_sn = a.sn
    WHERE d.device_type = 3 AND d.deleted = 0
""")
rows = cursor.fetchall()

print(f"\n{'飞机SN':<20} {'飞机名称':<20} {'机场SN':<20} {'机场名称'}")
print("-" * 85)
for row in rows:
    print(f"{row[0]:<20} {row[1]:<20} {row[2]:<20} {row[3]}")

print("\n" + "=" * 70)
print("机场的wayline_uuid")
print("=" * 70)

cursor.execute("""
    SELECT s.device_sn, COALESCE(s.wayline_uuid, '') as wayline_uuid, s.update_time
    FROM device_realtime_status s
    JOIN device d ON s.device_sn = d.sn
    WHERE d.device_type = 1 AND s.deleted = 0
    ORDER BY s.update_time DESC
""")
rows = cursor.fetchall()

print(f"\n{'机场SN':<20} {'wayline_uuid':<40} {'update_time':<25}")
print("-" * 85)
for row in rows:
    print(f"{row[0]:<20} {row[1]:<40} {str(row[2]):<25}")

conn.close()
