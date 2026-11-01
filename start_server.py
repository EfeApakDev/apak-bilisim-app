import http.server
import socketserver
import ssl
import socket
import os
import threading
import sys

# Force UTF-8 for console output
sys.stdout.reconfigure(encoding='utf-8')

PORT_HTTP = 8080
PORT_HTTPS = 8443

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return '192.168.1.35'

ip = get_local_ip()

Handler = http.server.SimpleHTTPRequestHandler
Handler.extensions_map.update({
    '.json': 'application/json',
    '.svg': 'image/svg+xml',
    '.js': 'application/javascript',
    '.css': 'text/css',
})

def run_https():
    cert_path = os.path.join(os.path.dirname(__file__), 'cert.pem')
    key_path = os.path.join(os.path.dirname(__file__), 'key.pem')

    if os.path.exists(cert_path) and os.path.exists(key_path):
        httpd_ssl = socketserver.TCPServer(("", PORT_HTTPS), Handler)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certfile=cert_path, keyfile=key_path)
        httpd_ssl.socket = context.wrap_socket(httpd_ssl.socket, server_side=True)
        try:
            httpd_ssl.serve_forever()
        except Exception:
            pass

def run_http():
    httpd = socketserver.TCPServer(("", PORT_HTTP), Handler)
    try:
        httpd.serve_forever()
    except Exception:
        pass

# Start HTTPS in a background thread
t_https = threading.Thread(target=run_https, daemon=True)
t_https.start()

print("=" * 65)
print("APAK BILISIM RESMI UYGULAMA SUNUCUSU AKTIF")
print("=" * 65)
print(f"Bilgisayarda Acilacak Adres:       http://localhost:{PORT_HTTP}")
print(f"Telefonda Acilacak Adres (HTTPS):  https://{ip}:{PORT_HTTPS}")
print(f"Telefonda Acilacak Adres (HTTP):   http://{ip}:{PORT_HTTP}")
print("=" * 65)
print("ANDROID KAMERA & MIKROFON IZINLERI ICIN:")
print("1. Telefonundan Chrome ile 'https://' adresine gir:")
print(f"   -> https://{ip}:{PORT_HTTPS}")
print("2. 'Gelismis' -> 'Siteye Ilerle' butonuna bas.")
print("3. HTTPS sayesinde Android Kamera, Flas ve Mikrofonu acar!")
print("=" * 65)

run_http()
