# FCA4J UI — Guide de déploiement

## Prérequis par plateforme

### Windows
- JDK 21+ ([Adoptium Temurin](https://adoptium.net/temurin/releases/?version=21))
- [WiX Toolset 3.x](https://github.com/wixtoolset/wix3/releases) — requis par jpackage pour générer le `.msi`
  - Télécharger `wix3xx-binaries.zip`, extraire, ajouter le dossier au `PATH`
  - Vérifier : `candle.exe --version`
- Maven 3.8+

### Linux
- JDK 21+
- `fakeroot` : `sudo apt install fakeroot` (Debian/Ubuntu) ou `sudo dnf install fakeroot` (Fedora)
- Maven 3.8+

### macOS
- JDK 21+
- Xcode Command Line Tools : `xcode-select --install`
- Maven 3.8+

---

## Structure des ressources de déploiement

```
src/main/deploy/
└── icons/
    ├── fca4j-ui.ico    ← Windows  (256x256, format ICO)
    ├── fca4j-ui.png    ← Linux    (512x512, PNG)
    └── fca4j-ui.icns   ← macOS    (format ICNS)
```

### Créer les icônes

À partir d'une image source 512x512 PNG :

**Windows (.ico)** — avec ImageMagick :
```bash
magick convert fca4j-ui-512.png -resize 256x256 fca4j-ui.ico
```
Ou utiliser [IcoFX](https://icofx.ro/) / [ConvertICO](https://convertico.com/) en ligne.

**Linux (.png)** — copier directement le PNG 512x512.

**macOS (.icns)** :
```bash
# Sur macOS uniquement
mkdir fca4j-ui.iconset
sips -z 512 512 fca4j-ui-512.png --out fca4j-ui.iconset/icon_512x512.png
sips -z 256 256 fca4j-ui-512.png --out fca4j-ui.iconset/icon_256x256.png
sips -z 128 128 fca4j-ui-512.png --out fca4j-ui.iconset/icon_128x128.png
sips -z 64  64  fca4j-ui-512.png --out fca4j-ui.iconset/icon_64x64.png
sips -z 32  32  fca4j-ui-512.png --out fca4j-ui.iconset/icon_32x32.png
sips -z 16  16  fca4j-ui-512.png --out fca4j-ui.iconset/icon_16x16.png
iconutil -c icns fca4j-ui.iconset
```

---

## Étapes de build

### 1. Installer les JARs FCA4J localement

Depuis le répertoire `fca4j-project` :
```bash
mvn install
```

### 2. Compiler et créer le JAR fat

```bash
cd fca4j-ui
mvn package -DskipTests
```
→ Produit `target/FCA4J-UI-0.1.0-standalone.jar`

### 3. Créer l'installeur natif

```bash
mvn jpackage:jpackage
```
→ Produit `target/installer/` :
- Windows : `FCA4J-UI-0.1.0.msi`
- Linux   : `fca4j-ui_0.1.0-1_amd64.deb`
- macOS   : `FCA4J-UI-0.1.0.dmg`

### Commande complète (build + package + installeur) :
```bash
mvn package jpackage:jpackage -DskipTests
```

---

## Distribution aux testeurs

L'installeur embarque le JRE complet — **les testeurs n'ont pas besoin d'installer Java**.

### Instructions pour les testeurs

**Windows :**
1. Télécharger `FCA4J-UI-0.1.0.msi`
2. Double-cliquer pour installer
3. FCA4J UI apparaît dans le menu Démarrer
4. Au premier lancement : **Fichier > Préférences** → configurer le chemin vers `fca4j-cli-0.4.6.jar`
5. Configurer éventuellement le chemin vers `dot` (GraphViz) si installé

**Linux :**
```bash
sudo dpkg -i fca4j-ui_0.1.0-1_amd64.deb
fca4j-ui   # ou depuis le menu Applications > Science
```

**macOS :**
1. Télécharger `FCA4J-UI-0.1.0.dmg`
2. Ouvrir le DMG et glisser l'application dans `/Applications`
3. Au premier lancement : clic droit → Ouvrir (nécessaire si non signé)

---

## Dépannage fréquent

### Windows : "WiX toolset not found"
Vérifier que WiX est dans le PATH :
```cmd
candle.exe --version
```
Si absent : ajouter le répertoire de WiX (`C:\Program Files (x86)\WiX Toolset v3.x\bin`) aux variables d'environnement.

### macOS : "The application cannot be opened"
L'application n'est pas signée. Contournement :
```bash
sudo xattr -rd com.apple.quarantine /Applications/FCA4J-UI.app
```
Ou : **Préférences Système > Confidentialité et sécurité > Ouvrir quand même**.

### Linux : dépendance manquante
```bash
sudo apt install --fix-broken
```

### GraphViz non trouvé
Installer GraphViz, puis configurer le chemin dans **Fichier > Préférences** :
- Windows : `C:\Program Files\Graphviz\bin\dot.exe`
- Linux   : `/usr/bin/dot`
- macOS   : `/opt/homebrew/bin/dot`

---

## Mettre à jour la version

Dans `pom.xml`, modifier :
```xml
<app.version>0.2.0</app.version>
```
Et relancer `mvn package jpackage:jpackage -DskipTests`.

Le `winUpgradeUuid` dans le `pom.xml` doit rester **identique** entre les versions
pour que Windows reconnaisse la mise à jour et désinstalle l'ancienne version automatiquement.
