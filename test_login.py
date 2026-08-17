import requests, json
# 测试 screen-v3 登录
for user in ['admin', 'niuniu', 'xiaodong']:
    r = requests.post('http://localhost:8080/auth/login', json={'username': user, 'password': '888888'}, timeout=5)
    print(f'{user}/888888: status={r.status_code}, resp={r.text[:200]}')
