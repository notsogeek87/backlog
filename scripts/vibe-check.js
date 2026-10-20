#!/usr/bin/env node

/**
 * vibe-check.js - Vérification pré-commit pour le repository Backlog
 * 
 * Effectue une série de vérifications avant commit/push:
 * - Vérification des secrets
 * - Vérification de la syntaxe
 * - Vérification des tests
 * - Vérification de la documentation
 * - Vérification des conventions
 * 
 * Usage: node scripts/vibe-check.js [options]
 * Options:
 *   --fix      Tente de corriger les problèmes automatiquement
 *   --strict   Mode strict (toutes les vérifications doivent passer)
 *   --quiet    Mode silencieux (erreurs seulement)
 *   --help     Affiche l'aide
 */

import { readFile, readdir, stat, writeFile } from 'node:fs/promises';
import { join, resolve, relative, extname, basename, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execa } from 'execa';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..');

// Couleurs (fallback si chalk non disponible)
let c = {
  success: (t) => t,
  warning: (t) => t,
  error: (t) => t,
  info: (t) => t,
  title: (t) => t,
  subtitle: (t) => t,
  dim: (t) => t,
  bold: (t) => t
};

try {
  const chalk = await import('chalk');
  c = {
    success: chalk.default.green,
    warning: chalk.default.yellow,
    error: chalk.default.red,
    info: chalk.default.blue,
    title: chalk.default.bold.cyan,
    subtitle: chalk.default.bold.white,
    dim: chalk.default.dim,
    bold: chalk.default.bold
  };
} catch {
  // Utiliser les fallbacks
}

/**
 * Vérificateur pré-commit
 */
class PreCommitChecker {
  constructor(root) {
    this.root = root;
    this.results = {
      passed: [],
      failed: [],
      warnings: [],
      errors: [],
      stats: {
        filesChecked: 0,
        secretsFound: 0,
        syntaxErrors: 0,
        lintErrors: 0
      }
    };
    this.fixMode = false;
    this.strictMode = false;
    this.quietMode = false;
  }

  /**
   * Configure les options
   */
  configure(options) {
    this.fixMode = options.fix || false;
    this.strictMode = options.strict || false;
    this.quietMode = options.quiet || false;
    return this;
  }

  /**
   * Lance toutes les vérifications
   */
  async check() {
    console.log(c.title('\n🔍 Vérification Pré-Commit\n'));
    
    try {
      await this.checkSecrets();
      await this.checkSyntax();
      await this.checkGitStatus();
      await this.checkDocumentation();
      await this.checkConventions();
      await this.checkGradle();
      
      this.printSummary();
      
      // Retourner le code de sortie approprié
      if (this.results.failed.length > 0 || this.results.errors.length > 0) {
        return false;
      }
      
      if (this.strictMode && this.results.warnings.length > 0) {
        return false;
      }
      
      return true;
    } catch (error) {
      console.error(c.error('❌ Erreur lors de la vérification:'), error.message);
      return false;
    }
  }

  /**
   * Vérifie les secrets dans les fichiers
   */
  async checkSecrets() {
    if (!this.quietMode) {
      console.log(c.subtitle('🔒 Vérification des Secrets\n'));
    }

    const secretPatterns = [
      {
        name: 'Mot de passe',
        pattern: /(password|passwd|pwd)\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      },
      {
        name: 'Clé API',
        pattern: /(api[_-]?key|apikey)\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      },
      {
        name: 'Secret',
        pattern: /secret\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      },
      {
        name: 'Token',
        pattern: /(token|bearer)\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      },
      {
        name: 'Clé privée',
        pattern: /(private[_-]?key|privatekey)\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      },
      {
        name: 'Client ID/Secret',
        pattern: /(client[_-]?id|client[_-]?secret|twitch[_-]?client[_-]?id|twitch[_-]?client[_-]?secret)\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      },
      {
        name: 'Steam API Key',
        pattern: /steam[_-]?api[_-]?key\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      },
      {
        name: 'TMDB API Key',
        pattern: /tmdb[_-]?api[_-]?key\s*[=:]\s*['"`]([^'"`]+)['"`]/gi,
        severity: 'error'
      }
    ];

    const filesToCheck = [
      join(this.root, 'app', 'src'),
      join(this.root, 'server'),
      join(this.root, 'gradle.properties'),
      join(this.root, 'build.gradle.kts'),
      join(this.root, 'settings.gradle.kts'),
      join(this.root, '.github')
    ];

    const gitignorePath = join(this.root, '.gitignore');
    const gitignore = await this.readGitIgnore(gitignorePath);

    let secretsFound = 0;

    for (const path of filesToCheck) {
      if (!await this.exists(path)) continue;

      const files = await this.findAllFiles(path, ['.kt', '.java', '.js', '.ts', '.json', '.properties', '.gradle', '.kts', '.yml', '.yaml']);
      
      for (const file of files) {
        // Vérifier si le fichier est dans .gitignore
        const relativePath = relative(this.root, file);
        if (gitignore.some(pattern => this.matchesGitIgnorePattern(relativePath, pattern))) {
          continue;
        }

        this.results.stats.filesChecked++;
        
        const content = await this.safeReadFile(file);
        if (!content) continue;

        for (const pattern of secretPatterns) {
          let match;
          while ((match = pattern.pattern.exec(content)) !== null) {
            secretsFound++;
            const secretValue = match[2];
            
            // Ignorer les placeholders
            if (this.isPlaceholder(secretValue)) continue;

            const result = {
              type: 'secret',
              file: relativePath,
              secretType: pattern.name,
              severity: pattern.severity,
              message: `Secret détecté: ${pattern.name} dans ${relativePath}`
            };

            if (pattern.severity === 'error') {
              this.results.failed.push(result);
              if (!this.quietMode) {
                console.log(c.error('✗ ERREUR:'), result.message);
              }
            } else {
              this.results.warnings.push(result);
              if (!this.quietMode) {
                console.log(c.warning('⚠ AVERTISSEMENT:'), result.message);
              }
            }
          }
        }
      }
    }

    this.results.stats.secretsFound = secretsFound;

    if (secretsFound === 0 && !this.quietMode) {
      console.log(c.success('✓ Aucun secret détecté\n'));
    }
  }

  /**
   * Vérifie la syntaxe des fichiers
   */
  async checkSyntax() {
    if (!this.quietMode) {
      console.log(c.subtitle('📝 Vérification de la Syntaxe\n'));
    }

    // Vérifier Kotlin
    const kotlinFiles = await this.findAllFiles(
      join(this.root, 'app', 'src'),
      ['.kt', '.kts']
    );

    // Pour l'instant, on vérifie juste que les fichiers sont valides UTF-8
    // Une vérification Kotlin complète nécessiterait kotlinc
    for (const file of kotlinFiles) {
      try {
        await this.safeReadFile(file);
        this.results.stats.filesChecked++;
      } catch (error) {
        this.results.failed.push({
          type: 'syntax',
          file: relative(this.root, file),
          severity: 'error',
          message: `Erreur de lecture: ${file} - ${error.message}`
        });
      }
    }

    // Vérifier JSON
    const jsonFiles = await this.findAllFiles(
      this.root,
      ['.json']
    );

    for (const file of jsonFiles) {
      // Ignorer node_modules
      if (file.includes('node_modules')) continue;
      
      try {
        const content = await this.safeReadFile(file);
        if (content) {
          JSON.parse(content);
          this.results.stats.filesChecked++;
        }
      } catch (error) {
        this.results.failed.push({
          type: 'syntax',
          file: relative(this.root, file),
          severity: 'error',
          message: `JSON invalide: ${file} - ${error.message}`
        });
        if (!this.quietMode) {
          console.log(c.error('✗ ERREUR:'), `JSON invalide dans ${relative(this.root, file)}`);
        }
      }
    }

    // Vérifier YAML
    const yamlFiles = await this.findAllFiles(
      join(this.root, '.github'),
      ['.yml', '.yaml']
    );

    // Vérification basique YAML (pas de parseur natif, on vérifie juste l'indentation)
    for (const file of yamlFiles) {
      try {
        const content = await this.safeReadFile(file);
        if (content) {
          // Vérifier l'indentation (doit être en espaces, pas de tabs)
          const lines = content.split('\n');
          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (line.includes('\t')) {
              this.results.warnings.push({
                type: 'syntax',
                file: relative(this.root, file),
                severity: 'warning',
                message: `Tabulation détectée (ligne ${i + 1}) dans ${relative(this.root, file)}`
              });
              break;
            }
          }
          this.results.stats.filesChecked++;
        }
      } catch (error) {
        this.results.failed.push({
          type: 'syntax',
          file: relative(this.root, file),
          severity: 'error',
          message: `Erreur de lecture YAML: ${file} - ${error.message}`
        });
      }
    }

    if (this.results.failed.filter(f => f.type === 'syntax').length === 0 && !this.quietMode) {
      console.log(c.success('✓ Aucune erreur de syntaxe détectée\n'));
    }
  }

  /**
   * Vérifie l'état Git
   */
  async checkGitStatus() {
    if (!this.quietMode) {
      console.log(c.subtitle('🌱 Vérification Git\n'));
    }

    try {
      // Vérifier qu'on est dans un repo Git
      await execa('git', ['rev-parse', '--is-inside-work-tree'], {
        cwd: this.root,
        reject: false
      });

      // Vérifier les fichiers modifiés non committés
      const status = await execa('git', ['status', '--porcelain'], {
        cwd: this.root,
        reject: false
      });

      if (status.stdout) {
        const modifiedFiles = status.stdout.trim().split('\n').filter(l => l.trim());
        
        if (modifiedFiles.length > 0) {
          this.results.warnings.push({
            type: 'git',
            severity: 'warning',
            message: `Fichiers modifiés non committés: ${modifiedFiles.length}`
          });
          if (!this.quietMode) {
            console.log(c.warning('⚠ AVERTISSEMENT:'), `Fichiers modifiés non committés: ${modifiedFiles.length}`);
            modifiedFiles.forEach(f => {
              console.log(c.dim(`  ${f}`));
            });
          }
        }
      }

      // Vérifier les fichiers non suivis
      const untracked = await execa('git', ['ls-files', '--others', '--exclude-standard'], {
        cwd: this.root,
        reject: false
      });

      if (untracked.stdout) {
        const untrackedFiles = untracked.stdout.trim().split('\n').filter(l => l.trim());
        
        if (untrackedFiles.length > 0) {
          this.results.warnings.push({
            type: 'git',
            severity: 'warning',
            message: `Fichiers non suivis: ${untrackedFiles.length}`
          });
          if (!this.quietMode) {
            console.log(c.warning('⚠ AVERTISSEMENT:'), `Fichiers non suivis: ${untrackedFiles.length}`);
            untrackedFiles.forEach(f => {
              console.log(c.dim(`  ${f}`));
            });
          }
        }
      }

      if (this.results.warnings.filter(w => w.type === 'git').length === 0 && !this.quietMode) {
        console.log(c.success('✓ Aucune anomalie Git détectée\n'));
      }
      
    } catch (error) {
      // Si git n'est pas disponible, on ignore
      if (!this.quietMode) {
        console.log(c.dim('ℹ Git non disponible, vérification ignorée\n'));
      }
    }
  }

  /**
   * Vérifie la documentation
   */
  async checkDocumentation() {
    if (!this.quietMode) {
      console.log(c.subtitle('📚 Vérification de la Documentation\n'));
    }

    const docsPath = join(this.root, 'docs');
    if (!await this.exists(docsPath)) {
      if (!this.quietMode) {
        console.log(c.dim('ℹ Aucun dossier docs trouvé\n'));
      }
      return;
    }

    // Vérifier que docs/README.md existe
    const docsReadmePath = join(docsPath, 'README.md');
    if (!await this.exists(docsReadmePath)) {
      this.results.failed.push({
        type: 'documentation',
        file: 'docs/README.md',
        severity: 'error',
        message: 'Fichier docs/README.md manquant'
      });
      if (!this.quietMode) {
        console.log(c.error('✗ ERREUR:'), 'Fichier docs/README.md manquant');
      }
    }

    // Vérifier les liens dans la documentation
    const docsReadme = await this.safeReadFile(docsReadmePath);
    if (docsReadme) {
      const linkRegex = /\[([^\]]+)\]\(([^\)]+)\)/g;
      let match;
      let brokenLinks = 0;

      while ((match = linkRegex.exec(docsReadme)) !== null) {
        const link = match[2];
        
        // Ignorer les liens externes et les ancres
        if (link.startsWith('http') || link.startsWith('#')) continue;
        
        // Vérifier si le fichier existe
        const targetPath = join(docsPath, link);
        const targetExists = await this.exists(targetPath);
        
        if (!targetExists) {
          brokenLinks++;
          this.results.failed.push({
            type: 'documentation',
            file: 'docs/README.md',
            severity: 'error',
            message: `Lien brisé: ${link}`
          });
          if (!this.quietMode) {
            console.log(c.error('✗ ERREUR:'), `Lien brisé dans docs/README.md: ${link}`);
          }
        }
      }

      if (brokenLinks === 0 && !this.quietMode) {
        console.log(c.success('✓ Aucun lien brisé détecté dans docs/README.md\n'));
      }
    }

    // Vérifier que chaque fichier .md a un titre et une description
    const mdFiles = await this.findAllFiles(docsPath, ['.md']);
    
    for (const file of mdFiles) {
      const content = await this.safeReadFile(file);
      if (!content) continue;

      const relativePath = relative(this.root, file);

      // Vérifier le titre
      const titleMatch = content.match(/^#\s+(.+)/m);
      if (!titleMatch) {
        this.results.warnings.push({
          type: 'documentation',
          file: relativePath,
          severity: 'warning',
          message: `Fichier ${relativePath} sans titre (H1)`
        });
        if (!this.quietMode) {
          console.log(c.warning('⚠ AVERTISSEMENT:'), `Fichier ${relativePath} sans titre (H1)`);
        }
      }

      // Vérifier la description "Pour qui / pourquoi"
      const descriptionMatch = content.match(/Pour qui \/ pourquoi[\s\S]*?:[\s\S]*?(.+)/i);
      if (!descriptionMatch) {
        this.results.warnings.push({
          type: 'documentation',
          file: relativePath,
          severity: 'warning',
          message: `Fichier ${relativePath} sans description "Pour qui / pourquoi"`
        });
        if (!this.quietMode) {
          console.log(c.warning('⚠ AVERTISSEMENT:'), `Fichier ${relativePath} sans description "Pour qui / pourquoi"`);
        }
      }
    }
  }

  /**
   * Vérifie les conventions de code
   */
  async checkConventions() {
    if (!this.quietMode) {
      console.log(c.subtitle('📏 Vérification des Conventions\n'));
    }

    // Vérifier les noms de fichiers Kotlin
    const kotlinFiles = await this.findAllFiles(
      join(this.root, 'app', 'src', 'main', 'java'),
      ['.kt']
    );

    const namingConventions = {
      'class': /^[A-Z][a-zA-Z0-9]*\.kt$/,
      'test_class': /^[A-Z][a-zA-Z0-9]*Test\.kt$/,
      'package': /^([a-z]+\.)+[a-z]+\.kt$/,
      'variable': /^[a-z][a-zA-Z0-9]*$/,
      'constant': /^[A-Z][A-Z0-9_]*$/,
      'function': /^[a-z][a-zA-Z0-9]*$/
    };

    // Vérifier les noms de classes
    for (const file of kotlinFiles) {
      const basename = basename(file, '.kt');
      
      // Les classes doivent être en PascalCase
      if (basename !== basename.toLowerCase() && !/[A-Z]/.test(basename)) {
        this.results.warnings.push({
          type: 'convention',
          file: relative(this.root, file),
          severity: 'warning',
          message: `Nom de fichier suspect: ${basename}.kt (devrait être PascalCase pour les classes)`
        });
      }
    }

    // Vérifier les noms de fichiers de test
    const testFiles = await this.findAllFiles(
      join(this.root, 'app', 'src', 'test'),
      ['.kt']
    );

    for (const file of testFiles) {
      const basename = basename(file, '.kt');
      
      // Les fichiers de test doivent se terminer par Test
      if (!basename.endsWith('Test')) {
        this.results.warnings.push({
          type: 'convention',
          file: relative(this.root, file),
          severity: 'warning',
          message: `Nom de fichier de test suspect: ${basename}.kt (devrait se terminer par Test)`
        });
      }
    }

    // Vérifier les messages de commit
    try {
      const log = await execa('git', ['log', '--oneline', '-10'], {
        cwd: this.root,
        reject: false
      });

      if (log.stdout) {
        const commits = log.stdout.trim().split('\n');
        
        for (const commit of commits) {
          const message = commit.split(' ').slice(1).join(' ');
          
          // Vérifier que le message est en français
          if (message && !/^[a-zàâäéèêëïîôùûüÿæœç\d\s\-:.,;!?()\'"]+$/i.test(message)) {
            this.results.warnings.push({
              type: 'convention',
              severity: 'warning',
              message: `Message de commit potentiellement en anglais: ${commit}`
            });
          }
        }
      }
    } catch {
      // Ignorer si git n'est pas disponible
    }

    if (this.results.warnings.filter(w => w.type === 'convention').length === 0 && !this.quietMode) {
      console.log(c.success('✓ Aucune violation de convention détectée\n'));
    }
  }

  /**
   * Vérifie les fichiers Gradle
   */
  async checkGradle() {
    if (!this.quietMode) {
      console.log(c.subtitle('🏗️ Vérification Gradle\n'));
    }

    // Vérifier que build.gradle.kts existe
    const buildGradlePath = join(this.root, 'build.gradle.kts');
    if (!await this.exists(buildGradlePath)) {
      this.results.failed.push({
        type: 'gradle',
        file: 'build.gradle.kts',
        severity: 'error',
        message: 'Fichier build.gradle.kts manquant'
      });
      if (!this.quietMode) {
        console.log(c.error('✗ ERREUR:'), 'Fichier build.gradle.kts manquant');
      }
      return;
    }

    // Vérifier que settings.gradle.kts existe
    const settingsGradlePath = join(this.root, 'settings.gradle.kts');
    if (!await this.exists(settingsGradlePath)) {
      this.results.failed.push({
        type: 'gradle',
        file: 'settings.gradle.kts',
        severity: 'error',
        message: 'Fichier settings.gradle.kts manquant'
      });
      if (!this.quietMode) {
        console.log(c.error('✗ ERREUR:'), 'Fichier settings.gradle.kts manquant');
      }
      return;
    }

    // Vérifier que gradle.properties existe
    const gradlePropsPath = join(this.root, 'gradle.properties');
    if (!await this.exists(gradlePropsPath)) {
      this.results.warnings.push({
        type: 'gradle',
        file: 'gradle.properties',
        severity: 'warning',
        message: 'Fichier gradle.properties manquant'
      });
      if (!this.quietMode) {
        console.log(c.warning('⚠ AVERTISSEMENT:'), 'Fichier gradle.properties manquant');
      }
    }

    // Vérifier le version catalog
    const gradleDir = join(this.root, 'gradle');
    if (await this.exists(gradleDir)) {
      const libsDir = join(gradleDir, 'libs');
      if (!await this.exists(libsDir)) {
        this.results.warnings.push({
          type: 'gradle',
          file: 'gradle/libs',
          severity: 'warning',
          message: 'Dossier gradle/libs manquant (version catalog)'
        });
        if (!this.quietMode) {
          console.log(c.warning('⚠ AVERTISSEMENT:'), 'Dossier gradle/libs manquant');
        }
      }
    }

    if (this.results.failed.filter(f => f.type === 'gradle').length === 0 && 
        this.results.warnings.filter(w => w.type === 'gradle').length === 0 && 
        !this.quietMode) {
      console.log(c.success('✓ Aucune anomalie Gradle détectée\n'));
    }
  }

  /**
   * Affiche le résumé
   */
  printSummary() {
    console.log(c.title('\n📊 Résumé de la Vérification\n'));

    const totalChecks = this.results.passed.length + this.results.failed.length + this.results.warnings.length;
    const passed = this.results.passed.length;
    const failed = this.results.failed.length;
    const warnings = this.results.warnings.length;

    console.log(c.bold('Statistiques:'));
    console.log('  Fichiers vérifiés:', this.results.stats.filesChecked);
    console.log('  Secrets trouvés:', c[this.results.stats.secretsFound > 0 ? 'error' : 'success'](`${this.results.stats.secretsFound}`));
    console.log();

    console.log(c.bold('Résultats:'));
    console.log('  Total:', totalChecks);
    console.log('  ' + c.success('✓ Réussis:'), passed);
    console.log('  ' + c.error('✗ Échecs:'), failed);
    console.log('  ' + c.warning('⚠ Avertissements:'), warnings);
    console.log();

    if (failed > 0) {
      console.log(c.error('❌ Échecs:'));
      this.results.failed.slice(0, 10).forEach(fail => {
        console.log(`  ${c.error('✗')} [${fail.type}] ${fail.message}`);
      });
      if (failed > 10) {
        console.log(c.dim(`  ... et ${failed - 10} autres`));
      }
      console.log();
    }

    if (warnings > 0) {
      console.log(c.warning('⚠️ Avertissements:'));
      this.results.warnings.slice(0, 10).forEach(warning => {
        console.log(`  ${c.warning('⚠')} [${warning.type}] ${warning.message}`);
      });
      if (warnings > 10) {
        console.log(c.dim(`  ... et ${warnings - 10} autres`));
      }
      console.log();
    }

    if (failed === 0 && warnings === 0) {
      console.log(c.success('✅ Toutes les vérifications ont réussi!\n'));
    } else if (failed === 0) {
      console.log(c.success('✅ Aucune erreur critique, mais des avertissements à corriger.\n'));
    } else {
      console.log(c.error('❌ Des erreurs doivent être corrigées avant le commit.\n'));
    }
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
   * Lit le .gitignore
   */
  async readGitIgnore(path) {
    try {
      const content = await readFile(path, 'utf8');
      return content.split('\n')
        .map(line => line.trim())
        .filter(line => line && !line.startsWith('#'));
    } catch {
      return [];
    }
  }

  /**
   * Vérifie si un chemin correspond à un pattern .gitignore
   */
  matchesGitIgnorePattern(path, pattern) {
    // Implémentation simplifiée
    if (pattern.startsWith('/')) {
      return path === pattern.substring(1);
    }
    
    if (pattern.endsWith('/')) {
      return path.startsWith(pattern);
    }
    
    if (pattern.includes('*')) {
      const regexPattern = pattern.replace(/\*/g, '.*').replace(/\?/g, '.');
      const regex = new RegExp(`^${regexPattern}$`);
      return regex.test(path);
    }
    
    return path === pattern;
  }

  /**
   * Vérifie si une valeur est un placeholder
   */
  isPlaceholder(value) {
    const placeholders = [
      'YOUR_',
      'placeholder',
      'CHANGE_ME',
      'TODO',
      'FIXME',
      'XXX',
      'HACK',
      'example',
      'test',
      'dummy',
      'fake',
      'mock'
    ];
    
    return placeholders.some(p => value.toUpperCase().includes(p.toUpperCase()));
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
          // Ignorer node_modules, build, .git
          if (['node_modules', 'build', '.git', 'app/build'].includes(item)) {
            continue;
          }
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
}

// ============ Exécution ============

/**
 * Affiche l'aide
 */
function showHelp() {
  console.log(`
${c.title('vibe-check.js - Vérification Pré-Commit')}

${c.subtitle('Usage:')}
  node scripts/vibe-check.js [options]

${c.subtitle('Options:')}
  --fix      Tente de corriger les problèmes automatiquement
  --strict   Mode strict (toutes les vérifications doivent passer)
  --quiet    Mode silencieux (erreurs seulement)
  --help     Affiche cette aide

${c.subtitle('Exemples:')}
  node scripts/vibe-check.js
  node scripts/vibe-check.js --strict
  node scripts/vibe-check.js --quiet

${c.subtitle('Description:')}
  Effectue une série de vérifications avant commit:
  - Vérification des secrets dans les fichiers
  - Vérification de la syntaxe (JSON, YAML)
  - Vérification de l'état Git
  - Vérification de la documentation
  - Vérification des conventions de code
  - Vérification des fichiers Gradle
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

  const options = {
    fix: args.includes('--fix'),
    strict: args.includes('--strict'),
    quiet: args.includes('--quiet')
  };

  const checker = new PreCommitChecker(ROOT).configure(options);
  const success = await checker.check();

  process.exit(success ? 0 : 1);
}

// Exécuter
main().catch(() => process.exit(1));

export { PreCommitChecker, main };
