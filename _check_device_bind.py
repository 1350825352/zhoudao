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
print("设备绑定关系")
print("=" * 70)

cursor.execute("SELECT sn, gateway_sn, device_type, device_name, online_status FROM device WHERE deleted = 0")
rows = cursor.fetchall()

print(f"\n{'SN':<20} {'Gateway_SN':<20} {'Type':<10} {'Name':<20} {'Online'}")
print("-" * 85)

airports = []
aircrafts = []

for row in rows:
    sn = row[0]
    gw_sn = row[1] if row[1] else ""
    dev_type = "机场" if row[2] == 1 else ("飞机" if row[2] == 2 else str(row[2]))
    name = row[3] if row[3] else ""
    online = "在线" if row[4] == 1 else "离线"
    
    if dev_type == "机场":
        airports.append((sn, name))
    elif dev_type == "飞机":
        aircrafts.append((sn, gw_sn, name))
    
    print(f"{sn:<20} {gw_sn:<20} {dev_type:<10} {name:<20} {online}")

print(f"\n机场列表 ({len(airports)}个):")
for sn, name in airports:
    print(f"  {sn}: {name}")

print(f"\n飞机列表 ({len(aircrafts)}个):")
for sn, gw_sn, name in aircrafts:
    print(f"  {sn}: {name}, 绑定机场: {gw_sn}")

conn.close()
