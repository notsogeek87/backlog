#!/usr/bin/env node

/**
 * vibe-analyze.js - Script d'analyse complète du repository Backlog
 * 
 * Analyse la structure, la stack technique, les dépendances, les tests,
 * la documentation et les workflows CI/CD.
 * 
 * Usage: node scripts/vibe-analyze.js [options]
 * Options:
 *   --json     Output en JSON
 *   --quiet    Mode silencieux (erreurs seulement)
 *   --help     Affiche l'aide
 */

import { readFile, readdir, stat } from 'node:fs/promises';
import { join, resolve, relative, extname, basename, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..');

// Charger chalk si disponible
let chalk;
try {
  const require = createRequire(import.meta.url);
  chalk = require('chalk');
} catch {
  // Fallback sans couleurs
  chalk = {
    green: (t) => t,
    yellow: (t) => t,
    red: (t) => t,
    blue: (t) => t,
    cyan: (t) => t,
    bold: { cyan: (t) => t, white: (t) => t },
    dim: (t) => t
  };
}

// Couleurs
const c = {
  success: chalk.green,
  warning: chalk.yellow,
  error: chalk.red,
  info: chalk.blue,
  title: chalk.bold.cyan,
  subtitle: chalk.bold.white,
  dim: chalk.dim,
  bold: chalk.bold
};

/**
 * Exécute une commande shell
 */
async function exec(command, options = {}) {
  const { spawn } = await import('node:child_process');
  const { cwd = ROOT, reject = true } = options;
  
  return new Promise((resolve, rejectFn) => {
    const child = spawn(command, { shell: true, cwd, stdio: 'pipe' });
    let stdout = '';
    let stderr = '';
    
    child.stdout.on('data', (data) => { stdout += data.toString(); });
    child.stderr.on('data', (data) => { stderr += data.toString(); });
    
    child.on('close', (code) => {
      if (code !== 0 && reject) {
        const error = new Error(`Command failed with exit code ${code}: ${command}`);
        error.stdout = stdout;
        error.stderr = stderr;
        rejectFn(error);
      } else {
        resolve({ stdout, stderr, code });
      }
    });
    
    child.on('error', rejectFn);
  });
}

/**
 * Analyseur du repository Backlog
 */
class BacklogAnalyzer {
  constructor(root) {
    this.root = root;
    this.results = {
      metadata: {},
      structure: {},
      stack: {},
      dependencies: {},
      tests: {},
      documentation: {},
      workflows: {},
      issues: [],
      warnings: [],
      stats: {}
    };
  }

  /**
   * Lance l'analyse complète
   */
  async analyze() {
    console.log(c.title('\n🔍 Analyse du Repository Backlog\n'));
    
    try {
      await this.analyzeMetadata();
      await this.analyzeStructure();
      await this.analyzeStack();
      await this.analyzeDependencies();
      await this.analyzeTests();
      await this.analyzeDocumentation();
      await this.analyzeWorkflows();
      await this.analyzeCodeQuality();
      
      this.generateStats();
      this.printSummary();
      
      return this.results;
    } catch (error) {
      console.error(c.error('❌ Erreur lors de l\'analyse:'), error.message);
      throw error;
    }
  }

  /**
   * Analyse des métadonnées du projet
   */
  async analyzeMetadata() {
    console.log(c.subtitle('📋 Métadonnées du Projet\n'));
    
    const readmePath = join(this.root, 'README.md');
    const contributingPath = join(this.root, 'CONTRIBUTING.md');
    const packageJsonPath = join(this.root, 'server', 'package.json');
    
    try {
      // Lire README
      const readme = await this.safeReadFile(readmePath);
      if (readme) {
        const titleMatch = readme.match(/^#\s+(.+)/m);
        const descriptionMatch = readme.match(/^(.+)\n/m);
        
        this.results.metadata.name = titleMatch ? titleMatch[1].trim() : 'Backlog';
        this.results.metadata.description = descriptionMatch ? descriptionMatch[1].trim() : '';
        
        // Extraire la stack
        const stackSection = readme.match(/## Stack[\s\S]*?(?=##|\n\n|$)/);
        if (stackSection) {
          this.results.metadata.stackDescription = stackSection[0].trim();
        }
      }

      // Lire CONTRIBUTING
      const contributing = await this.safeReadFile(contributingPath);
      if (contributing) {
        this.results.metadata.contributing = contributing;
      }

      // Lire server/package.json
      const serverPackage = await this.safeReadJson(packageJsonPath);
      if (serverPackage) {
        this.results.metadata.server = {
          name: serverPackage.name,
          version: serverPackage.version,
          nodeVersion: serverPackage.engines?.node,
          type: serverPackage.type
        };
      }

      // Lire gradle.properties
      const gradlePropsPath = join(this.root, 'gradle.properties');
      const gradleProps = await this.safeReadFile(gradlePropsPath);
      if (gradleProps) {
        const props = {};
        gradleProps.split('\n').forEach(line => {
          const [key, value] = line.split('=');
          if (key && value) {
            props[key.trim()] = value.trim();
          }
        });
        this.results.metadata.gradleProperties = props;
      }

      console.log(c.info('✓ Nom:'), this.results.metadata.name);
      console.log(c.info('✓ Description:'), this.results.metadata.description || 'Non spécifiée');
      console.log(c.info('✓ Version serveur:'), this.results.metadata.server?.version || 'Non spécifiée');
      console.log();
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'metadata',
        message: `Erreur lecture métadonnées: ${error.message}`
      });
    }
  }

  /**
   * Analyse la structure du projet
   */
  async analyzeStructure() {
    console.log(c.subtitle('📁 Structure du Projet\n'));
    
    const structure = {
      root: {
        files: [],
        dirs: []
      },
      app: {
        files: [],
        dirs: [],
        packages: []
      },
      server: {
        files: [],
        dirs: []
      },
      docs: {
        files: [],
        dirs: []
      },
      tools: {
        files: [],
        dirs: []
      },
      github: {
        files: [],
        dirs: []
      }
    };

    try {
      // Analyser la racine
      const rootItems = await readdir(this.root);
      for (const item of rootItems) {
        if (item.startsWith('.') && !['.github', '.gitignore'].includes(item)) continue;
        if (item === 'node_modules' || item === 'build' || item === 'app') continue;
        
        const path = join(this.root, item);
        const itemStat = await stat(path);
        
        if (itemStat.isDirectory()) {
          structure.root.dirs.push(item);
        } else {
          structure.root.files.push(item);
        }
      }

      // Analyser app/
      const appPath = join(this.root, 'app');
      if (await this.exists(appPath)) {
        await this.analyzeDirectory(appPath, structure.app, 'app');
        
        // Extraire les packages Kotlin
        const mainJavaPath = join(appPath, 'src', 'main', 'java');
        if (await this.exists(mainJavaPath)) {
          const packages = await this.findPackages(mainJavaPath, 'com.davidgcd.backlog');
          structure.app.packages = packages;
        }
      }

      // Analyser server/
      const serverPath = join(this.root, 'server');
      if (await this.exists(serverPath)) {
        await this.analyzeDirectory(serverPath, structure.server, 'server');
      }

      // Analyser docs/
      const docsPath = join(this.root, 'docs');
      if (await this.exists(docsPath)) {
        await this.analyzeDirectory(docsPath, structure.docs, 'docs');
      }

      // Analyser tools/
      const toolsPath = join(this.root, 'tools');
      if (await this.exists(toolsPath)) {
        await this.analyzeDirectory(toolsPath, structure.tools, 'tools');
      }

      // Analyser .github/
      const githubPath = join(this.root, '.github');
      if (await this.exists(githubPath)) {
        await this.analyzeDirectory(githubPath, structure.github, '.github');
      }

      this.results.structure = structure;

      console.log(c.bold('Racine:'));
      console.log('  Dossiers:', structure.root.dirs.length > 0 ? structure.root.dirs.join(', ') : 'Aucun');
      console.log('  Fichiers:', structure.root.files.length > 0 ? structure.root.files.join(', ') : 'Aucun');
      console.log();
      
      console.log(c.bold('Application Android (app/):'));
      console.log('  Dossiers:', structure.app.dirs.length);
      console.log('  Fichiers:', structure.app.files.length);
      console.log('  Packages:', structure.app.packages.length > 0 ? structure.app.packages.join(', ') : 'Aucun');
      console.log();
      
      console.log(c.bold('Serveur (server/):'));
      console.log('  Dossiers:', structure.server.dirs.length);
      console.log('  Fichiers:', structure.server.files.length);
      console.log();
      
      console.log(c.bold('Documentation (docs/):'));
      console.log('  Dossiers:', structure.docs.dirs.length);
      console.log('  Fichiers:', structure.docs.files.length);
      console.log();
      
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'structure',
        message: `Erreur analyse structure: ${error.message}`
      });
    }
  }

  /**
   * Analyse la stack technique
   */
  async analyzeStack() {
    console.log(c.subtitle('🛠️ Stack Technique\n'));
    
    const stack = {
      android: {
        language: 'Kotlin',
        ui: [],
        architecture: [],
        persistence: [],
        networking: [],
        images: [],
        testing: []
      },
      server: {
        language: 'JavaScript',
        runtime: 'Node.js',
        framework: [],
        database: [],
        testing: []
      },
      ci_cd: {
        platform: 'GitHub Actions',
        workflows: []
      },
      tools: {
        build: [],
        dependency_management: [],
        linting: [],
        formatting: []
      }
    };

    try {
      // Analyser app/build.gradle.kts
      const appBuildGradle = join(this.root, 'app', 'build.gradle.kts');
      const appBuildContent = await this.safeReadFile(appBuildGradle);
      
      if (appBuildContent) {
        // Extraire les dépendances
        const dependencies = appBuildContent.match(/dependencies\s*\{[\s\S]*?\}/);
        if (dependencies) {
          const depsText = dependencies[0];
          
          // Compose
          if (depsText.includes('compose')) {
            stack.android.ui.push('Jetpack Compose');
          }
          
          // Room
          if (depsText.includes('room')) {
            stack.android.persistence.push('Room');
          }
          
          // Retrofit
          if (depsText.includes('retrofit')) {
            stack.android.networking.push('Retrofit');
          }
          
          // Coil
          if (depsText.includes('coil')) {
            stack.android.images.push('Coil');
          }
          
          // Tests
          if (depsText.includes('junit')) {
            stack.android.testing.push('JUnit');
          }
          if (depsText.includes('robolectric')) {
            stack.android.testing.push('Robolectric');
          }
        }

        // Architecture
        stack.android.architecture.push('MVVM');
      }

      // Analyser server/package.json
      const serverPackage = join(this.root, 'server', 'package.json');
      const serverPackageContent = await this.safeReadJson(serverPackage);
      
      if (serverPackageContent) {
        stack.server.runtime = `Node.js ${serverPackageContent.engines?.node || '>=20'}`;
        stack.server.type = serverPackageContent.type || 'commonjs';
        
        if (serverPackageContent.dependencies) {
          if (serverPackageContent.dependencies['@neondatabase/serverless']) {
            stack.server.database.push('Neon (PostgreSQL serverless)');
          }
        }
      }

      // Analyser les workflows
      const workflowsPath = join(this.root, '.github', 'workflows');
      if (await this.exists(workflowsPath)) {
        const workflows = await readdir(workflowsPath);
        stack.ci_cd.workflows = workflows.filter(w => w.endsWith('.yml') || w.endsWith('.yaml'));
      }

      // Outils
      stack.tools.build.push('Gradle');
      stack.tools.dependency_management.push('Gradle Version Catalog');

      this.results.stack = stack;

      console.log(c.bold('Android:'));
      console.log('  Langage:', stack.android.language);
      console.log('  Architecture:', stack.android.architecture.join(', '));
      console.log('  UI:', stack.android.ui.join(', '));
      console.log('  Persistance:', stack.android.persistence.join(', '));
      console.log('  Réseau:', stack.android.networking.join(', '));
      console.log('  Images:', stack.android.images.join(', '));
      console.log('  Tests:', stack.android.testing.join(', '));
      console.log();
      
      console.log(c.bold('Serveur:'));
      console.log('  Runtime:', stack.server.runtime);
      console.log('  Type:', stack.server.type);
      console.log('  Base de données:', stack.server.database.join(', '));
      console.log();
      
      console.log(c.bold('CI/CD:'));
      console.log('  Plateforme:', stack.ci_cd.platform);
      console.log('  Workflows:', stack.ci_cd.workflows.length > 0 ? stack.ci_cd.workflows.join(', ') : 'Aucun');
      console.log();
      
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'stack',
        message: `Erreur analyse stack: ${error.message}`
      });
    }
  }

  /**
   * Analyse les dépendances
   */
  async analyzeDependencies() {
    console.log(c.subtitle('📦 Dépendances\n'));
    
    const dependencies = {
      android: {
        total: 0,
        byCategory: {},
        outdated: []
      },
      server: {
        total: 0,
        byCategory: {},
        outdated: []
      }
    };

    try {
      // Analyser app/build.gradle.kts
      const appBuildGradle = join(this.root, 'app', 'build.gradle.kts');
      const appBuildContent = await this.safeReadFile(appBuildGradle);
      
      if (appBuildContent) {
        const depsMatch = appBuildContent.match(/dependencies\s*\{[\s\S]*?\}/);
        if (depsMatch) {
          const depsText = depsMatch[0];
          const lines = depsText.split('\n');
          
          let inDependencies = false;
          for (const line of lines) {
            const trimmed = line.trim();
            
            if (trimmed === 'dependencies {') {
              inDependencies = true;
              continue;
            }
            
            if (trimmed === '}' && inDependencies) {
              inDependencies = false;
              continue;
            }
            
            if (inDependencies && trimmed && !trimmed.startsWith('//')) {
              dependencies.android.total++;
              
              // Catégoriser
              if (trimmed.includes('compose')) {
                dependencies.android.byCategory['Compose'] = (dependencies.android.byCategory['Compose'] || 0) + 1;
              } else if (trimmed.includes('room')) {
                dependencies.android.byCategory['Room'] = (dependencies.android.byCategory['Room'] || 0) + 1;
              } else if (trimmed.includes('retrofit') || trimmed.includes('okhttp') || trimmed.includes('moshi')) {
                dependencies.android.byCategory['Networking'] = (dependencies.android.byCategory['Networking'] || 0) + 1;
              } else if (trimmed.includes('coil')) {
                dependencies.android.byCategory['Images'] = (dependencies.android.byCategory['Images'] || 0) + 1;
              } else if (trimmed.includes('test') || trimmed.includes('junit') || trimmed.includes('robolectric')) {
                dependencies.android.byCategory['Testing'] = (dependencies.android.byCategory['Testing'] || 0) + 1;
              } else if (trimmed.includes('androidx')) {
                dependencies.android.byCategory['AndroidX'] = (dependencies.android.byCategory['AndroidX'] || 0) + 1;
              } else {
                dependencies.android.byCategory['Other'] = (dependencies.android.byCategory['Other'] || 0) + 1;
              }
            }
          }
        }
      }

      // Analyser server/package.json
      const serverPackage = join(this.root, 'server', 'package.json');
      const serverPackageContent = await this.safeReadJson(serverPackage);
      
      if (serverPackageContent?.dependencies) {
        dependencies.server.total = Object.keys(serverPackageContent.dependencies).length;
        dependencies.server.byCategory = { ...serverPackageContent.dependencies };
      }

      this.results.dependencies = dependencies;

      console.log(c.bold('Android:'));
      console.log('  Total:', dependencies.android.total);
      Object.entries(dependencies.android.byCategory).forEach(([category, count]) => {
        console.log(`    ${category}: ${count}`);
      });
      console.log();
      
      console.log(c.bold('Serveur:'));
      console.log('  Total:', dependencies.server.total);
      Object.entries(dependencies.server.byCategory).forEach(([dep, version]) => {
        console.log(`    ${dep}: ${version}`);
      });
      console.log();
      
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'dependencies',
        message: `Erreur analyse dépendances: ${error.message}`
      });
    }
  }

  /**
   * Analyse les tests
   */
  async analyzeTests() {
    console.log(c.subtitle('🧪 Tests\n'));
    
    const tests = {
      android: {
        unit: {
          count: 0,
          files: [],
          coverage: 'Unknown'
        },
        instrumentation: {
          count: 0,
          files: []
        }
      },
      server: {
        count: 0,
        files: [],
        coverage: 'Unknown'
      }
    };

    try {
      // Tests Android unitaires
      const androidTestPath = join(this.root, 'app', 'src', 'test', 'java');
      if (await this.exists(androidTestPath)) {
        const testFiles = await this.findFiles(androidTestPath, '.kt');
        tests.android.unit.files = testFiles.filter(f => f.includes('Test'));
        tests.android.unit.count = tests.android.unit.files.length;
      }

      // Tests Android instrumentés
      const androidInstrumentationPath = join(this.root, 'app', 'src', 'androidTest', 'java');
      if (await this.exists(androidInstrumentationPath)) {
        const testFiles = await this.findFiles(androidInstrumentationPath, '.kt');
        tests.android.instrumentation.files = testFiles.filter(f => f.includes('Test'));
        tests.android.instrumentation.count = tests.android.instrumentation.files.length;
      }

      // Tests serveur
      const serverTestPath = join(this.root, 'server', 'test');
      if (await this.exists(serverTestPath)) {
        const testFiles = await this.findFiles(serverTestPath, '.js');
        tests.server.files = testFiles.filter(f => f.includes('.test.js'));
        tests.server.count = tests.server.files.length;
      }

      this.results.tests = tests;

      console.log(c.bold('Android:'));
      console.log('  Tests unitaires:', tests.android.unit.count);
      if (tests.android.unit.files.length > 0) {
        console.log('    Fichiers:', tests.android.unit.files.slice(0, 5).join(', ') + (tests.android.unit.files.length > 5 ? '...' : ''));
      }
      console.log('  Tests instrumentés:', tests.android.instrumentation.count);
      console.log();
      
      console.log(c.bold('Serveur:'));
      console.log('  Tests:', tests.server.count);
      if (tests.server.files.length > 0) {
        console.log('    Fichiers:', tests.server.files.join(', '));
      }
      console.log();
      
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'tests',
        message: `Erreur analyse tests: ${error.message}`
      });
    }
  }

  /**
   * Analyse la documentation
   */
  async analyzeDocumentation() {
    console.log(c.subtitle('📚 Documentation\n'));
    
    const docs = {
      totalFiles: 0,
      byCategory: {},
      issues: [],
      links: {
        total: 0,
        internal: 0,
        external: 0,
        broken: 0
      }
    };

    try {
      const docsPath = join(this.root, 'docs');
      if (await this.exists(docsPath)) {
        const allFiles = await this.findAllFiles(docsPath, '.md');
        docs.totalFiles = allFiles.length;
        
        // Catégoriser
        for (const file of allFiles) {
          const relativePath = relative(docsPath, file);
          const category = relativePath.split('/')[0] || 'root';
          
          docs.byCategory[category] = (docs.byCategory[category] || 0) + 1;
        }

        // Vérifier les liens dans la documentation
        const docsReadmePath = join(docsPath, 'README.md');
        const docsReadme = await this.safeReadFile(docsReadmePath);
        
        if (docsReadme) {
          const linkRegex = /\[([^\]]+)\]\(([^\)]+)\)/g;
          let match;
          
          while ((match = linkRegex.exec(docsReadme)) !== null) {
            const link = match[2];
            docs.links.total++;
            
            if (link.startsWith('http')) {
              docs.links.external++;
            } else if (link.startsWith('#')) {
              // Lien interne à la page
              docs.links.internal++;
            } else {
              // Lien relatif
              docs.links.internal++;
              
              // Vérifier si le fichier existe
              const targetPath = join(docsPath, link);
              try {
                await stat(targetPath);
              } catch {
                docs.links.broken++;
                docs.issues.push({
                  file: relativePath,
                  link,
                  message: `Lien brisé: ${link}`
                });
              }
            }
          }
        }
      }

      this.results.documentation = docs;

      console.log(c.bold('Documentation:'));
      console.log('  Fichiers totaux:', docs.totalFiles);
      Object.entries(docs.byCategory).forEach(([category, count]) => {
        console.log(`    ${category}: ${count}`);
      });
      console.log();
      
      console.log(c.bold('Liens:'));
      console.log('  Total:', docs.links.total);
      console.log('  Internes:', docs.links.internal);
      console.log('  Externes:', docs.links.external);
      console.log('  Brisés:', c[docs.links.broken > 0 ? 'error' : 'success'](`${docs.links.broken}`));
      console.log();
      
      if (docs.issues.length > 0) {
        console.log(c.warning('⚠️ Problèmes de documentation:'));
        docs.issues.slice(0, 5).forEach(issue => {
          console.log(`  ${c.error('✗')} ${issue.message} (dans ${issue.file})`);
        });
        console.log();
      }
      
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'documentation',
        message: `Erreur analyse documentation: ${error.message}`
      });
    }
  }

  /**
   * Analyse les workflows CI/CD
   */
  async analyzeWorkflows() {
    console.log(c.subtitle('⚙️ Workflows CI/CD\n'));
    
    const workflows = {
      total: 0,
      list: [],
      triggers: {},
      jobs: {}
    };

    try {
      const workflowsPath = join(this.root, '.github', 'workflows');
      if (await this.exists(workflowsPath)) {
        const files = await readdir(workflowsPath);
        
        for (const file of files) {
          if (!file.endsWith('.yml') && !file.endsWith('.yaml')) continue;
          
          const filePath = join(workflowsPath, file);
          const content = await this.safeReadFile(filePath);
          
          if (content) {
            workflows.total++;
            workflows.list.push(file);
            
            // Extraire les triggers
            const onMatch = content.match(/^on:\s*\n((?:\s+[\w-]+:\s*\n(?:\s+[\w-]+:\s*\n)?.*?(?=\n\w|\n$))+)/m);
            if (onMatch) {
              const triggers = onMatch[0].split('\n').slice(1).filter(l => l.trim().startsWith('- '));
              workflows.triggers[file] = triggers.map(t => t.trim().substring(2));
            }
            
            // Extraire les jobs
            const jobsMatch = content.match(/^jobs:\s*\n((?:\s+[\w-]+:\s*\n(?:\s+[\w-]+:\s*\n)?.*?(?=\n\w|\n$))+)/m);
            if (jobsMatch) {
              const jobLines = jobsMatch[0].split('\n').slice(1);
              const jobNames = jobLines.filter(l => l.trim() && !l.trim().startsWith(' ') && !l.trim().startsWith('\t')).map(l => l.trim().replace(':', ''));
              workflows.jobs[file] = jobNames;
            }
          }
        }
      }

      this.results.workflows = workflows;

      console.log(c.bold('Workflows:'));
      console.log('  Total:', workflows.total);
      console.log('  Liste:', workflows.list.join(', '));
      console.log();
      
      Object.entries(workflows.triggers).forEach(([file, triggers]) => {
        console.log(`  ${c.subtitle(file)}:`);
        console.log('    Déclencheurs:', triggers.join(', '));
      });
      console.log();
      
      Object.entries(workflows.jobs).forEach(([file, jobs]) => {
        console.log(`  ${c.subtitle(file)}:`);
        console.log('    Jobs:', jobs.join(', '));
      });
      console.log();
      
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'workflows',
        message: `Erreur analyse workflows: ${error.message}`
      });
    }
  }

  /**
   * Analyse la qualité du code
   */
  async analyzeCodeQuality() {
    console.log(c.subtitle('✨ Qualité du Code\n'));
    
    const quality = {
      issues: [],
      warnings: [],
      metrics: {}
    };

    try {
      // Vérifier les secrets dans les fichiers
      const secretPatterns = [
        /password\s*=\s*['"`]/i,
        /api[_-]?key\s*=\s*['"`]/i,
        /secret\s*=\s*['"`]/i,
        /token\s*=\s*['"`]/i
      ];

      const filesToCheck = [
        join(this.root, 'app', 'src'),
        join(this.root, 'server'),
        join(this.root, 'gradle.properties'),
        join(this.root, 'build.gradle.kts'),
        join(this.root, 'settings.gradle.kts')
      ];

      for (const path of filesToCheck) {
        if (!await this.exists(path)) continue;
        
        const files = await this.findAllFiles(path, ['.kt', '.java', '.js', '.ts', '.properties', '.gradle']);
        
        for (const file of files) {
          const content = await this.safeReadFile(file);
          if (!content) continue;
          
          for (const pattern of secretPatterns) {
            if (pattern.test(content)) {
              quality.issues.push({
                type: 'security',
                file: relative(this.root, file),
                message: 'Secret potentiel détecté'
              });
            }
          }
        }
      }

      // Vérifier les TODO/FIXME
      const todoPattern = /\/\/\s*(TODO|FIXME|XXX|HACK):\s*(.+)/g;
      const todoFiles = [
        join(this.root, 'app', 'src'),
        join(this.root, 'server')
      ];

      for (const path of todoFiles) {
        if (!await this.exists(path)) continue;
        
        const files = await this.findAllFiles(path, ['.kt', '.java', '.js', '.ts']);
        
        for (const file of files) {
          const content = await this.safeReadFile(file);
          if (!content) continue;
          
          let match;
          while ((match = todoPattern.exec(content)) !== null) {
            quality.warnings.push({
              type: 'todo',
              file: relative(this.root, file),
              message: match[2].trim(),
              priority: match[1]
            });
          }
        }
      }

      this.results.quality = quality;

      if (quality.issues.length > 0) {
        console.log(c.error('❌ Problèmes de sécurité:'));
        quality.issues.forEach(issue => {
          console.log(`  ${c.error('✗')} ${issue.message} dans ${issue.file}`);
        });
        console.log();
      }

      if (quality.warnings.length > 0) {
        console.log(c.warning('⚠️ TODO/FIXME détectés:'));
        quality.warnings.slice(0, 10).forEach(warning => {
          console.log(`  ${c.warning('⚠')} [${warning.priority}] ${warning.message} dans ${warning.file}`);
        });
        if (quality.warnings.length > 10) {
          console.log(`  ${c.dim(`... et ${quality.warnings.length - 10} autres`)}`);
        }
        console.log();
      }

      if (quality.issues.length === 0 && quality.warnings.length === 0) {
        console.log(c.success('✓ Aucune alerte de qualité détectée\n'));
      }
      
    } catch (error) {
      this.results.issues.push({
        type: 'error',
        category: 'quality',
        message: `Erreur analyse qualité: ${error.message}`
      });
    }
  }

  /**
   * Génère les statistiques
   */
  generateStats() {
    const stats = {
      totalFiles: 0,
      totalLines: 0,
      byLanguage: {},
      byDirectory: {}
    };

    // Compter les fichiers par type
    const countFiles = (path, type) => {
      const files = this.results.structure[path]?.files || [];
      return files.filter(f => f.endsWith(type)).length;
    };

    stats.byLanguage = {
      'Kotlin (.kt)': countFiles('app', '.kt') + countFiles('app', '.kts'),
      'Java (.java)': countFiles('app', '.java'),
      'JavaScript (.js)': countFiles('server', '.js'),
      'TypeScript (.ts)': countFiles('server', '.ts'),
      'Markdown (.md)': this.results.structure.docs.files.filter(f => f.endsWith('.md')).length + 
                        this.results.structure.root.files.filter(f => f.endsWith('.md')).length,
      'YAML (.yml)': this.results.structure.github.files.filter(f => f.endsWith('.yml')).length,
      'Gradle (.gradle)': this.results.structure.root.files.filter(f => f.endsWith('.gradle') || f.endsWith('.kts')).length + 
                         this.results.structure.app.files.filter(f => f.endsWith('.gradle') || f.endsWith('.kts')).length,
      'SQL (.sql)': this.results.structure.server.files.filter(f => f.endsWith('.sql')).length,
      'Python (.py)': this.results.structure.tools.files.filter(f => f.endsWith('.py')).length
    };

    // Calculer le total
    stats.totalFiles = Object.values(stats.byLanguage).reduce((sum, count) => sum + count, 0);

    this.results.stats = stats;
  }

  /**
   * Affiche le résumé
   */
  printSummary() {
    console.log(c.title('\n📊 Résumé de l\'Analyse\n'));
    
    console.log(c.bold('Statistiques:'));
    console.log('  Fichiers totaux:', this.results.stats.totalFiles);
    Object.entries(this.results.stats.byLanguage).forEach(([lang, count]) => {
      if (count > 0) {
        console.log(`    ${lang}: ${count}`);
      }
    });
    console.log();

    console.log(c.bold('Tests:'));
    console.log('  Android unitaires:', this.results.tests.android.unit.count);
    console.log('  Android instrumentés:', this.results.tests.android.instrumentation.count);
    console.log('  Serveur:', this.results.tests.server.count);
    console.log();

    console.log(c.bold('Documentation:'));
    console.log('  Fichiers:', this.results.documentation.totalFiles);
    console.log('  Liens brisés:', this.results.documentation.links.broken);
    console.log();

    console.log(c.bold('Problèmes:'));
    console.log('  Erreurs:', c[this.results.issues.length > 0 ? 'error' : 'success'](`${this.results.issues.length}`));
    console.log('  Avertissements:', c[this.results.warnings.length > 0 ? 'warning' : 'success'](`${this.results.warnings.length}`));
    
    if (this.results.issues.length > 0) {
      console.log(c.error('\n❌ Problèmes détectés:'));
      this.results.issues.forEach(issue => {
        console.log(`  ${c.error('✗')} [${issue.category}] ${issue.message}`);
      });
    }

    if (this.results.warnings.length > 0) {
      console.log(c.warning('\n⚠️ Avertissements:'));
      this.results.warnings.slice(0, 5).forEach(warning => {
        console.log(`  ${c.warning('⚠')} ${warning.message}`);
      });
    }

    console.log(c.title('\n✅ Analyse terminée!\n'));
  }

  // ============ Méthodes utilitaires ============

  /**
   * Vérifie si un fichier existe
   */
  async exists(path) {
    try {
      await stat(path);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Lit un fichier en toute sécurité
   */
  async safeReadFile(path) {
    try {
      return await readFile(path, 'utf8');
    } catch {
      return null;
    }
  }

  /**
   * Lit un JSON en toute sécurité
   */
  async safeReadJson(path) {
    try {
      const content = await readFile(path, 'utf8');
      return JSON.parse(content);
    } catch {
      return null;
    }
  }

  /**
   * Analyse un dossier récursivement
   */
  async analyzeDirectory(path, target, prefix = '') {
    try {
      const items = await readdir(path);
      
      for (const item of items) {
        const itemPath = join(path, item);
        const itemStat = await stat(itemPath);
        
        const relativePath = prefix ? join(prefix, item) : item;
        
        if (itemStat.isDirectory()) {
          target.dirs.push(relativePath);
          await this.analyzeDirectory(itemPath, target, relativePath);
        } else {
          target.files.push(relativePath);
        }
      }
    } catch {
      // Ignorer les erreurs de lecture
    }
  }

  /**
   * Trouve tous les fichiers avec une extension donnée
   */
  async findFiles(path, extension) {
    const files = [];
    
    try {
      const items = await readdir(path);
      
      for (const item of items) {
        const itemPath = join(path, item);
        const itemStat = await stat(itemPath);
        
        if (itemStat.isDirectory()) {
          const subFiles = await this.findFiles(itemPath, extension);
          files.push(...subFiles);
        } else if (extname(item) === extension) {
          files.push(itemPath);
        }
      }
    } catch {
      // Ignorer
    }
    
    return files;
  }

  /**
   * Trouve tous les fichiers avec plusieurs extensions
   */
  async findAllFiles(path, extensions) {
    const files = [];
    
    try {
      const items = await readdir(path);
      
      for (const item of items) {
        const itemPath = join(path, item);
        const itemStat = await stat(itemPath);
        
        if (itemStat.isDirectory()) {
          const subFiles = await this.findAllFiles(itemPath, extensions);
          files.push(...subFiles);
        } else if (extensions.includes(extname(item))) {
          files.push(itemPath);
        }
      }
    } catch {
      // Ignorer
    }
    
    return files;
  }

  /**
   * Trouve les packages Kotlin
   */
  async findPackages(path, basePackage) {
    const packages = new Set();
    packages.add(basePackage);
    
    try {
      const items = await readdir(path);
      
      for (const item of items) {
        const itemPath = join(path, item);
        const itemStat = await stat(itemPath);
        
        if (itemStat.isDirectory()) {
          const subPackages = await this.findPackages(itemPath, `${basePackage}.${item}`);
          subPackages.forEach(pkg => packages.add(pkg));
        }
      }
    } catch {
      // Ignorer
    }
    
    return Array.from(packages);
  }
}

// ============ Exécution ============

/**
 * Affiche l'aide
 */
function showHelp() {
  console.log(`
${c.title('vibe-analyze.js - Analyse du Repository Backlog')}

${c.subtitle('Usage:')}
  node scripts/vibe-analyze.js [options]

${c.subtitle('Options:')}
  --json     Output en JSON
  --quiet    Mode silencieux (erreurs seulement)
  --help     Affiche cette aide

${c.subtitle('Exemples:')}
  node scripts/vibe-analyze.js
  node scripts/vibe-analyze.js --json
  node scripts/vibe-analyze.js --quiet

${c.subtitle('Description:')}
  Analyse complète du repository Backlog incluant:
  - Structure du projet
  - Stack technique
  - Dépendances
  - Tests
  - Documentation
  - Workflows CI/CD
  - Qualité du code
`);
}

/**
 * Point d'entrée
 */
async function main() {
  const args = process.argv.slice(2);
  
  if (args.includes('--help') || args.includes('-h')) {
    showHelp();
    process.exit(0);
  }

  const jsonOutput = args.includes('--json');
  const quietMode = args.includes('--quiet');

  const analyzer = new BacklogAnalyzer(ROOT);
  
  try {
    const results = await analyzer.analyze();
    
    if (jsonOutput) {
      console.log(JSON.stringify(results, null, 2));
    }
    
    if (results.issues.length > 0) {
      process.exit(1);
    }
    
  } catch (error) {
    if (!quietMode) {
      console.error(c.error('❌ Erreur fatale:'), error.message);
    }
    process.exit(1);
  }
}

// Exécuter
main().catch(() => process.exit(1));

export { BacklogAnalyzer, main };
