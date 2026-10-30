# Media3 and Compose ship their own consumer rules.
# Keep the services and receivers referenced from the manifest (R8 does this automatically),
# and keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
