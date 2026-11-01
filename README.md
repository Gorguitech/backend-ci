# InternatAdmin : backend microservices

Backend de gestion administrative et financiere d'un Daara, issu du decoupage du monolithe.
Java 21, Spring Boot 3.5, PostgreSQL (une base par service), Flyway, RabbitMQ pour l'audit.

Demarrage local et profils Compose : voir `docker-compose.yml` et `.env.example`.

## Audit

Les services metier publient un evenement sur RabbitMQ pour chaque action annotee `@Audited`.
`audit-service` le consomme et l'enregistre dans sa base. Cote consommateur, un message en echec
est retente puis conserve dans la file `audit.events.dlq`.

### Limite connue : perte d'evenement si RabbitMQ est indisponible

L'evenement est publie apres la validation de l'action metier, sans stockage intermediaire.
Si RabbitMQ est indisponible a ce moment-la :

- l'action metier reussit et reste en base ;
- l'evenement d'audit est perdu : il n'est ni conserve ni republie plus tard ;
- une ligne `ERROR` est ecrite dans le journal du service (`Evenement d'audit non publie`), avec
  l'identifiant de l'evenement, l'action, le type et l'identifiant de l'entite, jamais le detail.

Ce comportement est verifie par `RabbitMqIndisponibleIT` (enfant-service). Pendant la panne, le test
de sante de ce service (`/health`) a aussi ete mesure a 503 : Docker le signale donc `unhealthy`,
alors que les actions metier continuent de reussir.

### Donnees personnelles dans `details`

Le champ `details` contient la reponse de l'action auditee sous forme de texte, limitee a 500 caracteres,
comme dans le monolithe. Il peut donc contenir des donnees personnelles, par exemple :

- eleve : nom, date de naissance, nom et telephone du tuteur, quartier ;
- employe : nom, telephone, salaire de base ;
- paiements : nom de l'eleve ou de l'employe, matricule, montant ;
- utilisateur : nom complet et identifiant de connexion.

Ces donnees transitent par RabbitMQ, sont conservees dans la base d'audit et, en cas d'echec de
traitement, dans la file `audit.events.dlq`. L'historique complet (`/api/audit`) est reserve au role ADMIN ;
chaque utilisateur peut lire ses propres traces (`/api/audit/me`). Le journal d'erreur de publication
n'ecrit jamais `details`. Aucun mot de passe, code de recuperation ni jeton n'y figure :
les reponses auditees n'ont pas ces champs et `SecretsTraceIT` le verifie sur le parcours d'authentification.

### Evolution prevue : outbox transactionnelle

Pour ne plus perdre d'evenement, chaque service producteur enregistrerait l'evenement dans une table
`outbox`, dans la meme transaction que l'action metier, puis un processus le publierait sur RabbitMQ.
Cette evolution n'est pas realisee : elle demande une migration Flyway dans chaque service producteur.
