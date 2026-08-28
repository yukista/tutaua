# Tutaua Backend

Servei mínim de catàleg d'actualitzacions, dissenyat per funcionar dins la LAN.
No conté URLs fixes: `tutaua-box` rep la seva URL durant el provisionament o des
del panell d'administració.

Endpoints:

- `GET /health`
- `GET /v1/releases`
- `GET /v1/releases/{applicationId}/{channel}`
- `GET /artifacts/{filename.apk}`

El catàleg persistent és `data/releases.json` i les APK es desen a
`data/artifacts/`. Cada release ha d'incloure com a mínim `app`, `channel`,
`versionCode`, `versionName`, `url` i `sha256`. La signatura criptogràfica del
manifest s'afegirà abans d'activar instal·lacions automàtiques.
