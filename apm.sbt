lazy val apmPackage = taskKey[File]("Generates package.json for apm")

apmPackage := {
  import play.api.libs.json._

  val log = streams.value.log
  val packageJson = baseDirectory.value / "package.json"

  val author = developers.value.head
  val repository = scmInfo.value.get.browseUrl.toString
  val mainJs = (artifactPath in (Compile, fullOptJS)).value
  val licenseName = licenses.value.head._1

  val json = Json.obj(
    "name" -> name.value.stripPrefix("atom-"),
    "main" -> s"./${mainJs.relativeTo(baseDirectory.value).get}",
    "version" -> version.value,
    "description" -> description.value,
    "author" -> Json.obj(
      "name"  -> author.name,
      "email" -> author.email,
      "url"   -> author.url.toString
    ),
    "keywords" -> Json.arr(
      "scala",
      "scalameta",
      "ide",
      "atom-ide",
      "lsp",
      "language-server",
      "language-server-protocol"
    ),
    "repository" -> repository,
    "bugs"       -> s"${repository}/issues",
    "license" -> licenseName,
    "engines" -> Json.obj(
      "atom" -> ">=1.21.0 <2.0.0"
    ),
    "enhancedScopes" -> Json.arr(
      "source.scala"
    ),
    "dependencies" -> Json.obj(
      "atom-languageclient" -> "0.6.7"
    ),
    "consumedServices" -> Json.obj(
      "linter-indie" -> Json.obj(
        "versions" -> Json.obj("2.0.0" -> "consumeLinterV2")
      ),
      "datatip" -> Json.obj(
        "versions" -> Json.obj("0.1.0" -> "consumeDatatip")
      ),
      "atom-ide-busy-signal" -> Json.obj(
        "versions" -> Json.obj("0.1.0" -> "consumeBusySignal")
      ),
    ),
    "providedServices" -> Json.obj(
      "definitions" -> Json.obj(
        "versions" -> Json.obj("0.1.0" -> "provideDefinitions")
      ),
      "hyperclick" -> Json.obj(
        "versions" -> Json.obj("0.1.0" -> "provideHyperclick")
      ),
      "autocomplete.provider" -> Json.obj(
        "versions" -> Json.obj("2.0.0" -> "provideAutocomplete")
      ),
      "code-format.range" -> Json.obj(
        "versions" -> Json.obj("0.1.0" -> "provideCodeFormat")
      ),
      "outline-view" -> Json.obj(
        "versions" -> Json.obj("0.1.0" -> "provideOutlines")
      )
    )
  )

  IO.write(packageJson, Json.prettyPrint(json))
  packageJson
}

publish := {
  import sys.process._
  val log = streams.value.log

  // TODO: version bumping
  // val nextVersion = (Space ~> oneOf(Seq("major", "minor", "patch").map(literal))).parsed
  val tagName = s"v${version.value}"

  val mainJs = fullOptJS.in(Compile).value.data
  val packageJson = apmPackage.value

  def git(args: String*) = ("git" +: args).!(log)
  def HEAD = Seq("git", "rev-parse", "HEAD").!!.trim

  // Prepare commit on detached HEAD
  git("checkout", "--quiet", "--detach", HEAD)
  git("add", "--force", mainJs.getPath, packageJson.getPath)
  git("status", "-s")
  git("commit", "--quiet", "-m", s"Prepared ${tagName} release")
  git("log", "--format='%s (%h)'", "-1")
  // Tag and push to Github
  git("tag", tagName)
  git("push", "--porcelain", "--verbose", "origin", tagName)

  val exitCode = Seq("apm", "publish", "--tag", tagName).!(log)
  if (exitCode == 0) {
    Seq("apm", "view", "ide-scala").!(log)
  } else {
    // Revert the tag
    git("checkout", "--quiet", "-")
    git("push", "-d", "origin", tagName)
    git("tag",  "-d",           tagName)

    sys.error("apm publish failed")
  }
}