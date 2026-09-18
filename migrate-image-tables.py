import paramiko

SRC_HOST = "192.168.2.200"   # 源库
SRC_USER = "szy02"
SRC_PWD = "123456"
DST_HOST = "192.168.2.201"   # 目标库
DST_USER = "szy02"
DST_PWD = "123456"
DB = "sikong_cloud_api"
TABLES = ["zhimou_image_detection", "self_model_image_detection"]

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
    stdout.channel.set_combine_stderr(True)
    return stdout.read().decode(errors='replace')

def get_create_table(host, user, pwd, table):
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(host, username="szy01", password="szy666", timeout=10) if host == "192.168.2.201" else None
    # 用 mysql 客户端直连
    import subprocess
    return None

def main():
    # 通过 SSH 到 201，从 201 用 mysql 客户端连接 200 拿建表语句
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect("192.168.2.201", username="szy01", password="szy666", timeout=10)

    for tbl in TABLES:
        print(f"\n=== 从 200 导出 {tbl} ===")
        # 从 201 连 200
        cmd = f"mysql -h{SRC_HOST} -u{SRC_USER} -p'{SRC_PWD}' {DB} -e 'SHOW CREATE TABLE {tbl}\\G' 2>&1 | grep -v 'Warning'"
        out = run(ssh, cmd)
        print(out[:200])
        # 提取 Create Table 部分
        lines = out.split("\n")
        create_lines = []
        capture = False
        for line in lines:
            if line.startswith("Create Table: "):
                create_lines.append(line.replace("Create Table: ", ""))
                capture = True
            elif capture:
                if line.startswith("***************************") or line.startswith("Table:") or line.strip()=="":
                    capture = False
                    break
                create_lines.append(line)
        ddl = "\n".join(create_lines).strip()
        if not ddl.endswith(";"):
            ddl += ";"
        print(f"DDL 长度: {len(ddl)} 字符")

        # 在 201 执行
        print(f"=== 在 201 执行 {tbl} ===")
        # 写到临时文件再执行，避免转义
        sftp = paramiko.SFTPClient.from_transport(ssh.get_transport())
        with sftp.file(f"/tmp/create_{tbl}.sql", "w") as f:
            f.write(ddl)
        res = run(ssh, f"mysql -h127.0.0.1 -u{DST_USER} -p'{DST_PWD}' {DB} < /tmp/create_{tbl}.sql 2>&1")
        print(res if res.strip() else "OK")
        # 验证
        verify = run(ssh, f"mysql -h127.0.0.1 -u{DST_USER} -p'{DST_PWD}' {DB} -e 'SHOW CREATE TABLE {tbl}\\G' 2>&1 | grep -c annotated_local_path")
        print(f"含 annotated_local_path 字段计数: {verify.strip()}")

    ssh.close()

if __name__ == "__main__":
    main()
