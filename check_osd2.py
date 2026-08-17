import pymysql, json
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()
cursor.execute("SELECT device_sn, osd_raw_json FROM device_realtime_status WHERE device_sn='1581F8HGX252F00A018X' LIMIT 1")
row = cursor.fetchone()
if row and row[1]:
    data = json.loads(row[1])
    # 打印host下的所有顶级key
    host = data.get('host', {})
    print('host 下的所有key:')
    for k in sorted(host.keys()):
        v = host[k]
        if not isinstance(v, (dict, list)):
            print(f'  {k} = {v}')
        elif k == 'gps':
            print(f'  gps = {json.dumps(v, indent=4)}')
    print()
    # 查找 distance_to_home
    def find_all(obj, target, prefix=''):
        if isinstance(obj, dict):
            for k, v in obj.items():
                full = f'{prefix}.{k}' if prefix else k
                if target in k.lower():
                    print(f'  FOUND: {full} = {v}')
                find_all(v, target, full)
        elif isinstance(obj, list):
            for i, v in enumerate(obj):
                find_all(v, target, f'{prefix}[{i}]')
    print('查找 distance_to_home:')
    find_all(data, 'distance_to_home')
    print()
    print('查找 gps/altitude:')
    find_all(data, 'gps')
    find_all(data, 'altitude')
conn.close()
