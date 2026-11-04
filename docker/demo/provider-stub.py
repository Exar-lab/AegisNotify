"""Demo notification provider. Accepts any POST and never contacts a real recipient.

Stands in for SendGrid, Twilio and Vonage in the AWS demo so no real message is ever sent.
Unlike docs/testing/notification-provider-stub.py it listens on all interfaces (it runs in a
container on the compose network) and answers every path.
"""

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORTS = (8091, 8092)
BODY = json.dumps({"sid": "SMdemo0000000000000000000000000000", "status": "queued"}).encode()


class Provider(BaseHTTPRequestHandler):
    def _reply(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length:
            self.rfile.read(length)
        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(BODY)))
        self.end_headers()
        self.wfile.write(BODY)

    do_POST = do_PUT = do_GET = _reply

    def log_message(self, fmt, *args):
        print("provider-stub", self.command, self.path, flush=True)


if __name__ == "__main__":
    servers = [ThreadingHTTPServer(("0.0.0.0", port), Provider) for port in PORTS]
    for server in servers[1:]:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    servers[0].serve_forever()
