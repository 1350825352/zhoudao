import pymysql, json
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()
cursor.execute("SELECT device_sn, osd_raw_json FROM device_realtime_status WHERE device_sn='1581F8HGX252F00A018X' LIMIT 1")
row = cursor.fetchone()
if row and row[1]:
    data = json.loads(row[1])
    print('Root keys:', list(data.keys()))
    host = data.get('host', {})
    print('\nhost keys (非dict/list):')
    for k in sorted(host.keys()):
        v = host[k]
        if not isinstance(v, (dict, list)):
            print(f'  {k} = {v}')
    # 查找所有包含elev或height的key
    def find_all(obj, prefix=''):
        if isinstance(obj, dict):
            for k, v in obj.items():
                full = f'{prefix}.{k}' if prefix else k
                kl = k.lower()
                if 'elev' in kl or 'height' in kl or 'altit' in kl:
                    print(f'  FOUND: {full} = {v}')
                find_all(v, full)
        elif isinstance(obj, list):
            for i, v in enumerate(obj):
                find_all(v, f'{prefix}[{i}]')
    print('\n查找elev/height/altitude:')
    find_all(data)
conn.close()
