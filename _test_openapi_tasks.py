import requests
import json

headers = {
    'X-Language': 'zh-CN',
    'X-Organization-Uuid': '83f6ba9b-cd2c-4fe0-a4a5-3783baec77f6',
    'X-Project-Uuid': '5ac8e7fe-210e-4ad0-95db-c59c67bea827'
}

try:
    resp = requests.get('https://es-flight-api-cn.djigate.com/openapi/v2.0/flight-task', headers=headers, params={'pageSize': 100, 'pageNum': 1}, timeout=30)
    print(f"Status: {resp.status_code}")
    data = resp.json()
    print(f"Response keys: {data.keys()}")
    if 'data' in data:
        print(f"data keys: {data['data'].keys()}")
        if 'list' in data['data']:
            print(f"Task count: {len(data['data']['list'])}")
            if data['data']['list']:
                print("\nFirst task:")
                print(json.dumps(data['data']['list'][0], indent=2, ensure_ascii=False))
except Exception as e:
    print(f"Error: {e}")
