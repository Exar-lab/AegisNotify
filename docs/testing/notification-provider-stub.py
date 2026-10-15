"""Local HTTP providers for manual delivery tests. Never contacts real recipients."""

import argparse
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def handler(status):
    class Provider(BaseHTTPRequestHandler):
        def do_POST(self):
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            if self.path != "/v3/mail/send":
                self.send_error(404)
                return
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{}' if status < 400 else b'{"error":"Synthetic provider failure"}')

    return Provider


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--primary", type=int, default=202, choices=range(200, 600))
    parser.add_argument("--fallback", type=int, default=202, choices=range(200, 600))
    args = parser.parse_args()
    servers = [ThreadingHTTPServer(("127.0.0.1", port), handler(status))
               for port, status in [(8091, args.primary), (8092, args.fallback)]]
    for server in servers:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    print(f"Local providers: primary :8091 -> {args.primary}, fallback :8092 -> {args.fallback}")
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        for server in servers:
            server.shutdown()
