import paramiko

SRC_HOST = "192.168.2.200"
SRC_USER = "szy02"
SRC_PWD = "123456"
DST_USER = "szy02"
DST_PWD = "123456"
DB = "sikong_cloud_api"
TABLES = ["task_switch_config"]

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
    stdout.channel.set_combine_stderr(True)
    return stdout.read().decode(errors='replace')

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect("192.168.2.201", username="szy01", password="szy666", timeout=10)

    for tbl in TABLES:
        print(f"\n=== 从 200 导出 {tbl} ===")
        cmd = f"mysql -h{SRC_HOST} -u{SRC_USER} -p'{SRC_PWD}' {DB} -e 'SHOW CREATE TABLE {tbl}\\G' 2>&1 | grep -v 'Warning'"
        out = run(ssh, cmd)
        lines = out.split("\n")
        create_lines = []
        capture = False
        for line in lines:
            if line.startswith("Create Table: "):
                create_lines.append(line.replace("Create Table: ", ""))
                capture = True
            elif capture:
                if line.startswith("***************************") or line.startswith("Table:"):
                    break
                create_lines.append(line)
        ddl = "\n".join(create_lines).strip()
        if not ddl.endswith(";"): ddl += ";"
        print(f"DDL 长度: {len(ddl)} 字符")

        # 也导出数据
        data_out = run(ssh, f"mysql -h{SRC_HOST} -u{SRC_USER} -p'{SRC_PWD}' {DB} -e 'SELECT * FROM {tbl};' 2>&1 | grep -v Warning")
        print(f"数据预览:\n{data_out[:500]}")

        # 写 DDL 到临时文件
        sftp = paramiko.SFTPClient.from_transport(ssh.get_transport())
        with sftp.file(f"/tmp/create_{tbl}.sql", "w") as f:
            f.write(ddl)
        # 执行
        res = run(ssh, f"mysql -h127.0.0.1 -u{DST_USER} -p'{DST_PWD}' {DB} < /tmp/create_{tbl}.sql 2>&1")
        print(f"执行: {res if res.strip() else 'OK'}")
        # 导数据
        if "Empty set" not in data_out and len(data_out.split("\n")) > 1:
            # 用 mysqldump 导出数据再导入
            dump = run(ssh, f"mysqldump -h{SRC_HOST} -u{SRC_USER} -p'{SRC_PWD}' --no-create-info --skip-triggers {DB} {tbl} 2>/dev/null")
            with sftp.file(f"/tmp/data_{tbl}.sql", "w") as f:
                f.write(dump)
            res2 = run(ssh, f"mysql -h127.0.0.1 -u{DST_USER} -p'{DST_PWD}' {DB} < /tmp/data_{tbl}.sql 2>&1")
            print(f"导数据: {res2 if res2.strip() else 'OK'}")
        # 验证
        verify = run(ssh, f"mysql -h127.0.0.1 -u{DST_USER} -p'{DST_PWD}' {DB} -e 'SELECT * FROM {tbl};' 2>&1 | grep -v Warning")
        print(f"验证:\n{verify[:500]}")

    ssh.close()

if __name__ == "__main__":
    main()
