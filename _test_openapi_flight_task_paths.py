import requests
import json

headers = {
    'X-Language': 'zh-CN',
    'X-Organization-Uuid': '83f6ba9b-cd2c-4fe0-a4a5-3783baec77f6',
    'X-Project-Uuid': '5ac8e7fe-210e-4ad0-95db-c59c67bea827',
    'X-User-Token': 'eyJhbGciOiJIUzUxMiIsImNyaXQiOlsidHlwIiwiYWxnIiwia2lkIl0sImtpZCI6IjhiZmRiZmRkLWM4OGYtNGE5Yi04NzI3LWQ0ZGYzYWE5OTJlOSIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50IjoiIiwiZXhwIjoyMDc0MTM4OTM5LCJuYmYiOjE3NTg2MDYxMzksIm9yZ2FuaXphdGlvbl91dWlkIjoiODNmNmJhOWItY2QyYy00ZmUwLWE0YTUtMzc4M2JhZWM3N2Y2IiwicHJvamVjdF91dWlkIjoiIiwic3ViIjoiZmgyIiwidXNlcl9pZCI6IjE3NzIwOTc2OTQzODUzODEzNzYifQ.9npesr7vSNvAh4wNE--3XndsuJ5MiC1EOuHVcY1URjqvmyFfY__xxu4cxUj8xAEVy0INcePM8jKipcjAsd_1Rw'
}

paths = [
    '/openapi/v2.0/flight-task/list',
    '/openapi/v2.0/flight-task/query',
    '/openapi/v2.0/task',
    '/openapi/v2.0/task/list',
    '/openapi/v2.0/flight/task',
    '/openapi/v2.0/flight/task/list',
]

base_url = 'https://es-flight-api-cn.djigate.com'

for path in paths:
    try:
        resp = requests.get(base_url + path, headers=headers, params={'pageSize': 10, 'pageNum': 1}, timeout=30)
        print(f"Path: {path}")
        print(f"Status: {resp.status_code}")
        try:
            data = resp.json()
            print(f"Response: {json.dumps(data, indent=2, ensure_ascii=False)[:300]}")
        except:
            print(f"Response text: {resp.text[:200]}")
        print()
    except Exception as e:
        print(f"Path: {path}")
        print(f"Error: {e}")
        print()
