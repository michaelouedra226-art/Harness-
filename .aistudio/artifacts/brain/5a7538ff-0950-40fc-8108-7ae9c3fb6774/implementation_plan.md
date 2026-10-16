# Plan d'Améliorations Techniques — Nuru Studio AI

Plan d'action exhaustif intégrant le streaming en temps réel des réponses IA, la gestion avancée des sous-dossiers, la console de débogage et le mode hors-ligne avec résilience réseau.

## Décisions Clés Validées

> [!IMPORTANT]
> L'utilisateur a validé l'intégration simultanée des 4 piliers d'amélioration technique ainsi qu'un système de contrôle autonome complet intégrant exécution directe et journal d'audit.

- **Pilier 1 (Streaming IA)** : Implémentation du streaming Server-Sent Events (SSE) et de l'effet dactylographique pour afficher les tokens au fur et à mesure de l'inférence.
- **Pilier 2 (Gestion des Sous-Dossiers)** : Support des arborescences imbriquées (`src/components/`, `assets/css/`, `lib/models/`) dans le sélecteur de fichiers et le modèle de projet.
- **Pilier 3 (Console de Débogage & Diff)** : Journalisation temps réel de chaque action autonome de l'IA (`CREATE_FILE`, `UPDATE_FILE`) avec historique des modifications.
- **Pilier 4 (Mode Hors-Ligne & Résilience)** : Détection automatique de la connectivité réseau et bascule transparente vers les fournisseurs locaux (Ollama / cache).

---

## 1. Vue d'Ensemble & Objectifs

Nuru Studio AI passe à un niveau supérieur de réactivité et d'autonomie technique :
1. **Fluidité d'affichage** : Fin des temps d'attente bloquants grâce au flux de tokens en direct.
2. **Projets complexes** : Capacité de gérer des architectures réelles avec sous-dossiers et modules.
3. **Traçabilité totale** : Visualisation claire de ce que l'IA a modifié ou créé dans le projet.
4. **Indépendance réseau** : Continuité de travail en mobilité même en cas de coupure Internet.

---

## 2. Expérience Utilisateur & Design

### Flux Utilisateur Optimisés
- **Chat avec Streaming** : Le message de l'agent s'affiche de manière progressive avec indicateur d'activité ("neural pulse") et bouton d'interruption immédiate.
- **Explorateur d'Arborescence** : Barre de navigation hiérarchique (breadcrumbs) permettant de naviguer dans les répertoires du projet actif sans encombrer l'écran mobile.
- **Volet d'Audit des Actions** : Dès qu'une action autonome s'exécute, un badge discret permet de déplier le détail des fichiers touchés et des lignes ajoutées.
- **Indicateur de Statut Réseau** : Pastille de connectivité dans la barre supérieure indiquant l'état en direct (Cloud actif, Local Ollama, ou Hors-ligne).

---

## 3. Choix Techniques & Arbitrages

```
┌─────────────────────────────────────────────────────────────┐
│                    Nuru Studio AI Core                      │
├──────────────────────────────┬──────────────────────────────┤
│       UI Presentation        │       Engine & Storage       │
│  ┌────────────────────────┐  │  ┌────────────────────────┐  │
│  │ Streaming Chat Bubble  │  │  │ OkHttp SSE EventSource │  │
│  └────────────────────────┘  │  └────────────────────────┘  │
│  ┌────────────────────────┐  │  ┌────────────────────────┐  │
│  │ Hierarchical Explorer  │  │  │ NuruLocalStorage (Disk)│  │
│  └────────────────────────┘  │  └────────────────────────┘  │
│  ┌────────────────────────┐  │  ┌────────────────────────┐  │
│  │ Autonomous Action Hub  │  │  │ ConnectivityManager    │  │
│  └────────────────────────┘  │  └────────────────────────┘  │
└──────────────────────────────┴──────────────────────────────┘
```

- **Streaming HTTP** : Utilisation d'un stream `OkHttpClient` avec lecture continue des `data: ` SSE pour les fournisseurs compatibles (OpenAI, Gemini, OpenRouter).
- **Structure des Fichiers** : Normalisation des chemins relatifs UNIX (`folder/subfolder/file.ext`) avec extraction dynamique de l'arborescence des dossiers.
- **Gestion d'État** : Intégration de flux `StateFlow` réactifs dans `NuruViewModel` sans blocage du thread UI principal.

---

## 4. Étapes d'Exécution Prévues

1. **Module Streaming** : Ajout du flux SSE dans `AIProviderClient.kt` et propagation mot par mot vers `ChatMessage`.
2. **Arborescence & Sous-Dossiers** : Mise à jour du composant de sélection de fichiers dans `CodeEditorScreen.kt` avec regroupement par dossier.
3. **Journal des Actions Autonomes** : Ajout d'une modale de détails des actions exécutées par l'agent IA avec comparatif de lignes.
4. **Détection Réseau & Offline** : Implémentation du `ConnectivityObserver` pour basculer automatiquement sur Ollama ou signaler l'absence de réseau.
5. **Compilation & Validation** : Exécution de `compile_applet` et vérification des permissions CI/CD.
