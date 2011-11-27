
// no binary
publishArtifact in (Compile, packageBin) := false

// no javadoc
publishArtifact in (Compile, packageDoc) := false

// no source
publishArtifact in (Compile, packageSrc) := false
