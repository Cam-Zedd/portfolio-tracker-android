# Portfolio Tracker Android V1 — base V5.7 MASTER

Cette application Android reprend les fichiers d'entrée du paquet **Portfolio_tracker_V5_7_MASTER_FULL_NO_SETUP_BAT** du 11/09/2026.

## Ce qui fonctionne dans la V1

- Dashboard principal dans une `WebView`.
- Deux vues :
  - **Dashboard Android live** recalculé directement sur le téléphone.
  - **Dashboard V5.7 original** embarqué comme référence visuelle.
- Édition locale des CSV V5.7 dans le menu **Données** :
  - transactions CTO / PEA / eToro ;
  - cash movements ;
  - config ;
  - instruments et objectifs ;
  - watchlist News ;
  - historiques annuels et benchmarks ;
  - `_transaction_overrides.csv` (PRU / FX / coûts historiques avancés).
- Ajout, modification et suppression de lignes dans chaque CSV.
- Import d'un CSV par son nom V5.7.
- Export de tous les CSV dans un ZIP.
- Moteur Kotlin natif pour :
  - BUY / SELL ;
  - ventes partielles ;
  - PRU ;
  - coûts historiques ;
  - PV/MV réalisée et latente ;
  - cash à partir de l'ancre `cash_anchor_*` ;
  - apports / retraits ;
  - FX courants et historiques ;
  - récupération des cours Yahoo Finance ;
  - variation de séance ;
  - conversion globale EUR/USD.
- Heatmap / treemap et répartitions Plotly dans le dashboard Android.
- Bouton **PDF** : utilise le système d'impression Android, qui permet notamment « Enregistrer au format PDF ».

## Fidélité à la V5.7

Le moteur Android reprend le mécanisme `_transaction_overrides.csv` de la V5.7. Il conserve notamment les corrections historiques nécessaires aux lignes telles que Innate Pharma, BX4 et Xerox.

Le fichier `cto_config.csv` livré dans l'application conserve bien `cash_anchor_eur = 485.26`.

## Différences avec le tracker PC V5.7

La V1 Android ne lance pas le script Python d'origine. Elle remplace le cœur de calcul quotidien par un moteur Kotlin afin d'éviter de dépendre de pandas/matplotlib/yfinance/Playwright dans l'APK.

Ne sont pas encore portés à l'identique :

- News Intelligence / Discord ;
- la totalité des graphiques d'historique intraday/journalier ;
- le journal de trades détaillé avec exactement la même mise en page que le HTML PC ;
- la génération PDF Chromium automatique. Sur Android le bouton PDF passe par le moteur d'impression du téléphone.

Les CSV restent compatibles avec le tracker PC : un export depuis l'application peut donc être réutilisé comme fichiers d'entrée de la V5.7.

## Compilation

1. Ouvrir le dossier `PortfolioTrackerAndroid_V1` dans Android Studio.
2. Laisser Android Studio synchroniser Gradle.
3. Installer Android SDK 35 si Android Studio le demande.
4. Lancer `app` sur un téléphone ou émulateur Android 8.0+.
5. Pour créer l'APK : **Build > Build APK(s)**.

Configuration du projet :

- package : `fr.zeddcara.portfoliotracker`
- minSdk : 26
- targetSdk : 35
- Kotlin : 2.0.21
- Android Gradle Plugin : 8.7.3
- aucune dépendance AndroidX ou bibliothèque réseau externe.

## Structure utile

- `app/src/main/assets/input/` : fichiers CSV V5.7 initiaux.
- `app/src/main/assets/dashboard/` : dashboard V5.7 original + Plotly + images.
- `PortfolioEngine.kt` : calcul portefeuille.
- `YahooFinanceClient.kt` : cours et FX.
- `DashboardRenderer.kt` : dashboard live Android.
- `CsvEditorActivity.kt` : édition des fichiers d'entrée.

## Validation effectuée

La logique de cash a été comparée au moteur Python V5.7 sur les fichiers fournis :

- CTO : `692.0217755756798`
- PEA : `377.9`
- eToro : `20.62`

La somme des PV/MV réalisées CTO obtenue avec les overrides V5.7 est également identique au moteur Python (`58.211114285...`).
