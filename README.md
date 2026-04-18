# ChunkyBlockRenderer

This is a small command line tool that renders single Minecraft blocks using [Chunky][chunky]. It is mostly useful for debugging or if you need images of blocks.

[chunky]: https://github.com/chunky-dev/chunky

## Building

The project is built with Gradle. Run the following command to build the jar file.

```
./gradlew jar
```

## Usage

Detailed usage information is printed by running `java -jar ChunkyBlockRenderer.jar`. Here are two examples.

### Render images of all blocks in an octree

```
java -jar ChunkyBlockRenderer.java octree -i ~/.chunky/scenes/your_scene/your_scene.octree2 --textures ~/.minecraft/versions/26.1/26.1.jar
```

### Render an image of a blockstate

```
java -jar ChunkyBlockRenderer.java blockstate minecraft:redstone_torch[lit=true] --textures ~/.minecraft/versions/26.1/26.1.jar
```

### Use a different Chunky version

This tool comes bundled with a Chunky 2.5.0 snapshot. You can replace it with a compatible one by modifying the classpath:

```
java -cp $HOME/.chunky/lib/chunky-core-2.5.0-SNAPSHOT.459.g1db9225.jar:ChunkyBlockRenderer.jar blockrenderer.BlockRenderer blockstate minecraft:redstone_torch[lit=true]  --textures ~/.minecraft/versions/26.1/26.1.jar
```
