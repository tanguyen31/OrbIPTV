# Orbit IPTV

Lecteur IPTV pour **smartphone Android et Android TV** (un seul APK pour les deux), au design inspiré de Netflix et de l'Apple TV.
Orbit ne fournit aucune chaîne : il lit les accès de ton fournisseur (Xtream Codes ou playlist M3U).

## Télécharger l'APK

Chaque modification envoyée sur `main` recompile l'APK et le publie ici (lien direct, toujours la dernière version) :

**https://github.com/tanguyen31/OrbIPTV/releases/latest/download/OrbitIPTV.apk**

## Fonctions

**Accueil**
- Grande bannière animée avec les nouveautés (image, résumé, note, boutons Lecture / Plus d'infos)
- Rangées : Reprendre la lecture, Ma liste, Regardées récemment, Films et séries récents, Les mieux notés, catégories

**TV en direct**
- Catégories, favoris, numéros de chaîne, programme en cours avec barre de progression
- Sur TV : panneau de droite avec le guide (en cours, à suivre)

**Films et séries**
- Grilles d'affiches, filtres par catégorie, tri (récents, A à Z, mieux notés)
- Fiches détaillées : image de fond, résumé, durée, genre, note, réalisateur, casting, titres similaires
- Séries : saisons, épisodes avec vignettes, épisodes vus, reprise au bon épisode

**Lecteur**
- Commandes façon Apple TV, barre de progression au doigt ou à la télécommande
- Audio et sous-titres, format d'image (ajusté, remplir, zoom), vitesse de lecture
- Reprise automatique là où tu t'étais arrêté
- Épisode suivant automatique avec compte à rebours
- Zapping (haut/bas, CH+/CH-), liste des chaînes ou des épisodes en panneau latéral
- Double tap pour avancer ou reculer de 10 s, image dans l'image sur smartphone

**Et aussi**
- Recherche globale (chaînes, films, séries), insensible aux accents
- Ma liste (appui long sur n'importe quelle vignette, ou bouton « Ma liste »)
- Plusieurs sources, date d'expiration de l'abonnement
- Contrôle parental : masquage des catégories adultes protégé par code PIN
- Menu latéral et navigation complète à la télécommande sur Android TV

## Installer sur le smartphone

1. Ouvre le lien de l'APK et touche le fichier téléchargé.
2. Autorise l'installation depuis ton navigateur si Android le demande.

## Installer sur Android TV / Google TV / Fire TV

1. Installe l'appli **Downloader** sur la TV et autorise-la à installer des applis inconnues.
2. Saisis le lien de l'APK dans Downloader, puis **Installer**.

## Structure du code

```
app/src/main/java/com/orbit/iptv/
  data/     Modèles, client Xtream, lecteur M3U, stockage local, Repo
  ui/       MainActivity, navigation, menu, composants (cartes, boutons, rangées)
  ui/screens/  Accueil, Direct, Films/Séries, fiches, Recherche, Ma liste, Réglages, Ajout de source
  player/   Lecteur (ExoPlayer + interface Compose)
.github/workflows/build-apk.yml   compilation et publication automatiques
```

L'APK est signé avec une clé de développement, adaptée à un usage personnel.
