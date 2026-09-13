# Génération automatique de l'APK

Le dépôt contient `.github/workflows/build-apk.yml`.
À chaque push sur `main` (ou lancement manuel), GitHub Actions compile :

`app/build/outputs/apk/debug/app-debug.apk`

L'APK est publié comme artifact `PortfolioTrackerAndroid-V1-debug`.
