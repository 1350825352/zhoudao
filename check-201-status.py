import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,aircraft_sn,status,zhimou_status,deleted FROM live_stream WHERE aircraft_sn=\"1581F8HGX252F00A018X\" ORDER BY id DESC LIMIT 5;' 2>&1 | grep -v Warning")
    ssh.close()

if __name__ == "__main__":
    main()
