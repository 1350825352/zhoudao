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

try:
    cursor.execute("ALTER TABLE device_realtime_status ADD COLUMN wayline_uuid VARCHAR(100) DEFAULT NULL COMMENT '航线UUID' AFTER current_task_name")
    print("成功添加 wayline_uuid 字段")
except pymysql.err.OperationalError as e:
    if e.args[0] == 1060:
        print("wayline_uuid 字段已存在")
    else:
        raise

conn.commit()
conn.close()
