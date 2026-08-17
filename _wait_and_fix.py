import time
import requests

time.sleep(20)

try:
    resp = requests.get('http://localhost:8080/api/alerts', timeout=10)
    print(f"Alerts API Status: {resp.status_code}")
    
    print("\n=== 调用修复接口 ===")
    resp = requests.post('http://localhost:8080/api/alerts/fix-wayline-uuid', timeout=60)
    print(f"Status: {resp.status_code}")
    print(f"Response: {resp.text}")
except Exception as e:
    print(f"Error: {e}")
