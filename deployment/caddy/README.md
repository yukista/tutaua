# Proxy HTTPS de Tutaua

Aquest Caddyfile publica el servidor Jellyfin mitjançant
`https://tutaua-demo.duckdns.org` i Stream Hub sota
`https://tutaua-app.duckdns.org/tv/`.

El router ha de redirigir els ports TCP 80 i 443 a `192.168.1.139`.
Els ports interns 8091 (Fleet), 8092 (Stream Hub) i 8096 (Jellyfin) no s'han
d'exposar directament a Internet. Caddy elimina els prefixos `/control` i
`/tv` abans de reenviar la petició al servei local.

L'adreça `192.168.1.139` s'ha de reservar al DHCP del router perquè no canviï.
Com que Caddy i Jellyfin s'executen al mateix equip, Caddy envia les peticions
a `127.0.0.1:8096`; Stream Hub rep les de TV a `127.0.0.1:8092`. Fleet només
es publica a `/control/`. L'administració `/admin` queda limitada a IPs privades.

## Validació i recuperació

Abans de recarregar Caddy:

```sh
sudo caddy validate --adapter caddyfile --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Comprovar des del servidor amb el nom HTTPS preservat:

```sh
curl --resolve tutaua-app.duckdns.org:443:127.0.0.1 https://tutaua-app.duckdns.org/tv/health
curl --resolve tutaua-demo.duckdns.org:443:127.0.0.1 https://tutaua-demo.duckdns.org/System/Info/Public
```

La còpia anterior a la publicació de TV del 2026-09-11 és
`/etc/caddy/Caddyfile.before-tutaua-tv-20260911`. Per recuperar-la, copiar-la
sobre `/etc/caddy/Caddyfile`, validar-la i recarregar Caddy.
