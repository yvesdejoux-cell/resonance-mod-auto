# Guide de soumission — Play Console

Tout ce que je ne peux pas remplir à ta place (compte/formulaires interactifs), avec les réponses à donner.

## Fichiers prêts dans ce dossier (`store/`)

- `icon-512.png` — icône haute résolution (512×512)
- `feature-graphic-1024x500.png` — image de fonctionnalité (1024×500)
- `screenshot_login.png`, `screenshot_1_home.png`, `screenshot_2_player.png`, `screenshot_3_settings.png` — captures d'écran téléphone
  - ⚠️ Les captures `_home`/`_player`/`_settings` viennent du projet original et affichent encore "Resonance" (pas "Resonance Mod Auto") dans l'interface. Idéalement, remplace-les par tes propres captures une fois connecté à ton serveur — elles montreront alors le français et les nouveaux boutons.
- `short_description.txt` — description courte (72/80 caractères)
- `full_description.txt` — description longue (1716/4000 caractères)
- `privacy_policy.html` — politique de confidentialité (publiée : voir lien fourni dans le chat)

Le fichier `.aab` signé a été envoyé séparément dans la conversation.

## Fiche du Store — Store listing

1. **Nom de l'app** : `Resonance Mod Auto`
2. **Description courte / longue** : copier-coller depuis les fichiers `.txt` ci-dessus
3. **Icône** : `icon-512.png`
4. **Image de fonctionnalité** : `feature-graphic-1024x500.png`
5. **Captures d'écran** : au moins 2, format téléphone

## Content rating (questionnaire de classification)

Catégorie : **Utilitaire / Musique et audio**

Réponds "Non" à toutes les questions concernant :
- Violence, contenu sexuel, langage grossier, contenu effrayant
- Jeux d'argent simulés
- Partage de localisation
- Interaction utilisateur (chat, contenu généré par les utilisateurs)
- Achats intégrés

C'est un lecteur de musique personnel sans réseau social ni contenu généré par les utilisateurs → classification attendue : **Tout public / PEGI 3**.

## Data safety (sécurité des données)

Réponds **"Non, cette application ne collecte ni ne partage aucune donnée utilisateur"** à la question principale.

Justification (à donner si demandé en détail) : l'app ne contient aucun SDK d'analytics, de publicité, ni de crash reporting tiers. Les seules données réseau échangées sont entre l'appareil et le serveur Navidrome/Subsonic que l'utilisateur configure lui-même — elles ne transitent jamais par un serveur de l'éditeur.

## Politique de confidentialité

Colle l'URL fournie dans le chat (ou ton URL sur `ydjixi.fr` si tu héberges `privacy_policy.html` toi-même) dans le champ correspondant de la Play Console.

## Coordonnées développeur

- Email de contact : `support@ydjixi.fr`
- Site web (optionnel) : `https://ydjixi.fr` ou lien vers le dépôt GitHub

## Déclaration de conformité GPL / propriété du code

Si la Play Console (ou une vérification manuelle Google) pose une question sur l'origine du code : c'est un fork open source sous licence GPLv3 du projet **Resonance** par **AlanHuang99**. Code source complet disponible publiquement : `https://github.com/yvesdejoux-cell/resonance-mod-auto`.

## Release — App Bundle

Uploader `app-release.aab` (envoyé dans le chat) dans "Production" (ou "Test fermé" si tu préfères tester avant publication complète).

## ⚠️ Dernier rappel critique

Le fichier `release-keystore.jks` et son mot de passe (déjà communiqués dans le chat) doivent être sauvegardés en lieu sûr **avant** de continuer. Sans eux, impossible de publier une future mise à jour de cette fiche.
