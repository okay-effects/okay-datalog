# Releasing

Releases are a Maven repository on GitHub Pages: the `gh-pages` branch, its `maven/` directory, served at
https://okay-effects.github.io/okay-datalog/maven. While nobody but us uses it, a change is REPUBLISHED under the
current number (0.1.0), the tag moved; a new number only when someone else depends on it.

1. Everything green with no local copies: `sbt -Dsbt.ivy.home=$(mktemp -d) clean test`.
2. `ThisBuild / version` without `-SNAPSHOT`; committed, tagged (`git tag -f v0.1.0`).
3. `rm -rf target/pages-maven && sbt publish` (both platforms).
4. Onto `gh-pages`, under `maven/`:

   ```sh
   git worktree add /tmp/okay-datalog-pages gh-pages   # the first time: git worktree add --orphan -b gh-pages …
   mkdir -p /tmp/okay-datalog-pages/maven && rsync -ac target/pages-maven/ /tmp/okay-datalog-pages/maven/
   touch /tmp/okay-datalog-pages/.nojekyll
   git -C /tmp/okay-datalog-pages add -A && git -C /tmp/okay-datalog-pages commit -m "release 0.1.0"
   git -C /tmp/okay-datalog-pages push origin gh-pages && git worktree remove /tmp/okay-datalog-pages
   git push -f origin v0.1.0
   ```

5. A user that had the old jars clears them: `rm -rf ~/Library/Caches/Coursier/v1/https/okay-effects.github.io/okay-datalog`.
