#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { minify } = require('html-minifier-terser');
const { execSync } = require('child_process');

// Configuration
const DATAFAST_SCRIPT = '<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU" data-domain="varun.surf" src="https://datafa.st/js/script.js"></script>';

const MINIFY_OPTIONS = {
  collapseWhitespace: true,
  removeComments: true,
  minifyCSS: true,
  minifyJS: true,
  collapseBooleanAttributes: true,
  removeAttributeQuotes: true,
  removeRedundantAttributes: true,
  removeScriptTypeAttributes: true,
  removeStyleLinkTypeAttributes: true,
  useShortDoctype: true
};

// Get a version from git
function getVersion() {
  try {
    const version = execSync("git tag --list 'v*' --sort=-version:refname | head -1", { encoding: 'utf-8' }).trim();
    if (!version) {
      console.log('⚠️  No git tag found, using default version: v0.0.0');
      return 'v0.0.0';
    }
    console.log(`📌  Using version: ${version}`);
    return version;
  } catch (error) {
    console.log('⚠️  Error getting git version, using default: v0.0.0');
    return 'v0.0.0';
  }
}

// Read file safely
function readFile(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf-8');
  } catch (error) {
    console.log(`⚠️  Could not read ${filePath}`);
    return '';
  }
}

// Inline CSS and JS into HTML
function inlineAssets(html, cssFiles, jsFiles) {
  let result = html;

  // Remove CSS link tags and inline CSS
  if (cssFiles.length > 0) {
    const cssContent = cssFiles.map(f => readFile(f)).filter(c => c).join('\n');
    if (cssContent) {
      result = result.replace(
        /<link\s+rel="stylesheet"\s+href="[^"]*">/g,
        `<style>${cssContent}</style>`
      );
    }
  }

  // Remove script tags and inline JS
  if (jsFiles.length > 0) {
    const jsContent = jsFiles.map(f => readFile(f)).filter(c => c).join('\n');
    if (jsContent) {
      // Remove individual script tags
      result = result.replace(/<script\s+src="[^"]*translations\.js"><\/script>/g, '');
      result = result.replace(/<script\s+src="[^"]*country-flags\.js"><\/script>/g, '');
      result = result.replace(/<script\s+src="\.\.\/js\/page\/\w+\.js"><\/script>/g, `<script>${jsContent}</script>`);
    }
  }

  return result;
}

// Normalize datafast script tag
function normalizeDatafastScript(html) {
  return html.replace(
    /<script\s+defer\s+data-website-id="[^"]*"\s+data-domain="[^"]*"\s+src="[^"]*">\s*<\/script>/gs,
    DATAFAST_SCRIPT
  );
}

// Build a single HTML page
async function buildPage(config) {
  const { name, htmlFile, cssFiles, jsFiles, outputFile } = config;

  console.log(`🚧  Building ${name}...`);

  // Read HTML template
  let html = readFile(htmlFile);
  if (!html) {
    console.log(`❌  Failed to read ${htmlFile}`);
    return;
  }

  // Normalize datafast script
  html = normalizeDatafastScript(html);

  // Inline CSS and JS
  html = inlineAssets(html, cssFiles, jsFiles);

  // Minify HTML
  console.log(`Minifying ${name}...`);
  const minified = await minify(html, MINIFY_OPTIONS);

  // Remove whitespace between tags and make single-line
  const compressed = minified.replace(/>\s+</g, '><').replace(/\n/g, '');

  // Fix defer attribute (defer="defer" -> defer)
  const fixed = compressed.replace(/defer="defer"/g, 'defer');

  // Write output
  fs.writeFileSync(outputFile, fixed);
  console.log(`✅  ${name} built successfully: ${outputFile}`);
}

// Copy static assets
function copyAssets() {
  console.log('Copying static assets...');

  const assets = [
    { src: 'frontend/assets/logo.png', dest: 'src/main/resources/static/logo.png' },
    { src: 'frontend/assets/ai.txt', dest: 'src/main/resources/static/ai.txt' },
    { src: 'frontend/assets/llms.txt', dest: 'src/main/resources/static/llms.txt' },
    { src: 'frontend/assets/robots.txt', dest: 'src/main/resources/static/robots.txt' },
    { src: 'frontend/assets/sitemap.xml', dest: 'src/main/resources/static/sitemap.xml' }
  ];

  for (const asset of assets) {
    try {
      fs.copyFileSync(asset.src, asset.dest);
      console.log(`✅  Copied ${path.basename(asset.src)}`);
    } catch (error) {
      console.log(`⚠️  Failed to copy ${asset.src}: ${error.message}`);
    }
  }
}

// Main build function
async function build() {
  console.log('🚧  Starting frontend build...');

  const version = getVersion();

  // Common files
  const commonCSS = ['frontend/css/styles.css'];
  const commonJS = [
    'frontend/js/common/translations.js',
    'frontend/js/common/country-flags.js'
  ];

  // Build index.html
  await buildPage({
    name: 'index.html',
    htmlFile: 'frontend/html/index.html',
    cssFiles: commonCSS,
    jsFiles: [...commonJS, 'frontend/js/page/index.js'],
    outputFile: 'src/main/resources/static/index.html'
  });

  // Build spot.html
  await buildPage({
    name: 'spot.html',
    htmlFile: 'frontend/html/spot.html',
    cssFiles: commonCSS,
    jsFiles: [...commonJS, 'frontend/js/page/spot.js'],
    outputFile: 'src/main/resources/static/spot.html'
  });

  // Build status.html
  await buildPage({
    name: 'status.html',
    htmlFile: 'frontend/html/status.html',
    cssFiles: commonCSS,
    jsFiles: ['frontend/js/page/status.js'],
    outputFile: 'src/main/resources/static/status.html'
  });

  // Copy static assets
  copyAssets();

  console.log('✅  Frontend build completed successfully!');
}

// Run build
build().catch(error => {
  console.error('❌  Build failed:', error);
  process.exit(1);
});