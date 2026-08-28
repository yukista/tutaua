# Tutaua OS per al Tanix TX5

Base de maquinari validada: Tanix TX5 `qurra`, Amlogic S905Y5, Android 14,
particions dinàmiques A/B.

## Principis

- Conservem kernel, HAL, còdecs i controladors Amlogic/Tanix.
- Eliminem Google, l'OTA Oranth per HTTP i les aplicacions preinstal·lades.
- Tutaua és l'únic launcher d'usuari, però manté una sortida administrativa local.
- Les actualitzacions finals utilitzaran l'slot inactiu A/B i paquets signats servits
  per HTTPS.
- El suport remot circula per una xarxa privada sortint. ADB mai s'exposa directament
  a Internet i queda desactivat per defecte en producció.

## Gestió remota prevista

1. Tutaua actua com a DPC/device owner del dispositiu dedicat.
2. Un manifest signat publica versions de Tutaua i de Tutaua OS.
3. Les APK s'instal·len amb `PackageInstaller`; les OTA completes s'apliquen a
   l'slot inactiu amb el mecanisme A/B d'Android.
4. El dispositiu informa de versió, slot, temperatura, espai, xarxa i darrer error.
5. El túnel de suport s'activa només amb política administrativa i permet diagnòstic
   i control remot autenticat.

La xarxa de gestió es basa en Headscale autoallotjat i el client Android oficial de
Tailscale. Cada caixa rep una identitat pròpia mitjançant una clau d'alta d'un sol ús.
Les ACL impedeixen trànsit entre caixes; només el servidor de gestió i les estacions
marcades com a suport hi poden accedir. Vegeu `deployment/headscale`.

El túnel no substitueix la confiança criptogràfica: Tutaua Manager només acceptarà
APK i OTA signades amb les claus de Tutaua. ADB queda tancat per defecte i una sessió
de suport futura haurà de tenir activació temporal, caducitat i registre d'auditoria.

## Estat del prototip

La primera imatge és de desenvolupament i arrencarà amb AVB/verity desactivat mentre
validem maquinari i paquets. No és una configuració de producció. Abans del desplegament
cal generar claus pròpies, provar rollback A/B i tancar ADB root.
