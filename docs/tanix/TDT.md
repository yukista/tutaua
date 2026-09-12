# Tutaua TDT

Tutaua TDT (`com.yukista.tutaua.tdt`) és un reproductor exclusiu per a les
Tutaua Box Tanix TX5. No té cap catàleg incorporat: el dispositiu rep un bloc
`tutaua_tdt` versionat mitjançant Tutaua Fleet i conserva l'última configuració
vàlida localment.

## Configuració remota

Al gestor de Fleet, editeu la configuració desitjada del dispositiu i publiqueu
un bloc com aquest. L'ordre de `channels` és l'ordre de zàping; `position` es
fa servir com a ordre explícit si cal reordenar-lo des d'una eina automàtica.

```json
{
  "tutaua_tdt": {
    "epg_url": "https://www.tdtchannels.com/epg/TV.json",
    "channels": [
      {
        "id": "la-1",
        "name": "La 1",
        "position": 1,
        "visible": true,
        "epg_id": "La1.TV",
        "streams": [
          { "type": "hls", "url": "https://rtvelivestream.rtve.es/rtvesec/la1/la1_main_dvr.m3u8" }
        ]
      },
      {
        "id": "3catinfo",
        "name": "3CatInfo",
        "position": 2,
        "visible": true,
        "epg_id": "324.TV",
        "streams": [
          { "type": "hls", "url": "https://directes-tv-es.3catdirectes.cat/live-content/canal324-hls/master.m3u8" }
        ]
      }
    ]
  }
}
```

Una publicació nova arriba amb el batec normal del Manager. La Box propaga la
configuració a Tutaua TDT i l'app recarrega canals i EPG sense reinstal·lar-se.
Cada canal pot tenir diverses fonts dins de `streams`; si falla la primera, el
reproductor prova la següent.

## Publicació de l'APK

La versió d'aquest mòdul es publica exactament igual que la resta d'APK. Després
de generar una release signada, incloeu `tdt-release.apk` a
`Prepare-FleetArtifacts.py` junt amb Box i Manager, i executeu
`Publish-FleetArtifacts.py`. Fleet només ofereix la nova versió a les TX5 que
ja tenen instal·lat `com.yukista.tutaua.tdt`; Manager en verifica hash,
certificat i versió abans d'instal·lar-la.
