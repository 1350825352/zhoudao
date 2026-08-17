import pymysql
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()

# 查看所有表
cursor.execute("SHOW TABLES")
tables = [r[0] for r in cursor.fetchall()]
print('所有表:', tables)

# 查找用户相关的表
for t in tables:
    if 'user' in t.lower() or 'account' in t.lower() or 'login' in t.lower():
        print(f'\n--- 表: {t} ---')
        try:
            cursor.execute(f"SELECT * FROM {t} LIMIT 5")
            cols = [d[0] for d in cursor.description]
            print('列:', cols)
            for row in cursor.fetchall():
                print(dict(zip(cols, row)))
        except Exception as e:
            print('Error:', e)

conn.close()
