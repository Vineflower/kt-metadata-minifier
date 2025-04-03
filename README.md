# kt-metadata-minifier

A library written with Java 24's Classfile API to cut out unnecessary files from Kotlin's metadata jar files.
This was created for the [Vineflower Kotlin plugin](https://github.com/Vineflower/vineflower/tree/develop/1.12.0/plugins/kotlin)
to keep the size of the shaded metadata jar small.

## What does this remove?

When run, this will download the latest Kotlin metadata jar and remove many files from it:
 - All Kotlin classes are removed. These are meant for usage in situations akin to kotlin-reflect, but are not needed
   for decompilation purposes.
 - Many generated Protobuf classes are removed. When Protobuf creates its classes, it adds a large number of builders
   that it never uses. Since the use case for the minified version is not as a dependency for other Protobuf collections,
   removing all builders that are not used within the Protobuf classes themselves is safe.
 - Inner class attributes are modified to be stripped of any removed classes, along with implemented interfaces.
 - Methods referencing removed classes are also removed.


