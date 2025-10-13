#!/bin/bash

# this script builds and optionally runs the whole app
# Usage: ./build.sh [--run]

echo "🚧  starting frontend build..."

# Get the latest git tag with v prefix
VERSION=$(git tag --list 'v*' --sort=-version:refname | head -1)
if [ -z "$VERSION" ]; then
    VERSION="v0.0.0"
    echo "⚠️  No git tag found, using default version: $VERSION"
else
    echo "📌  Using version: $VERSION"
fi

# First, inline CSS and JavaScript into index.full.html
echo "Inlining CSS and JavaScript..."
cp frontend/index.html index.temp.html

# Flatten the datafast script tag to a single line BEFORE any processing
echo "Flattening multiline script tags..."
perl -0777 -pe 's/<script\s+defer\s+data-website-id="[^"]*"\s+data-domain="[^"]*"\s+src="[^"]*">\s*<\/script>/<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU" data-domain="varun.surf" src="https:\/\/datafa.st\/js\/script.js"><\/script>/gs' index.temp.html > index.temp3.html
mv index.temp3.html index.temp.html

# Extract and temporarily remove the datafast script to protect it during processing
echo "Protecting external scripts..."
echo '<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU" data-domain="varun.surf" src="https://datafa.st/js/script.js"></script>' > datafast.tmp
perl -i.bak -pe 's|<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU"[^>]*></script>|\nDATAFASTPLACEHOLDER|g' index.temp.html

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

# Inline JavaScript (translations first, then script.js)
if [ -f frontend/script.js ]; then
    echo "Inlining JavaScript..."
    # Create marker file
    sed 's|<script src="script.js"></script>|JSPLACEHOLDER|' index.temp.html > index.temp2.html

    # Insert translations.js and script.js content at placeholder
    if [ -f frontend/translations.js ]; then
        echo "Including translations..."
        awk '/JSPLACEHOLDER/{system("echo \"<script>\"; cat frontend/translations.js; echo \"\"; cat frontend/script.js; echo \"</script>\"");next}1' index.temp2.html > index.temp.html
    else
        echo "⚠️  frontend/translations.js not found, inlining script.js only"
        awk '/JSPLACEHOLDER/{system("echo \"<script>\"; cat frontend/script.js; echo \"</script>\"");next}1' index.temp2.html > index.temp.html
    fi
    rm index.temp2.html
    echo "✅  JavaScript inlined successfully"
else
    echo "⚠️  frontend/script.js not found, skipping JS inlining"
fi

# Restore the datafast script
echo "Restoring external scripts..."
sed -i.bak2 "s|DATAFASTPLACEHOLDER|$(cat datafast.tmp)|g" index.temp.html

# Add version to footer - DISABLED
# echo "Adding version $VERSION to footer..."
# sed -i.bak4 "s|</a></p>|</a> \&bull; <a href=\"https://github.com/pwittchen/varun.surf/releases/tag/$VERSION\" target=\"_blank\" class=\"footer-external-link\">$VERSION</a></p>|g" index.temp.html

echo "Minifying HTML..."
npx html-minifier-terser index.temp.html -o index.min.html \
  --collapse-whitespace \
  --remove-comments \
  --minify-css true \
  --minify-js true \
  --collapse-boolean-attributes \
  --remove-attribute-quotes \
  --remove-redundant-attributes \
  --remove-script-type-attributes \
  --remove-style-link-type-attributes \
  --use-short-doctype
perl -pe 's/>\s+</></g' index.min.html | tr -d '\n' > index.html

# Fix defer attribute format back to standalone (defer instead of defer="defer")
sed -i.bak3 's/defer="defer"/defer/g' index.html
rm index.min.html index.temp.html
cp index.html src/main/resources/static/index.html
rm index.html

# Copy logo file to static directory
echo "Copying logo file..."
cp frontend/logo.png src/main/resources/static/logo.png
echo "✅  logo file copied successfully"

echo "✅  frontend was built successfully"

# Clean up temporary frontend files
echo "Cleaning up temporary files..."
rm -f index.temp.html index.temp2.html index.min.html index.html index.temp.html.bak index.temp.html.bak2 index.temp.html.bak4 index.html.bak3 datafast.tmp frontend/index.html.bak frontend/index.html.bak2
echo "✅  temporary files cleaned up"

echo "🚧  starting backend build..."
./gradlew clean bootJar
echo "✅  backend was built successfully"

if [ "$1" == "--run" ]; then
    echo "🚀  starting the app"
    java --enable-preview -jar build/libs/*.jar
    echo "🛑  app was stopped"
fi