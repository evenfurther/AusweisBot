La finalité de ce bot (dont le [code source est librement disponible](https://github.com/samueltardieu/AusweisBot)) est la génération d'auto-attestations dérogatoires de sortie respectant les prescriptions réglementaires sous la seule responsabilité de l'utilisateur de ce service. Ces attestations incluent un QR-code permettant aux forces de l'ordre d'en vérifier le contenu tout en respectant les distances de sécurité.

Les commandes suivantes sont disponibles à tout moment :

- `/help` : Cette aide
- `/start` : Efface toutes les données personnelles et recommence la saisie
- `/privacy` : Indique la politique de traitement des données personnelles
- `/data` : Liste les données personnelles

Les commandes suivantes sont disponibles une fois que les données personnelles nécessaires à la génération des attestations ont été fournies :

- `/sport` : Génère une attestation pour la pratique sportive, la promenade ou la sortie des animaux.
- `/sport oubli` : Idem daté d'il y a une vingtaine de minutes en cas d'oubli de sauvegarde de l'attestation avant de sortir.
- `/sport 11h30` : Idem pour une sortie à 11h30.
- `/autre motifs`, `/autre motifs oubli` ou `/autre motifs 11h30` : Attestation pour un ou plusieurs motifs séparés par `+` (par exemple `/autre courses+sport 10h30`).
- `/vierge` : Fournit un formulaire pré-rempli à imprimer, compléter et signer.

La liste des motifs utilisables est :

REASONS

Telegram ne transmettant pas le fuseau horaire dans lequel se trouve l'utilisateur, la génération des attestations sans heure explicite ne fonctionne que pour la France métropolitaine.

__Attention__ : la faculté de choisir l'heure sur l'attestation, comme c'est le cas sur l'attestation papier, n'autorise pas à s'affranchir des prescriptions en matière de temps de sortie maximum autorisé par jour. De même, il n'est pas clair que l'utilisation de plusieurs motifs simultanément (par exemple famille+santé) soit réglementaire.
