import { minify } from "html-minifier-terser";
import { join, basename } from "path";
import { watch } from "fs";

const ROOT_DIR = import.meta.dir;
const FRONTEND_DIR = join(ROOT_DIR, "src/frontend");
const OUTPUT_DIR = join(ROOT_DIR, "src/main/resources/static");

// Entry points configuration
const HTML_ENTRIES = [
  { name: "index", path: join(FRONTEND_DIR, "html/index.html") },
  { name: "spot", path: join(FRONTEND_DIR, "html/spot.html") },
  { name: "status", path: join(FRONTEND_DIR, "html/status.html") },
  { name: "embed", path: join(FRONTEND_DIR, "html/embed.html") },
  { name: "tv", path: join(FRONTEND_DIR, "html/tv.html") },
];

// Static assets to copy
const STATIC_ASSETS = [
  { src: "assets/logo.png", dest: "logo.png" },
  { src: "assets/ai.txt", dest: "ai.txt" },
  { src: "assets/llms.txt", dest: "llms.txt" },
  { src: "assets/robots.txt", dest: "robots.txt" },
  { src: "assets/sitemap.xml", dest: "sitemap.xml" },
];

// HTML minification options (matching Vite config)
const HTML_MINIFY_OPTIONS = {
  collapseWhitespace: true,
  removeComments: true,
  minifyCSS: true,
  minifyJS: true,
  collapseBooleanAttributes: true,
  removeAttributeQuotes: true,
  removeRedundantAttributes: true,
  removeScriptTypeAttributes: true,
  removeStyleLinkTypeAttributes: true,
  useShortDoctype: true,
};

async function cleanOutputDir(): Promise<void> {
  console.log("Cleaning output directory...");
  try {
    const files = await Array.fromAsync(new Bun.Glob("**/*").scan({ cwd: OUTPUT_DIR, absolute: true }));
    for (const file of files) {
      await Bun.file(file).delete();
    }
  } catch {
    // Directory might not exist
  }
  await Bun.write(join(OUTPUT_DIR, ".gitkeep"), "");
}

async function bundleJavaScript(): Promise<Map<string, string>> {
  console.log("Bundling JavaScript...");

  // Find all JS entry points
  const jsEntries = [
    join(FRONTEND_DIR, "js/page/index.js"),
    join(FRONTEND_DIR, "js/page/spot.js"),
    join(FRONTEND_DIR, "js/page/status.js"),
    join(FRONTEND_DIR, "js/page/tv.js"),
  ];

  const results = new Map<string, string>();

  for (const entry of jsEntries) {
    const entryName = basename(entry, ".js");

    const result = await Bun.build({
      entrypoints: [entry],
      outdir: join(OUTPUT_DIR, "assets"),
      naming: `${entryName}.[hash].js`,
      minify: true,
      sourcemap: "none",
      target: "browser",
    });

    if (!result.success) {
      console.error(`Failed to bundle ${entry}:`);
      for (const log of result.logs) {
        console.error(log);
      }
      throw new Error(`Bundle failed for ${entry}`);
    }

    // Get the output file name
    for (const output of result.outputs) {
      const outputName = basename(output.path);
      results.set(entryName, `/assets/${outputName}`);
      console.log(`  ${entryName}.js -> assets/${outputName}`);
    }
  }

  return results;
}

async function bundleCSS(): Promise<Map<string, string>> {
  console.log("Bundling CSS...");

  const cssFiles = [
    { src: join(FRONTEND_DIR, "css/styles.css"), name: "styles" },
    { src: join(FRONTEND_DIR, "css/tv.css"), name: "tv" },
  ];

  const results = new Map<string, string>();

  for (const { src, name } of cssFiles) {
    const file = Bun.file(src);
    if (!(await file.exists())) {
      console.log(`  Skipping ${name}.css (not found)`);
      continue;
    }

    const content = await file.text();

    // Generate hash for cache busting
    const hasher = new Bun.CryptoHasher("md5");
    hasher.update(content);
    const hash = hasher.digest("hex").slice(0, 8);

    const outputName = `${name}.${hash}.css`;
    const outputPath = join(OUTPUT_DIR, "assets", outputName);

    // Minify CSS using simple regex-based minification
    const minifiedCSS = content
      .replace(/\/\*[\s\S]*?\*\//g, "") // Remove comments
      .replace(/\s+/g, " ") // Collapse whitespace
      .replace(/\s*([{}:;,>+~])\s*/g, "$1") // Remove spaces around selectors
      .replace(/;}/g, "}") // Remove last semicolon
      .trim();

    await Bun.write(outputPath, minifiedCSS);
    results.set(name, `/assets/${outputName}`);
    console.log(`  ${name}.css -> assets/${outputName}`);
  }

  return results;
}

async function processHTML(jsMap: Map<string, string>, cssMap: Map<string, string>): Promise<void> {
  console.log("Processing HTML files...");

  for (const { name, path } of HTML_ENTRIES) {
    const file = Bun.file(path);
    if (!(await file.exists())) {
      console.log(`  Skipping ${name}.html (not found)`);
      continue;
    }

    let html = await file.text();

    // Replace CSS references
    html = html.replace(
      /href="\.\.\/css\/styles\.css"/g,
      `href="${cssMap.get("styles") || "/assets/styles.css"}"`
    );
    html = html.replace(
      /href="\.\.\/css\/tv\.css"/g,
      `href="${cssMap.get("tv") || "/assets/tv.css"}"`
    );

    // Replace JS references
    html = html.replace(
      /src="\.\.\/js\/page\/index\.js"/g,
      `src="${jsMap.get("index") || "/assets/index.js"}"`
    );
    html = html.replace(
      /src="\.\.\/js\/page\/spot\.js"/g,
      `src="${jsMap.get("spot") || "/assets/spot.js"}"`
    );
    html = html.replace(
      /src="\.\.\/js\/page\/status\.js"/g,
      `src="${jsMap.get("status") || "/assets/status.js"}"`
    );
    html = html.replace(
      /src="\.\.\/js\/page\/tv\.js"/g,
      `src="${jsMap.get("tv") || "/assets/tv.js"}"`
    );

    // Minify HTML
    const minifiedHtml = await minify(html, HTML_MINIFY_OPTIONS);

    // Make single-line (remove newlines between tags)
    const singleLineHtml = minifiedHtml.replace(/>\s+</g, "><").replace(/\n/g, "");

    // Write to output
    const outputPath = join(OUTPUT_DIR, `${name}.html`);
    await Bun.write(outputPath, singleLineHtml);
    console.log(`  ${name}.html`);
  }
}

async function copyStaticAssets(): Promise<void> {
  console.log("Copying static assets...");

  for (const { src, dest } of STATIC_ASSETS) {
    const srcPath = join(FRONTEND_DIR, src);
    const destPath = join(OUTPUT_DIR, dest);

    const file = Bun.file(srcPath);
    if (await file.exists()) {
      await Bun.write(destPath, file);
      console.log(`  ${src} -> ${dest}`);
    } else {
      console.log(`  Skipping ${src} (not found)`);
    }
  }

  // Copy spot images if they exist
  const spotImagesDir = join(FRONTEND_DIR, "images/spots");
  const spotImagesGlob = new Bun.Glob("**/*.{png,jpg,jpeg}");

  try {
    const spotImages = await Array.fromAsync(spotImagesGlob.scan({ cwd: spotImagesDir, absolute: false }));
    if (spotImages.length > 0) {
      console.log("  Copying spot images...");
      for (const img of spotImages) {
        const srcPath = join(spotImagesDir, img);
        const destPath = join(OUTPUT_DIR, "images/spots", img);
        await Bun.write(destPath, Bun.file(srcPath));
        console.log(`    images/spots/${img}`);
      }
    }
  } catch {
    // No spot images directory
  }
}

async function build(): Promise<void> {
  console.log("\n=== Building frontend with Bun ===\n");

  const startTime = performance.now();

  await cleanOutputDir();
  const jsMap = await bundleJavaScript();
  const cssMap = await bundleCSS();
  await processHTML(jsMap, cssMap);
  await copyStaticAssets();

  // Remove .gitkeep
  try {
    await Bun.file(join(OUTPUT_DIR, ".gitkeep")).delete();
  } catch {
    // Ignore
  }

  const endTime = performance.now();
  console.log(`\n=== Build completed in ${(endTime - startTime).toFixed(0)}ms ===\n`);
}

async function dev(): Promise<void> {
  console.log("Starting development mode with watch...");
  await build();

  console.log("\nWatching for changes in src/frontend...");

  const watcher = watch(FRONTEND_DIR, { recursive: true }, (_event, filename) => {
    if (filename && !filename.includes("node_modules")) {
      console.log(`\nFile changed: ${filename}`);
      void build();
    }
  });

  process.on("SIGINT", () => {
    watcher.close();
    process.exit(0);
  });
}

// Main entry point
const command = process.argv[2];

if (command === "dev") {
  void dev();
} else {
  void build();
}