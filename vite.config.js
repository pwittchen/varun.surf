import { defineConfig } from 'vite';
import { createHtmlPlugin } from 'vite-plugin-html';
import { viteStaticCopy } from 'vite-plugin-static-copy';
import path from 'path';
import fs from 'fs';

// Custom plugin to move HTML files to root and make them single-line
function flattenHtmlPlugin() {
  return {
    name: 'flatten-html',
    closeBundle() {
      const staticDir = path.resolve(__dirname, 'src/main/resources/static');
      const htmlDir = path.join(staticDir, 'html');

      if (fs.existsSync(htmlDir)) {
        const htmlFiles = fs.readdirSync(htmlDir);

        for (const file of htmlFiles) {
          if (file.endsWith('.html')) {
            const source = path.join(htmlDir, file);
            const dest = path.join(staticDir, file);

            // Read HTML content
            let htmlContent = fs.readFileSync(source, 'utf-8');

            // Remove all newlines and extra whitespace between tags
            htmlContent = htmlContent.replace(/>\s+</g, '><').replace(/\n/g, '');

            // Write single-line HTML to destination
            fs.writeFileSync(dest, htmlContent);
            console.log(`✓ Moved ${file} to static root (single-line)`);
          }
        }

        // Remove the HTML directory
        fs.rmSync(htmlDir, { recursive: true });
      }
    }
  };
}

const spotImagesDir = path.resolve(__dirname, 'frontend/images/spots');

function findSpotImages(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }

  const entries = fs.readdirSync(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...findSpotImages(entryPath));
    } else if (/\.(png|jpe?g)$/i.test(entry.name)) {
      files.push(entryPath);
    }
  }

  return files;
}

const staticCopyTargets = [
  { src: 'assets/logo.png', dest: '.' },
  { src: 'assets/ai.txt', dest: '.' },
  { src: 'assets/llms.txt', dest: '.' },
  { src: 'assets/robots.txt', dest: '.' },
  { src: 'assets/sitemap.xml', dest: '.' }
];

if (findSpotImages(spotImagesDir).length > 0) {
  staticCopyTargets.push({ src: 'images/spots/**/*', dest: 'images/spots' });
} else {
  console.log('ℹ️  No spot images found, skipping copy step.');
}

export default defineConfig({
  root: 'frontend',
  base: '/',

  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,

    rollupOptions: {
      input: {
        index: path.resolve(__dirname, 'frontend/html/index.html'),
        spot: path.resolve(__dirname, 'frontend/html/spot.html'),
        status: path.resolve(__dirname, 'frontend/html/status.html'),
        embed: path.resolve(__dirname, 'frontend/html/embed.html')
      },
      output: {
        inlineDynamicImports: false,
        assetFileNames: 'assets/[name].[hash][extname]',
        chunkFileNames: 'assets/[name].[hash].js',
        entryFileNames: 'assets/[name].[hash].js',
      }
    },

    // Minification options
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: false,
        pure_funcs: []
      },
      format: {
        comments: false
      }
    },

    cssMinify: true,
    assetsInlineLimit: 100000000, // Inline all assets (large limit)
  },

  plugins: [
    createHtmlPlugin({
      minify: {
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
      },
    }),

    viteStaticCopy({
      targets: staticCopyTargets
    }),

    flattenHtmlPlugin()
  ],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'frontend'),
      '@js': path.resolve(__dirname, 'frontend/js'),
      '@css': path.resolve(__dirname, 'frontend/css')
    }
  }
});
