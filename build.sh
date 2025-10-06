#!/bin/bash

# this script builds and optionally runs the whole app
# Usage: ./build.sh [--run]

echo "🚧  starting frontend build..."

# First, inline CSS and JavaScript into index.full.html
echo "Inlining CSS and JavaScript..."
cp frontend/index.html index.temp.html

# Inline CSS
if [ -f frontend/styles.css ]; then
    echo "Inlining CSS..."
    # Create marker file
    sed '/<link rel="stylesheet" href="styles.css">/c\
CSSPLACEHOLDER' index.temp.html > index.temp2.html

    # Insert CSS content at placeholder
    awk '/CSSPLACEHOLDER/{system("echo \"    <style>\"; cat frontend/styles.css; echo \"    </style>\"");next}1' index.temp2.html > index.temp.html
    rm index.temp2.html
    echo "✅  CSS inlined successfully"
else
    echo "⚠️  frontend/styles.css not found, skipping CSS inlining"
fi

# Inline JavaScript
if [ -f frontend/script.js ]; then
    echo "Inlining JavaScript..."
    # Create marker file
    sed 's|<script src="script.js"></script>|JSPLACEHOLDER|' index.temp.html > index.temp2.html

    # Insert JS content at placeholder
    awk '/JSPLACEHOLDER/{system("echo \"<script>\"; cat frontend/script.js; echo \"</script>\"");next}1' index.temp2.html > index.temp.html
    rm index.temp2.html
    echo "✅  JavaScript inlined successfully"
else
    echo "⚠️  frontend/script.js not found, skipping JS inlining"
fi

echo "Minifying HTML..."
npx html-minifier-terser index.temp.html -o index.min.html \
  --collapse-whitespace \
  --custom-attr-collapse \
  --remove-comments \
  --minify-js true \
  --minify-css true
sed -r 's/ {2,}/  /g' index.min.html > index.html
rm index.min.html index.temp.html
cp index.html src/main/resources/static/index.html
rm index.html
echo "✅  frontend was built successfully"

# Clean up temporary frontend files
echo "Cleaning up temporary files..."
rm -f index.temp.html index.temp2.html index.min.html index.html frontend/index.html.bak frontend/index.html.bak2
echo "✅  temporary files cleaned up"

echo "🚧  starting backend build..."
./gradlew clean bootJar
echo "✅  backend was built successfully"

if [ "$1" == "--run" ]; then
    echo "🚀  starting the app"
    java --enable-preview -jar build/libs/*.jar
    echo "🛑  app was stopped"
fi