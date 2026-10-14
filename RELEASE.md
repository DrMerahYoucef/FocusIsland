# Publier Focus Island 2.2.2

Version : `versionName 2.2.2`, `versionCode 12` (dans `app/build.gradle.kts`).
À chaque nouvelle publication sur Google Play, augmentez `versionCode` d'au moins 1.

## 1. Avant de pousser sur GitHub

1. Appliquez le zip cumulatif, puis supprimez les fichiers listés dans `APPLY.md`.
2. Firebase console :
   - Firestore → Règles : collez `firestore.rules` → Publier.
   - Authentication → Méthodes de connexion : E-mail/Mot de passe activé, Anonyme activé (recommandé).
3. Clé de signature (Settings → Secrets and variables → Actions → Repository secrets).
   Une nouvelle clé « upload » a été créée le 13/10/2026 (fichiers `focus-island-upload.jks` et
   `focus-island-github-secrets.txt`, à garder hors du dépôt). Pour chacun des 4 secrets, cliquez sur
   le crayon (Update), collez la valeur du fichier texte, puis « Update secret » :
   `RELEASE_ALIAS`, `RELEASE_SIGNING_PASSWORD`, `RELEASE_KEY_PASSWORD`, `KEYSTORE_BASE64` (la longue ligne entière).
   Les téléphones qui ont une version signée avec l'ancienne clé doivent désinstaller l'app une fois.
   Si vous perdez la clé avant la première publication, recréez-en une ; après publication, c'est
   « Signature d'application par Google Play » qui vous permet de la remplacer.

## 2. Ce que fait la CI (`.github/workflows/build-and-sign.yml`)

À chaque push sur `main` :
1. lance les tests unitaires (une erreur bloque la publication) ;
2. construit l'APK et l'App Bundle en release, réduits par R8 ;
3. les signe avec votre clé ;
4. publie 3 artefacts : `FocusIsland-<version>-APK` (installation directe), `FocusIsland-<version>-AAB`
   (pour Google Play) et `FocusIsland-R8-mapping` (pour lire les rapports de plantage).

Les pull requests lancent seulement les tests.

## 3. Tester l'APK signé avant de publier

- Installez `FocusIsland-<version>-APK` sur un vrai téléphone (par-dessus l'ancienne version : même clé, données gardées).
- Parcours minimum : connexion et mode invité, une session de focus complète, mode Focus, création d'une
  carte par photo, révision, sauvegarde puis restauration des cartes, radio, blocage d'une app, widgets,
  téléphone en français puis en anglais, thème sombre, texte à 200 %.
- Si une fonction marche en debug mais pas en release, c'est presque toujours R8 : ajoutez une règle
  `-keep` dans `app/proguard-rules.pro` pour la classe concernée.

## 4. Google Play Console

1. Créez l'application (nom : Focus Island ; package : `com.focusisland`).
2. Production (ou Test interne d'abord) → Créer une version → envoyez `FocusIsland-<version>-AAB`.
   Dans la même version, envoyez `mapping.txt` (section « Fichiers de désobscurcissement »).
3. Recommandé : activez « Signature d'application par Google Play » et gardez votre clé actuelle comme clé d'importation.

### Déclarations à remplir

**Sécurité des données** (d'après le code de l'app) :

| Donnée | Collectée | Partagée | Pourquoi | Facultatif |
|---|---|---|---|---|
| Adresse e-mail | Oui (compte) | Non | Gestion du compte | Oui (mode invité) |
| Nom affiché | Oui | Visible par les autres membres (classement, amis) | Fonctions sociales | Oui |
| Statistiques de focus (minutes, sessions, série, points) | Oui | Visibles par les membres | Classement, synchronisation | Oui (invité = local) |
| Contenu des cartes (texte) | Oui, si connecté | Non | Synchronisation entre appareils | Oui |
| Photos de notes | Envoyées à Google Gemini seulement si l'utilisateur choisit « Lire avec Gemini » avec sa propre clé | Google (traitement) | Lecture du texte | Oui |
| Station de radio écoutée | Seulement si « Partager ce que j'écoute » est activé | Amis | Fonction sociale | Oui (désactivé par défaut) |

Restent sur le téléphone, jamais envoyés : enregistrements vocaux, photos des cartes, pages scannées
(le scanner de documents de Google tourne sur l'appareil, dans les services Google Play), défis, liste des
apps installées et données d'utilisation (bloqueur d'apps), notifications lues par le bloqueur.
Données chiffrées en transit : oui (HTTPS). Suppression possible : oui (Réglages → Supprimer le compte).

**Autorisations sensibles à justifier** :
- `PACKAGE_USAGE_STATS` (accès aux données d'utilisation) : détecter l'ouverture d'une app que l'utilisateur
  a choisi de bloquer pendant une session de focus. Usage local uniquement.
- `SYSTEM_ALERT_WINDOW` : afficher le rappel « Restez concentré » par-dessus l'app bloquée.
- Accès aux notifications (service `NotificationListenerService`) : masquer les notifications des apps choisies.
- `FOREGROUND_SERVICE_SPECIAL_USE` : le minuteur de focus et le bloqueur doivent tourner écran éteint.
- Caméra et micro : photographier ses notes, enregistrer une carte vocale.

**Politique de confidentialité** : Play l'exige (compte, caméra, micro). Elle doit reprendre le tableau ci-dessus,
le mode invité, la clé Gemini personnelle et la suppression du compte. Publiez-la sur une page web et collez son lien.

**Classification du contenu** : application éducative, contenu généré par les utilisateurs limité au nom
affiché (pas de messagerie).

## 5. Après la publication

- Surveillez « Plantages et ANR » dans Play Console les premiers jours (le mapping R8 rend les traces lisibles).
- Pour la version suivante : `versionCode = 13`, notes de version en français et en anglais.
