# Nuru Studio AI

Studio de création numérique mobile assisté par intelligence artificielle multi-agents et BYO-API.

## Fonctionnalités Principales

- **Orchestration Multi-Agents** : Équipe de 8 agents spécialisés (Architecte, Designer, Développeur, Testeur, Débogueur, Sécurité, DevOps, Documentaliste).
- **AI Provider Manager (BYO-API)** : Intégration de vos propres fournisseurs IA (OpenAI, Gemini, Anthropic Claude, DeepSeek, Mistral, OpenRouter, Ollama local).
- **Model Explorer** : Découverte automatique des modèles, comparaison, banc d'essai de latence et coût.
- **AI Router** : Routage intelligent des requêtes sur 5 slots (⚡ Rapide, 🧠 Puissant, 💻 Code, 👁 Vision, 🎨 Créatif) avec chaîne de fallback résiliente.
- **Nuru Code (IDE Mobile)** : Éditeur de code complet avec barre d'outils mobile dev, numérotation de lignes, coloration et raccourcis.
- **Aperçu Live Interactif** : Rendu WebView direct des sites HTML5/Tailwind avec bascule Mobile / Desktop.
- **Gestionnaire de Projets Multi-Livrables** : Génération de sites Web, d'applications mobiles Flutter et d'APIs.
- **Terminal Intégré & Git** : Commandes CLI simulées (`run`, `build`, `test`, `git`, `tree`).

## Compilation & CI/CD

Ce projet est configuré pour compiler automatiquement sur GitHub Actions (`.github/workflows/android.yml`) avec OpenJDK 21 et Android SDK 36.

### Build local :
```bash
./scripts/fix-executable-permissions.sh
./gradlew clean assembleDebug
```
