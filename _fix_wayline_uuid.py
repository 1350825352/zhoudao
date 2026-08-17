import requests

try:
    resp = requests.post('http://localhost:8080/api/alerts/fix-wayline-uuid', timeout=60)
    print(f"Status: {resp.status_code}")
    print(f"Response: {resp.text}")
except Exception as e:
    print(f"Error: {e}")
