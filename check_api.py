import urllib.request, json
try:
    resp = urllib.request.urlopen('http://192.168.2.201/api/alerts', timeout=10)
    data = json.loads(resp.read().decode())
    alerts = data.get('data', [])
    print('Alerts returned by API:', len(alerts))
    for a in alerts:
        print('  id=' + str(a['id']) + ', name=' + str(a['appName']) + ', rule=' + str(a['ruleName']))
except Exception as e:
    print('Error:', e)