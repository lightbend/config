# Submitting pull requests

Pull requests should go via GitHub; there are some nice
[general guidelines for contributing on GitHub](https://guides.github.com/activities/contributing-to-open-source/)
if you haven't done it before.

Unless your fix is trivial, it's a good idea to look for related
discussions and maybe open an issue to discuss, before making a
pull request. Discussion in advance may keep you from wasting time
on an approach that won't work out. However, if you communicate
better in code than in words, starting with a patch is
fine... just be willing to revise it!

Before we can accept pull requests, you will need to agree to the
Typesafe Contributor License Agreement online, using your GitHub
account - it takes 30 seconds.  You can do this at
http://www.typesafe.com/contribute/cla

Expect that most PRs will need revision before merge. If people
suggest revisions, you can make them yourself or wait for a
maintainer to do it on their own timeline. The larger the PR, the
more revision will likely be needed.

# Making a release

To make a release you'll need to be a maintainer with GitHub
permissions to push to the master and gh-pages branches, and
Sonatype permissions to publish.

Here are the steps, which should be automated but aren't (PR
welcome!):

  1. write release notes in NEWS.md following the format
     already in there. update README with the new version.
     Commit.
  2. create a signed git tag "vX.Y.Z"
  3. start sbt; `show version` should confirm that the version was
     taken from the tag
  4. clean
  5. test (double check that release works)
  6. doc (double check that docs build, plus build docs
     to be copied to gh-pages later)
  7. if test or doc fails, delete the tag, fix it, start over.
  8. publishSigned
  9. make a separate clone of the repo in another directory and
      check out the gh-pages branch
  10. /bin/rm -rf latest/api on gh-pages checkout
  11. copy config/target/api from master checkout to vX.Y.Z in
      gh-pages so you have vX.Y.Z/index.html
  12. copy config/target/api from master checkout into latest/
      so you have latest/api/index.html
  13. commit all that to gh-pages, check the diff for sanity
      (latest/api should be mostly just changed timestamps)
  14. push gh-pages
  15. log into sonatype website and go through the usual hoops
      (under Staging Repositories, search for com.typesafe, verify the
      artifacts in it, close, release)
  16. push the "vX.Y.Z" tag
  17. announce release, possibly wait for maven central to sync
      first
