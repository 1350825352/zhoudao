import pymysql

# Try connecting to 192.168.2.200 (same machine, different interface)
# with root credentials as per deploy config
try:
    conn = pymysql.connect(host='192.168.2.200', port=3306, user='root', password='123456', database='sikong_cloud_api', connect_timeout=5)
    cur = conn.cursor()
    cur.execute('SELECT COUNT(*) FROM zhimou_detection_result WHERE deleted=0')
    count = cur.fetchone()[0]
    print(f'Connection to 192.168.2.200 root: count={count}')
    
    # Show IDs
    cur.execute('SELECT id FROM zhimou_detection_result WHERE deleted=0 ORDER BY id DESC LIMIT 10')
    ids = [row[0] for row in cur.fetchall()]
    print(f'IDs: {ids}')
    conn.close()
except Exception as e:
    print(f'Error with root@200: {e}')

# Also check if maybe the server has MySQL on a different port
import socket
for port in [3306, 3307, 3308, 13306]:
    try:
        s = socket.create_connection(('192.168.2.201', port), timeout=2)
        s.close()
        print(f'Port {port}: OPEN')
    except:
        print(f'Port {port}: closed')