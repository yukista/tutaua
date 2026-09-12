# Provisionament desatès del Tanix TX5

Per configurar connexions locals o remotes, consulteu [CONNECTIONS.md](CONNECTIONS.md).

## Abans de començar

A la caixa nova només cal completar l'assistent sense compte de Google, connectar-la
a la xarxa, activar les opcions de desenvolupador i habilitar la depuració ADB sense
fil. Anota l'adreça `IP:port` que mostra Android i accepta l'empremta de l'ordinador.

A partir d'aquí una sola execució:

1. valida el maquinari i activa temporalment ADB al port 5555;
2. neutralitza de manera reversible el programari de fabricant i Google validat;
3. instal·la i configura les sis aplicacions Tutaua;
4. converteix Tutaua Manager en aplicació privilegiada del sistema;
5. autentica Jellyfin i desa només el token xifrat;
6. crea un codi d'un sol ús i enrola la caixa a Tutaua Fleet;
7. reinicia, valida el resultat i genera un informe JSON sense secrets;
8. tanca ADB persistent, llevat que s'indiqui `-KeepLabAdb`.

La build `eng.fjq.20250814.110011` reactiva sis paquets Google en arrencar. Per
aquesta build, el script en desa una còpia sota `device-backups`, prepara overlayfs
i reanomena les APK de sistema. És reversible amb
`Restore-AugustGoogleSystemApks.ps1`.

## Preparació única de l'ordinador

Les credencials es desen xifrades amb DPAPI i només funcionen per al mateix usuari
de Windows. El secret de provisionament de Fleet ja és a
`%LOCALAPPDATA%\Tutaua\fleet-provisioning.credential.xml`.

```powershell
$jellyfin = [pscredential]::new(
  'Cal Weasley',
  (ConvertTo-SecureString 'Canelons23' -AsPlainText -Force)
)
$jellyfin | Export-Clixml "$env:LOCALAPPDATA\Tutaua\jellyfin.credential.xml"
```

## Una ordre per caixa

Canvia només `-Target` per l'adreça que mostra la caixa:

```powershell
$jellyfin = Import-Clixml "$env:LOCALAPPDATA\Tutaua\jellyfin.credential.xml"
$storedFleet = Import-Clixml "$env:LOCALAPPDATA\Tutaua\fleet-provisioning.credential.xml"
$fleetKey = if ($storedFleet -is [pscredential]) { $storedFleet.Password } else { $storedFleet }

.\scripts\tanix\Provision-TX5.ps1 `
  -Target '192.168.1.150:37123' `
  -BoxApk 'box\build\outputs\apk\release\box-release.apk' `
  -TutauaApk 'app\build\outputs\apk\release\app-release.apk' `
  -TvApk 'build\tmp\tutaua-tv-1.0.3.apk' `
  -TdtApk 'tdt\build\outputs\apk\release\tdt-release.apk' `
  -GamesApk 'games\build\outputs\apk\release\games-release.apk' `
  -ManagerApk 'manager\build\outputs\apk\release\manager-release.apk' `
  -JellyfinServer 'http://192.168.1.139:8096' `
  -TvApiUrl 'http://192.168.1.80:5001' `
  -UpdateManifestUrl 'http://192.168.1.139:8090/v1/releases' `
  -Credential $jellyfin `
  -ControlServer 'https://tutaua-app.duckdns.org/control' `
  -ControlLanAddress '192.168.1.139' `
  -ProvisioningApiKey $fleetKey `
  -ScreenOffTimeoutMinutes 60 `
  -GamesEnabled $false `
  -AllowCompatibleHardware
```

La caixa queda gestionable tant dins com fora de la xarxa local. El Manager només
fa connexions HTTPS sortints; no cal obrir ADB ni cap port d'entrada a la caixa.
Les versions publicades al canal `stable` es descarreguen, se'n valida la signatura
i el hash, i s'instal·len silenciosament en el següent batec.

`-AllowCompatibleHardware` admet la build d'agost validada quan model, device, RAM
i eMMC coincideixen. Una build desconeguda s'ha de validar abans de producció.

`-KeepLabAdb` manté ADB root al port 5555 i és només per a laboratori.
