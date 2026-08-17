import pymysql, json
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()
cursor.execute("SELECT device_sn, osd_raw_json FROM device_realtime_status WHERE device_sn='1581F8HGX252F00A018X' LIMIT 1")
row = cursor.fetchone()
if row and row[1]:
    data = json.loads(row[1])
    host = data.get('host', {})
    print('elevation =', host.get('elevation'))
    print('height =', host.get('height'))
    print('home_distance =', host.get('home_distance'))
    # 看看有没有其他海拔相关字段
    for k in sorted(host.keys()):
        v = host[k]
        if not isinstance(v, (dict, list)):
            kl = k.lower()
            if 'alt' in kl or 'elev' in kl or 'height' in kl:
                print(f'  {k} = {v}')
conn.close()
