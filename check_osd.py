import pymysql, json
conn = pymysql.connect(host='192.168.2.200', port=3306, user='szy02', password='123456', database='sikong_cloud_api')
cursor = conn.cursor()
cursor.execute("SELECT device_sn, osd_raw_json FROM device_realtime_status WHERE device_sn='1581F8HGX252F00A018X' LIMIT 1")
row = cursor.fetchone()
if row and row[1]:
    data = json.loads(row[1])
    # 查找 altitude 相关字段
    def find_keys(obj, prefix=''):
        if isinstance(obj, dict):
            for k, v in obj.items():
                full = f'{prefix}.{k}' if prefix else k
                if 'alt' in k.lower() or 'height' in k.lower() or 'distance' in k.lower() or 'home' in k.lower():
                    print(f'  {full} = {v}')
                find_keys(v, full)
        elif isinstance(obj, list):
            for i, v in enumerate(obj):
                find_keys(v, f'{prefix}[{i}]')
    print('OSD中高度/距离相关字段:')
    find_keys(data)
else:
    print('没有OSD原始数据')
conn.close()
