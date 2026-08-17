-- 重置所有用户密码为 admin123
-- admin123 的标准 MD5 = 48253a020b74183264c5f4ac8339b8c7
-- 如果不对，再用服务器端 echo -n "admin123" | md5sum 确认

-- 先查看当前 admin 密码
SELECT username, password FROM user WHERE username = 'admin';

-- 重置 admin 密码为 admin123
UPDATE user SET password = '48253a020b74183264c5f4ac8339b8c7' WHERE username = 'admin';

-- 重置所有用户密码为 admin123（可选）
-- UPDATE user SET password = '48253a020b74183264c5f4ac8339b8c7';

-- 验证
SELECT username, password FROM user WHERE username = 'admin';
