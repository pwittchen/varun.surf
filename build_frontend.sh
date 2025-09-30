#!/bin/bash
echo "starting build..."
npx html-minifier-terser index.full.html -o index.min.html \
  --collapse-whitespace \
  --custom-attr-collapse \
  --remove-comments \
  --minify-js true \
  --minify-css true
sed -r 's/ {2,}/  /g' index.min.html > index.html
rm index.min.html
cp index.html src/main/resources/static/index.html
rm index.html
echo "frontend was build successfully"