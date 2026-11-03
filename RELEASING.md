Releasing
=========

 1. Change the version to a non-SNAPSHOT version
 2. Update `CHANGELOG.md` for the impending release
 3. Run every test suite with `./run-all-tests.sh all`
 4. `git commit -am "Prepare for release X.Y.Z"` (where X.Y.Z is the new version)
 5. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
 6. Publish the artifacts with `./gradlew publish` to the repositories configured in
    `buildsystem/maven-push.gradle`, or with `./gradlew publishToMavenLocal`
 7. Change the version to the next `-SNAPSHOT` version
 8. `git commit -am "Prepare next development version"`
 9. `git push && git push --tags`
