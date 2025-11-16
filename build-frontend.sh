#!/bin/bash
# this script builds frontend of the app

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
cp frontend/html/index.html index.temp.html

# Flatten the datafast script tag to a single line BEFORE any processing
echo "Flattening multiline script tags..."
perl -0777 -pe 's/<script\s+defer\s+data-website-id="[^"]*"\s+data-domain="[^"]*"\s+src="[^"]*">\s*<\/script>/<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU" data-domain="varun.surf" src="https:\/\/datafa.st\/js\/script.js"><\/script>/gs' index.temp.html > index.temp3.html
mv index.temp3.html index.temp.html

# Extract and temporarily remove the datafast script to protect it during processing
echo "Protecting external scripts..."
echo '<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU" data-domain="varun.surf" src="https://datafa.st/js/script.js"></script>' > datafast.tmp
perl -i.bak -pe 's|<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU"[^>]*></script>|\nDATAFASTPLACEHOLDER|g' index.temp.html

# Inline CSS
if [ -f frontend/css/styles.css ]; then
    echo "Inlining CSS..."
    # Create marker file
    sed '/<link rel="stylesheet" href="styles.css">/c\
CSSPLACEHOLDER' index.temp.html > index.temp2.html

    # Insert CSS content at placeholder
    awk '/CSSPLACEHOLDER/{system("echo \"    <style>\"; cat frontend/css/styles.css; echo \"    </style>\"");next}1' index.temp2.html > index.temp.html
    rm index.temp2.html
    echo "✅  CSS inlined successfully"
else
    echo "⚠️  frontend/css/styles.css not found, skipping CSS inlining"
fi

# Inline JavaScript (translations, country flags, then page index script)
if [ -f frontend/js/page/index.js ]; then
    echo "Inlining JavaScript..."
    # Create marker file
    sed 's|<script src="../js/page/index.js"></script>|JSPLACEHOLDER|' index.temp.html > index.temp2.html

    # Remove standalone country flag script tag before inlining
    perl -0pi -e 's|\s*<script src="[^"]*country-flags\.js"></script>||' index.temp2.html

    # Insert translations.js, country-flags.js, and page JS content at placeholder
    if [ -f frontend/js/common/translations.js ]; then
        echo "Including translations and shared helpers..."
        if [ -f frontend/js/common/country-flags.js ]; then
            awk '/JSPLACEHOLDER/{system("echo \"<script>\"; cat frontend/js/common/translations.js; echo \"\"; cat frontend/js/common/country-flags.js; echo \"\"; cat frontend/js/page/index.js; echo \"</script>\"");next}1' index.temp2.html > index.temp.html
        else
            echo "⚠️  frontend/js/common/country-flags.js not found, skipping shared helper"
            awk '/JSPLACEHOLDER/{system("echo \"<script>\"; cat frontend/js/common/translations.js; echo \"\"; cat frontend/js/page/index.js; echo \"</script>\"");next}1' index.temp2.html > index.temp.html
        fi
    else
        echo "⚠️  frontend/js/common/translations.js not found, inlining remaining scripts only"
        if [ -f frontend/js/common/country-flags.js ]; then
            awk '/JSPLACEHOLDER/{system("echo \"<script>\"; cat frontend/js/common/country-flags.js; echo \"\"; cat frontend/js/page/index.js; echo \"</script>\"");next}1' index.temp2.html > index.temp.html
        else
            awk '/JSPLACEHOLDER/{system("echo \"<script>\"; cat frontend/js/page/index.js; echo \"</script>\"");next}1' index.temp2.html > index.temp.html
        fi
    fi
    rm index.temp2.html
    echo "✅  JavaScript inlined successfully"
else
    echo "⚠️  frontend/js/page/index.js not found, skipping JS inlining"
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
cp frontend/assets/logo.png src/main/resources/static/logo.png
echo "✅  logo file copied successfully"

#Copy other files to static directory
echo "Copying other files to static directory"
cp frontend/assets/ai.txt src/main/resources/static/ai.txt
cp frontend/assets/llms.txt src/main/resources/static/llms.txt
cp frontend/assets/robots.txt src/main/resources/static/robots.txt
cp frontend/assets/sitemap.xml src/main/resources/static/sitemap.xml

# Build spot.html (single spot page)
echo "🚧  building spot.html..."
cp frontend/html/spot.html spot.temp.html

# Flatten the datafast script tag for spot.html
echo "Flattening multiline script tags in spot.html..."
perl -0777 -pe 's/<script\s+defer\s+data-website-id="[^"]*"\s+data-domain="[^"]*"\s+src="[^"]*">\s*<\/script>/<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU" data-domain="varun.surf" src="https:\/\/datafa.st\/js\/script.js"><\/script>/gs' spot.temp.html > spot.temp3.html
mv spot.temp3.html spot.temp.html

# Extract and temporarily remove the datafast script for spot.html
echo "Protecting external scripts in spot.html..."
perl -i.bak -pe 's|<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU"[^>]*></script>|\nDATAFASTPLACEHOLDER_SPOT|g' spot.temp.html

# Inline CSS for spot.html
if [ -f frontend/css/styles.css ]; then
    echo "Inlining CSS into spot.html..."
    sed '/<link rel="stylesheet" href="styles.css">/c\
CSSPLACEHOLDER_SPOT' spot.temp.html > spot.temp2.html
    python3 - <<'PY'
from pathlib import Path

template = Path('spot.temp2.html').read_text()
base_css = Path('frontend/css/styles.css').read_text().rstrip()

extra_path = Path('frontend/css/spot-inline.css')
extra_css = ''
if extra_path.exists():
    extra_css = '\n' + extra_path.read_text().strip()

replacement = '    <style>\n' + base_css + extra_css + '\n    </style>'

Path('spot.temp.html').write_text(template.replace('CSSPLACEHOLDER_SPOT', replacement, 1))
PY
    rm spot.temp2.html
    echo "✅  CSS inlined successfully into spot.html"
fi

# Inline JavaScript for spot.html (translations, country flags, then spot script)
if [ -f frontend/js/page/spot.js ]; then
    echo "Inlining JavaScript into spot.html..."
    sed 's|<script src="../js/page/spot.js"></script>|JSPLACEHOLDER_SPOT|' spot.temp.html > spot.temp2.html
    # Remove standalone translations and country flag script tags before inlining
    perl -0pi -e 's|\s*<script src="[^"]*translations\.js"></script>||' spot.temp2.html
    perl -0pi -e 's|\s*<script src="[^"]*country-flags\.js"></script>||' spot.temp2.html
    if [ -f frontend/js/common/translations.js ]; then
        echo "Including translations and shared helpers in spot.html..."
        if [ -f frontend/js/common/country-flags.js ]; then
            awk '/JSPLACEHOLDER_SPOT/{system("echo \"<script>\"; cat frontend/js/common/translations.js; echo \"\"; cat frontend/js/common/country-flags.js; echo \"\"; cat frontend/js/page/spot.js; echo \"</script>\"");next}1' spot.temp2.html > spot.temp.html
        else
            echo "⚠️  frontend/js/common/country-flags.js not found, skipping shared helper"
            awk '/JSPLACEHOLDER_SPOT/{system("echo \"<script>\"; cat frontend/js/common/translations.js; echo \"\"; cat frontend/js/page/spot.js; echo \"</script>\"");next}1' spot.temp2.html > spot.temp.html
        fi
    else
        echo "⚠️  frontend/js/common/translations.js not found, inlining remaining scripts only"
        if [ -f frontend/js/common/country-flags.js ]; then
            awk '/JSPLACEHOLDER_SPOT/{system("echo \"<script>\"; cat frontend/js/common/country-flags.js; echo \"\"; cat frontend/js/page/spot.js; echo \"</script>\"");next}1' spot.temp2.html > spot.temp.html
        else
            awk '/JSPLACEHOLDER_SPOT/{system("echo \"<script>\"; cat frontend/js/page/spot.js; echo \"</script>\"");next}1' spot.temp2.html > spot.temp.html
        fi
    fi
    rm spot.temp2.html
    echo "✅  JavaScript inlined successfully into spot.html"
fi

# Restore the datafast script for spot.html
echo "Restoring external scripts in spot.html..."
sed -i.bak2 "s|DATAFASTPLACEHOLDER_SPOT|$(cat datafast.tmp)|g" spot.temp.html

# Minify spot.html
echo "Minifying spot.html..."
npx html-minifier-terser spot.temp.html -o spot.min.html \
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
perl -pe 's/>\s+</></g' spot.min.html | tr -d '\n' > spot.html

# Fix defer attribute format for spot.html
sed -i.bak3 's/defer="defer"/defer/g' spot.html
rm spot.min.html spot.temp.html
cp spot.html src/main/resources/static/spot.html
rm spot.html
echo "✅  spot.html was built successfully"

# Build status.html (status page)
echo "🚧  building status.html..."
cp frontend/html/status.html status.temp.html

# Flatten the datafast script tag for status.html
echo "Flattening multiline script tags in status.html..."
perl -0777 -pe 's/<script\s+defer\s+data-website-id="[^"]*"\s+data-domain="[^"]*"\s+src="[^"]*">\s*<\/script>/<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU" data-domain="varun.surf" src="https:\/\/datafa.st\/js\/script.js"><\/script>/gs' status.temp.html > status.temp3.html
mv status.temp3.html status.temp.html

# Extract and temporarily remove the datafast script for status.html
echo "Protecting external scripts in status.html..."
perl -i.bak -pe 's|<script defer data-website-id="dfid_yUtxeXdTh4uWWN5KSQ3qU"[^>]*></script>|\nDATAFASTPLACEHOLDER_STATUS|g' status.temp.html

# Inline CSS for status.html
if [ -f frontend/css/styles.css ]; then
    echo "Inlining CSS into status.html..."
    sed '/<link rel="stylesheet" href="styles.css">/c\
CSSPLACEHOLDER_STATUS' status.temp.html > status.temp2.html
    awk '/CSSPLACEHOLDER_STATUS/{system("echo \"    <style>\"; cat frontend/css/styles.css; echo \"    </style>\"");next}1' status.temp2.html > status.temp.html
    rm status.temp2.html
    echo "✅  CSS inlined successfully into status.html"
fi

# Inline JavaScript for status.html
if [ -f frontend/js/page/status.js ]; then
    echo "Inlining JavaScript into status.html..."
    sed 's|<script src="../js/page/status.js"></script>|JSPLACEHOLDER_STATUS|' status.temp.html > status.temp2.html
    awk '/JSPLACEHOLDER_STATUS/{system("echo \"<script>\"; cat frontend/js/page/status.js; echo \"</script>\"");next}1' status.temp2.html > status.temp.html
    rm status.temp2.html
    echo "✅  JavaScript inlined successfully into status.html"
else
    echo "⚠️  frontend/js/page/status.js not found, skipping JS inlining"
fi

# Restore the datafast script for status.html
echo "Restoring external scripts in status.html..."
sed -i.bak2 "s|DATAFASTPLACEHOLDER_STATUS|$(cat datafast.tmp)|g" status.temp.html

# Minify status.html
echo "Minifying status.html..."
npx html-minifier-terser status.temp.html -o status.min.html \
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
perl -pe 's/>\s+</></g' status.min.html | tr -d '\n' > status.html

cp status.html src/main/resources/static/status.html
rm status.html status.min.html status.temp.html
echo "✅  status.html was built successfully"

echo "✅  frontend was built successfully"

# Clean up temporary frontend files
echo "Cleaning up temporary files..."
rm -f index.temp.html index.temp2.html index.min.html index.html index.temp.html.bak index.temp.html.bak2 index.temp.html.bak4 index.html.bak3 datafast.tmp frontend/index.html.bak frontend/index.html.bak2
rm -f spot.temp.html spot.temp2.html spot.min.html spot.html spot.temp.html.bak spot.temp.html.bak2 spot.html.bak3 frontend/spot.html.bak frontend/spot.html.bak2
rm -f status.temp.html status.temp2.html status.temp3.html status.min.html status.html status.temp.html.bak status.temp.html.bak2 frontend/status.html.bak
echo "✅  temporary files cleaned up"
