import pymysql, json
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()
cursor.execute("SELECT device_sn, height, altitude, elevation, osd_raw_json FROM device_realtime_status WHERE device_sn='1581F8HGX252F00A018X' LIMIT 1")
row = cursor.fetchone()
if row:
    print(f'height={row[1]}, altitude={row[2]}, elevation={row[3]}')
    if row[4]:
        data = json.loads(row[4])
        host = data.get('host', {})
        print(f'OSD height={host.get("height")}')
        print(f'OSD altitude={host.get("altitude")}')
        print(f'OSD elevation={host.get("elevation")}')
conn.close()
