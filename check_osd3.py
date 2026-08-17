import pymysql, json
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()
cursor.execute("SELECT device_sn, osd_raw_json FROM device_realtime_status WHERE device_sn='1581F8HGX252F00A018X' LIMIT 1")
row = cursor.fetchone()
if row and row[1]:
    data = json.loads(row[1])
    print('root 下的所有顶级key:')
    for k in sorted(data.keys()):
        v = data[k]
        if not isinstance(v, (dict, list)):
            print(f'  {k} = {v}')
    print()
    # 查找所有层级的 distance_to_home
    def find_all(obj, prefix=''):
        if isinstance(obj, dict):
            for k, v in obj.items():
                full = f'{prefix}.{k}' if prefix else k
                if 'distance' in k.lower():
                    print(f'  DISTANCE: {full} = {v}')
                if k == 'position_state':
                    print(f'  position_state = {json.dumps(v, indent=2)}')
                find_all(v, full)
        elif isinstance(obj, list):
            for i, v in enumerate(obj):
                find_all(v, f'{prefix}[{i}]')
    find_all(data)
conn.close()
