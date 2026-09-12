# Connexions locals i remotes

Actualitzat: 2026-09-11.

Tutaua Box → Administració permet editar Jellyfin, API TV, directe opcional,
servidor Fleet i IP local opcional de Fleet. «Suggerir locals» i «Suggerir
remotes» omplen els camps; només «Desar canvis» els aplica. Els camps continuen
sent editables. Les dues columnes tenen desplaçament independent i el botó de
desar queda sempre visible.

| Servei | Suggeriment local | Suggeriment remot |
| --- | --- | --- |
| Jellyfin | `http://192.168.1.139:8096` | `https://tutaua-demo.duckdns.org` |
| API TV | `http://192.168.1.139:8092` | `https://tutaua-app.duckdns.org/tv` |
| Directe | Buit: adreça retornada per l’API | Buit: adreça retornada per l’API |
| Fleet | `https://tutaua-app.duckdns.org/control` | Mateix domini HTTPS |
| IP local Fleet | `192.168.1.139` | Buida: resolució pública normal |

La ruta pública `/tv` es publica a Caddy cap al Stream Hub local i elimina el
prefix abans de reenviar les peticions. Les adreces remotes continuen requerint
una comprovació des d'una xarxa exterior.
El perfil remot deixa buida la IP LAN de Fleet; dins de casa pot requerir
NAT loopback o DNS local adequat per accedir al domini públic.
No donar l’accés exterior per validat fins a provar-lo des d’una altra xarxa.

## Aplicació dels canvis

- Tutaua i TV rellegeixen Box quan es reprenen. TV recrea la pantalla si canvia
  l’API o el directe, sense reiniciar Android. Sense configuració Box, TV conserva
  el servidor definit a la compilació per compatibilitat.
- Canviar Jellyfin elimina la sessió associada a l’adreça anterior i demana
  autenticar-se de nou, també si són dues adreces del mateix servidor.
- Manager llegeix la connexió de Box a cada sincronització; desar a Box sol·licita
  una sincronització. Un servidor Fleet buit conserva l’enrolament anterior.
  Amb un servidor explícit, una IP local buida desactiva el mapatge LAN.
- Fleet sempre utilitza HTTPS amb validació del certificat. Canviar una URL no
  trasllada identitats a una instal·lació Fleet diferent: aquesta necessita un
  enrolament propi. Les dues adreces han de conduir al mateix servei.
- Les APK es descarreguen del Fleet seleccionat pel seu ID, després de verificar
  el manifest original; continuen validant-se hash, certificat i versió.
- El camp de catàleg antic es conserva per compatibilitat. No és l’origen de les
  actualitzacions de Manager, que provenen de Fleet.
- Games utilitza ROM locals i no necessita cap adreça de servidor.
- Una configuració posterior enviada des de Fleet pot actualitzar els camps de
  Box; cal mantenir coherent l’estat desitjat de la caixa a Fleet.

## Validació a la TX5 192.168.1.135

Versions instal·lades: Box 0.5.0 (9), Tutaua 1.0.14 (45), Manager 0.3.0 (12),
TV 4.5.3 (29). Les APK anteriors i les preferències es conserven al directori
local ignorat `device-backups/135-before-central-urls`.

- 17 proves unitàries superades: Box 6, Manager 4, Tutaua 3, TV 4.
- Compilació release de les quatre apps i lint de Box, Manager i TV correctes.
- Interfície de suggeriments locals/remots revisada a 1920×1080; configuració
  local desada des de la interfície i biblioteca Jellyfin carregada.
- Proxy temporal amb prefix `/tv`: peticions reals de TV a l’API i gravacions.
- Directe alternatiu: petició real a `/override/playlist.m3u8` i a segments.
- Proxy retirat i configuració local restaurada després de la prova.

L’informe de la prova de connexions és a `captures/config-smoke.json` (ignorat
per Git). No és una prova integral de partit ni una prova des d’Internet.

## Validació exterior del 2026-09-11

La TX5 `PILOT-TX5-001` s'ha connectat a una xarxa externa amb els suggeriments
remots aplicats. L'usuari ha iniciat sessió de nou a Jellyfin i ha confirmat
Tutaua i TV funcionals. Fleet ha rebut un batec a les `15:51:04 UTC`, un minut
abans de la comprovació del servidor, amb Box 0.5.0, Manager 0.3.0, Tutaua
1.0.14, TV 4.5.3 i Games 0.1.0. Això valida l'accés sortint de Manager a Fleet.

No hi havia cap release publicada més nova que les versions de la caixa; per
tant encara falta exercitar, en una propera release, la descàrrega externa d'una
APK signada i la seva instal·lació silenciosa.
