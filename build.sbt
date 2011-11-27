
version in GlobalScope := "0.1"

// no binary for the root project
publishArtifact in (Compile, packageBin) := false

// no javadoc for the root project
publishArtifact in (Compile, packageDoc) := false

// no source for the root project
publishArtifact in (Compile, packageSrc) := false
