# Tutaua Box

`com.yukista.tutaua.box` és l'únic HOME del dispositiu. La pantalla pública
ofereix Sèries i pel·lícules (`com.yukista.tutaua`) i TV (`tv.tutaua.app`).
Una icona d'engranatge discreta al peu de la pantalla obre el panell
d'administració propi; abans calia mantenir BACK quatre segons.

## Configuració central

El proveïdor `content://com.yukista.tutaua.box.config/config` està protegit pel
permís `com.yukista.tutaua.permission.READ_CONFIG` de nivell `signature`.
Només les APK signades amb la clau Tutaua poden llegir:

- `jellyfin.base_url`;
- `jellyfin.username`;
- `tv.api_base_url`;
- `tv.stream_url`;
- `updates.manifest_url`;
- `updates.channel`.

No hi ha endpoints compilats per defecte. HTTP local i HTTPS són vàlids. Les
sessions i tokens continuen dins de cada aplicació i no s'exposen pel proveïdor.

## Credencials remotes de Jellyfin

Fleet pot definir `jellyfin.username` i `jellyfin.password` a la configuració
desitjada. La contrasenya s'envia al Box pel canal autenticat del pla de control
i s'hi desa xifrada amb una clau Android Keystore no exportable
(`BoxCredentials`); **no s'exposa mai** pel proveïdor de configuració ni a la
configuració reportada. El Box envia les credencials a l'app Tutaua per un
broadcast protegit pel permís de signatura `com.yukista.tutaua.permission.MANAGE_BOX`
(`com.yukista.tutaua.action.APPLY_CREDENTIALS`); l'app autentica, desa només el
token xifrat i fa auto-login. A l'arrencada de la Box, `HomeActivity` reenvia les
credencials; si l'app no té sessió, pot demanar-les a la Box amb
`com.yukista.tutaua.box.action.REQUEST_CREDENTIALS` (permís `READ_CONFIG`).

## Backend de laboratori

El catàleg LAN està desplegat a `http://192.168.1.139:8090`. És un contenidor
independent a `/opt/docker/tutaua-backend`; les dades són a `data/` i es munten
en mode només lectura. Les URLs dels artefactes del catàleg són relatives, de
manera que la IP efectiva depèn sempre de la configuració de cada caixa.

La descàrrega automàtica encara no està activada: abans cal signar el manifest,
implementar verificació/PackageInstaller i provar rollback.
