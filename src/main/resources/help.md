La finalité de ce bot (dont le [code source est librement disponible](https://github.com/samueltardieu/AusweisBot)) est la génération d'auto-attestations dérogatoires de sortie pendant le couvre-feu respectant les prescriptions réglementaires sous la seule responsabilité de l'utilisateur de ce service. Ces attestations incluent un QR-code permettant aux forces de l'ordre d'en vérifier le contenu tout en respectant les distances de sécurité.

Les commandes suivantes sont disponibles à tout moment :

- `/help` : Cette aide
- `/start` : Efface toutes les données personnelles et recommence la saisie
- `/privacy` : Indique la politique de traitement des données personnelles
- `/data` : Liste les données personnelles

Les commandes suivantes sont disponibles une fois que les données personnelles nécessaires à la génération des attestations ont été fournies :

- `/animaux` : Génère une attestation pour la promenade des animaux.
- `/animaux oubli` : Idem daté d'il y a une vingtaine de minutes en cas d'oubli de sauvegarde de l'attestation avant de sortir.
- `/animaux 11h30` : Idem pour une sortie à 11h30.
- `/famille`, `/famille oubli` ou `/famille 11h30` : Idem mais pour une sortie famille.
- `/autre motif`, `/autre motif oubli` ou `/autre motif 11h30` : Attestation pour un motif particulier.
- `/vierge` : Fournit un formulaire pré-rempli à imprimer, compléter et signer.

Les motifs utilisables dans la commande `/autre`, séparés par un `+`, sont : `travail`, `santé`, `famille`, `handicap`, `convocation`, `mission`, `transit` ou `animaux`. Exemples d'utilisation :

- `/autre transit`
- `/autre santé oubli`
- `/autre famille 11h30`

Telegram ne transmettant pas le fuseau horaire dans lequel se trouve l'utilisateur, la génération des attestations sans heure explicite ne fonctionne que pour la France métropolitaine.

