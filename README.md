# HomeHabit (from scratch)

Squelette Android (Kotlin + Jetpack Compose) reprenant les principes de
l'ancienne app HomeHabit : dashboard configurable par JSON, grille libre de
widgets redimensionnables, thème sombre par défaut, écran maintenu allumé.

## État actuel

- Moteur de grille libre (`engine/GridEngine.kt`) : placement `x, y, w, h`,
  détection de collision, recherche de première case libre. Trous autorisés,
  aucun réagencement automatique.
- Modèle de config JSON (`model/DashboardConfig.kt`) + fichier d'exemple
  (`app/src/main/assets/dashboard_config.json`).
- **Client Domoticz réel** (`data/domoticz/`) :
  - `DomoticzClient.kt` — appels HTTP vers `/json.htm` (lecture device,
    switch on/off, dimmer, volet open/close/stop/set level, consigne
    thermostat), auth Basic optionnelle.
    **Important** : toutes les lectures utilisent `type=command&param=getdevices`
    et non l'ancien `type=devices` — ce dernier a été déprécié en 2023.2 et
    **complètement supprimé (404) depuis Domoticz 2025.1**, donc obligatoire
    à jour pour toute version récente (2026.x incluse). Les commandes
    d'écriture (`switchlight`, `setsetpoint`) utilisaient déjà le bon format
    dès le départ.
  - `DomoticzRepository.kt` — poll périodique (5s par défaut) des widgets
    dont `source.provider = "domoticz"`, mapping vers `WidgetLiveState`.
  - `DomoticzConfig.kt` — host/port/credentials, dérivé de `AppSettings`
    (persistés, éditables depuis l'écran de réglages — voir section dédiée
    plus bas). Valeurs par défaut `192.168.1.10:8080` si jamais modifié.
  - Le widget lumière est cliquable dans le dashboard et envoie une vraie
    commande `switchlight` au serveur Domoticz configuré.
- `DashboardViewModel` fusionne les états Domoticz réels avec des valeurs de
  démo (`FakeStateProvider`) pour les widgets non-Domoticz (météo, caméra).
- `DashboardScreen` positionne chaque widget en pixels à partir de ses
  coordonnées de grille (cellules carrées, largeur = hauteur).
- **Mode édition avec drag & resize** (`DashboardScreen.kt` → `EditOverlay`) :
  - Bouton crayon en haut à droite bascule `isEditMode`
  - En mode édition, chaque widget affiche un contour bleu ; glisser le
    corps déplace le widget (`x, y`), glisser la poignée en bas à droite
    redimensionne (`w, h`), avec un minimum de 1×1
  - Le placement candidat est recalculé à chaque évènement de drag et
    validé via `GridEngine.isValidPlacement` (système libre, cf discussion
    précédente : pas de réagencement automatique). Si invalide, le widget
    reste accroché à sa dernière position/taille valide jusqu'à ce que le
    doigt revienne dans une zone libre — pas de widget "ghost" séparé, le
    widget réel *est* la prévisualisation
  - Le bouton "+" (ajout de widget) n'apparaît qu'en mode édition
  - Les clics normaux (toggle lumière) sont désactivés pendant l'édition
    pour éviter les déclenchements accidentels pendant un drag
- `WidgetCard` affiche 6 types : weather, light, thermostat, shutter, lock,
  camera (icônes Material Design en placeholder, à remplacer par
  Android-Iconics + FontAwesome).
- Thème sombre (`ui/theme/`) repris des mockups validés.
- `MainActivity` pose le flag `FLAG_KEEP_SCREEN_ON` (écran mural).
- `usesCleartextTraffic="true"` dans le manifest, nécessaire car Domoticz
  tourne généralement en HTTP simple sur le réseau local (bloqué par défaut
  depuis Android 9).

### Pour tester avec un vrai serveur Domoticz

1. Lancer l'app, ouvrir le bouton engrenage (toujours visible, coin haut
   droit) et saisir host/port/identifiants — plus besoin de modifier le
   code (voir section "Écran de réglages Domoticz" plus bas).
2. Mettre à jour les `deviceId` (`"idx:12"`, etc.) dans
   `assets/dashboard_config.json` avec les vrais idx de vos appareils
   (visibles dans Domoticz > Setup > Devices), ou utiliser le bouton "+"
   en mode édition pour les découvrir automatiquement.

## Multi-dashboard (pages avec swipe)

**Breaking change de schéma.** La racine du JSON de config n'a plus
`grid`/`widgets` à plat : c'est désormais une liste de `pages`, chacune
avec sa propre grille et ses propres widgets.

```json
{
  "pages": [
    { "id": "page_accueil", "name": "Accueil", "grid": { "columns": 4 }, "widgets": [...] },
    { "id": "page_chambre", "name": "Chambre", "grid": { "columns": 4 }, "widgets": [...] }
  ]
}
```

Un `dashboard_config.json` déjà persisté en stockage interne avec
l'ancien format (avant ce changement) **ne sera pas migré
automatiquement** — `pages` repartira sur son défaut (une page vide) et
les anciens champs `grid`/`widgets` seront simplement ignorés par le
parseur (`ignoreUnknownKeys = true`). Si tu avais déjà lancé l'app avant
cette mise à jour : désinstalle/réinstalle, ou vide le stockage de l'app,
pour repartir sur l'asset par défaut.

- **Navigation** (`DashboardScreen.kt`) : `HorizontalPager` (Compose
  Foundation, pas de dépendance accompanist nécessaire — stable depuis
  longtemps dans la version de compose-bom utilisée). Swipe horizontal
  entre les pages.
- **Barre d'onglets** (`PageTabsBar.kt`) : un onglet par page en haut de
  l'écran, tap pour switcher (anime le pager plutôt qu'un saut brut),
  appui long en mode édition pour ouvrir `PageManageDialog` (renommer ou
  supprimer). Onglet "+" visible uniquement en mode édition.
- **Toujours au moins une page** : `removePage()` refuse silencieusement
  si c'est la dernière page restante — impossible de se retrouver sans
  aucune page.
- **États live globaux, pas par page** : le polling Domoticz/météo tourne
  sur TOUS les widgets de TOUTES les pages en permanence
  (`cfg.allWidgets()` dans le ViewModel), pas seulement la page visible.
  Une lumière allumée sur la page "Chambre" reste à jour même si tu es en
  train de regarder "Accueil". Seule la sparkline suit la même logique
  (éligibilité calculée sur tous les widgets, indépendamment de la page).
- **Ajout de widget** : toujours sur la page actuellement visible dans le
  pager (`DashboardViewModel.currentPageIndex`, synchronisé depuis
  `HorizontalPager` via `LaunchedEffect`).
- **Découverte Domoticz** : le filtre "déjà utilisé" vérifie les idx sur
  **toutes** les pages, pas juste la page courante — pas de risque de
  proposer deux fois le même device sur deux pages différentes par
  erreur (rien n'empêche de le faire volontairement en éditant le JSON
  à la main, mais l'UI ne le suggère pas).
- **Drag & resize inchangés dans leur logique**, juste re-scopés à la
  page affichée : `GridEngine` reste entièrement page-agnostic (il ne
  connaît que des listes de widgets + un nombre de colonnes), c'est
  l'appelant (`DashboardScreen`, dans le pager) qui lui passe la bonne
  page à chaque fois.
- **Pas de confirmation avant suppression de page** — cohérent avec le
  reste de l'app (`removeWidget` n'en a pas non plus), mais une page peut
  contenir beaucoup de widgets d'un coup. À reconsidérer si ça s'avère
  source d'erreurs en usage réel.
- **Non testé sur device réel**, comme le reste : le geste de swipe du
  `HorizontalPager` peut entrer en conflit avec le drag de
  repositionnement en mode édition (les deux répondent à un geste
  horizontal) — à valider concrètement, potentiellement à désactiver le
  swipe de page pendant qu'un widget est en cours de déplacement.

## Client météo (Open-Meteo)

- `data/weather/OpenMeteoClient.kt` — appel de
  `https://api.open-meteo.com/v1/forecast` (gratuit, sans clé API) :
  température courante, code météo WMO, min/max du jour. Un seul appel
  HTTP par widget par cycle.
- `data/weather/WeatherRepository.kt` — même pattern que Domoticz (poll +
  `flatMapLatest` sur les changements de config), mais intervalle par
  défaut de **15 minutes** plutôt que 5 secondes : la météo change
  lentement, inutile de solliciter l'API plus souvent.
- `data/weather/WeatherCodeMapper.kt` — traduit le code WMO numérique
  renvoyé par Open-Meteo (`weather_code`) en libellé français ("Ciel
  dégagé", "Averses", "Orage"...). Couvre les codes les plus courants ;
  un code non reconnu retombe sur "Conditions inconnues" plutôt que
  d'afficher un nombre brut ou de planter.
- `WidgetSource` étendu avec `latitude`/`longitude` (Open-Meteo travaille
  en coordonnées, pas en code commune — le `dashboard_config.json`
  d'exemple utilise Paris : `48.8566, 2.3522`, à changer pour ta ville).
- Dans le ViewModel, le polling météo tourne dans son propre job
  (`startWeatherPolling`), indépendant de celui de Domoticz
  (`startDomoticzPolling`, redémarrable — voir section réglages) plutôt
  que combinés ensemble.

## Widget Prévision 7 jours (FORECAST)

Distinct du widget `weather` (température actuelle) : `forecast` affiche
les 7 prochains jours en ligne (jour, icône, max/min), toujours via
Open-Meteo — **pas l'API officielle Météo-France**, écartée dès le début
du projet car pas simplement accessible aux apps tierces sans plus de
travail (clé API, accès limité). "Météo France" ici veut dire "météo
pour un lieu en France", pas le service du même nom.

- **Même appel Open-Meteo que le widget `weather`**
  (`OpenMeteoClient.getForecast`), juste avec `forecastDays` différent
  (1 vs 7) et le paramètre `daily` étendu avec `weather_code` (absent
  avant, nécessaire pour avoir une icône différente par jour plutôt
  qu'une seule pour toute la semaine).
- **Distinction faite dans `WeatherRepository.observeStates`** selon
  `widget.widgetType` : `WEATHER` construit un `WidgetLiveState.Weather`
  comme avant, `FORECAST` construit un `WidgetLiveState.Forecast(days)`
  en zippant les tableaux `time`/`weather_code`/`temperature_2m_max`/`min`
  renvoyés par Open-Meteo (un array par variable demandée, pas un objet
  par jour — d'où le zip manuel plutôt qu'un simple mapping direct).
- **Libellé du jour déjà formaté côté repository** (`formatDayLabel`,
  ex "Lun", "Mar") plutôt que de repasser une date ISO brute à l'UI —
  `SimpleDateFormat("EEE", Locale.FRANCE)`, retombe sur `"--"` si le
  format Open-Meteo change un jour.
- **Rendu** (`WidgetCard.kt` → `ForecastContent`) : ligne **défilable
  horizontalement** (`horizontalScroll`) plutôt qu'un layout fixe à 7
  colonnes — reste utilisable quelle que soit la taille du widget.
  Recommandé en largeur `w=4` dans le JSON pour voir plusieurs jours
  sans avoir à swiper (exemple : widget `prevision_paris`, page
  "Accueil"). **Non testé sur device réel** : le scroll horizontal à
  l'intérieur d'un widget pourrait entrer en conflit avec le swipe de
  page du `HorizontalPager` (les deux répondent à un geste horizontal),
  comme déjà noté pour le drag de repositionnement vs le swipe de page —
  à valider concrètement.
- **Mapping icône par code WMO** (`iconForWeatherCode`, dans
  `WidgetCard.kt`) — volontairement séparé de `WeatherCodeMapper`
  (qui ne gère que le libellé texte) plutôt que fusionné, pour ne pas
  coupler le mapping visuel (icône Compose) à la couche data.

**Effet de bord** : `FakeStateProvider` ne contient plus que la caméra en
démo — les anciennes valeurs fake pour lumières/thermostat/volet/serrure
ont été retirées puisqu'elles servaient uniquement de filet avant que
l'intégration Domoticz existe. Ça veut dire qu'un widget Domoticz
n'affiche plus rien tant que le premier poll n'a pas répondu (avant, il
affichait une fausse valeur pendant ce court instant). Cohérent avec
l'objectif de ne plus avoir de fausses données qui traînent, mais à
garder en tête si l'écran parait "vide" une fraction de seconde au
démarrage.

## Interactions Domoticz dans l'UI

- **Lumière** : tap = toggle on/off (déjà en place précédemment).
- **Volet — style configurable par widget** (`source.shutterStyle` dans
  le JSON : `"buttons"` par défaut, ou `"toggle"`) :
  - **`"buttons"`** (défaut) : 3 boutons dédiés ouvrir/stop/fermer
    (`WidgetCard.kt` → `ShutterButtonsContent`/`ShutterButton`).
    `DomoticzClient.stopShutter()` existait déjà depuis le début du
    projet mais n'était jamais appelé — fonctionnalité à moitié
    construite, maintenant reliée. Ouvrir/fermer restent en mise à jour
    optimiste (0%/100%) ; **stop n'a volontairement aucune mise à jour
    optimiste** — impossible de connaître la position exacte à laquelle
    le volet s'arrête, le prochain poll (5s) ramène la vraie valeur.
    Zone tactile volontairement petite (20dp) pour tenir 3 boutons sur
    un widget 1×1 — à valider au doigt sur un vrai écran, plus
    confortable sur un widget redimensionné en 2×1.
  - **`"toggle"`** : tap sur tout le widget bascule ouvert/fermé selon
    la position actuelle (seuil 50%, `ShutterToggleContent` +
    `viewModel.toggleShutter`), plus compact mais pas de bouton stop
    accessible dans ce mode. Exemple dans `dashboard_config.json`
    (widget `volet_chambre`, page "Chambre").
  - Pas d'UI pour changer le style depuis l'app — uniquement via le JSON
    (édition manuelle ou serveur HTTP embarqué). Cohérent avec le reste
    des réglages fins du projet (pas encore d'écran dédié à ce niveau de
    détail par widget).
- **Serrure** : tap = toggle verrouillé/déverrouillé (`toggleLock`), même
  logique optimiste que la lumière.
- **Thermostat** : tap ouvre `ThermostatAdjustDialog` — boutons +/- par
  pas de 0.5°C (bornés 5-30°C), "Valider" envoie la nouvelle consigne via
  `setThermostatSetpoint`.
- Les autres suivent le même pattern que la lumière : mise à jour
  optimiste de l'UI seulement si la commande Domoticz a réellement
  réussi (pas d'affichage d'un état qui ne s'est pas produit si la
  requête échoue).

## Capteur générique (SENSOR)

**Avant toute chose : dépendance corrigée.** En composant ce widget, j'ai
réalisé que `material-icons-extended` n'avait jamais été ajoutée au
`build.gradle.kts`, alors que plusieurs icônes déjà utilisées depuis le
début (`Thermostat`, `Blinds`, `Videocam`, `Palette`, `Cloud`,
`WbIncandescent`...) n'existent que dans ce module — `material-icons-core`
(inclus par défaut avec material3) ne contient qu'un set restreint
(Add, Check, Close, Edit, Search, Settings...). Sans ce fix, le projet
ne compilait probablement pas depuis l'ajout de ces icônes. Corrigé.

Beaucoup de devices Domoticz (température seule, humidité, pluie, vent,
UV, baromètre, compteurs d'énergie, capteurs custom...) ne rentraient
dans aucune catégorie et tombaient en `UNKNOWN`. Un type `sensor` générique
couvre ça maintenant.

- **Détection** (`DomoticzTypeMapper.kt`) : large éventail de mots-clés
  sur le champ `Type` Domoticz (Temp, Humidity, Rain, Wind, UV,
  Barometer, Percentage, Usage/kWh/Counter, Custom Sensor, Air Quality,
  Visibility, Solar Radiation, Soil Moisture, Leaf Wetness, General).
  Comme toujours avec ces heuristiques, à ajuster si des capteurs
  précis tombent encore en `UNKNOWN`.
- **Affichage** (`WidgetCard.kt` → `SensorContent`) : icône selon la
  catégorie (`SensorKind`) + valeur affichée directement depuis le champ
  `Data` de Domoticz (déjà formaté avec son unité par Domoticz lui-même,
  ex `"21.5 C"`, `"68 %"` — pas de reformattage custom).
- **Jauge visuelle volontairement limitée** : une fine barre de
  progression n'apparaît que pour les grandeurs naturellement bornées
  0-100 (humidité, pourcentage/batterie). Pour tout le reste (pluie,
  vent, énergie...), pas de jauge — inventer une échelle arbitraire
  aurait été trompeur plutôt qu'utile. Si tu veux des jauges pour
  d'autres grandeurs (ex température avec une plage -10/40°C), il
  faudra définir des bornes par capteur, probablement configurables
  dans le JSON plutôt qu'en dur dans le code.

## Sparkline température (SENSOR kind=TEMPERATURE et THERMOSTAT)

Mini-graphe des dernières 24h, dessiné directement dans le widget, en
plus de la valeur actuelle.

- **Source des données** (`DomoticzClient.getTempGraphDay`) :
  `GET /json.htm?type=command&param=graph&sensor=temp&idx=IDX&range=day`
  — endpoint dédié de Domoticz, distinct de `getdevices`. Renvoie environ
  288 points sur 24h (un point toutes les ~5min selon la config Domoticz),
  champ `te` pour la température de chaque point.
- **Sous-échantillonnage** (`DomoticzRepository.fetchTemperatureSparkline`) :
  ramené à 48 points max avant d'atteindre l'UI — largement suffisant
  pour une sparkline de la taille d'un widget, inutile d'envoyer les 288
  points bruts.
- **Polling séparé et peu fréquent** (`DashboardViewModel.startSparklinePolling`) :
  toutes les 10 minutes, indépendamment du poll principal Domoticz (5s).
  Cet appel est plus coûteux (récupère un historique complet plutôt
  qu'un état ponctuel) et purement décoratif — pas besoin de la même
  fraîcheur que l'état on/off d'une lumière.
- **Éligibilité déterminée après coup** : la fonction regarde l'état déjà
  connu de chaque widget (`WidgetLiveState.Sensor` avec
  `kind == TEMPERATURE`, ou `WidgetLiveState.Thermostat`) plutôt que de
  déclarer un type à part — évite un appel réseau inutile vers l'API
  graph pour un capteur d'humidité ou de pluie, qui n'a pas de courbe de
  température pertinente à cet idx.
- **Rendu** (`WidgetCard.kt` → `Sparkline`) : `Canvas` minimaliste, une
  ligne normalisée entre le min et le max de la série fournie, sans axes
  ni labels — purement une indication de tendance, pas un vrai graphique
  analytique. Remplace/complète l'espace utilisé par la jauge chez
  `SensorContent` (mutuellement exclusifs : la jauge ne s'affiche que
  pour humidité/pourcentage, la sparkline uniquement pour température —
  jamais les deux en même temps sur un même widget).
- **Dégradation silencieuse** : si l'appel graph échoue ou renvoie un
  résultat vide (device sans historique, serveur qui ne répond pas...),
  pas de sparkline affichée — juste la valeur seule, comme avant. Cohérent
  avec le reste du projet (snapshot RTSP, couleur Hue) : best-effort,
  jamais bloquant.
- **Non testé sur device réel** : le format exact des points renvoyés par
  `range=day` peut varier légèrement selon la version de Domoticz — à
  valider une fois connecté à un vrai serveur.

## Types de lumières (LIGHT / DIMMER / COLOR_LIGHT)

Domoticz expose des capacités très différentes selon le type de lumière
(interrupteur simple, variateur, Hue couleur), donc trois `WidgetType`
distincts plutôt qu'un seul générique :

- **`light`** : interrupteur simple. Tap = toggle on/off. Rendu inchangé.
- **`dimmer`** : ajoute la luminosité. Tap = toggle on/off (comme avant),
  **appui long** = ouvre `LightAdjustDialog` avec +/- par pas de 10%. Le
  widget affiche le pourcentage de luminosité sous le label quand allumé.
- **`color_light`** : ajoute la couleur en plus de la luminosité. Même
  interaction que `dimmer` (tap/appui long), mais la modale affiche en
  plus une **palette de 9 couleurs presets** (blanc chaud/froid + 7
  teintes) plutôt qu'un vrai color picker HSV — plus simple à utiliser au
  doigt sur un écran mural, largement suffisant pour une Hue. L'icône et
  le fond du widget se teintent avec la couleur active quand elle est
  connue.

**Auto-close de `LightAdjustDialog` après inactivité** : luminosité et
couleur envoient déjà leur commande immédiatement à chaque tap (pas de
bouton "Valider" séparé) — sans fermeture automatique, il fallait un tap
en plus sur "Fermer" après chaque réglage, pénible sur un écran mural.
Fermer dès le premier tap aurait cassé l'ajustement par paliers de 10%
(taper plusieurs fois de suite pour atteindre la luminosité voulue),
donc la fermeture se déclenche après **1.5s d'inactivité** plutôt
qu'immédiatement : chaque action (brightness +/- ou sélection de
couleur) reporte le délai, ce qui permet d'enchaîner les taps sans que
la modale se ferme entre deux, tout en se fermant seule dès que
l'utilisateur arrête d'interagir. Le bouton "Fermer" reste disponible
pour une fermeture immédiate explicite.

**`ThermostatAdjustDialog` non modifiée** : elle se ferme déjà
automatiquement sur "Valider" (le +/- n'envoie rien tant qu'on n'a pas
confirmé, contrairement à la luminosité qui est "live") — le mécanisme
d'auto-close par inactivité n'avait pas de raison d'être ajouté là.

**Détection automatique** (`DomoticzTypeMapper.kt`) : Domoticz expose les
lumières RGB/RGBW (Hue et similaires, quel que soit le protocole — Hue
Bridge, Zigbee...) sous `Type = "Color Switch"` → `COLOR_LIGHT`. Un
`SwitchType` contenant "Dimmer" sans être une color switch → `DIMMER`.
Le reste avec `Type` contenant "Light"/"Switch" → `LIGHT` simple. Comme
pour le reste des heuristiques Domoticz du projet, c'est un best-effort à
ajuster une fois confronté à du vrai matériel.

**Couleur — best-effort assumé** (`DomoticzColorParser.kt`) : Domoticz
encode la couleur dans un champ `Color` qui est lui-même une chaîne JSON
avec un mode (`m`: blanc, température, RGB, custom...). Seul le mode RGB
explicite est géré pour l'instant — les modes blanc/température
retombent sur "pas de couleur affichée" plutôt que d'inventer une teinte
approximative. Pour l'écriture, `DomoticzClient.setColor()` envoie
toujours en mode RGB explicite (`m=3`) via la commande Domoticz
`setcolbrightnessvalue`, non testée sur un vrai bridge Hue.

## Thème cohérent (icônes + fonds teintés selon l'état)

Les lumières (light/dimmer/color) et les scènes teintaient déjà leur
icône et le fond de leur widget selon l'état — le volet et la serrure ne
suivaient pas cette convention (icône toujours en `TextPrimary`, aucun
fond teinté). Corrigé, avec une convention de couleur désormais
documentée directement dans `ui/theme/Color.kt` plutôt que laissée
implicite :

- **`AccentGreen`(`+Surface`)** : état actif/engagé — lumière allumée,
  volet ouvert (>50%), scène/groupe actif. C'est le seul cas où
  "quelque chose se passe en ce moment" mérite une couleur.
- **`AccentRed`(`+Surface`, nouveau)** : état d'attention/alerte —
  utilisé **uniquement** pour la serrure **déverrouillée**. Choix
  volontairement inversé par rapport au reste : contrairement à une
  lumière où "allumé" = vert, ici c'est l'état "verrouillé" qui reste
  neutre (rien à signaler) et "déverrouillé" qui capte l'œil (sécurité).
- **`AccentOrange`** : chaleur/énergie (température, thermostat, UV,
  conso électrique) — déjà cohérent avant ce passage.
- **`AccentBlueMuted`** : grandeurs "froides"/informatives (humidité,
  pluie, météo) — déjà cohérent avant ce passage.
- **`TextSecondary`/`TextMuted`** : état neutre/inactif/inconnu, jamais
  de fond teinté associé.

**Volet** : icône + fond vert si `percentOpen > 50`, gris sinon — même
seuil que celui déjà utilisé pour le style `"toggle"`. S'applique aux
deux styles (`buttons` et `toggle`).

**Serrure** : icône (glyphe différent selon l'état, déjà en place) +
teinte rouge uniquement si déverrouillée. Verrouillée reste visuellement
neutre — c'est l'état par défaut/attendu, pas un état "actif" à mettre
en avant.

**Thermostat non retouché** : pas d'état binaire on/off à représenter
(juste une valeur continue), `AccentOrange` fixe reste approprié et
cohérent avec le capteur de température (`SensorKind.TEMPERATURE` utilise
déjà la même couleur).

## Widget Scène / Groupe (SCENE)

Déclenche une scène Domoticz ("Soirée ciné", "Je pars"...) ou bascule un
groupe on/off, en un tap.

- **Ressource Domoticz distincte** : contrairement aux devices
  (`getdevices`), les scènes/groupes viennent de
  `type=command&param=getscenes` (depuis stable 2023.2 — même migration
  que les devices, vérifiée avant d'écrire le code cette fois). Un seul
  appel par cycle de poll suffit pour **tous** les widgets scène, plutôt
  qu'un appel par widget comme pour les devices individuels
  (`DomoticzRepository.observeStates` sépare les deux).
- **Scene vs Group, distinction Domoticz importante** : une **Scene**
  est un déclencheur sans état persistant — Domoticz n'autorise que
  `switchcmd=On`, jamais `Off`. Un **Group** est un vrai interrupteur
  on/off togglable, comme une lumière. `WidgetLiveState.Scene.isGroup`
  porte cette distinction : `SceneContent` n'affiche "Actif"/"Inactif"
  que pour un Group.
- **Tap** : `DashboardViewModel.triggerScene()` — toggle on/off si Group,
  toujours "On" si Scene. Mise à jour optimiste comme les autres widgets,
  mais pour une vraie Scene le prochain poll (5s) va probablement
  ramener l'état à "Off" (Domoticz ne garde pas d'état durable) : le
  fond vert du widget flashe brièvement après le tap puis s'estompe —
  volontaire, sert de retour visuel "déclenchée" plutôt qu'un vrai état.
- **Découverte** : section séparée dans `AddWidgetDialog` ("Scènes &
  groupes" au-dessus de "Appareils"), avec son propre appel
  `discoverDomoticzScenes()`. Le filtre "déjà utilisé" est calculé
  séparément de celui des devices (`widgetType == SCENE` vs `!= SCENE`)
  — les scènes et les devices sont deux tables Domoticz distinctes qui
  pourraient en théorie partager un même numéro `idx`, mélanger les deux
  filtres aurait pu masquer à tort un device ou une scène légitimes.
- **Non testé sur device réel**, comme le reste du client Domoticz.

## Mode nuit (assombrissement + extinction planifiée)

Deux niveaux bien distincts, avec des garanties très différentes — c'est
important de ne pas les confondre.

### Niveau 1 — Assombrissement (toujours fiable, aucune permission)

`ui/dashboard/NightModeEffect.kt` ajuste `window.attributes.screenBrightness`
selon l'horaire configuré, vérifié toutes les 60s. Ne touche qu'à la
luminosité de la fenêtre de l'app, aucune permission système requise,
fonctionne à 100% du temps tant que l'app est au premier plan (ce qui
est le cas en continu vu l'usage écran mural visé par le projet).
`power/NightModeSchedule.kt` calcule la plage horaire (gère le cas où
elle traverse minuit, ex 22h → 7h) indépendamment de tout code Android —
logique pure, facilement vérifiable.

### Niveau 2 — Extinction réelle + rallumage automatique (best-effort, opt-in)

Beaucoup plus fragile, documenté comme tel dans le code
(`ScreenPowerController.kt`) :

- **Nécessite les droits "Administrateur de l'appareil"** (Device
  Admin), demandés via un dialogue système standard depuis l'écran de
  réglages — **pas** besoin d'ADB ni de "Device Owner" (qui demanderait
  de provisionner l'appareil avant tout compte configuré). Politique
  minimale déclarée (`res/xml/device_admin_policies.xml`) : uniquement
  `force-lock`, rien d'autre.
- **Extinction** : `DevicePolicyManager.lockNow()` au moment planifié —
  verrouille/éteint réellement l'écran, contrairement à l'assombrissement.
- **Planification via `AlarmManager`** (`ScreenPowerController` +
  `ScreenAlarmReceiver`), pas un simple `delay()` dans une coroutine :
  survit à l'app en arrière-plan. Chaque déclenchement replanifie
  explicitement le lendemain (`setExactAndAllowWhileIdle`, pas
  `setRepeating`, plus fiable sur Android récent).
- **`BootReceiver`** replanifie après un redémarrage de l'appareil (les
  alarmes `AlarmManager` ne survivent pas à un reboot).
- **Rallumage** : relance `MainActivity` avec les flags
  `showWhenLocked`/`turnScreenOn` (manifest, API 27+) et leurs
  équivalents `WindowManager.LayoutParams` dépréciés mais fonctionnels
  pour les versions plus anciennes (`minSdk = 23`).

**Réserves à prendre au sérieux avant de compter dessus :**

- Permission `SCHEDULE_EXACT_ALARM` déclarée dans le manifest, mais sur
  Android 12+ certains fabricants demandent quand même une autorisation
  manuelle (`Paramètres > Applications > Accès spécial > Alarmes et
  rappels`) — pas automatiquement garanti.
- Les gestionnaires de batterie agressifs de certains fabricants (MIUI,
  Samsung, etc.) peuvent tuer l'app en arrière-plan et empêcher le
  déclenchement malgré l'alarme système.
- **Le rallumage peut atterrir sur l'écran de verrouillage** si
  l'appareil a un code/schéma configuré — recommandé de **ne pas avoir
  de verrouillage** sur un appareil dédié à l'affichage mural pour que
  ça fonctionne proprement.
- Si l'utilisateur révoque les droits admin depuis les paramètres
  système, `lockNow()` échoue silencieusement (`SecurityException`
  interceptée) — le réglage reste activé côté app mais n'a plus d'effet
  tant que les droits ne sont pas réaccordés. Pas de détection/alerte
  automatique de cet état pour l'instant.
- **Rien de tout ça n'a été testé sur un vrai appareil** — comme le
  reste des intégrations plateforme de ce projet, mais celle-ci en
  particulier touche à des mécanismes qui varient beaucoup d'un
  fabricant à l'autre.

### Réglages

Section "Mode nuit" dans `SettingsDialog` : horaires début/fin (pas
d'1h via `HourStepper`), luminosité nocturne (pas de 5% via
`PercentStepper`), et un toggle "Extinction réelle" séparé qui
déclenche la demande de droits admin seulement s'ils ne sont pas déjà
accordés.

**Bug corrigé au passage** : `SettingsDialog.onSave` reconstruisait un
`AppSettings` complet sans jamais inclure `httpAuthToken`, qui
retombait donc silencieusement sur `""` à chaque sauvegarde des
réglages Domoticz — cassant l'auth du serveur HTTP jusqu'au prochain
redémarrage complet de l'app (`ConfigRepository.ensureHttpAuthToken` ne
régénère qu'au chargement initial, pas à chaque `updateConfig`). Le
token est maintenant explicitement préservé depuis `initial.httpAuthToken`.

## Écran de réglages Domoticz

**Referme un vrai gap d'usage** : jusqu'ici host/port/identifiants
Domoticz étaient en dur dans le code (`DomoticzConfig()` avec des
valeurs par défaut) — personne ne pouvait utiliser l'app sans la
recompiler. Ce n'est plus le cas.

- **Modèle** : `AppSettings` (dans `model/DashboardConfig.kt`) — nouveau
  champ `settings` à la racine de `DashboardConfig`, additif (valeur par
  défaut, pas de breaking change pour un fichier déjà au format
  multi-pages). Éditable via l'écran de réglages **ou** directement dans
  le JSON via le serveur HTTP embarqué — les deux passent par le même
  `repository.updateConfig()`.
- **UI** (`SettingsDialog.kt`) : bouton engrenage toujours visible (pas
  seulement en mode édition, empilé sous le bouton crayon), formulaire
  host/port/utilisateur/mot de passe/HTTPS. Validation minimale (host non
  vide, port numérique) avant d'enregistrer.
- **Reconfiguration à chaud** (`DashboardViewModel.updateDomoticzSettings`) :
  persiste les nouveaux réglages, **ferme explicitement** l'ancien
  `DomoticzClient` (évite de fuiter la connexion HTTP), en recrée un
  nouveau avec la nouvelle config, et relance le polling — sans
  redémarrer l'app. Le polling météo n'a pas besoin de cette mécanique
  (chaque widget porte ses propres lat/lon, pas de réglage global à
  changer).
- **Toujours pas de lat/lon global pour la météo** — chaque widget météo
  garde ses propres coordonnées dans son `WidgetSource` (déjà flexible,
  permet plusieurs villes sur plusieurs widgets), donc pas de champ
  correspondant dans `AppSettings`.
- **Non testé sur device réel**, comme le reste : notamment le
  changement à chaud du client Domoticz pendant qu'un polling est en
  cours — la fenêtre entre l'annulation de l'ancien job et le démarrage
  du nouveau devrait être instantanée, mais à valider concrètement.

## Pas encore fait (volontairement)

- Intégration Android-Iconics / FontAwesome (dépendances commentées dans
  `app/build.gradle.kts`)

## Drag & drop de repositionnement (réagencement en cascade)

Le déplacement au doigt ne se contente plus de refuser un placement
invalide — il **pousse les widgets sur le chemin vers le bas**, façon
masonry (comme react-grid-layout), avec un aperçu en temps réel qui ne
se committe qu'au relâchement du doigt.

- `GridEngine.resolvePushLayout()` (pure, sans effet de bord) : calcule
  la disposition complète si le widget déplacé était posé à une position
  candidate. Les autres widgets sont poussés **verticalement uniquement**
  (`y` augmente, `x`/`w`/`h` ne changent jamais pour eux) — plus simple et
  plus prévisible qu'un repacking 2D complet. Traitement dans l'ordre
  d'origine (`y` puis `x`) pour un résultat stable, cascade si une
  poussée en entraîne une autre.
- Pendant le drag (`DashboardScreen.kt` → `EditOverlay`) : le widget
  déplacé suit le doigt **sans lag** (delta brut en pixels, pas
  d'animation) et passe à **60% d'opacité** (l'effet "grisé" demandé).
  Les autres widgets animent (`animateDpAsState`) vers leur position
  prévisualisée par `resolvePushLayout`, recalculée à chaque évènement de
  drag — donc si le doigt s'éloigne d'une zone avant de lâcher, les
  widgets qu'on avait poussés reviennent naturellement à leur position
  d'origine (rien de spécial à coder pour ça, c'est une conséquence directe
  du recalcul continu).
- **Le commit réel n'a lieu qu'au relâchement** (`onDragEnd`) : le dernier
  aperçu calculé est envoyé à `DashboardViewModel.applyLayout()`, qui
  persiste tous les widgets concernés (déplacé + poussés) via
  `repository.updateConfig()` en un seul appel.
- `onDragCancel` (interruption système du geste) : annule sans committer,
  tout revient à l'état d'avant le drag.
- **Ce changement ne concerne que le déplacement.** Le redimensionnement
  (poignée coin bas-droit) garde le comportement précédent : commit
  immédiat à chaque étape valide, refus silencieux si chevauchement — pas
  de poussée en cascade pour le resize.

**Repositionnement du concept "trous autorisés"** : ce système de
poussée par drag coexiste avec la grille libre décrite plus haut. Un
widget ajouté via `findFirstFreeSlot` ou déplacé manuellement dans le
JSON via le serveur HTTP peut toujours laisser des trous — seul le drag
tactile déclenche désormais un réagencement automatique.

## Points d'attention sur le drag & resize actuel

- **Non testé sur device réel** — l'algorithme de poussée cascade est
  couvert par la logique mais le ressenti tactile (fluidité de
  `animateDpAsState` avec plusieurs widgets qui bougent en même temps,
  zone de déclenchement du drag vs. tap simple) demande une vraie
  validation manette en main.
- Pas de retour visuel distinct type halo si la position finale sort de
  la grille en largeur (actuellement juste clampée silencieusement dans
  `resolvePushLayout`).
- Le tap sur la poignée de redimensionnement est petit (22dp) : à tester
  au doigt sur un vrai écran, potentiellement à agrandir la zone tactile
  au-delà du visuel via `Modifier.padding` négatif ou une zone invisible
  plus large. Même remarque pour le bouton de suppression (coin
  haut-gauche, même taille).
- **Suppression de widget** : bouton rouge "×" en haut à gauche de chaque
  widget en mode édition (`EditOverlay`, symétrique de la poignée de
  resize), appelle `viewModel.removeWidget()`. **Aucune confirmation**
  avant suppression — cohérent avec le reste de l'app (`removePage` n'en
  a pas non plus), mais à reconsidérer si ça s'avère source d'accidents
  en usage réel : un tap malheureux perd le widget (et sa position/config)
  immédiatement, sans "annuler".
- La marge visuelle ajoutée en bas de la grille pendant un drag (4 lignes
  supplémentaires dans `totalHeightDp`) est arbitraire — à ajuster si des
  poussées plus profondes se révèlent courantes en usage réel.

## Serveur HTTP embarqué (édition via navigateur)

- `data/ConfigRepository.kt` est maintenant la **source de vérité unique** :
  un `StateFlow<DashboardConfig>` persisté dans le stockage interne de
  l'app (`filesDir/dashboard_config.json`), copié depuis l'asset embarqué
  au tout premier lancement.
- `server/ConfigHttpServer.kt` (Ktor CIO, démarré dans `HomeHabitApp.onCreate`)
  expose sur le port **8090** :
  - `GET /` — page d'édition JSON minimale (`server/ConfigEditorHtml.kt`)
  - `GET /config` — JSON courant
  - `POST /config` — remplace la config (validée par parsing avant écriture)
- Le ViewModel et le serveur partagent la **même instance** de
  `ConfigRepository` (exposée par `HomeHabitApp`), donc toute modification
  — qu'elle vienne du drag & resize tactile ou d'un `POST /config` depuis
  un navigateur — est immédiatement visible des deux côtés, sans
  synchronisation manuelle.

### Authentification (token simple)

Toutes les routes exigent désormais un token — plus d'accès en lecture/
écriture libre pour n'importe quel appareil du réseau, ce qui comptait
puisque la config contient maintenant le mot de passe Domoticz et
pilote de vrais volets/serrures.

- **Génération** (`ConfigRepository.ensureHttpAuthToken`) : 8 caractères,
  alphabet restreint sans caractères ambigus (pas de `0`/`O`, `1`/`I`/`L`)
  puisque l'utilisateur doit potentiellement le retaper. Généré une
  seule fois au tout premier lancement, jamais régénéré ensuite tant
  qu'il n'est pas vide — persiste avec le reste de la config.
- **Vérification** (`ConfigHttpServer.isAuthorized`) : accepte le token
  soit en query param (`?token=...`), soit en en-tête
  `Authorization: Bearer ...`. Si aucun token n'est configuré (ne
  devrait plus arriver), **refuse tout par défaut** plutôt que d'ouvrir
  l'accès en grand.
- **`GET /`** accepte uniquement le query param (un chargement de page
  classique ne peut pas porter d'en-tête `Authorization`), et **injecte
  le token dans le JS de la page servie** (`configEditorHtml(token)`) —
  les appels `fetch()` suivants vers `/config` l'envoient automatiquement
  via l'en-tête, l'utilisateur n'a besoin de le saisir qu'une seule fois
  dans l'URL.
- **Dans l'app**, en mode édition, l'URL affichée (coin haut-gauche)
  inclut déjà le token (`http://<ip>:8090/?token=XXXXXXXX`) — **tap pour
  copier** dans le presse-papier (`LocalClipboardManager` + `Toast` de
  confirmation), plus besoin de le retaper à la main.
- **Toujours pas de HTTPS** (cohérent avec `usesCleartextTraffic="true"`
  déjà en place pour Domoticz) : le token circule en clair sur le réseau
  local. Cohérent avec le niveau de confiance supposé (LAN domestique),
  mais **à revisiter sérieusement** si l'app doit un jour être exposée
  au-delà du LAN — un token en clair sur un réseau non fiable n'apporte
  quasiment aucune protection réelle.
- Pas d'écran pour régénérer le token depuis l'app — uniquement en
  vidant les données de l'app (le prochain lancement en génère un
  nouveau) ou en éditant le JSON directement.

## Flux caméra (RTSP)

- `camera/RtspPlayer.kt` — wrapper autour de libVLC 3.7.0. Une instance =
  une session de lecture, créée/libérée avec le cycle de vie de la
  modale : **pas de lecture RTSP en arrière-plan**, le flux ne tourne que
  pendant que la modale est ouverte (important pour la charge CPU/réseau
  vu l'écran allumé en permanence).
- `ui/dashboard/CameraStreamModal.kt` — modale plein écran : tap sur un
  widget caméra (uniquement si `source.rtspUrl` est renseigné dans la
  config) → ouverture immédiate, connexion RTSP lancée en parallèle.
  Pendant la connexion, affiche le `source.url` (snapshot) désaturé en
  noir et blanc (`ColorMatrix.setToSaturation(0f)` via Coil) en
  arrière-plan flou, avec un indicateur "Connexion au flux...". Dès que
  libVLC atteint l'état `Playing`, **attend 300ms supplémentaires**
  avant de considérer le flux "visuellement prêt" et de lancer le fondu
  (600ms) vers la vidéo couleur. Ce délai existe parce que l'event
  `Playing` de libVLC signale un changement d'état interne, pas qu'une
  frame ait réellement été rendue à l'écran (négociation RTSP, attente
  de keyframe, démarrage du décodage matériel) — sans lui, le fondu
  risquait de démarrer avant qu'il y ait une vraie image, provoquant un
  flash noir entre le poster et le flux. Même logique de précaution déjà
  utilisée dans `RtspThumbnailGrabber` pour la capture de snapshot.
  Non testé sur device réel — la valeur de 300ms est une estimation, pas
  une mesure.
- Options libVLC actuelles : `--no-audio` (pas de son), `--rtsp-tcp`
  (RTSP sur TCP, plus fiable que UDP sur réseau domestique), cache réseau
  réduit à 300ms pour limiter la latence.

### Snapshot dans le widget (implémenté)

`WidgetCard.kt` → `CameraContent` gère maintenant trois cas, dans cet ordre
de priorité :

1. **`source.url` renseigné** → `SnapshotImage` charge l'image via Coil et
   la rafraîchit toutes les `source.refreshSeconds` secondes (5s minimum),
   avec un cache-busting simple (`?_t=<timestamp>`) pour forcer le
   rechargement plutôt que de servir une version en cache.
2. **`source.url` vide mais `source.rtspUrl` renseigné** → `RtspFallbackThumbnail`
   tente une capture *best-effort* d'une frame du flux RTSP via
   `RtspThumbnailGrabber` (voir `camera/RtspThumbnailGrabber.kt`).
   Volontairement moins fiable qu'un vrai snapshot HTTP : la capture
   ouvre une vraie connexion RTSP (coûteux), et la méthode dépend du
   rendu interne de libVLC (`TextureView.getBitmap()` ou `PixelCopy`
   selon le device — `PixelCopy` indisponible avant Android 7.0/API 24).
   En cas d'échec, retombe simplement sur le placeholder générique, sans
   bloquer l'affichage. L'intervalle réel est plafonné à 30s minimum quel
   que soit `refreshSeconds`, pour éviter d'ouvrir une connexion RTSP en
   boucle rapide.
3. **Ni l'un ni l'autre** → placeholder générique (icône).

A tester sur device réel : le comportement de capture RTSP (cas 2) varie
significativement selon le firmware/codec de la caméra et le device
Android — c'est un best-effort assumé, pas une garantie.

### Ce qui manque encore côté caméra

- Pas de gestion de reconnexion automatique si le flux RTSP tombe en
  cours de visionnage dans la modale plein écran (état `ERROR` affiché,
  mais pas de retry).
- Pas de test sur device réel avec une vraie caméra RTSP — le
  comportement de libVLC varie significativement selon le codec/firmware
  de la caméra (H.264 vs H.265, profils, etc.), à valider concrètement.

## Badge "dernière mise à jour"

- Chaque widget affiche désormais un petit badge discret en haut à droite
  (`WidgetCard.kt` → `LastUpdateBadge`) : "à l'instant" / "il y a Xmin" /
  "il y a Xh" en dessous de 24h, puis `JJ/MM` au-delà.
- `data/WidgetStateEntry.kt` associe chaque `WidgetLiveState` à son
  timestamp — introduit sans toucher au sealed class existant, pour ne
  pas propager le changement dans chaque variante (Weather, Light, etc.).
- **Widgets Domoticz** : le timestamp vient du vrai champ `LastUpdate`
  renvoyé par le serveur (pas de l'heure du poll local), parsé depuis le
  format Domoticz `yyyy-MM-dd HH:mm:ss`. **Limite connue** : ce format ne
  contient pas de fuseau horaire, donc on suppose que le serveur Domoticz
  et le téléphone sont dans le même fuseau (cas normal sur un réseau
  domestique, mais à garder en tête).
- **Widgets démo** (météo, caméra) : timestamp = moment du chargement de
  l'app, puisqu'il n'y a pas encore de vraie source de données qui se
  rafraîchit pour eux.
- Le badge se rafraîchit tout seul toutes les 30s (`LaunchedEffect` local
  à `LastUpdateBadge`) pour que le texte relatif reste juste sans
  attendre un nouvel évènement métier.

## Ouvrir le projet

Ouvrir le dossier `homehabit/` dans Android Studio (Koala ou plus récent).
Gradle générera le wrapper au premier sync. `minSdk = 23` (Android 6.0),
`compileSdk = 34`.

**Pas encore d'icône de lancement** : `AndroidManifest.xml` ne référence
plus `android:icon` (retiré volontairement, aucune ressource mipmap
n'existe encore dans le projet — le référencer sans la fournir aurait fait
échouer `processDebugResources`). L'app utilisera l'icône par défaut
d'Android tant qu'un vrai jeu d'icônes (`res/mipmap-*/ic_launcher.png` ou
un adaptive icon) n'est pas ajouté.

**Notes sur le manifest** : deux corrections y ont été apportées après
coup — le namespace `xmlns:android` pointait par erreur vers `res-auto`
au lieu de `res/android` (empêchait tout attribut `android:*` de résoudre
correctement, cause du premier échec de `processDebugMainManifest`), et
`android:extractNativeLibs="true"` + `tools:replace="android:extractNativeLibs"`
ont été ajoutés car libVLC embarque son propre manifest qui impose cette
valeur, en conflit avec le défaut d'AGP. Depuis, le mode nuit a ajouté
`RECEIVE_BOOT_COMPLETED` et `SCHEDULE_EXACT_ALARM` (permissions), plus
trois `<receiver>` (`HomeHabitDeviceAdminReceiver`, `ScreenAlarmReceiver`,
`BootReceiver` — voir section dédiée plus haut) et
`showWhenLocked`/`turnScreenOn` sur `MainActivity` (rallumage après
extinction planifiée).

## État du projet

Toutes les briques listées dans la demande initiale sont couvertes :
Domoticz (lecture + écriture + découverte), météo (courante + prévision
7 jours), caméra (snapshot + RTSP), dashboard configurable (JSON +
serveur HTTP authentifié + écran de réglages natif), multi-pages avec
swipe, drag & resize avec réagencement en cascade, thème cohérent, et
mode nuit. Ce qui reste dans le README au fil des sections comme
"non testé" ou "best-effort" est la vraie limite actuelle : **rien de
tout ça n'a jamais tourné sur un appareil Android réel** — uniquement
vérifié par lecture de code et analyse statique (équilibre des
accolades, résolution des imports, cohérence des signatures). Un premier
vrai build + test sur device fera probablement remonter des ajustements,
en particulier sur les parties qui touchent à des comportements
spécifiques au fabricant (mode nuit niveau 2, alarmes exactes, gestion
de batterie agressive).
