# Full de ruta de Tutaua Appliance

## Fase 0 — Congelació i evidència

- [x] Aturar escriptures al dispositiu.
- [x] Registrar identitat Android i USB DNL.
- [x] Calcular el hash de `super.img` i afegir-lo al manifest.
- [x] Copiar el backup a un segon suport i tornar-ne a verificar els hashes.
  Còpia verificada a `C:\Tutaua-backups\TX5-20260820-143837`.
- [x] Posar codi, configuració i documentació sota control de versions, excloent
  secrets i imatges grans.

## Fase 1 — Recuperació stock

- [ ] Obtenir el paquet oficial exacte `TX5_S905Y5-20251111` o un paquet nou
  certificat explícitament per a la mateixa revisió.
- [x] Registrar la font oficial actual i descartar els mirrors/enllaços reciclats
  coneguts (`FIRMWARE_SOURCES.md`).
- [ ] Identificar revisió de placa i xip físic de ràdio. RAM identificada per
  bootloader: 2 GB DDR3; eMMC de 16 GB identificada com `HAG2e`; ràdio informada
  lògicament com `aml_w1`.
- [x] Capturar i validar GPT primària/secundària, mapa `by-name`,
  `/proc/partitions`, primers/darrers sectors i `mmcblk0boot0/1`.
- [ ] Validar i inventariar el paquet sense flashejar.
- [ ] Provar restauració completa en una unitat de laboratori.

## Fase 2 — Baseline reversible

- [ ] Restaurar stock conegut.
- [ ] Auditar APK, serveis, permisos privilegiats, init scripts, certificats i
  connexions de xarxa.
- [ ] Definir una allowlist mínima per a la futura imatge controlada.
- [x] Desactivar components d'un en un amb proves entre canvis.
- [x] Posar en quarantena reversible la cadena RSS del fabricant i els canals
  Google d'actualització o interferència, amb proves funcionals i reinicis.
- [ ] Executar una prova d'estabilitat de 72 hores.

## Fase 3 — Tutaua Manager i kiosk

- [ ] Separar la interfície Tutaua del component privilegiat de gestió.
- [ ] Provisionar device owner sense comptes personals.
- [x] Fer Tutaua HOME efectiva a la build de laboratori desactivant el launcher
  Google de manera reversible; validat amb comandament i després de reinici.
- [ ] Configurar lock-task i restriccions necessàries.
- [ ] Implementar sortida administrativa amb combinació i PIN.
- [ ] Implementar watchdog i fallback si Tutaua no inicia.
- [ ] Mapar botons en una capa configurable i recuperable.
- [ ] Corregir/validar UDC Android (`fe350000.crgudc`) per disposar d'ADB USB de
  manteniment; la ROM stock només enumera USB en DNL.
- [x] Preparar provisionament d'una ordre a partir d'un ADB ja autoritzat, amb
  preflight de maquinari, login Jellyfin, reinici, validació i informe.

## Fase 4 — Gestió remota (ajornada)

- [ ] Desplegar Headscale darrere HTTPS.
- [x] Validar localment la configuració i les ACL amb Headscale 0.29.3.
- [ ] Crear identitat individual i ACL per caixa.
- [ ] Implementar heartbeat, inventari, logs i health checks.
- [ ] Implementar ordres remotes signades i auditades.
- [ ] Implementar suport temporal; ADB tancat per defecte.
- [x] Preparar activació/desactivació reversible d'ADB TCP fix per al laboratori;
  queda expressament prohibit com a configuració de producció.
- [ ] Avaluar control visual remot separat d'ADB.

## Fase 5 — Actualització

- [ ] Definir primer el model d'actualització pròpia de l'APK: signatura estable,
  manifest signat, descàrrega verificada, instal·lació i rollback.
- [ ] APK signada, canals, staged rollout i rollback d'aplicació.
- [ ] Build de sistema reproduïble i claus AVB pròpies.
- [ ] Generar `target_files` i OTA completa.
- [ ] Validar `update_engine`, snapshots Virtual A/B i boot control.
- [ ] Provar tall d'alimentació, OTA fallida i rollback automàtic.

## Fase 6 — Producció

- [ ] Provisionament per dispositiu sense secrets compartits.
- [ ] Inventari de sèrie, client, ubicació i versions.
- [ ] Pilot reduït abans de desplegament general.
- [ ] Runbook d'incidències i procés de substitució física.
