#!/bin/bash
npx html-minifier-terser index.full.html -o index.html \
  --collapse-whitespace \
  --remove-comments \
  --minify-js true \
  --minify-css true
