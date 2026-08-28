# Xarxa privada de gestió Tutaua

Headscale és el pla de control autoallotjat i les caixes utilitzen el client Android
oficial de Tailscale. La xarxa serveix per a telemetria i sessions de suport; les
actualitzacions continuen requerint artefactes signats i no confien només en la VPN.

## Abans d'arrencar

Validar sempre la configuració amb exactament la versió fixada al Compose. Des
de Windows amb WSL:

    wsl.exe -d Ubuntu -- bash /mnt/e/projectes/client_android_jellyfin/deployment/headscale/validate-config.sh

L'script descarrega el binari oficial 0.29.3 en un directori temporal, en
comprova el SHA-256 publicat i redirigeix les escriptures de `configtest` al
mateix directori temporal. No inicia el servidor ni obre ports.

1. Crear `tutaua-control` al compte de DuckDNS i afegir-lo a
   `DUCKDNS_DOMAINS` de `deployment/duckdns/docker-compose.yml`.
2. Afegir al Caddyfile:

       tutaua-control.duckdns.org {
           reverse_proxy 127.0.0.1:8090
       }

3. Obrir/reenviar només TCP 443 cap al servidor Caddy. Headscale queda vinculat a
   `127.0.0.1`; no s'exposen ni el port 8090 ni les mètriques.
4. Crear el directori persistent `data` i iniciar amb `docker compose up -d`.

## Alta segura d'una caixa

Crear una clau diferent, d'un sol ús i amb caducitat curta per a cada dispositiu:

    docker compose exec headscale headscale users create tutaua
    docker compose exec headscale headscale preauthkeys create \
      --user tutaua --expiration 30m --reusable=false --tags tag:box

La clau només s'introdueix durant el provisionament i no es desa al repositori ni a
la imatge de fàbrica. Després de l'alta, verificar el nom i retirar qualsevol clau
que no s'hagi consumit.

La sintaxi de polítiques de Headscale 0.29 identifica aquest usuari com
`tutaua@`; aquesta és la forma que ha de constar a `tagOwners`.

## Accés de suport

L'ordinador de suport s'enregistra amb `tag:support`. Les ACL permeten que arribi a
les caixes, però impedeixen que una caixa contacti amb una altra. ADB no s'activa de
manera permanent: Tutaua Manager l'haurà d'obrir temporalment, registrar l'auditoria
i tornar-lo a tancar. No s'ha de publicar mai TCP 5555 al router.

## Còpies i actualització

Fer còpia de `data/db.sqlite` i de `data/noise_private.key`. Abans de canviar la
versió fixada de la imatge, llegir la guia d'actualització de Headscale, fer còpia i
executar `headscale configtest` amb la nova versió.
