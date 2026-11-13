# Metin TV

*(Metin se prononce « Métine ».)*

Application **Metin** pour **Google TV** : elle ouvre https://metin-ia.fr en plein écran.

- Navigation avec les flèches et le bouton OK de la télécommande ; Retour revient à la page précédente.
- **Dictée** : le bouton « Parler » du site utilise la reconnaissance vocale de la télé ; le bouton micro de la
  télécommande, s'il est transmis à l'application, écrit aussi dans la zone de message.
- **Voix** : la lecture à voix haute du site passe par la synthèse vocale de la télé.
- Le site reconnaît l'application par `?ecran=tele` et par `MetinTV/1.0` dans le User-Agent (mode télé).
- La connexion reste gardée par le site (stockage de l'application).
- **Mode souris (1.0.3)** : une flèche verte au liseré fin. Les flèches de la télécommande la déplacent (plus on garde
  appuyé, plus elle va vite) ; OK = cliquer ; OK gardé = appui long ; OK gardé + flèches = glisser. On passe du mode
  souris au mode habituel (sélection par cadre), et retour, avec la touche **Menu** (ou Réglages, Info, Guide selon la
  télécommande) ou un **appui long sur Retour**. Le mode choisi est retenu.
- Aucune clé ni aucune donnée dans ce dépôt.

## Fabrication

À chaque enregistrement sur `main`, GitHub Actions (« Fabriquer Metin TV ») fabrique l'application et la publie dans
les Releases. Dernière version :

https://github.com/metin-ia/metin-tv/releases/latest/download/metin-tv.apk

La clé de signature (la même pour toutes les mises à jour) est rangée dans les **secrets du dépôt**
(`METIN_TV_KEYSTORE_B64`, `METIN_TV_KEYSTORE_MDP`), jamais dans les fichiers.

## Installer sur Google TV

1. Paramètres › Système › À propos › appuyer 7 fois sur « Build du système d'exploitation Android TV » (mode développeur).
2. Paramètres › Applications › Sécurité et restrictions › Sources inconnues › autoriser **Downloader**.
3. Ouvrir **Downloader**, taper l'adresse ci-dessus, « Go », puis « Installer ».
4. Ouvrir **Metin** depuis l'écran d'accueil (rangée « Vos applications »).

Mise à jour : même chemin ; l'application se met à jour sans perdre la connexion.
