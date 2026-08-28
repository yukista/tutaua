# Provisionament desatès del Tanix TX5

## Contracte

La caixa ha de tenir Android iniciat, xarxa configurada i ADB autoritzat. Des
d'aquest punt, `Provision-TX5.ps1` executa tot el procés sense intervenció:

1. identifica model, device, maquinari, build, RAM i mida d'eMMC;
2. rebutja unitats diferents del prototip validat;
3. instal·la les APK signades de Tutaua Box, Tutaua i Tutaua TV;
4. configura totes les URLs i autentica Jellyfin, desant només el token xifrat;
5. aplica les 22 quarantenes validades de fabricant i Google;
6. reinicia, reconnecta i valida HOME, Settings, WebView AOSP i xarxa;
7. escriu un informe JSON sense secrets;
8. tanca ADB TCP persistent, excepte si s'indica `-KeepLabAdb`.

El receptor Android de provisionament exigeix `android.permission.DUMP`, que el
compte `shell` d'ADB té però una aplicació ordinària no. La contrasenya només es
fa servir per obtenir un token Jellyfin i no es desa a la caixa ni a l'informe.

## Prova de compatibilitat sense canvis

```powershell
$credential = Get-Credential
.\scripts\tanix\Provision-TX5.ps1 `
  -Target '192.168.1.150:37123' `
  -BoxApk 'box\build\outputs\apk\release\box-release.apk' `
  -TutauaApk 'app\build\outputs\apk\release\app-release.apk' `
  -TvApk 'C:\ruta\tutaua-tv\app\build\outputs\apk\release\app-release.apk' `
  -JellyfinServer 'https://jellyfin.example' `
  -TvApiUrl 'http://192.168.1.80:5001' `
  -UpdateManifestUrl 'http://192.168.1.139:8090/v1/releases' `
  -Credential $credential `
  -WhatIf
```

## Una ordre per caixa

```powershell
.\scripts\tanix\Provision-TX5.ps1 `
  -Target '192.168.1.150:37123' `
  -BoxApk 'box\build\outputs\apk\release\box-release.apk' `
  -TutauaApk 'app\build\outputs\apk\release\app-release.apk' `
  -TvApk 'C:\ruta\tutaua-tv\app\build\outputs\apk\release\app-release.apk' `
  -JellyfinServer 'https://jellyfin.example' `
  -TvApiUrl 'http://192.168.1.80:5001' `
  -UpdateManifestUrl 'http://192.168.1.139:8090/v1/releases' `
  -Credential (Get-Credential)
```

El diàleg de credencial és previ; després d'acceptar-lo el procés és desatès. No
s'ha de passar una contrasenya literal a la línia d'ordres ni guardar-la al
repositori.

Per a lots, PowerShell permet exportar una credencial xifrada lligada al mateix
usuari i ordinador Windows:

```powershell
Get-Credential | Export-Clixml -LiteralPath "$env:LOCALAPPDATA\Tutaua\jellyfin.credential.xml"
$credential = Import-Clixml -LiteralPath "$env:LOCALAPPDATA\Tutaua\jellyfin.credential.xml"
.\scripts\tanix\Provision-TX5.ps1 -Target '192.168.1.150:37123' `
  -BoxApk 'box\build\outputs\apk\release\box-release.apk' `
  -TutauaApk 'app\build\outputs\apk\release\app-release.apk' `
  -TvApk 'C:\ruta\tutaua-tv\app\build\outputs\apk\release\app-release.apk' `
  -JellyfinServer 'https://jellyfin.example' `
  -TvApiUrl 'http://192.168.1.80:5001' `
  -UpdateManifestUrl 'http://192.168.1.139:8090/v1/releases' `
  -Credential $credential
```

`-AllowCompatibleHardware` permet una build diferent només quan model, device,
RAM i eMMC coincideixen. És una excepció de laboratori: una build nova s'ha de
validar abans d'usar-la en producció.

`-KeepLabAdb` manté ADB root al port 5555 i no s'ha d'usar en caixes desplegades.
