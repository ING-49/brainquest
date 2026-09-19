"""一键部署 pk_server.py 到阿里云服务器（SSH）。

密码不再写在脚本里：从环境变量 PK_SSH_PASS 读取，缺省时交互输入。
用法：PK_SSH_PASS=xxxx python tools/deploy_pk_server.py
"""
import getpass
import os

import paramiko

HOST, USER = '8.148.192.129', 'root'
PWD = os.environ.get('PK_SSH_PASS') or getpass.getpass(f'{HOST} 的 {USER} 密码: ')

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PWD, timeout=15)


def run(cmd, label=''):
    _, out, err = c.exec_command(cmd, timeout=300)
    code = out.channel.recv_exit_status()
    o, e = out.read().decode(), err.read().decode()
    print(f'[{label or cmd[:40]}] exit={code}')
    if o.strip():
        print('  ', o.strip()[-400:])
    if e.strip() and code != 0:
        print('  ERR:', e.strip()[-300:])
    return code


# 1. 依赖
run('apt-get update -qq && apt-get install -y -qq python3 python3-pip > /dev/null 2>&1 || pip3 install websockets', 'install pip')
run('pip3 install websockets --break-system-packages 2>/dev/null || pip3 install websockets', 'websockets')

# 2. 上传 pk_server.py
sftp = c.open_sftp()
run('mkdir -p /opt/pk', 'mkdir')
sftp.put('update-server/pk_server.py', '/opt/pk/pk_server.py')
print('[upload] pk_server.py OK')
sftp.close()

# 3. systemd 服务（含 websockets 路径探测）
unit = '''[Unit]
Description=BrainQuest PK WebSocket Server
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/pk
ExecStart=/bin/bash -c 'python3 /opt/pk/pk_server.py 8765'
Restart=always
RestartSec=3
User=root

[Install]
WantedBy=multi-user.target
'''
run('echo \'%s\' > /etc/systemd/system/pk-server.service' % unit.replace("'", "'\\''"), 'unit file')
run('systemctl daemon-reload && systemctl enable --now pk-server', 'enable+start')

# 4. 验证
import time
time.sleep(2)
run('systemctl is-active pk-server && ss -tlnp | grep 8765', 'verify')
c.close()
print('部署完成')
