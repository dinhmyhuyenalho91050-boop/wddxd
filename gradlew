#!/usr/bin/env sh

##############################################################################
# Lightweight Gradle bootstrapper without committing binary wrapper files.   #
##############################################################################

WRAPPER_DIR="gradle/wrapper"
WRAPPER_PROPERTIES="$WRAPPER_DIR/gradle-wrapper.properties"
DIST_DIR="$WRAPPER_DIR/dist"

if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  echo "Gradle wrapper properties not found."
  exit 1
fi

DISTRIBUTION_URL=$(grep distributionUrl "$WRAPPER_PROPERTIES" | cut -d'=' -f2 | sed "s#\\://#://#g")
if [ -z "$DISTRIBUTION_URL" ]; then
  echo "distributionUrl not defined in $WRAPPER_PROPERTIES"
  exit 1
fi

VERSION=$(echo "$DISTRIBUTION_URL" | sed -n 's#.*/\(gradle-[0-9A-Za-z.\-]*\)\.zip#\1#p')
if [ -z "$VERSION" ]; then
  echo "Unable to parse Gradle version from distribution URL"
  exit 1
fi

INSTALL_DIR="$DIST_DIR/$VERSION"
ZIP_FILE="$DIST_DIR/$VERSION.zip"
GRADLE_CMD="$INSTALL_DIR/bin/gradle"

if [ ! -x "$GRADLE_CMD" ]; then
  mkdir -p "$DIST_DIR"
  echo "Downloading Gradle distribution $VERSION..."
  curl -fsSL "$DISTRIBUTION_URL" -o "$ZIP_FILE"
  unzip -q "$ZIP_FILE" -d "$DIST_DIR"
  rm -f "$ZIP_FILE"
  # distribution unpacks into directory named like gradle-8.4
  if [ ! -x "$GRADLE_CMD" ]; then
    INNER_DIR=$(find "$DIST_DIR" -maxdepth 1 -type d -name "gradle-*" | head -n 1)
    if [ -n "$INNER_DIR" ]; then
      mv "$INNER_DIR" "$INSTALL_DIR"
    fi
  fi
fi

exec "$GRADLE_CMD" "$@"
