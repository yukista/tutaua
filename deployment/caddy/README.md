# Proxy HTTPS de Tutaua

Aquest Caddyfile publica el servidor Jellyfin de demostració mitjançant
`https://tutaua-demo.duckdns.org`.

El router ha de redirigir els ports TCP 80 i 443 a `192.168.1.139`.
El port 8097 del Jellyfin de demostració no s'ha d'exposar directament a
Internet.

L'adreça `192.168.1.139` s'ha de reservar al DHCP del router perquè no canviï.
Com que Caddy i Jellyfin s'executen al mateix equip, Caddy envia les peticions
a `127.0.0.1:8097`. El Jellyfin personal continua al port 8096 i no és publicat
per aquest domini.
