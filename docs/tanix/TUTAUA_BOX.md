# Tutaua Box

`com.yukista.tutaua.box` és l'únic HOME del dispositiu. La pantalla pública
ofereix Sèries i pel·lícules (`com.yukista.tutaua`) i TV (`tv.tutaua.app`).
BACK mantingut quatre segons obre el panell d'administració propi.

## Configuració central

El proveïdor `content://com.yukista.tutaua.box.config/config` està protegit pel
permís `com.yukista.tutaua.permission.READ_CONFIG` de nivell `signature`.
Només les APK signades amb la clau Tutaua poden llegir:

- `jellyfin.base_url`;
- `tv.api_base_url`;
- `tv.stream_url`;
- `updates.manifest_url`;
- `updates.channel`.

No hi ha endpoints compilats per defecte. HTTP local i HTTPS són vàlids. Les
sessions i tokens continuen dins de cada aplicació i no s'exposen pel proveïdor.

## Backend de laboratori

El catàleg LAN està desplegat a `http://192.168.1.139:8090`. És un contenidor
independent a `/opt/docker/tutaua-backend`; les dades són a `data/` i es munten
en mode només lectura. Les URLs dels artefactes del catàleg són relatives, de
manera que la IP efectiva depèn sempre de la configuració de cada caixa.

La descàrrega automàtica encara no està activada: abans cal signar el manifest,
implementar verificació/PackageInstaller i provar rollback.
