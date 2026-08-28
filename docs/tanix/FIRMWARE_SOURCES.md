# Registre de fonts de firmware del Tanix TX5

Data de comprovació: 2026-08-20

## Font acceptada per descarregar i inspeccionar

| Estat | Model | SoC | Versió publicada | Font | Identificador |
|---|---|---|---|---|---|
| Pendent de descàrrega | Tanix TX5 | Amlogic S905Y5 | 2026.03.25 | Centre de firmware oficial de Tanix | Google Drive `17kjabRCmTr4rbrl810qY5fGPYbJtQomF` |

Pàgina canònica: <https://www.tanixtvbox.com/firmware-centre/>

L'accés a Drive va retornar temporalment quota excedida. Que sigui una font
oficial permet descarregar-la per inspeccionar-la, però **no demostra** que sigui
compatible amb aquesta unitat de 2 GB DDR3 / 32 GB ni autoritza a flashejar-la.
Abans d'usar-la cal registrar SHA-256, validar el contenidor Amlogic i comparar
els blobs DDR, DTB, ràdio i particions amb el dispositiu.

## Firmware exacte observat al dispositiu

- Build: `TX5_S905Y5-20251111` / `eng.fjq.20251111.100710`
- Estat: encara no s'ha localitzat un paquet complet oficial conservat.
- Prioritat: demanar-lo al fabricant amb la revisió 2 GB DDR3 / 32 GB i el número
  de sèrie si la versió publicada el 2026 no prova compatibilitat exacta.

## Fonts descartades

| Font | Motiu |
|---|---|
| `https://mega.nz/folder/T5UURCbL#a_he_ATNuFX3SsgsbJYhGA` | L'enllaç intermedi `2lnks.com/go/20d` ha estat reciclat: només conté carpetes Android 8.1 i Android 9, incompatibles amb el TX5 S905Y5 Android 14. |
| Mirrors que exigeixen registre o no publiquen hash | Origen, integritat i compatibilitat no verificables. |

## Regla operativa

Cap fitxer passa de «candidat» a «restauració» només pel nom. Cal conservar la
URL d'origen, data, mida i SHA-256; `Inspect-AmlogicFirmware.ps1` ha de reconèixer
el contenidor; i tots els bloquejadors de `ROADMAP.md` han d'estar resolts.
