"""一键部署 pk_server.py 到阿里云服务器（SSH）。

认证：优先用本机 SSH 密钥（~/.ssh/id_rsa，2026-09-25 起已装服务器 authorized_keys，免密）；
密钥不可用时回退到环境变量 PK_SSH_PASS 或交互输入。
用法：python tools/deploy_pk_server.py   （或 PK_SSH_PASS=xxxx python tools/deploy_pk_server.py）
"""
import getpass
import os

import paramiko

HOST, USER = '8.148.192.129', 'root'


def connect():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        c.connect(HOST, username=USER, timeout=15)  # 密钥优先（look_for_keys 默认开）
        print('[auth] SSH 密钥免密登录 OK')
        return c
    except paramiko.ssh_exception.AuthenticationException:
        pwd = os.environ.get('PK_SSH_PASS') or getpass.getpass(f'{HOST} 的 {USER} 密码: ')
        c.connect(HOST, username=USER, password=pwd, timeout=15)
        print('[auth] 口令登录 OK')
        return c


c = connect()


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

# 2b. 上传题库（服务器中立对战：assets 内置题库 + 热更包源题库，含答案供服务器判分）
try:
    sftp.mkdir('/opt/pk/questions')
except IOError:
    pass  # 目录已存在
import glob
n_q = 0
for src in glob.glob('app/src/main/assets/questions/*.json') + glob.glob('update-server/packs/src/*.json'):
    sftp.put(src, '/opt/pk/questions/' + os.path.basename(src))
    n_q += 1
print(f'[upload] 题库 {n_q} 个 JSON OK')
sftp.close()

# 3. systemd 服务（含 websockets 路径探测）
unit = '''[Unit]
Description=BrainQuest PK WebSocket Server
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/pk
ExecStart=/bin/bash -c 'python3 -u /opt/pk/pk_server.py 8765'
Restart=always
RestartSec=3
User=root

[Install]
WantedBy=multi-user.target
'''
run('echo \'%s\' > /etc/systemd/system/pk-server.service' % unit.replace("'", "'\\''"), 'unit file')
# restart 而非 enable --now：服务已在运行时 --now 不会重启，会一直跑旧代码
run('systemctl daemon-reload && systemctl enable pk-server && systemctl restart pk-server', 'enable+restart')

# 4. 验证
import time
time.sleep(2)
run('systemctl is-active pk-server && ss -tlnp | grep 8765', 'verify')
c.close()
print('部署完成')
