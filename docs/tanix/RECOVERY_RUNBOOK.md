# Runbook de recuperació del Tanix TX5

Estat: **NO AUTORITZA ENCARA CAP ESCRIPTURA**.

## Regles

- No usar `erase`, `burn`, `flash`, `download`, `wipe` ni `factory reset` fins
  que totes les portes de validació estiguin aprovades.
- No assumir compatibilitat només perquè el SoC sigui S905Y5.
- No desactivar la verificació de bootloader a l'eina Amlogic.
- No alimentar el dispositiu simultàniament des de dues fonts USB.
- Conservar sempre una via local de recuperació; ADB remot no és una via de
  recuperació de primer nivell.

## Modes coneguts

### Android normal

- ADB sense fil usa un port TLS efímer i pot quedar desactivat després d'un reboot.
- La IP observada no s'ha de considerar fixa.

### Android Recovery

- Build recovery `userdebug/test-keys`.
- Menú amb `Apply update from ADB`, SD i Udisk, fastbootd, rescue i wipes.
- El comandament original pot no funcionar; preveure teclat USB.

### Fastbootd/recovery fastboot

- VID/PID previst per propietats Android: Google `18d1`, PID recovery fastboot
  `4ee0`.
- Aquest mode és diferent del DNL Amlogic.

### Amlogic DNL

- VID/PID observat: `1b8e:c004`.
- Interfície USB: vendor-specific `ff/42/03`.
- Identificació observada: protocol 6, U-Boot/TPL.
- Accepta consultes mínimes i `reboot`, però no implementa les variables de
  partició del fastboot Android estàndard.

## Artefactes obligatoris per a una restauració

- Firmware oficial complet compatible amb la revisió exacta.
- SHA-256 del fitxer descarregat i registre de la URL/origen/data.
- Eina Amlogic compatible amb S905Y5, preferentment la versió indicada pel
  fabricant del firmware.
- Controlador USB corresponent. El WinUSB instal·lat amb Zadig pot haver de ser
  substituït temporalment pel controlador de l'eina Amlogic.
- Dumps locals verificats i una segona còpia fora del disc de treball.

## Validació del paquet stock sense flashejar

1. Comprovar mida i SHA-256.
2. Importar-lo a l'eina Amlogic sense connectar el dispositiu.
3. Confirmar que el paquet es parseja sense errors.
4. Inventariar DDR/BL2/BL30/BL31/U-Boot, DTB i particions.
5. Comparar model, build, capacitat i identificadors amb `getprop.txt` i els dumps.
6. Extreure les particions quan l'eina ho permeti i comparar-ne els hashes amb
   la còpia del dispositiu.
7. Registrar les opcions exactes de l'eina; no marcar opcions d'esborrat o de
   substitució del bootloader sense una justificació explícita.

## Prova futura de restauració

La primera escriptura s'ha de fer preferentment en una segona unitat. La prova ha
d'incloure: flaix stock complet, primer boot, Wi-Fi, Ethernet, Bluetooth, comandament
IR/BT, HDMI/CEC, vídeo 4K, àudio, reinici, recovery i reentrada a DNL.

Només després d'aquesta prova el paquet es pot considerar una xarxa de seguretat
per al dispositiu principal.
