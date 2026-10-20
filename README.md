# Orbit IPTV

Lecteur IPTV pour **smartphone Android et Android TV** (un seul APK pour les deux).
Orbit ne fournit aucune chaîne : il lit les accès de ton fournisseur (Xtream Codes ou playlist M3U).

## Fonctions

- Connexion **Xtream Codes** (serveur, identifiant, mot de passe) ou **lien M3U/M3U8**
- Onglets **TV en direct**, **Films**, **Favoris**
- Catégories, recherche, logos des chaînes
- Favoris : appui long sur une chaîne (ou bouton étoile dans le lecteur)
- Lecteur ExoPlayer : HLS, TS, MP4, MKV ; nouvelle tentative automatique dans un autre format si un flux échoue
- Zapping : flèches haut/bas ou touches CH+/CH- de la télécommande, boutons ◀ ▶ sur le téléphone
- Programme en cours et suivant (EPG) pour les comptes Xtream
- Interface pensée pour la télécommande sur Android TV (contour ambre sur l'élément sélectionné)
- Liste des chaînes gardée en cache : ouverture instantanée, bouton « Actualiser » pour recharger

## Obtenir l'APK sans rien installer (depuis ton téléphone)

1. Crée un compte sur **github.com** si tu n'en as pas.
2. Crée un nouveau dépôt (bouton **+** puis **New repository**), privé si tu préfères.
3. Sur la page du dépôt : **Add file** → **Upload files**, puis envoie **tout le contenu** du dossier `OrbitIPTV`
   (y compris le dossier caché `.github`). Valide avec **Commit changes**.
   - Astuce : si l'envoi depuis le téléphone ne prend pas les dossiers, fais-le depuis un ordinateur,
     ou utilise l'appli GitHub Desktop.
4. Ouvre l'onglet **Actions** : la compilation « Compiler l'APK » démarre seule (5 à 8 minutes).
5. Quand elle est verte, clique dessus, puis télécharge **OrbitIPTV-apk** en bas de la page.
   Tu obtiens un `.zip` qui contient `OrbitIPTV.apk`.

## Installer sur le smartphone

1. Ouvre le zip et touche `OrbitIPTV.apk`.
2. Android demande d'autoriser l'installation depuis ton navigateur ou ton gestionnaire de fichiers :
   accepte (**Paramètres → Autoriser cette source**).
3. Installe, puis ouvre **Orbit IPTV** et saisis tes accès.

## Installer sur Android TV / Google TV / Fire TV

Méthode simple avec l'appli **Downloader** (gratuite sur le Play Store de la TV et sur Fire TV) :

1. Mets `OrbitIPTV.apk` en ligne quelque part (Google Drive avec lien public, ou ton propre hébergement).
2. Sur la TV, installe **Downloader**, autorise-la à installer des applis inconnues
   (**Paramètres → Applications → Sécurité → Sources inconnues → Downloader**).
3. Saisis le lien de l'APK dans Downloader, puis **Installer**.

Autres méthodes : l'appli **Send Files to TV** (téléphone → TV en Wi-Fi), une clé USB avec un
gestionnaire de fichiers sur la TV, ou `adb install OrbitIPTV.apk` depuis un ordinateur.

L'appli apparaît sur l'écran d'accueil de la TV avec sa bannière.

## Compiler avec Android Studio (optionnel)

Ouvre le dossier `OrbitIPTV` dans Android Studio, attends la synchronisation, branche le téléphone
en mode débogage USB et clique sur ▶. Ou en ligne de commande : `./gradlew assembleRelease`.

## Structure

```
app/src/main/java/com/orbit/iptv/
  data/   Models, Http, M3uParser, XtreamClient, Repository (source, cache, favoris)
  ui/     SetupActivity (connexion), BrowseActivity (navigation), PlayerActivity (lecture), Adapters
app/src/main/res/  layouts (portrait + paysage/TV), thème, icône, bannière TV
.github/workflows/build-apk.yml  compilation automatique
```

## Remarques

- L'APK est signé avec une clé de développement : parfait pour ton usage personnel.
  Pour une publication sur le Play Store, il faudra créer ta propre clé de signature.
- Les identifiants Xtream sont stockés sur l'appareil, dans les préférences de l'application.
- Séries Xtream, enregistrement et contrôle parental ne sont pas encore inclus.
