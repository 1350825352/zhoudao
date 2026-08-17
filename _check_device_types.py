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
print("所有设备的类型设置")
print("=" * 70)

cursor.execute("SELECT sn, device_type, device_name, gateway_sn FROM device WHERE deleted = 0")
rows = cursor.fetchall()

type_map = {1: "机场(GATEWAY_DOCK)", 2: "遥控器(GATEWAY_REMOTE)", 3: "飞机(AIRCRAFT)", 4: "相机(CAMERA)"}

print(f"\n{'SN':<25} {'类型':<20} {'名称':<20} {'Gateway_SN'}")
print("-" * 90)
for row in rows:
    t = type_map.get(row[1], str(row[1]))
    name = row[2] if row[2] else ""
    gw = row[3] if row[3] else ""
    print(f"{row[0]:<25} {t:<20} {name:<20} {gw}")

conn.close()
