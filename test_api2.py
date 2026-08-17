import requests

try:
    r = requests.get('http://localhost:8080/api/devices/list')
    print('状态码:', r.status_code)
    data = r.json()
    devices = data.get('data', [])
    print('设备数量:', len(devices))
    print()
    for d in devices:
        print(f'  SN: {d["sn"]}')
        print(f'  名称: {d["name"]}')
        print(f'  类型: {d["kind"]}')
        print(f'  状态: {d["status"]}')
        print()

    print("=" * 50)
    r2 = requests.get('http://localhost:8080/api/devices/stats')
    stats = r2.json().get('data', {})
    print('设备统计:')
    print(f'  无人机数: {stats.get("uavCount")}')
    print(f'  机场数: {stats.get("airportCount")}')
    print(f'  在线数: {stats.get("onlineCount")}')
    print(f'  飞行中: {stats.get("flyingCount")}')
except Exception as e:
    print('请求失败:', e)
