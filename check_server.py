import urllib.request, json

# Try to access the server's backend to find out what MySQL it's using
# Also check if there's a Redis cache involved

# First, let's check if the API has any admin/debug endpoints
urls_to_check = [
    'http://192.168.2.201/api/alerts',
    'http://192.168.2.201/actuator/env',
    'http://192.168.2.201/actuator/info',
]

for url in urls_to_check:
    try:
        resp = urllib.request.urlopen(url, timeout=5)
        data = resp.read().decode()
        if 'alerts' in url:
            parsed = json.loads(data)
            print(f'{url}: {len(parsed.get("data", []))} records')
        else:
            print(f'{url}: accessible ({len(data)} bytes)')
    except urllib.error.HTTPError as e:
        print(f'{url}: HTTP {e.code}')
    except Exception as e:
        print(f'{url}: {e}')

# Also try connecting to the server's Redis to check cache
import socket
try:
    s = socket.create_connection(('192.168.2.201', 6379), timeout=5)
    s.send(b'PING\r\n')
    resp = s.recv(1024)
    print(f'Redis PING: {resp.decode().strip()}')
    # Check if there's an alerts cache key
    s.send(b'KEYS *alerts*\r\n')
    resp = s.recv(1024)
    print(f'Redis KEYS *alerts*: {resp.decode().strip()}')
    s.close()
except Exception as e:
    print(f'Redis check: {e}')