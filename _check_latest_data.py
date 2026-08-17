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
print("最新的知眸识别结果")
print("=" * 70)

cursor.execute("SELECT id, aircraft_sn, event_time, wayline_uuid FROM zhimou_detection_result WHERE deleted = 0 ORDER BY event_time DESC LIMIT 20")
rows = cursor.fetchall()

print(f"\n{'ID':<6} {'aircraft_sn':<20} {'event_time':<25} {'wayline_uuid':<40}")
print("-" * 95)
for row in rows:
    wu = row[3] if row[3] else ""
    print(f"{row[0]:<6} {row[1]:<20} {str(row[2]):<25} {wu:<40}")

conn.close()
