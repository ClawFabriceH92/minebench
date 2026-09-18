# MineBench

Application Android qui réunit trois usages autour du Bitcoin :

- **Benchmark** : mesure le débit SHA-256d réel de l'appareil selon le moteur (MessageDigest natif ou
  Kotlin pur), le nombre de threads et la charge CPU visée (duty cycle). Affiche aussi température
  batterie, statut thermique et l'espérance économique (part du réseau, revenu par jour, temps pour 1 €).
- **Minage** : client Stratum (subscribe, authorize, notify, set_difficulty, submit) qui mine pour de vrai
  sur un pool, avec limiteurs de charge et garde-fous thermiques.
- **Explorateur** : recherche un bloc par hauteur ou par hash et lit les sorties OP_RETURN de ses
  transactions (messages et données inscrits dans la blockchain), rendues en hexadécimal et en texte.
  Lecture seule via l'API Esplora de mempool.space ; l'analyse est bornée aux premières transactions
  du bloc pour rester rapide.

## Version

1.0.0 (versionCode 1)

## Limitation CPU

- nombre de threads (1 à tous les cœurs) ;
- duty cycle : la boucle travaille par petits lots puis dort proportionnellement (10 % à 100 %) ;
- arrêt automatique si la batterie dépasse la température limite (42 °C par défaut) ;
- arrêt automatique si le statut thermique système passe en sévère ;
- arrêt si l'appareil est débranché ou sous le seuil de batterie (minage en charge uniquement par défaut).

## Ordre de grandeur

Aucun téléphone n'est rentable pour miner du Bitcoin. À 30 MH/s, l'espérance est d'environ
0,000001 € par jour, contre 0,13 € par jour pour un ASIC de 4 TH/s. L'application sert à mesurer,
apprendre, et éventuellement tenter la loterie du minage solo.

## Vérifications

`./gradlew testDebugUnitTest` couvre :

- vecteurs SHA-256 et double SHA-256 (dont « abc » et la chaîne vide) ;
- égalité entre l'implémentation Kotlin pure et MessageDigest ;
- reconstruction de l'en-tête du bloc 966856 : le hash recalculé est identique à l'identifiant réseau ;
- conversion des prevhash entre format RPC et format Stratum (aller-retour) ;
- assemblage complet d'un en-tête de 80 octets avec des fixtures calculées hors application (Python) ;
- conversion nBits vers cible et cohérence de la difficulté ;
- décodage OP_RETURN (push direct, PUSHDATA1, pushes multiples, script non-OP_RETURN, push tronqué,
  rendu texte des octets non imprimables), sur fixtures validées hors application.

## Distribution

APK signé, publié en release GitHub. Google Play interdit les applications de minage : distribution
en APK direct uniquement.
