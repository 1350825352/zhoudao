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
print("修复 zhimou_detection_result 表的 wayline_uuid 字段")
print("=" * 70)

wayline_uuid = "6ea1d4d0-4a79-44e3-898b-0b434595aac8"
print(f"使用航线UUID: {wayline_uuid}")
print()

cursor.execute("SELECT COUNT(*) FROM zhimou_detection_result WHERE deleted = 0 AND wayline_uuid = '手动飞行'")
count = cursor.fetchone()[0]
print(f"需要修复的记录数: {count}")
print()

query = "UPDATE zhimou_detection_result SET wayline_uuid = %s WHERE deleted = 0 AND wayline_uuid = '手动飞行'"
cursor.execute(query, [wayline_uuid])
affected = cursor.rowcount
conn.commit()

print(f"成功修复 {affected} 条记录")
print()

cursor.execute("SELECT wayline_uuid, COUNT(*) as cnt FROM zhimou_detection_result WHERE deleted = 0 GROUP BY wayline_uuid")
print("修复后统计:")
for row in cursor.fetchall():
    print(f"  {row[0]}: {row[1]} 条")

conn.close()
