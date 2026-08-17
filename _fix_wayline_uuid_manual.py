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
print("可用航线列表 (从OpenAPI获取)")
print("=" * 70)
print("1. 6ea1d4d0-4a79-44e3-898b-0b434595aac8 - 新建巡逻航线")
print("2. f658804b-71d4-4... (其他航线)")
print()

wayline_uuid = input("请输入要关联的航线UUID: ").strip()
if not wayline_uuid:
    print("未输入航线UUID，退出")
    conn.close()
    exit()

print("\n" + "=" * 70)
print("选择要修复的记录范围")
print("=" * 70)
print("1. 全部修复")
print("2. 按飞机SN筛选")
print("3. 按时间范围筛选")
choice = input("请选择 (1/2/3): ").strip()

query = "UPDATE zhimou_detection_result SET wayline_uuid = %s WHERE deleted = 0 AND wayline_uuid = '手动飞行'"
params = [wayline_uuid]

if choice == '2':
    aircraft_sn = input("请输入飞机SN: ").strip()
    query += " AND aircraft_sn = %s"
    params.append(aircraft_sn)
elif choice == '3':
    start_time = input("请输入开始时间 (格式: 2026-07-15 00:00:00): ").strip()
    end_time = input("请输入结束时间 (格式: 2026-07-15 23:59:59): ").strip()
    query += " AND event_time >= %s AND event_time <= %s"
    params.append(start_time)
    params.append(end_time)

print(f"\n执行SQL: {query}")
print(f"参数: {params}")

confirm = input("确认执行? (y/N): ").strip()
if confirm.lower() != 'y':
    print("取消执行")
    conn.close()
    exit()

cursor.execute(query, params)
affected = cursor.rowcount
conn.commit()

print(f"\n成功修复 {affected} 条记录")

cursor.execute("SELECT wayline_uuid, COUNT(*) as cnt FROM zhimou_detection_result WHERE deleted = 0 GROUP BY wayline_uuid")
print("\n修复后统计:")
for row in cursor.fetchall():
    print(f"  {row[0]}: {row[1]} 条")

conn.close()
