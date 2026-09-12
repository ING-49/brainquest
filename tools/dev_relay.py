"""
开发中继：模拟器访问 http://10.0.2.2:8000，本脚本把请求转发到
GitHub Releases 的 latest 下载地址（主机网络可达 GitHub，模拟器不一定可达）。

用法：python tools/dev_relay.py [端口，默认8000]
"""
import http.server
import sys
import urllib.request

BASE = "https://github.com/ING-49/brainquest/releases/latest/download/"


class RelayHandler(http.server.BaseHTTPRequestHandler):
    server_version = "BQRelay/1.0"

    def do_GET(self):
        path = self.path.lstrip("/").split("?")[0]
        url = BASE + path
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "bq-relay"})
            with urllib.request.urlopen(req, timeout=120) as r:
                self.send_response(200)
                self.send_header("Content-Type", r.headers.get("Content-Type", "application/octet-stream"))
                length = r.headers.get("Content-Length")
                if length:
                    self.send_header("Content-Length", length)
                self.end_headers()
                while True:
                    chunk = r.read(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
            print(f"[relay] {path} → 200")
        except Exception as e:  # noqa: BLE001
            print(f"[relay] {path} → 失败: {e}")
            self.send_error(404, f"relay fetch failed: {e}")

    def log_message(self, *args):  # 静默默认日志（上面已自定义）
        pass


class ThreadingServer(http.server.ThreadingHTTPServer):
    daemon_threads = True


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    print(f"GitHub 更新中继已启动: http://10.0.2.2:{port} → {BASE}")
    print("Ctrl+C 停止")
    ThreadingServer(("0.0.0.0", port), RelayHandler).serve_forever()
