import time
import requests

time.sleep(15)

try:
    resp = requests.get('http://localhost:8080/api/alerts', timeout=10)
    print(f"Status: {resp.status_code}")
    print(f"Response: {resp.text[:500]}")
except Exception as e:
    print(f"Error: {e}")
