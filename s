#!/bin/bash

git checkout releases/gcc-14

# Determine the absolute path of the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"

# Define the target directory relative to the script's location
TARGET_DIR="${SCRIPT_DIR}/gcc14"

# Create the target directory if it does not exist
mkdir -p "$TARGET_DIR"
mkdir -p objdir

cd objdir

../configure --prefix="$TARGET_DIR" --enable-languages=c

make
make install