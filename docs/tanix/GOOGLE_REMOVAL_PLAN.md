# Retirada gradual de Google

## Dependències observades

- Tutaua usa `com.android.webview` 116, no Chrome ni un WebView Google.
- HOME és `com.yukista.tutaua/.SplashActivity`.
- `com.google.android.katniss` conserva el rol ASSISTANT i proporciona la veu.
- `com.google.android.tv.remote.service` escolta als ports locals 6466/6467. És
  el comandament Google per LAN i no controla el receptor IR físic ni els
  dispositius d'entrada Bluetooth.
- GMS, GSF i Backup Transport comparteixen UID 10023; no es desactiven dins el
  primer grup.

## Tier 1 — interfície i configuració inicial

El script `Disable-GoogleTier1.ps1` posa en quarantena, sense esborrar dades:

- AtvWidget, Backdrop i LandscapeWallpaper;
- sincronitzadors Google de calendari i contactes;
- OneTimeInitializer, PartnerSetup i SetupWizard;
- TVRecommendations i LeanbackLauncher.

`Restore-GoogleTier1.ps1` restaura tot el grup. Cal validar Settings, HOME,
comandament, Wi-Fi, reproducció, àudio i reinici abans de continuar.

Estat de laboratori: **completat i validat després de reinici**. Els 10 paquets
continuen `disabled-user`, Tutaua continua sent HOME, Settings resol correctament
i no hi ha processos del grup actius.

## Components conservats deliberadament

- GoogleExtShared i GoogleExtServices no executen els processos GMS/GSF ni són
  un canal d'actualització equivalent a Play Store o ConfigUpdater. Es conserven
  mentre no hi hagi evidència d'interferència.
- Els overlays Droidlogic/Google TV i Connected Devices poden afectar Settings,
  Bluetooth o HDMI-CEC. No es desactiven a cegues amb `pm`.

L'objectiu de laboratori és impedir actualitzacions i interferències de Google,
no eliminar cada APK pel nom. Els components inerts es mantenen quan retirar-los
no aporta un benefici funcional o de seguretat clar.

## Tier 2 — botiga, configuració remota Google i còpies

Sense comptes configurats, es posen en quarantena reversible:

- `com.android.vending` (Play Store);
- `com.google.android.configupdater`;
- `com.google.android.backuptransport`.

Els scripts són `Disable-GoogleTier2.ps1` i `Restore-GoogleTier2.ps1`. GMS i GSF
es conserven en aquest pas malgrat compartir UID amb BackupTransport.

Estat de laboratori: **completat i validat després de reinici**. Els tres paquets
continuen `disabled-user`; Tutaua, Settings i WebView AOSP funcionen.

## Assistent de Google

Després de validar físicament que BACK llarg obre Settings i HOME torna a Tutaua,
`Disable-GoogleAssistant.ps1` pot desactivar `com.google.android.katniss`.
`Restore-GoogleAssistant.ps1` el restaura. Això elimina la via de veu Google però
no el servei de comandament remot de la xarxa local.

Estat de laboratori: **completat i validat després de reinici**. El rol ASSISTANT
queda buit; BACK llarg, Settings, HOME, comandament IR, reproducció i àudio han
estat validats.

## Comandament Google per LAN

`Disable-GoogleRemoteService.ps1` posa en quarantena
`com.google.android.tv.remote.service`; `Restore-GoogleRemoteService.ps1` el
restaura. El servei manté processos en primer pla, integra serveis de GSF i
escolta als ports TCP 6466/6467. No proporciona accés remot fora de la LAN i no
forma part del camí d'entrada del comandament IR del Tanix.

Abans de reiniciar cal validar físicament HOME, BACK llarg, navegació del
comandament, reproducció i àudio. La gestió remota de producció es resoldrà amb
una VPN privada i eines pròpies, no exposant ADB a Internet.

Estat de laboratori: **completat i validat després de reinici**. El paquet
continua `disabled-user`, els ports 6466/6467 i el procés han desaparegut, i
HOME, Settings, comandament IR, reproducció i àudio continuen funcionant.

## Nucli de serveis Google

`com.google.android.gms` i `com.google.android.gsf` comparteixen l'UID 10023 i
es proven conjuntament amb `Disable-GoogleCore.ps1`. Tutaua no declara cap
biblioteca Google, només el permís `INTERNET`, i usa `com.android.webview`.
`Restore-GoogleCore.ps1` restaura GSF i GMS en aquest ordre.

No s'inclouen en aquest grup els overlays Droidlogic, Connected Devices ni
GoogleExtShared/GoogleExtServices. Cal validar xarxa, Settings, HOME, comandament,
vídeo i àudio abans i després del reinici.

Estat de laboratori: **completat i validat després de reinici**. GMS i GSF
continuen `disabled-user`, no hi ha processos `com.google.android.gms` ni
`com.google.process`, i HOME, Settings, WebView AOSP, xarxa, comandament, vídeo i
àudio continuen funcionant.

## Criteri de tancament

La retirada gradual de Google queda tancada mentre es mantinguin desactivats la
Play Store, ConfigUpdater, Backup Transport, GMS, GSF, launchers, assistent i
servei remot Google. Qualsevol canvi futur ha de respondre a una interferència
observada o formar part d'una imatge de sistema reproduïble i recuperable.
