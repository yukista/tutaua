# Pla reversible de retirada de bloatware

Data de l'auditoria: 2026-08-20

## Evidència preservada

Els APK i `dumpsys package` són a `captures/tanix-apk-audit/20260820`.

| Paquet | SHA-256 de l'APK | Observació |
|---|---|---|
| `com.android.assistant.setting.pro` | `3FD499EA872D0611C9059E02F9678B0EF226083A2A3BA7B4B661615B00CAD8C2` | UID 1000, clau Android de prova, instal·la/esborra paquets, escriu secure settings, arrenca al boot i inicia els dos serveis RSS. |
| `com.hopper.rsshub.stb` | `A85B10EAFA85D6D30D0B760DCC84C906271DD2E906672592D61629284F9186BD` | UID 1000, clau Android de prova, overlay/reboot/boot, servei persistent `MyForegroundService`. |
| `com.core.rss` | `D070FD928B8AE06E6532232904C6BBAC0BDC21CE078E90295C5C1CBC11DE5E97` | UID d'app, certificat tercer, reboot/boot, servei persistent `SystemEmsService` i connexions externes observades. |
| `com.systemguard.android` | `579D07EB81F22E665ABD9AE87F6B8738DBEB28D2FEB771D4F443B57E8F600549` | Certificat tercer, overlay/reboot/boot/control multimèdia; ja estava desactivat. |

En la captura en viu, `com.core.rss` tenia sessions TCP amb `31.13.83.52:443`
i `43.173.111.241:198/279`. No s'atribueixen aquests IP sense més evidència.

## Ordre de laboratori

1. [x] `com.core.rss`: desactivat; Tutaua, Settings, xarxa, reproducció i reinici
   validats.
2. [x] `com.hopper.rsshub.stb`: desactivat; servei absent i reinici validat.
3. [x] `com.android.assistant.setting.pro`: desactivat després de provar que
   `android.settings.SETTINGS` obre directament el Settings real i que BACK torna
   a Tutaua; reinici validat.
4. [x] `com.systemguard.android`: continua desactivat.

Cada canvi usa `pm disable-user --user 0`, que no esborra ni APK ni dades. El
script `Restore-SuspiciousPackages.ps1` reverteix els quatre estats. No s'ha de
fer `pm uninstall`, editar `/system` ni incloure aquests APK en una ROM final.

## HOME reversible

El launcher Google té una prioritat HOME superior a Tutaua i `set-home-activity`
no la substitueix en aquesta build. `Set-TutauaHome.ps1` comprova que Tutaua sigui
candidat HOME, desactiva només `com.google.android.apps.tv.launcherx`, verifica
resolver i focus, i restaura automàticament el launcher si falla. La reversió
manual és `Restore-StockHome.ps1`. No s'han d'eliminar les dades del launcher.

La configuració s'ha validat amb botó HOME físic i després de diversos reinicis:
`com.google.android.apps.tv.launcherx` continua desactivat i el resolver HOME és
`com.yukista.tutaua/.SplashActivity`.

## Criteris després de cada paquet

- Tutaua resol i continua sent HOME.
- Settings resol a una activitat existent.
- comandament, Wi-Fi, àudio i vídeo funcionen;
- no apareix pantalla negra després de tornar de Settings;
- ADB continua disponible durant la prova de laboratori;
- reinici controlat i prova prolongada abans del paquet següent.
