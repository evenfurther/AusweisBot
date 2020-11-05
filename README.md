# Présentation générale

AusweisBot est un logiciel libre permettant de générer
pendant la pandémie de covid-19 une attestation dérogatoire de
déplacement en France à travers le réseau de communication
[Telegram](https://telegram.org). Une instance publique,
utilisable par tous, est disponible sur Telegram sous le nom
[@AusweisBot](https://telegram.me/AusweisBot).

[__Accéder à l'instance publique @AusweisBot en cliquant ici__](https://telegram.me/AusweisBot)

AusweisBot est écrit en [Scala](https://www.scala-lang.org/) et utilise notamment l'intergiciel
[Akka](https://akka.io), la bibliothèque [bots4s.telegram](https://github.com/bot4s/telegram),
la bibliothèque [Apache PDFBox](https://pdfbox.apache.org/) et la bibliothèque [ZXing](https://github.com/zxing/zxing).
Vous pouvez utiliser le gestionnaire de tickets et de _pull requests_ de GitHub pour faire
des remarques sur des fonctionnalités ou proposer des changements.

# FAQ

__Est-ce que AusweisBot stocke des données à caractère personnel ?__

AusweisBot conserve les seules données strictement nécessaires à la génération
des attestations dérogatoires de sortie. Les données stockées peuvent être
consultées à tout moment en utilisant la commande `/data` et effacées avec la
commande `/start`.

Cette politique de gestion des données est détaillée dans le résultat de la
commande `/privacy` qui peut être utilisée à tout moment, notamment avant la collecte
des données personnelles.

__Pourquoi ce nom ?__

> « Nous sommes en guerre » (Emmanuel Macron, 16 mars 2020)

« _Ausweis_ » est un mot allemand signifiant « pièce d'identité ». Il était utilisé lors de la
dernière guerre mondiale pour désigner les laissez-passer dérogatoires dont la présentation
était obligatoire pour pouvoir circuler. Et puis cela permet de court-circuiter
la [loi de Godwin](https://fr.wikipedia.org/wiki/Loi_de_Godwin) avant même de commencer la
discussion.

__Quelles garanties apportez-vous ?__

Pour être clair : _aucune_. Ce logiciel ainsi que le service en ligne
associé ont pour but de faciliter, sous votre propre responsabilité, la création
d'une attestation dérogatoire de déplacement conforme à ce qu'exige la
réglementation. C'est à vous de vous assurer que les informations qui
figurent sur le document sont exactes. Le plus grand soin a également
été apporté au contenu du QR-code contenu dans l'attestation afin de
permettre une lecture sans contact du document par les forces de l'ordre
afin de préserver la santé de chacun, mais là aussi, l'utilisation du
QR-code lors d'un contrôle se fait sous la seule responsabilité de
l'utilisateur.

__AusweisBot est-il utilisable dans les territoires d'outre-mer ?__

AusweisBot est avant tout destiné à la France métropolitaine : Telegram ne fournit aucune
information sur la localisation de l'utilisateur ou le fuseau horaire dans lequel il se
trouve. On suppose donc que l'utilisateur se trouve, par défaut, en France métropolitaine.

Cela n'empêche pas d'utiliser AusweisBot depuis les territoires d'outre-mer en spécifiant
un horaire explicite. Toutefois, une attention particulière devra être apportée à l'examen
du document produit pour s'assurer que tout est en ordre.

__Cela permet-il de sortir dans plus de cas ou plus souvent ?__

Non, AusweisBot permet simplement de générer les attestations dérogatoires de déplacement
tel que vous le feriez avec l'attestation papier ou le générateur d'attestation mis à disposition
par le ministère de l'intérieur. __Restez chez vous !__

__Puis-je déployer mon propre bot à partir du code source de AusweisBot sur Telegram ?__

Bien entendu, du moment que vous respectez les termes des licenses des logiciels
et données utilisés (cf. ci-dessous). De plus, vous devez vous assurer que les informations
permettant de vous contacter sont à jour.

__Que pensez-vous du principe de cette attestation dérogatoire de déplacement ?__

Peu importe ce que j'en pense, la présentation d'un document attestant d'une raison valable
de sortie est rendue obligatoire par l'article 4 du
[décret n° 2020-1310 du 29 octobre 2020](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000042475143).
Pour limiter les contacts lors du contrôle, il me semble plus simple de présenter une attestation
identique à ce que propose le service en ligne du ministère de l'intérieur en y incluant
un QR-code incorporant le même contenu.

__Pourquoi Scala et pas rust, elixir, etc. ?__

La bibliothèque [Apache PDFBox](https://pdfbox.apache.org) écrite pour Java (et donc disponible directement
en Scala) permet une manipulation facile du fichier PDF contenant l'attestation. De plus, [Akka](https://akka.io) facilite la supervision
et la reprise sur faute, notamment en cas de défaillance temporaire des serveurs Telegram ou en cas
de perte de connectivité de l'ordinateur sur lequel le service est hébergé.

__Pourquoi Scala 2.12 et pas Scala 2.13 ?__

La bibliothèque [bots4s.telegram](https://github.com/bot4s/telegram) n'est pas à ce jour disponible
pour Scala 2.13 car elle utilise une dépendance (`slogging`) qui n'est plus maintenue et n'a
jamais été adaptée pour Scala 2.13.

# Licence

AusweisBot est distribué sous les termes de la licence [GNU Affero
Public License version 3.0](https://www.gnu.org/licenses/agpl-3.0.html).

Pour faciliter la saisie du lieu de confinement, ce logiciel utilise une
liste des communes de France et de leurs codes postaux qui s'accompagne
de la licence suivante :

> Ce(tte) œuvre de https://sql.sh est mise à disposition
> selon les termes de la [licence Creative Commons
> Attribution – Partage dans les Mêmes Conditions 4.0
> International](http://creativecommons.org/licenses/by-sa/4.0/).
>
> Vous êtes libre de partager, distribuer ou utiliser cette base de données,
> pour des utilisations commerciales ou non, à condition de conserver cette licence
> et d’attribuer un lien vers le site sql.sh.

## Comment lancer mon propre bot ?

Vous aurez besoin d'un ordinateur avec une connectivité permanente à Telegram pour faire tourner
votre version du bot. Il vous faudra également créer un jeton d'authentification du bot
auprès du [gestionnaire de robots BotFather](https://telegram.me/BotFather).

### À la main

Vous aurez besoin de [`sbt`](https://www.scala-sbt.org) pour compiler et lancer ce programme.

Le fichier de configuration se trouve dans `src/main/resources/application.conf`. Il vous
faudra _a minima_ remplir l'entrée `ausweis.telegram-token` avec le jeton d'authentification
fourni par BotFather et `ausweis.contact-email` avec votre adresse mail de contact pour la gestion
des données personnelles. Vous pouvez préférer placer ces informations dans un fichier à la
racine de votre dépôt et le passer en premier argument du programme.

Par défaut, le programme se connecte à une base de données [CouchDB](https://couchdb.apache.org)
tournant localement sur le port par défaut en mode non protégé. Il faut donc n'autoriser que
des connexions locales, ou utiliser des conteneurs (cf. ci-dessous).

Vous pouvez lancer le bot avec la commande `run` (ou `run configuration-file`) de `sbt`.

### Dans des conteneurs

Un fichier de configuration pour
[docker-compose](https://docs.docker.com/compose/) est fourni à la racine
du dépôt, auquel cas seuls `docker` et `docker-compose` sont strictement nécessaires.

Pour lancer votre instance du bot, il vous faut d'abord créer un fichier
de configuration `.env` à la racine du dépôt contenant _a minima_
`TELEGRAM_TOKEN=le-token-donné-par-BotFather` et
`CONTACT_EMAIL=votre-adresse-mail-de-contact`. Vous pouvez ensuite générer
les conteneurs avec `docker-compose build` puis les lancer avec
`docker-compose up`.

Vous pouvez aussi récupérer automatiquement la dernière version officielle des conteneurs
à partir de [DockerHub](https://hub.docker.com/r/rfc1149/ausweisbot), et lancer le tout
en tâche de fond grâce aux commandes

```bash
$ docker-compose pull
$ docker-compose up -d
```

Dans ce cas, vous n'avez besoin de rien d'autre que le fichier `.env` que vous avez créé
et le fichier `docker-compose.yml` se trouvant à la racine du dépôt.

### Gérer la liste des commandes pour BotFather

La liste des commandes à donner à BotFather pour générer la complétion automatique peut
être générée en faisant à l'aide :

- soit de `sbt genCommands` lorsqu'on compile depuis les sources, qui générera un fichier
  `commands.txt` dans le répertoire courant ;
- soit de la commande `docker run --rm rfc1149/ausweisbot cat commands.txt` pour les utilisateurs de Docker
