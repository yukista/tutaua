# Tutaua

Tutaua és un client independent per a servidors Jellyfin, dissenyat per a televisors Android i navegació amb comandament.

## Funcions

- Connexió a servidors Jellyfin locals per HTTP i públics per HTTPS.
- Autenticació amb token protegit per Android Keystore; la contrasenya no es desa.
- Inici editorial, pel·lícules, sèries, episodis, cerca, favorits i continuar veient.
- Reproducció directa o HLS amb selecció d’àudio i subtítols.
- Integració amb segments d’introducció i crèdits quan el servidor els proporciona.
- Navegació completa amb D-pad i controls de reproducció adaptats al televisor.
- Sense anuncis, analítica, Google Play Services ni compres integrades.

## Compilació de desenvolupament

```powershell
.\gradlew.bat assembleDebug
```

## Mantenir GitHub actualitzat

Aquest repositori és la font de veritat i s'ha de mantenir sempre sincronitzat
amb GitHub. Cap canvi no es dona per acabat si no està pujat:

- treballar sempre a la branca principal i fer `git push` de tots els canvis
  validats abans de tancar la sessió;
- no pujar secrets, claus, APK, bases de dades, captures ni artefactes locals;
  comprovar-ho amb `git status` i l'escàner de secrets del projecte;
- si una tasca queda a mitges, pujar igualment l'estat de treball i anotar-ho a
  la documentació del projecte.

## Avís d’independència

Tutaua és un projecte independent i no està afiliat, patrocinat ni avalat pel projecte Jellyfin. Jellyfin és una marca dels seus titulars respectius. Tutaua només s’ha d’utilitzar per accedir a contingut que l’usuari tingui dret a reproduir.
