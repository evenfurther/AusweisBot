La finalité de ce bot (dont le [code source est librement disponible](https://github.com/samueltardieu/AusweisBot)) est la génération d'auto-attestations dérogatoires de sortie respectant les prescriptions réglementaires sous la seule responsabilité de l'utilisateur de ce service. Ces attestations incluent un QR-code permettant aux forces de l'ordre d'en vérifier le contenu tout en respectant les distances de sécurité.

Pour interagir avec le bot, il faut lui envoyer un message contenant une commande. Une commande commence toujours par le symbole `/` suivi du nom de la commande.

Les commandes suivantes sont disponibles à tout moment :

- /help : Cette aide
- /start : Efface toutes les données personnelles et recommence la saisie
- /privacy : Indique la politique de traitement des données personnelles
- /data : Liste les données personnelles

Les commandes suivantes génèrent une attestation pour un motif donné.
Elles sont disponibles une fois que les données personnelles nécessaires à la génération des attestations ont été fournies :

REASONS

*Utilisation avancée*

- `/santé` : Génère une attestation de sortie pour recevoir des soins
- `/santé oubli` : Idem daté d'il y a une vingtaine de minutes en cas d'oubli de sauvegarde de l'attestation avant de sortir.
- `/santé 21h30` : Idem pour une sortie à 11h30.
- `/autre motifs`, `/autre motifs oubli` ou `/autre motifs 11h30` : Attestation pour un ou plusieurs motifs séparés par `+` (par exemple `/autre famille+handicap 23h30`).
- `/vierge` : Fournit un formulaire pré-rempli à imprimer, compléter et signer.

Les commandes suivantes permettent de changer certains détails stockés en base de données :

- /a : Permet de changer l'adresse en conservant l'identité actuelle
- /i : Permet de changer l'identité en conservant l'adresse actuelle

Telegram ne transmettant pas le fuseau horaire dans lequel se trouve l'utilisateur, la génération des attestations sans heure explicite ne fonctionne que pour la France métropolitaine.

__Attention__ : la faculté de choisir l'heure sur l'attestation, comme c'est le cas sur l'attestation papier, n'autorise pas à s'affranchir des prescriptions en matière de temps de sortie maximum autorisé par jour. De même, il n'est pas clair que l'utilisation de plusieurs motifs simultanément (par exemple famille+santé) soit réglementaire.
