import hashlib
# 测试常见密码
passwords = ['admin123', '123456', 'admin', '12345678', 'password', '888888', '666666', 'admin@123']
target = '21218cca77804d2ba1922c33e0151105'
for p in passwords:
    md5 = hashlib.md5(p.encode()).hexdigest()
    if md5 == target:
        print(f'找到密码: {p}')
        break
    print(f'{p}: {md5}')
