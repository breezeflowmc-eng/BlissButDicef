# Getting the .jar (pick ONE)

The zip is source code. The server needs a compiled .jar, so it has to be built once.

## A) Build online - nothing to install (easiest)
1. Make a free account at github.com and create a new repository.
2. Click "uploading an existing file" and drag in EVERYTHING from this folder
   (including the hidden `.github` folder - if your file explorer hides it, enable "show hidden files").
3. Open the repo's **Actions** tab -> "Build DiceGems" -> wait ~1 minute for the green tick.
4. Click the finished run -> download **DiceGems-jar** at the bottom -> unzip it -> `DiceGems-1.0.0.jar`.
5. Put that jar in your server's `plugins/` folder.

## B) Build on your PC
1. Install JDK 21 (adoptium.net) and Apache Maven (maven.apache.org).
2. Windows: double-click `build.bat`.   Mac/Linux: run `./build.sh`.
3. The jar is `target/DiceGems-1.0.0.jar`.

## Before starting the server
Delete `plugins/.paper-remapped/` so Paper doesn't reuse the broken copy.

If the build prints an error, send it to me and I'll fix the code.
