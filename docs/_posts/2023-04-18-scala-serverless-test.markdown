---
layout: post
title:  "Scala.js lenses, frontends, and server side rendering with an AWS Lambda Scala backend"
date:   2023-04-18 12:26:12 -0500
categories: scala serverless
---

As a long time Java coder I'm finding that it's a great time to start working with Scala.
Scala 3 libraries are fun to work with and [contextual abstractions](https://docs.scala-lang.org/scala3/book/ca-contextual-abstractions-intro.html) feel natural once you've used them a few times.
Unfortunately most Scala job postings available in Canada require a few years of professional Scala experience.

That puts Scala in the hobby language bucket for me but it's so nice to work with that I want to try using it with some more serious side projects.
AWS Lambda makes it cheaper to start and run a small side project and the recent JVM SnapStart feature reduces cold start latency.

I spun up an experimental project with a Scala.js frontend, a Scala AWS Lambda backend and the results were promising.
Using [Monocle](https://www.optics.dev/Monocle/) for lenses, [scala-js-snabbdom](https://github.com/buntec/scala-js-snabbdom) and [ScalaTags](https://com-lihaoyi.github.io/scalatags/) I was able to make an interesting frontend UI that could also be rendered on the backend.

I haven't done extensive performance testing.
The backend cold start times with SnapStart have been around 1 second measured with `curl` which is good enough for my uses.
Response times after cold start with `curl` have usually been between 100-200ms, but I will probably have to keep track of it as the logic and data access gets more involved.

For the frontend I stitched together snabbdom and ScalaTags with some of my own glue code.
The [Tyrian UI library](https://tyrian.indigoengine.io/) or [Slinky](https://slinky.dev/) would probably be a better choice for a more serious project.

The [RockTheJVM Fullstack Typelevel Stack course](https://rockthejvm.com/p/typelevel-rite-of-passage) uses Tyrian to build a complete application. 
It's on my list to work on in the future.

The code for this project is available on [GitHub](https://github.com/stevechy/scala-serverless-test).

<style type="text/css">
pre { background-color: #2b2b2b;}
.s0 { color: #cc7832;}
.s1 { color: #a9b7c6;}
.s2 { color: #6a8759;}
.s3 { color: #00b8bb; font-weight: bold;}
.s4 { color: #6897bb;}
</style>

# Building the Javascript bundle

I wanted this project to have shared logic and UI component code between the frontend and backend.
I like server side rendering and that was also something that I wanted support for. 

To setup the sbt project I ended up with 3 subprojects:
- `commonui` for the shared frontend and backend code
- `backend` for the backend code and AWS lambda package
- `frontend` for the frontend code and browser JavaScript bundle

The [sbt-crossproject](https://github.com/portable-scala/sbt-crossproject) created the commonui directory structure which saved me some time figuring out how to set it up.
It took me a while to figure out that a cross-project contains sbt projects but the cross-project root is not an sbt project itself but it also makes a lot of sense once you use it.

<pre class="s0">
- commonui/
  - shared/
    - src/main/scala/
  - js/
  - jvm/
  - build.sbt
- backend/
  - src/main/scala/
  - build.sbt
- frontend/
  - src/main/scala/
  - build.sbt
  - package.json
- build.sbt
</pre>

As an sbt novice I probably made a few mistakes but the main points should be the same even if they're hidden by a plugin:
- Compile the Scala code to Javascript with Scala.js
- Import the Scala.js Javascript to the `frontend` project
- Use a web bundler [Parcel.js](https://parceljs.org/) to create browser JavaScript bundle and include any npm libraries required

# Compiling Scala to Javascript

Compiling Scala.js projects is straightforward once you have the directory structure setup up properly.
The `commonui` project is setup as a cross project so it can output JVM and JS code.

First the plugins have to be added to `project/plugins.sbt`.

<pre class="s0">
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
</pre>

It's tempting to put the code that uses ScalaJS dependencies in the `commonui/js` subproject but for now I think it's cleaner to restrict it to just the shared code.

<pre>
<span class="s0">lazy val </span><span class="s1">root = project.in(file(</span><span class="s2">&quot;.&quot;</span><span class="s1">))</span>
<span class="s0">lazy val </span><span class="s1">commonui = (crossProject(JSPlatform</span><span class="s0">, </span><span class="s1">JVMPlatform) in file(</span><span class="s2">&quot;commonui&quot;</span><span class="s1">))</span>
  <span class="s1">.settings(libraryDependencies ++= Seq(</span>
    <span class="s2">&quot;dev.optics&quot; </span><span class="s1">%%% </span><span class="s2">&quot;monocle-core&quot; </span><span class="s0">,</span>
    <span class="s2">&quot;dev.optics&quot; </span><span class="s1">%%% </span><span class="s2">&quot;monocle-macro&quot;</span>
  <span class="s1">).map(_ % monocleVersion)   )</span>
</pre>

The `frontend` project is pure ScalaJS which allows it to import ScalaJS-only dependencies.
Using `dependsOn` adds the `commonui` library to the `frontend` project

<pre>
<span class="s0">lazy val </span><span class="s1">commonuiJS = commonui.js</span>
<span class="s0">lazy val </span><span class="s1">commonuiJVM = commonui.jvm</span>
<span class="s0">lazy val </span><span class="s1">circeDependencies = Seq(libraryDependencies ++= Seq(</span>
  <span class="s2">&quot;io.circe&quot; </span><span class="s1">%%% </span><span class="s2">&quot;circe-core&quot;</span><span class="s0">,</span>
  <span class="s2">&quot;io.circe&quot; </span><span class="s1">%%% </span><span class="s2">&quot;circe-generic&quot;</span><span class="s0">,</span>
  <span class="s2">&quot;io.circe&quot; </span><span class="s1">%%% </span><span class="s2">&quot;circe-parser&quot;</span>
<span class="s1">).map(_ % circeVersion))</span>
<span class="s0">lazy val </span><span class="s1">frontend = (project in file(</span><span class="s2">&quot;frontend&quot;</span><span class="s1">))</span>
  <span class="s1">.enablePlugins(ScalaJSPlugin)</span>
  <span class="s1">.dependsOn(commonuiJS)</span>
  <span class="s1">.settings(</span>
      <span class="s1">Compile / mainClass   := Some(</span><span class="s2">&quot;Main&quot;</span><span class="s1">)</span><span class="s0">,</span>
    <span class="s1">scalaJSUseMainModuleInitializer := </span><span class="s0">true,</span>
    <span class="s1">libraryDependencies ++= Seq(</span>
      <span class="s2">&quot;io.github.buntec&quot; </span><span class="s1">%%% </span><span class="s2">&quot;scala-js-snabbdom&quot; </span><span class="s1">% </span><span class="s2">&quot;0.1.0&quot;</span><span class="s0">,</span>
      <span class="s2">&quot;org.scala-js&quot; </span><span class="s1">%%% </span><span class="s2">&quot;scalajs-dom&quot; </span><span class="s1">% </span><span class="s2">&quot;2.4.0&quot;</span>
    <span class="s1">)</span><span class="s0">,</span>
    <span class="s1">circeDependencies)</span>
</pre>

With this setup the JS can be compiled with `sbt frontend/fastLinkJS` for the development version or `sbt frontend/fullLinkJS` for a minimized production version.
The [Scala.js build documentation](https://www.scala-js.org/doc/project/building.html) has more detailed information about these targets.

This produces JS code but it has to be bundled for the browser.

# I heard you liked build systems, so I connected a build system to your build system

The general problem of connecting JS bundlers to non-JS build systems is a leaky abstraction jungle for me.
Even when the bundler is just webpack it can be tough to map a pure webpack configuration to the equivalent abstraction in the build system.
At the same time, it's boring glue code that shouldn't be repeated everywhere.

I wanted an easy off-ramp if I wanted to change bundlers so I connected the bundler manually. 
The [Scala.js Vite sbt plugin](https://www.scala-lang.org/blog/2023/04/18/faster-scalajs-development-with-frontend-tooling.html) might be a better option in general though.

Running `sbt frontend/fastLinkJS` produces `frontend/target/scala-3.2.1/frontend-fastopt` and `sbt frontend/fastLinkJS` produces `frontend/target/scala-3.2.1/frontend-opt`.

<pre class="s0">
- frontend/
  - target/
    - scala-3.2.1/
      - frontend-fastopt/
        - main.js
        - main.js.map
      - frontend-opt/
        - main.js
        - main.js.map
</pre>

Looking at the [Parcel.js build docs](https://parceljs.org/getting-started/webapp/) the HTML entry point seemed to be the easiest way to plug in the source.
With the HTML entry point a content hash is generated and added to the bundle filename which makes deployment easier and is useful for having multiple versions live at the same time.

I added a hardcoded `frontend/src/main/html/index.html` to do this but it should probably be dynamically generated to support both `fastLinkJS` and `fullLinkJS`.
As a bonus this means I can test the frontend code from a browser with using the parcel dev server.

<pre><span class="s0">&lt;html&gt;</span>
<span class="s0">&lt;script </span><span class="s2">type</span><span class="s3">=&quot;module&quot; </span><span class="s2">src</span><span class="s3">=&quot;../../../target/scala-3.2.1/frontend-opt/main.js&quot;</span><span class="s0">&gt;&lt;/script&gt;</span>
<span class="s0">&lt;link </span><span class="s2">rel</span><span class="s3">=&quot;stylesheet&quot; </span><span class="s2">href</span><span class="s3">=&quot;https://unpkg.com/chota@latest&quot;</span><span class="s0">&gt;</span>
<span class="s0">&lt;body&gt;</span>
<span class="s1">Parcel bootstrap</span>
<span class="s0">&lt;div </span><span class="s2">id</span><span class="s3">=&quot;snabbdom-container&quot;</span><span class="s0">&gt;</span>

<span class="s0">&lt;/div&gt;</span>
<span class="s0">&lt;/body&gt;</span>
<span class="s0">&lt;/html&gt;</span></pre>

Parcel.js is installed by running `npm install` in the `frontend` directory.
It mostly works without configuration but adding the `source` configuration to `package.json` tells it where to find the HTML entry point without a command line argument.

<pre><span class="s0">{</span>
  <span class="s1">&quot;name&quot;</span><span class="s2">: </span><span class="s1">&quot;cardzfrontend&quot;</span><span class="s2">,</span>
  <span class="s1">&quot;version&quot;</span><span class="s2">: </span><span class="s1">&quot;1.0.0&quot;</span><span class="s2">,</span>
  <span class="s1">&quot;source&quot;</span><span class="s2">: </span><span class="s1">&quot;src/main/html/index.html&quot;</span><span class="s2">,</span>
  <span class="s1">&quot;devDependencies&quot;</span><span class="s2">: </span><span class="s0">{</span>
    <span class="s1">&quot;parcel&quot;</span><span class="s2">: </span><span class="s1">&quot;^2.8.3&quot;</span><span class="s2">,</span>
    <span class="s1">&quot;parcel-reporter-clean-dist&quot;</span><span class="s2">: </span><span class="s1">&quot;^1.0.4&quot;</span>
  <span class="s0">}</span>
<span class="s0">}</span>
</pre>

To run Parcel from sbt I added a `Global` target to the root project.

<pre>
<span class="s1">Global / parcelJavascriptFiles := {</span>
  <span class="s1">(frontend / Compile / fullLinkJS).value</span>
  <span class="s1">Process(</span><span class="s2">&quot;npx&quot; </span><span class="s1">:: </span><span class="s2">&quot;parcel&quot; </span><span class="s1">:: </span><span class="s2">&quot;build&quot; </span><span class="s1">:: Nil</span><span class="s0">, </span><span class="s1">file(</span><span class="s2">&quot;frontend&quot;</span><span class="s1">)) ! streams.value.log</span>
<span class="s1">}</span>
</pre>

Parcel.js will produce a bundle in `frontend/dist`.

<pre class="s0">
- frontend/
  - dist/
    - index.e1b1309e.js
    - index.html
</pre>

Adding a `.parcelrc` and `parcel-reporter-clean-dist` will clean out old versions.

<pre><span class="s0">{</span>
   <span class="s0">&quot;extends&quot;: [&quot;@parcel/config-default&quot;],</span>
   <span class="s0">&quot;reporters&quot;: [&quot;...&quot;, &quot;parcel-reporter-clean-dist&quot;]</span>
<span class="s0">}</span>
</pre>

I copied over the bundle filename with the hash over to the backend so that it can be included in the backend html.
The bundle itself will be served from S3.

<pre>
<span class="s1">Global / frontendJavascriptFiles := {</span>
  <span class="s1">parcelJavascriptFiles.value</span>
  <span class="s0">val </span><span class="s1">javascriptFiles = FileTreeView.default.list(Glob(Paths.get(</span><span class="s2">&quot;frontend&quot;</span><span class="s1">).toAbsolutePath) / </span><span class="s2">&quot;dist&quot; </span><span class="s1">/ </span><span class="s2">&quot;index*.js&quot;</span><span class="s1">)</span>
  <span class="s0">val </span><span class="s1">filenames = javascriptFiles.collectFirst {</span>
    <span class="s0">case </span><span class="s1">(path</span><span class="s0">, </span><span class="s1">attributes) =&gt; path.toAbsolutePath.getFileName.toString</span>
  <span class="s1">}</span>
  <span class="s1">filenames</span>
<span class="s1">}</span>
</pre>

Adding this as a `Global` target makes it easier to use the target in the `backend` project but maybe there's a better way?

<pre>
<span class="s0">lazy val </span><span class="s1">backend = (project in file(</span><span class="s2">&quot;backend&quot;</span><span class="s1">))</span>
  <span class="s1">.dependsOn(commonuiJVM)</span>
  <span class="s1">.enablePlugins(JavaAppPackaging)</span>
  <span class="s1">.settings(</span>
    <span class="s1">topLevelDirectory := None</span><span class="s0">,</span>
    <span class="s1">libraryDependencies ++= Seq(</span>
      <span class="s2">&quot;com.lihaoyi&quot; </span><span class="s1">%%% </span><span class="s2">&quot;scalatags&quot; </span><span class="s1">% </span><span class="s2">&quot;0.12.0&quot;</span><span class="s0">,</span>
      <span class="s2">&quot;com.amazonaws&quot; </span><span class="s1">% </span><span class="s2">&quot;aws-lambda-java-core&quot; </span><span class="s1">% </span><span class="s2">&quot;1.2.2&quot;</span><span class="s0">,</span>
      <span class="s2">&quot;com.amazonaws&quot; </span><span class="s1">% </span><span class="s2">&quot;aws-lambda-java-events&quot; </span><span class="s1">% </span><span class="s2">&quot;3.11.1&quot;</span><span class="s0">,</span>
      <span class="s2">&quot;io.github.crac&quot; </span><span class="s1">%  </span><span class="s2">&quot;org-crac&quot; </span><span class="s1">%  </span><span class="s2">&quot;0.1.3&quot;</span>
    <span class="s1">)</span><span class="s0">,</span>
    <span class="s1">circeDependencies</span><span class="s0">,</span>
    <span class="s1">Compile / resourceGenerators += Def.task {</span>
      <span class="s0">val </span><span class="s1">file = (Compile / resourceManaged).value / </span><span class="s2">&quot;frontend&quot; </span><span class="s1">/ </span><span class="s2">&quot;frontend.properties&quot;</span>
      <span class="s0">val </span><span class="s1">filename = (Global / frontendJavascriptFiles).value.head</span>
      <span class="s0">val </span><span class="s1">contents = </span><span class="s2">s&quot;frontend.javascript.entrypoint=</span><span class="s3">$</span><span class="s1">filename</span><span class="s2">&quot;</span>
      <span class="s1">IO.write(file</span><span class="s0">, </span><span class="s1">contents)</span>
      <span class="s1">Seq(file)</span>
    <span class="s1">}.taskValue</span>
  <span class="s1">)</span>
</pre>

This produces a `frontend.properties` file that we will read in the `backend` project.

<pre class="s0">
frontend.javascript.entrypoint=index.e1b1309e.js
</pre>

# AWS Lambda backend

We can now serve a basic index page and reference the generated bundle (deployment will come later).
For some basic CSS styling, I included the [chota](https://jenil.github.io/chota/) CSS framework.

The lambda handler class just has to implement the `RequestHandler` interface.
The class name will added to the deployment configuration later on.

<pre>
<span class="s0">import </span><span class="s1">com.amazonaws.services.lambda.runtime.Context</span>
<span class="s0">import </span><span class="s1">com.amazonaws.services.lambda.runtime.RequestHandler</span>
<span class="s0">import </span><span class="s1">com.amazonaws.services.lambda.runtime.LambdaLogger</span>
<span class="s0">import </span><span class="s1">com.amazonaws.services.lambda.runtime.events</span>
<span class="s0">import </span><span class="s1">com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent</span><span class="s0">, </span><span class="s1">APIGatewayProxyResponseEvent}</span>
<span class="s0">import </span><span class="s1">com.slopezerosolutions.scalatags.ScalaTagsBuilder</span>
<span class="s0">import </span><span class="s1">scalatags.Text.all.*</span>

<span class="s0">import </span><span class="s1">scala.jdk.CollectionConverters.MapHasAsJava</span>
<span class="s0">import </span><span class="s1">scala.io.Source</span>
<span class="s0">import </span><span class="s1">org.crac.Resource</span>
<span class="s0">import </span><span class="s1">org.crac.Core</span>
<span class="s0">import </span><span class="s1">scalatags.Text</span>

<span class="s0">class </span><span class="s1">LambdaHandler </span><span class="s0">extends </span><span class="s1">RequestHandler[APIGatewayProxyRequestEvent</span><span class="s0">, </span><span class="s1">APIGatewayProxyResponseEvent]</span><span class="s0">, </span><span class="s1">Resource {</span>

  <span class="s0">private val </span><span class="s1">frontendProperties = </span><span class="s0">new </span><span class="s1">FrontendProperties()</span>

  <span class="s0">override def </span><span class="s1">handleRequest(input: APIGatewayProxyRequestEvent</span><span class="s0">, </span><span class="s1">context: Context): APIGatewayProxyResponseEvent = {</span>
    <span class="s1">gamePage</span>
  <span class="s1">}</span>

  <span class="s0">private def </span><span class="s1">gamePage = {</span>
    <span class="s0">val </span><span class="s1">htmlOutput = html(</span>
      <span class="s1">head(</span>
        <span class="s1">script(raw(</span><span class="s2">s&quot;&quot;&quot;var apiBaseUrl=&quot;</span><span class="s3">$</span><span class="s1">{frontendProperties.apiBaseUrl}</span><span class="s2">&quot;;&quot;&quot;&quot;</span><span class="s1">))</span><span class="s0">,</span>
        <span class="s1">script(attr(</span><span class="s2">&quot;type&quot;</span><span class="s1">) := </span><span class="s2">&quot;module&quot;</span><span class="s0">, </span><span class="s1">src := entryPointUrl)</span><span class="s0">,</span>
        <span class="s1">link(rel := </span><span class="s2">&quot;stylesheet&quot;</span><span class="s0">, </span><span class="s1">href := </span><span class="s2">&quot;https://unpkg.com/chota@latest&quot;</span><span class="s1">)</span>
      <span class="s1">)</span><span class="s0">,</span>
      <span class="s1">body()</span>
    <span class="s1">).toString</span>
    <span class="s0">val </span><span class="s1">event = </span><span class="s0">new </span><span class="s1">APIGatewayProxyResponseEvent()</span>
      <span class="s1">.withStatusCode(</span><span class="s4">200</span><span class="s1">)</span>
      <span class="s1">.withHeaders(Map(</span><span class="s2">&quot;content-type&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;text/html&quot;</span><span class="s1">).asJava)</span>
      <span class="s1">.withBody(htmlOutput)</span>
    <span class="s1">event</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

For this basic page only a few dependencies are required.

<pre>
    <span class="s1">libraryDependencies ++= Seq(</span>
      <span class="s2">&quot;com.lihaoyi&quot; </span><span class="s1">%%% </span><span class="s2">&quot;scalatags&quot; </span><span class="s1">% </span><span class="s2">&quot;0.12.0&quot;</span><span class="s0">,</span>
      <span class="s2">&quot;com.amazonaws&quot; </span><span class="s1">% </span><span class="s2">&quot;aws-lambda-java-core&quot; </span><span class="s1">% </span><span class="s2">&quot;1.2.2&quot;</span><span class="s0">,</span>
      <span class="s2">&quot;com.amazonaws&quot; </span><span class="s1">% </span><span class="s2">&quot;aws-lambda-java-events&quot; </span><span class="s1">% </span><span class="s2">&quot;3.11.1&quot;</span><span class="s0">,</span>
      <span class="s2">&quot;io.github.crac&quot; </span><span class="s1">%  </span><span class="s2">&quot;org-crac&quot; </span><span class="s1">%  </span><span class="s2">&quot;0.1.3&quot;</span>
    <span class="s1">)</span>
</pre>

To package up everything, I'm using the [sbt-native-packager](https://sbt-native-packager.readthedocs.io/en/stable/) plugin and its `universal` target.
This will produce a zipped directory of jar files.
Some articles online recommend a deployment that merges all the jar files fatJar style but this approach worked well and it's nice to see all the dependencies packaged separately in the directory.

The resulting `project/plugins.sbt` looks like this.

<pre class="s0">
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.4")
</pre>

Because I now need to plug the sbt build system results into the Serverless framework build/deploy system, I added another target to output the artifact path to a json file.

<pre>
<span class="s0">lazy val </span><span class="s1">lambdaPackage = taskKey[Unit](</span><span class="s2">&quot;Lambda package&quot;</span><span class="s1">)</span>
<span class="s1">lambdaPackage := {</span>
  <span class="s0">val </span><span class="s1">packageZip = (backend / Universal / packageBin).value</span>
  <span class="s0">val </span><span class="s1">file = backend.base / </span><span class="s2">&quot;target&quot; </span><span class="s1">/ </span><span class="s2">&quot;universal&quot; </span><span class="s1">/ </span><span class="s2">&quot;lambda.json&quot;</span>
  <span class="s0">val </span><span class="s1">basePath = root.base.getAbsoluteFile</span>
  <span class="s0">val </span><span class="s1">zipFile = basePath.relativize(packageZip).get.toString</span>
  <span class="s0">val </span><span class="s1">contents = </span><span class="s2">s&quot;&quot;&quot;{&quot;artifact&quot;:&quot;</span><span class="s3">$</span><span class="s1">{zipFile}</span><span class="s2">&quot;}&quot;&quot;&quot;</span>
  <span class="s1">IO.write(file</span><span class="s0">, </span><span class="s1">contents)</span>
  <span class="s1">()</span>
<span class="s1">}</span>
</pre>

This writes to `backend/target/universal/lambda.json`

<pre class="s0">
{"artifact":"backend/target/universal/backend-0.1.0-SNAPSHOT.zip"}
</pre>

If you want to see how this gets plugged in to the deploy process, skip ahead.
For a change of pace, I'll move on to client and server side rendering.

# Client and server side rendering

ScalaTags is an awesome server side rendering library, and scala-js-snabbdom is an awesome client side virtual dom library.
Even for a side project, server side rendering has enough benefits that I would use another language to get it.

Fortunately projects like Tyrian have server side rendering support.
But when I took a look at the APIs for ScalaTags and scala-js-snabbdom it looked like an interesting exercise to link them together by hand.
It looks like a similar approach could work with [Slinky](https://slinky.dev/), but it might require a separate class for backend components.

The APIs are fairly similar, the main difference is the way dom attributes and event handlers are handled.
In scala-js-snabbdom, event handlers receive `org.scalajs.dom.Event`s but the backend doesn't need to know about these events.
ScalaTags can render inline event handlers but I just need the plain DOM in the initial render, scala-js-snabbdom can handle the events when it loads.

So I just need a common interface that outputs DOM elements, and a different implementation for ScalaTags and scala-js-snabbdom.
This basically boils down to something similar to a Visitor.
I also found out that the [Tagless final](https://blog.rockthejvm.com/tagless-final/) pattern is similar with better typechecking properties.

This case is just DOM elements all the way down so only one return type is needed but Scala has some nice syntax for this type of use case.

For the interface I just added a method for each tag that I needed, with some overloads for convenience.

<pre>
<span class="s0">package </span><span class="s1">com.slopezerosolutions.dombuilder</span>

<span class="s0">trait </span><span class="s1">DomBuilder[T] {</span>

  <span class="s0">def </span><span class="s1">div(domAttributes: DomAttributes</span><span class="s0">, </span><span class="s1">contents: String | List[T]): T</span>

  <span class="s0">def </span><span class="s1">div(contents: String | List[T] ): T = {</span>
    <span class="s1">div(DomAttributes.empty</span><span class="s0">, </span><span class="s1">contents)</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">button(domAttributes: DomAttributes</span><span class="s0">, </span><span class="s1">contents: String | List[T]): T</span>

  <span class="s0">def </span><span class="s1">input(domAttributes: DomAttributes): T</span>
<span class="s1">}</span>
</pre>

scala-js-snabbdom has a finer grained separation between different types of DOM attributes, so it was easier to use a similar level of separation in the interface layer.

<pre>
<span class="s0">object </span><span class="s1">DomAttributes {</span>
  <span class="s0">val </span><span class="s1">empty = DomAttributes()</span>
<span class="s1">}</span>
<span class="s0">case class </span><span class="s1">DomAttributes(props: Map[String</span><span class="s0">,</span><span class="s1">String] = Map()</span><span class="s0">,</span>
                         <span class="s1">attributes: Map[String</span><span class="s0">,</span><span class="s1">String] = Map()</span><span class="s0">,</span>
                         <span class="s1">handlers: Map[String</span><span class="s0">, </span><span class="s1">Any =&gt; Unit] = Map())</span>
</pre>

Implementing the interface is straightforward so I'm only including the ScalaTags implementation.
For the ScalaTags implementation the event handlers are not rendered.

<pre>
<span class="s0">class </span><span class="s1">ScalaTagsBuilder </span><span class="s0">extends </span><span class="s1">DomBuilder[ConcreteHtmlTag[String]] {</span>

  <span class="s0">def </span><span class="s1">div(domAttributes: DomAttributes</span><span class="s0">, </span><span class="s1">contents: String | List[ConcreteHtmlTag[String]]): ConcreteHtmlTag[String] = {</span>
    <span class="s1">contents </span><span class="s0">match </span><span class="s1">{</span>
      <span class="s0">case </span><span class="s1">text: String =&gt; scalatags.Text.all.div(modifiers(domAttributes): _*)(text)</span>
      <span class="s0">case </span><span class="s1">list: List[ConcreteHtmlTag[String]] =&gt; scalatags.Text.all.div(modifiers(domAttributes): _*)(list)</span>
    <span class="s1">}</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">button(domAttributes: DomAttributes</span><span class="s0">, </span><span class="s1">contents: String | List[ConcreteHtmlTag[String]]): ConcreteHtmlTag[String] = {</span>
    <span class="s1">contents </span><span class="s0">match </span><span class="s1">{</span>
      <span class="s0">case </span><span class="s1">text: String =&gt; scalatags.Text.all.button(modifiers(domAttributes): _*)(text)</span>
      <span class="s0">case </span><span class="s1">list: List[ConcreteHtmlTag[String]] =&gt; scalatags.Text.all.button(modifiers(domAttributes): _*)(list)</span>
    <span class="s1">}</span>
  <span class="s1">}</span>

  <span class="s0">override def </span><span class="s1">input(domAttributes: DomAttributes): ConcreteHtmlTag[String] = {</span>
    <span class="s1">scalatags.Text.all.input(modifiers(domAttributes): _*)</span>
  <span class="s1">}</span>

  <span class="s0">private def </span><span class="s1">modifiers(domAttributes: DomAttributes): Array[Modifier] = {</span>
    <span class="s0">val </span><span class="s1">modifiers = ArrayBuffer[Modifier]()</span>
    <span class="s0">for</span><span class="s1">((key</span><span class="s0">,</span><span class="s1">value) &lt;- domAttributes.props) {</span>
      <span class="s1">modifiers += (attr(key) := value)</span>
    <span class="s1">}</span>
    <span class="s0">for </span><span class="s1">((key</span><span class="s0">, </span><span class="s1">value) &lt;- domAttributes.attributes) {</span>
      <span class="s1">modifiers += (attr(key) := value)</span>
    <span class="s1">}</span>
    <span class="s1">modifiers.toArray</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

In Java the implementation would be similar but using this interface would be verbose.
But using Scala 3's [given and using clauses](https://blog.rockthejvm.com/scala-3-given-using/) and
the import trick from [Tagless final](https://blog.rockthejvm.com/tagless-final/) the abstraction is mostly invisible.

`given` and `using` pass the implementation through the call stack and `import` removes the references to the builder implementation when calling the interface methods.

<pre>
<span class="s0">object </span><span class="s1">EnemyComponent {</span>
  <span class="s0">def </span><span class="s1">mainView[T](enemy: Enemy</span><span class="s0">, </span><span class="s1">children: T*)(using domBuilder: DomBuilder[T]): T = {</span>
    <span class="s0">import </span><span class="s1">domBuilder.*</span>

    <span class="s1">div(DomAttributes(attributes = Map(</span><span class="s2">&quot;class&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;card&quot;</span><span class="s1">))</span><span class="s0">,</span>
      <span class="s1">List(</span>
        <span class="s1">div(</span><span class="s2">s&quot;Enemy </span><span class="s3">$</span><span class="s1">{enemy.id}</span><span class="s2">&quot;</span><span class="s1">)</span><span class="s0">,</span>
        <span class="s1">div(</span><span class="s2">s&quot;Health: </span><span class="s3">$</span><span class="s1">{enemy.health}</span><span class="s2">&quot;</span><span class="s1">)</span><span class="s0">,</span>
        <span class="s1">div(children.toList)</span>
      <span class="s1">))</span>
  <span class="s1">}</span>
<span class="s1">}</span>

<span class="s0">case class </span><span class="s1">Enemy(id: String</span><span class="s0">, </span><span class="s1">health: Int)</span>
</pre>

When `EnemyComponent.mainView` is called and a `given` `DomBuilder` is in scope, it will automatically be passed in.  
In the backend we use `given domBuilder: DomBuilder[Text.TypedTag[String]] = new ScalaTagsBuilder()`.  
In the frontend we can use `given domBuilder: DomBuilder[VNode] = new SnabDomBuilder()`.

Now we have a way to render the same component code on the server side and the client side, we just need a way to handle frontend state and events.

# Virtual DOM and contexts

Virtual DOM libraries are my preferred way to build frontends but a lot of frameworks optimize for the global context.
This makes sense for a lot of applications but I've also run into cases where local contexts are useful.
In React this ends up in [useState](https://react.dev/reference/react/useState) calls but with Scala it looked like there should be a more functional way to do it.

I started with a basic Redux like store, it stores a mutable reference to one object that should be immutable.
After the mutable reference is updated it is published to all subscribers.

<pre>
<span class="s0">package </span><span class="s1">com.slopezerosolutions.dombuilder</span>

<span class="s0">import </span><span class="s1">monocle.macros.GenLens</span>
<span class="s0">import </span><span class="s1">monocle.Iso</span>

<span class="s0">class </span><span class="s1">RootViewContext[S](</span><span class="s0">var </span><span class="s1">rootContext: S) {</span>
  <span class="s0">private var </span><span class="s1">subscribers: Vector[(RootViewContext[S])=&gt;Unit] = Vector()</span>
  <span class="s0">def </span><span class="s1">updateContext(update: S =&gt; S): Unit = {</span>
    <span class="s1">rootContext = update(rootContext)</span>
    <span class="s1">publish()</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">publish(): Unit = {</span>
    <span class="s0">for </span><span class="s1">(subscriber &lt;- subscribers) {</span>
      <span class="s1">subscriber.apply(</span><span class="s0">this</span><span class="s1">)</span>
    <span class="s1">}</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">viewContext: ViewContext[S</span><span class="s0">, </span><span class="s1">S] = {</span>
    <span class="s1">ViewContext(global = rootContext</span><span class="s0">,</span>
      <span class="s1">local = rootContext</span><span class="s0">,</span>
      <span class="s1">updateGlobal = updateContext</span><span class="s0">,</span>
      <span class="s1">updateLocal = updateContext)</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">subscribe(subscriber: RootViewContext[S] =&gt; Unit): Unit = {</span>
   <span class="s1">subscribers = subscribers :+ subscriber</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

After that I added a way to "zoom" into a section of the store using a `Lens` or a `Prism`.
The `Prism` zooms in to a field that could store different subclass instances (or any type where the instance has multiple cases).
This is useful for supporting a section of the page that could change to multiple different views.

I have a player name entry section that changes into a game view section after the player's name is entered.
If the ui switches from the player name section to the game view section and then sends an update to the player name section right after the `Prism` will safely ignore that update.
Since the `Prism` handles the case where the subclass instance isn't there anymore, the instance is passed in when zooming in to avoid introducing `Option`.

<pre>
<span class="s0">case class </span><span class="s1">ViewContext[R</span><span class="s0">, </span><span class="s1">C](global: R</span><span class="s0">,</span>
                             <span class="s1">local: C</span><span class="s0">,</span>
                             <span class="s1">updateGlobal: (R =&gt; R) =&gt; Unit</span><span class="s0">,</span>
                             <span class="s1">updateLocal: (C =&gt; C) =&gt; Unit) {</span>
  <span class="s0">def </span><span class="s1">zoomInto[Z](zoom: Lens[C</span><span class="s0">, </span><span class="s1">Z]): ViewContext[R</span><span class="s0">, </span><span class="s1">Z] = {</span>
    <span class="s0">val </span><span class="s1">zoomedContext = zoom.get(local)</span>
    <span class="s0">def </span><span class="s1">childUpdater(updateChild: (Z =&gt; Z)): Unit = {</span>
      <span class="s1">updateLocal(zoom.modify(updateChild))</span>
    <span class="s1">}</span>
    <span class="s1">copy[R</span><span class="s0">,</span><span class="s1">Z](</span>
      <span class="s1">local =  zoomedContext</span><span class="s0">,</span>
      <span class="s1">updateLocal = childUpdater</span>
    <span class="s1">)</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">zoomOptional[Z](zoomOptional: Optional[C</span><span class="s0">,</span><span class="s1">Z]</span><span class="s0">, </span><span class="s1">zoomedLocal: Z):  ViewContext[R</span><span class="s0">, </span><span class="s1">Z] = {</span>
    <span class="s0">def </span><span class="s1">optionalChildUpdater(updateChild: (Z =&gt; Z)): Unit = {</span>
      <span class="s1">updateLocal(zoomOptional.modify(updateChild))</span>
    <span class="s1">}</span>
    <span class="s1">copy[R</span><span class="s0">, </span><span class="s1">Z](</span>
      <span class="s1">local = zoomedLocal</span><span class="s0">,</span>
      <span class="s1">updateLocal = optionalChildUpdater</span>
    <span class="s1">)</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">update(updater: C =&gt; C): Unit = {</span>
    <span class="s1">updateLocal(updater)</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

It turns out this was enough to implement the event handling that I needed.

# What is your name?

At the start of the game the player enters their name.

![Player name page]({{site.baseurl}}/assets/2023-04-20_client_playername.png)

To implement this I created a global context that has a slot for the currently active page "section".

<pre>
<span class="s0">object </span><span class="s1">AppContext {</span>
  <span class="s0">val </span><span class="s1">viewContext: Lens[AppContext</span><span class="s0">, </span><span class="s1">AppSection.Context] = GenLens[AppContext](_.viewContext)</span>
  <span class="s0">val </span><span class="s1">playerName: Lens[AppContext</span><span class="s0">, </span><span class="s1">Option[String]] = GenLens[AppContext](_.playerName)</span>
<span class="s1">}</span>
<span class="s0">case class </span><span class="s1">AppContext(eventAdapter: EventAdapter</span><span class="s0">,</span>
                      <span class="s1">viewContext: AppSection.Context</span><span class="s0">,</span>
                      <span class="s1">uuidGenerator: () =&gt; String</span><span class="s0">,</span>
                      <span class="s1">playerName: Option[String] = None</span><span class="s0">,</span>
                      <span class="s1">gameServiceOption: Option[GameService] = None)</span>


</pre>

The `AppSection` is an empty base class that handles dispatching rendering to subclasses.
It's a primitive version of a frontend router.
When it renders a section, it also "zooms in" to that section's context.

<pre>
<span class="s0">object </span><span class="s1">AppSection {</span>
  <span class="s0">abstract class </span><span class="s1">Context</span>
  <span class="s0">val </span><span class="s1">defaultContext = </span><span class="s0">new </span><span class="s1">Context{}</span>

  <span class="s0">def </span><span class="s1">mainView[T](context: ViewContext[AppContext</span><span class="s0">, </span><span class="s1">AppContext])(using domBuilder: DomBuilder[T]): T = {</span>
    <span class="s0">import </span><span class="s1">domBuilder._</span>
    <span class="s0">val </span><span class="s1">local = context.local</span>
    <span class="s1">local.viewContext </span><span class="s0">match </span><span class="s1">{</span>
      <span class="s0">case </span><span class="s1">sectionContext: GameView.Context =&gt; {</span>
        <span class="s1">GameView.mainView(</span>
          <span class="s1">context.zoomOptional(AppContext.viewContext.andThen(GameView.gameContext)</span><span class="s0">,</span>
            <span class="s1">sectionContext)</span>
        <span class="s1">)</span>
      <span class="s1">}</span>
      <span class="s0">case </span><span class="s1">sectionContext: NameEntry.Context =&gt; {</span>
        <span class="s1">NameEntry.mainView(context.zoomOptional(AppContext.viewContext.andThen(NameEntry.context)</span><span class="s0">,</span>
          <span class="s1">sectionContext))</span>
      <span class="s1">}</span>
      <span class="s0">case </span><span class="s1">_ =&gt; div(DomAttributes(props = Map(</span><span class="s2">&quot;data-error&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;Unhandled ui context&quot;</span><span class="s1">))</span><span class="s0">, </span><span class="s1">List())</span>
    <span class="s1">}</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

The name entry section stores the state of the text input in the context.
When the `Enter name` button is clicked, it takes the player's name and stores it in the global context.

`context.updateLocal(playerName.modify(_ => input))` updates the player's name in the local context .
`context.updateGlobal(AppContext.playerName.modify(_ =>; Some(local.playerName)))` updates the player's name in the global context so it can be used by other parts of the app.

Both methods end up updating the same mutable reference in the `RootViewContext`, but hiding the `Lens` calls behind `updateLocal` is useful.

Because `playerName` is a Monocle `Lens`, `.modify` builds a new function that performs the immutable update.
[Introduction to Optics](https://www.baeldung.com/scala/monocle-optics) is a good introduction to the Monocle Lens library.

Each update call will update the root context and publish the updated state to any subscribers.
After the player enters their name and that change is published, `context.updateGlobal(AppContext.viewContext.modify(_ => GameView.createNewGame(global)))` switches the section to the `GameView` section.

<pre>
<span class="s0">object </span><span class="s1">NameEntry {</span>
  <span class="s0">case class </span><span class="s1">Context(playerName: String = </span><span class="s2">&quot;&quot;</span><span class="s1">) </span><span class="s0">extends </span><span class="s1">AppSection.Context</span>

  <span class="s0">val </span><span class="s1">context = Prism.partial[AppSection.Context</span><span class="s0">, </span><span class="s1">Context] { </span><span class="s0">case </span><span class="s1">c: Context =&gt; c }(identity)</span>
  <span class="s0">val </span><span class="s1">playerName: Lens[Context</span><span class="s0">, </span><span class="s1">String] = GenLens[Context](_.playerName)</span>

  <span class="s0">def </span><span class="s1">mainView[T](context: ViewContext[AppContext</span><span class="s0">, </span><span class="s1">Context])(using domBuilder: DomBuilder[T]): T = {</span>
    <span class="s0">import </span><span class="s1">domBuilder._</span>
    <span class="s0">val </span><span class="s1">local = context.local</span>
    <span class="s0">val </span><span class="s1">global = context.global</span>
    <span class="s1">div(List(</span>
      <span class="s1">div(List(input(DomAttributes(props = Map(</span><span class="s2">&quot;name&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;playerName&quot;</span><span class="s0">, </span><span class="s2">&quot;value&quot; </span><span class="s1">-&gt; local.playerName)</span><span class="s0">,</span>
        <span class="s1">handlers = Map(</span><span class="s2">&quot;change&quot; </span><span class="s1">-&gt; global.eventAdapter.textInputAdapter((input) =&gt; {</span>
          <span class="s1">context.updateLocal(playerName.modify(_ =&gt; input))</span>
        <span class="s1">}))</span>
      <span class="s1">))))</span><span class="s0">,</span>
      <span class="s1">div(List(</span>
        <span class="s1">button(DomAttributes(handlers = Map(</span><span class="s2">&quot;click&quot; </span><span class="s1">-&gt; global.eventAdapter.clickAdapter(() =&gt; {</span>
          <span class="s1">context.updateGlobal(AppContext.playerName.modify(_ =&gt; Some(local.playerName)))</span>
          <span class="s1">context.updateGlobal(AppContext.viewContext.modify(_ =&gt; GameView.createNewGame(global)))</span>
        <span class="s1">})))</span><span class="s0">,</span>
          <span class="s2">&quot;Enter name&quot;</span><span class="s1">)))</span>
    <span class="s1">))</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

There's one other detail here for server-side-rendering.
I want to hide the `org.scalajs.dom.Event` type from the backend code.
To do that I made the DOM event handlers `Any => Unit` so they can accept any type.

The problem is that I now need a place to handle `org.scalajs.dom.Event` on the frontend.
To do this I added an `EventAdapter` interface that has a do-nothing implementation in the backend and a useful implementation in the frontend.

<pre>
<span class="s0">trait </span><span class="s1">EventAdapter {</span>
  <span class="s0">def </span><span class="s1">textInputAdapter(handler: (String) =&gt; Unit): (Any =&gt; Unit) = {</span>
    <span class="s1">(any: Any) =&gt; ()</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">clickAdapter(handler: () =&gt; Unit): (Any =&gt; Unit) = {</span>
    <span class="s1">(any: Any) =&gt; ()</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

<pre>
<span class="s0">class </span><span class="s1">SnabDomEventAdapter </span><span class="s0">extends </span><span class="s1">EventAdapter {</span>
  <span class="s0">override def </span><span class="s1">textInputAdapter(handler: (String) =&gt; Unit): (Any =&gt; Unit) = {</span>
    <span class="s1">(event) =&gt;</span>
      <span class="s1">event </span><span class="s0">match </span><span class="s1">{</span>
        <span class="s0">case </span><span class="s1">inputEvent: dom.Event =&gt; {</span>
          <span class="s1">inputEvent.target </span><span class="s0">match </span><span class="s1">{</span>
            <span class="s0">case </span><span class="s1">e: HTMLInputElement =&gt; {</span>
              <span class="s1">handler(e.value)</span>
            <span class="s1">}</span>
          <span class="s1">}</span>
          <span class="s1">()</span>
        <span class="s1">}</span>
      <span class="s1">}</span>
  <span class="s1">}</span>

  <span class="s0">override def </span><span class="s1">clickAdapter(handler: () =&gt; Unit): (Any =&gt; Unit) = {</span>
    <span class="s1">(event) =&gt;</span>
      <span class="s1">event </span><span class="s0">match </span><span class="s1">{</span>
        <span class="s0">case </span><span class="s1">inputEvent: dom.MouseEvent =&gt; {</span>
          <span class="s1">handler()</span>
        <span class="s1">}</span>
      <span class="s1">}</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

After adding this to the global `AppContext` it can be accessed by the views.

<pre>
<span class="s1">handlers = Map(</span><span class="s2">&quot;change&quot; </span><span class="s1">-&gt; global.eventAdapter.textInputAdapter((input) =&gt; {</span>
          <span class="s1">context.updateLocal(playerName.modify(_ =&gt; input))</span>
        <span class="s1">}))</span>
</pre>

The question now is how this setup is initialized and how updates are rendered.
Most of the initialization is constructing the `RootViewContext` and then using `scala-js-snabbdom` to `patch` in DOM updates.

For proper server-side-rendering, you would want to render the same context in the frontend and the backend.
This would require passing some JSON representing the context from the backend to the frontend then using that to initialize the context.
I skipped this part since the page is small and I wouldn't notice the difference at this point.

The DOM update is provided by a `RootViewContext` subscriber.
It renders the updates and passes them to scala-js-snabbdom.
scala-js-snabbdom requires you to pass in the results of the previous `patch` call to work properly so there's some extra code to handle that case.

<pre>
<span class="s0">object </span><span class="s1">Main {</span>
  <span class="s0">def </span><span class="s1">main(args: Array[String]): Unit = {</span>
    <span class="s0">val </span><span class="s1">container = dom.document.getElementById(</span><span class="s2">&quot;snabbdom-container&quot;</span><span class="s1">)</span>
    <span class="s0">val </span><span class="s1">patch = init(Seq(Attributes.module</span><span class="s0">,</span>
      <span class="s1">Classes.module</span><span class="s0">,</span>
      <span class="s1">Props.module</span><span class="s0">,</span>
      <span class="s1">Styles.module</span><span class="s0">,</span>
      <span class="s1">EventListeners.module</span><span class="s0">,</span>
      <span class="s1">Dataset.module))</span>


    <span class="s0">given </span><span class="s1">domBuilder: DomBuilder[VNode] = </span><span class="s0">new </span><span class="s1">SnabDomBuilder()</span>

    <span class="s0">var </span><span class="s1">containerVnode: Option[VNode] = None</span>
    <span class="s0">val </span><span class="s1">rootViewContext = RootViewContext(AppContext(</span><span class="s0">new </span><span class="s1">SnabDomEventAdapter()</span><span class="s0">,</span>
      <span class="s1">NameEntry.Context()</span><span class="s0">,</span>
      <span class="s1">() =&gt; js.Dynamic.global.crypto.randomUUID().asInstanceOf[String]</span><span class="s0">,</span>
      <span class="s1">gameServiceOption = Some(</span><span class="s0">new </span><span class="s1">FrontendGameService(</span><span class="s0">new </span><span class="s1">FrontendConfiguration()))</span>
      <span class="s1">))</span>
    <span class="s1">rootViewContext.subscribe((root) =&gt; {</span>
      <span class="s0">val </span><span class="s1">context = root.viewContext</span>
      <span class="s0">val </span><span class="s1">vnodes = h(</span><span class="s2">&quot;div&quot;</span><span class="s0">,</span>
        <span class="s1">VNodeData(props = Map(</span><span class="s2">&quot;id&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;snabbdom-container&quot;</span><span class="s1">))</span><span class="s0">,</span>
        <span class="s1">Array[VNode](AppSection.mainView(context)))</span>
      <span class="s1">containerVnode = containerVnode </span><span class="s0">match </span><span class="s1">{</span>
        <span class="s0">case </span><span class="s1">Some(vNode) =&gt; {</span>
           <span class="s1">Some(patch(vNode</span><span class="s0">, </span><span class="s1">vnodes))</span>
        <span class="s1">}</span>
        <span class="s0">case </span><span class="s1">None =&gt; {</span>
          <span class="s1">Some(patch(container</span><span class="s0">, </span><span class="s1">vnodes))</span>
        <span class="s1">}</span>
      <span class="s1">}</span>

    <span class="s1">})</span>
    <span class="s1">rootViewContext.publish()</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

Now that we know what our name is, it's time to play the game.

# Gameplay

When the game starts the player sees their current hand of cards, a view of the current enemies and their health.
They have attack cards that can attack enemies and heal cards that can heal their health points.

![Initial game]({{site.baseurl}}/assets/2023-04-20_client_initial_game.png)

The hand of cards is rendered as some divs.
When the player selects a card the card goes into the selected card box to give you more options for how to play it.

<pre>
<span class="s0">def </span><span class="s1">selectCard(card: Card): Context =&gt; Context = {</span>
  <span class="s1">selectedCard.replace(Some(card))</span>
<span class="s1">}</span>
</pre>

Instead of removing the selected card from the hand, it was more convenient to filter out the selected card from the cards in hand.
If another piece of code wants to deactivate the selected card, it can just set it to `None` instead of replacing the card into the hand of cards.

<pre>
<span class="s1">div(DomAttributes(attributes = Map(</span><span class="s3">&quot;class&quot; </span><span class="s1">-&gt; </span><span class="s3">&quot;row&quot;</span><span class="s1">))</span><span class="s0">,</span>
  <span class="s1">List(</span>
    <span class="s1">div(DomAttributes(attributes = Map(</span><span class="s3">&quot;class&quot; </span><span class="s1">-&gt; </span><span class="s3">&quot;col bd-dark&quot;</span><span class="s1">))</span><span class="s0">,</span>
      <span class="s1">local.cards.toList.filterNot(local.selectedCard.isDefined &amp;&amp; _.id == local.selectedCard.get.id).map((card) =&gt;</span>
        <span class="s1">GameCard.mainView(card</span><span class="s0">, </span><span class="s1">_ =&gt; {</span>
          <span class="s1">context.update(selectCard(card))</span>
        <span class="s1">})))</span><span class="s0">,</span>
    <span class="s1">div(DomAttributes(attributes = Map(</span><span class="s3">&quot;class&quot; </span><span class="s1">-&gt; </span><span class="s3">&quot;col&quot;</span><span class="s1">))</span><span class="s0">,</span>
      <span class="s1">List(local.selectedCard </span><span class="s0">match </span><span class="s1">{</span>
        <span class="s0">case </span><span class="s1">Some(card) =&gt; GameCard.mainView(card</span><span class="s0">, </span><span class="s1">_ =&gt; {</span>
          <span class="s1">context.update(selectedCard.replace(None))</span>
        <span class="s1">})</span>
        <span class="s0">case </span><span class="s1">None =&gt; div(</span><span class="s3">&quot;Select a card&quot;</span><span class="s1">)</span>
      <span class="s1">}))</span>
  <span class="s1">))</span>
</pre>

The card view renders attack cards and heal cards using different pattern match cases.

<pre>
<span class="s0">object </span><span class="s1">GameCard {</span>

  <span class="s0">def </span><span class="s1">mainView[T](card: Card</span><span class="s0">,</span>
                  <span class="s1">cardClickHandler: Any =&gt; Unit = _ =&gt; ())(using domBuilder: DomBuilder[T]): T = {</span>
    <span class="s0">import </span><span class="s1">domBuilder._</span>

    <span class="s1">card </span><span class="s0">match </span><span class="s1">{</span>
      <span class="s0">case </span><span class="s1">attackCard: AttackCard =&gt; div(</span>
        <span class="s1">DomAttributes(attributes = Map(</span><span class="s2">&quot;class&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;card bd-dark&quot;</span><span class="s1">)</span><span class="s0">,</span>
          <span class="s1">handlers = Map(</span><span class="s2">&quot;click&quot; </span><span class="s1">-&gt; cardClickHandler))</span><span class="s0">,</span>
        <span class="s1">List(</span>
          <span class="s1">div(</span><span class="s2">s&quot;Attack card: </span><span class="s3">$</span><span class="s1">{attackCard.id}</span><span class="s2">&quot;</span><span class="s1">)</span><span class="s0">,</span>
          <span class="s1">div(</span><span class="s2">s&quot;Attack points: </span><span class="s3">$</span><span class="s1">{attackCard.attackPoints}</span><span class="s2">&quot;</span><span class="s1">)</span>
        <span class="s1">)</span>
      <span class="s1">)</span>
      <span class="s0">case </span><span class="s1">healCard: HealCard =&gt; div(</span>
        <span class="s1">DomAttributes(attributes = Map(</span><span class="s2">&quot;class&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;card bd-dark&quot;</span><span class="s1">)</span><span class="s0">,</span>
          <span class="s1">handlers = Map(</span><span class="s2">&quot;click&quot; </span><span class="s1">-&gt; cardClickHandler))</span><span class="s0">,</span>
        <span class="s1">List(div(</span><span class="s2">s&quot;Heal card: </span><span class="s3">$</span><span class="s1">{healCard.id}</span><span class="s2">&quot;</span><span class="s1">)</span><span class="s0">,</span>
        <span class="s1">div(</span><span class="s2">s&quot;Adds </span><span class="s3">$</span><span class="s1">{healCard.healPoints} </span><span class="s2">points of health&quot;</span><span class="s1">))</span>
      <span class="s1">)</span>
    <span class="s1">}</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

After picking an attack card the player can pick the enemy to attack.

![Select attack target]({{site.baseurl}}/assets/2023-04-20_client_attack_select_target.png)

When an attack card is selected the enemies will render with an `Attack` button so that they can be targeted.
The attack button should be extracted to another method, but it was nice to prototype this behavior inline.

<pre>
<span class="s1">EnemyComponent.mainView(enemy</span><span class="s0">,</span>
  <span class="s1">local.selectedCard </span><span class="s0">match </span><span class="s1">{</span>
    <span class="s0">case </span><span class="s1">Some(card@AttackCard(id</span><span class="s0">, </span><span class="s1">attackPoints)) =&gt; button(</span>
      <span class="s1">DomAttributes(attributes = Map(</span><span class="s3">&quot;class&quot; </span><span class="s1">-&gt; </span><span class="s3">&quot;button error&quot;</span><span class="s1">)</span><span class="s0">,</span>
        <span class="s1">handlers = Map(</span><span class="s3">&quot;click&quot; </span><span class="s1">-&gt; (_ -&gt; {</span>
          <span class="s1">context.update(</span>
            <span class="s1">playCard(card)</span>
              <span class="s1">.andThen(attackEnemy(enemy</span><span class="s0">, </span><span class="s1">card))</span>
              <span class="s1">.andThen(resolveKilledMonsters)</span>
          <span class="s1">)</span> 
        <span class="s1">}))</span>
      <span class="s1">)</span><span class="s0">,</span>
      <span class="s3">&quot;Attack&quot;</span><span class="s1">)</span>
    <span class="s0">case </span><span class="s1">_ =&gt; div(</span><span class="s3">&quot;&quot;</span><span class="s1">)</span>
  <span class="s1">}</span>
<span class="s1">)</span>
</pre>

After attacking an enemy their health is reduced, if the enemy health goes below zero the enemy is removed.

<pre>
<span class="s0">def </span><span class="s1">playCard(card: Card): Context =&gt; Context = {</span>
  <span class="s1">selectedCard.replace(None).andThen(cards.modify(_.filterNot(_.id == card.id)))</span>
<span class="s1">}</span>

<span class="s0">def </span><span class="s1">attackEnemy(enemy: Enemy</span><span class="s0">, </span><span class="s1">card: AttackCard): Context =&gt; Context = {</span>
  <span class="s1">// eachEnemy is a Monocle Traversal that updates every enemy</span>
  <span class="s1">eachEnemy.modify((otherEnemy) =&gt; </span><span class="s0">if </span><span class="s1">otherEnemy.id == enemy.id</span>
  <span class="s0">then </span><span class="s1">Enemy.health.modify(_ - card.attackPoints)(otherEnemy)</span>
  <span class="s0">else </span><span class="s1">otherEnemy)</span>
<span class="s1">}</span>

<span class="s0">def </span><span class="s1">resolveKilledMonsters(context: Context): Context = {</span>
  <span class="s0">val </span><span class="s1">killedEnemies = context.enemies.filter(_.health &lt;= </span><span class="s2">0</span><span class="s1">)</span>
  <span class="s1">context.copy(</span>
    <span class="s1">enemies = context.enemies.filterNot(_.health &lt;= </span><span class="s2">0</span><span class="s1">)</span><span class="s0">,</span>
    <span class="s1">enemiesKilled = context.enemiesKilled + killedEnemies.length</span>
  <span class="s1">)</span>
<span class="s1">}</span>
</pre>

The card is then removed from the player's hand and the player can then select another card.

![After attack]({{site.baseurl}}/assets/2023-04-20_client_after_attack.png)

After picking a heal card they can heal themselves.
This works basically the same way the attack code does but adds health instead of subtracting it.

![Select heal target]({{site.baseurl}}/assets/2023-04-20_client_heal_target.png)

![After heal]({{site.baseurl}}/assets/2023-04-20_client_after_heal.png)

# Drawing a new hand of cards

The player has played all the cards in their hand, an HTTP call is made to draw a new hand of cards.
For some games every player action should be sent to the server and the server should manage the game logic.
To stub out that case I added an HTTP API to the lambda to draw more cards.

<pre>
  <span class="s0">override def </span><span class="s1">handleRequest(input: APIGatewayProxyRequestEvent</span><span class="s0">, </span><span class="s1">context: Context): APIGatewayProxyResponseEvent = {</span>
    <span class="s0">if</span><span class="s1">(input.getPath == </span><span class="s2">&quot;/game&quot; </span><span class="s1">&amp;&amp; input.getHttpMethod == </span><span class="s2">&quot;GET&quot;</span><span class="s1">){</span>
      <span class="s0">return </span><span class="s1">gamePage</span>
    <span class="s1">} </span><span class="s0">else if </span><span class="s1">(input.getPath == </span><span class="s2">&quot;/cards/draw&quot; </span><span class="s1">&amp;&amp; input.getHttpMethod == </span><span class="s2">&quot;POST&quot;</span><span class="s1">){</span>
      <span class="s0">return </span><span class="s1">drawCards()</span>
    <span class="s1">}</span>
    <span class="s1">notFoundPage(input.getPath)</span>
  <span class="s1">}</span>
</pre>

<pre>
  <span class="s0">private def </span><span class="s1">drawCards() = {</span>
    <span class="s0">val </span><span class="s1">random = </span><span class="s0">new </span><span class="s1">Random()</span>
    <span class="s0">val </span><span class="s1">cards = (</span><span class="s4">1 </span><span class="s1">to </span><span class="s4">5</span><span class="s1">).map(</span>
      <span class="s1">_ =&gt; {</span>
        <span class="s1">random.nextInt(</span><span class="s4">2</span><span class="s1">) </span><span class="s0">match </span><span class="s1">{</span>
          <span class="s0">case </span><span class="s4">0 </span><span class="s1">=&gt; AttackCard(java.util.UUID.randomUUID().toString</span><span class="s0">, </span><span class="s1">random.nextInt(</span><span class="s4">5</span><span class="s1">) + </span><span class="s4">1</span><span class="s1">)</span>
          <span class="s0">case </span><span class="s4">1 </span><span class="s1">=&gt; HealCard(java.util.UUID.randomUUID().toString</span><span class="s0">,  </span><span class="s1">random.nextInt(</span><span class="s4">3</span><span class="s1">) + </span><span class="s4">1</span><span class="s1">)</span>
        <span class="s1">}</span>
      <span class="s1">}</span>
    <span class="s1">)</span>
    <span class="s0">val </span><span class="s1">event = </span><span class="s0">new </span><span class="s1">APIGatewayProxyResponseEvent()</span>
      <span class="s1">.withStatusCode(</span><span class="s4">200</span><span class="s1">)</span>
      <span class="s1">.withHeaders(Map(</span><span class="s2">&quot;content-type&quot; </span><span class="s1">-&gt; </span><span class="s2">&quot;application/json&quot;</span><span class="s1">).asJava)</span>
      <span class="s1">.withBody(cards.asJson.toString)</span>
    <span class="s1">event</span>
  <span class="s1">}</span>
</pre>

Again we have to stub out the backend because the frontend is using [scala-js-dom Fetch](https://scala-js.github.io/scala-js-dom/#dom.Fetch) to make the call.
I made an interface in `commonui` and `Option` it in the global `AppContext`.
Since I'm expecting service calls to be rare compared to event handlers, `Option` makes it clear that the implementation might not be available. 

<pre>
<span class="s0">import </span><span class="s1">scala.concurrent.Future</span>

<span class="s0">trait </span><span class="s1">GameService {</span>
  <span class="s0">def </span><span class="s1">drawCards(): Future[Option[Vector[Card]]]</span>
<span class="s1">}</span>
</pre>

<pre>
<span class="s0">case class </span><span class="s1">AppContext(eventAdapter: EventAdapter</span><span class="s0">,</span>
                      <span class="s1">viewContext: AppSection.Context</span><span class="s0">,</span>
                      <span class="s1">uuidGenerator: () =&gt; String</span><span class="s0">,</span>
                      <span class="s1">playerName: Option[String] = None</span><span class="s0">,</span>
                      <span class="s1">gameServiceOption: Option[GameService] = None)</span>


</pre>

Then the implementation is added to the `frontend` project.

<pre>
<span class="s0">import </span><span class="s1">org.scalajs.dom</span>
<span class="s0">import </span><span class="s1">org.scalajs.dom.{Request</span><span class="s0">, </span><span class="s1">RequestInit}</span>
<span class="s0">import </span><span class="s1">org.scalajs.dom.experimental.HttpMethod</span>

<span class="s0">import </span><span class="s1">scala.concurrent.ExecutionContext.Implicits.global</span>
<span class="s0">import </span><span class="s1">scala.concurrent.Future</span>
<span class="s0">import </span><span class="s1">scalajs.js.Thenable.Implicits.*</span>
<span class="s0">import </span><span class="s1">io.circe.*</span>
<span class="s0">import </span><span class="s1">io.circe.generic.auto.*</span>
<span class="s0">import </span><span class="s1">io.circe.parser.*</span>

<span class="s0">class </span><span class="s1">FrontendGameService(frontendConfiguration: FrontendConfiguration) </span><span class="s0">extends </span><span class="s1">GameService {</span>
  <span class="s0">override def </span><span class="s1">drawCards(): Future[Option[Vector[Card]]] = {</span>
    <span class="s0">val </span><span class="s1">request = </span><span class="s0">new </span><span class="s1">Request(frontendConfiguration.apiUrl(</span><span class="s2">&quot;cards/draw&quot;</span><span class="s1">)</span><span class="s0">,</span>
      <span class="s0">new </span><span class="s1">RequestInit {</span>
      <span class="s1">method = dom.HttpMethod.POST</span>
    <span class="s1">})</span>
    <span class="s0">val </span><span class="s1">response: Future[Either[Error</span><span class="s0">, </span><span class="s1">Vector[Card]]] = </span><span class="s0">for </span><span class="s1">{</span>
      <span class="s1">response &lt;- dom.fetch(request)</span>
      <span class="s1">bodyText &lt;- response.text()</span>
    <span class="s1">} </span><span class="s0">yield </span><span class="s1">{</span>
      <span class="s1">decode[Vector[Card]](bodyText)</span>
    <span class="s1">}</span>
    <span class="s1">response.map(_.toOption)</span>
  <span class="s1">}</span>
<span class="s1">}</span>
</pre>

The view code calls this API if a `GameService` is present in the global context.

Because `update` only accepts a function that performs an update, we don't have the actual result of that update.
That might be useful to add, but in that case it can only return an `Option` because when the update is applied, the section might have switched.

To work around this, I checked the current local state to see if the player's hand would be empty after playing a card.
Since the `GameService` returns a `Future`, the `update` with the new cards happens after the HTTP call completes.

`Prism` will prevent this update in case it happens after the section is changed.
One issue is if the player can switch between multiple game instances, this call could complete later and add the cards to a different instance.
In that case it might be useful to put a guard in the `Prism` that also checks the game instance id.

<pre>
<span class="s1">handlers = Map(</span><span class="s3">&quot;click&quot; </span><span class="s1">-&gt; (_ -&gt; {</span>
  <span class="s1">context.update(</span>
    <span class="s1">playCard(card)</span>
      <span class="s1">.andThen(attackEnemy(enemy</span><span class="s0">, </span><span class="s1">card))</span>
      <span class="s1">.andThen(resolveKilledMonsters)</span>
  <span class="s1">)</span>
  <span class="s0">if </span><span class="s1">(isEmptyAfterPlaying(card</span><span class="s0">, </span><span class="s1">local)) {</span>
    <span class="s1">global.gameServiceOption.map( gameService =&gt; {</span>
      <span class="s1">gameService.drawCards().map {</span>
        <span class="s0">case </span><span class="s1">Some(drawnCards) =&gt; context.update(cards.modify(_ ++ drawnCards))</span>
        <span class="s0">case </span><span class="s1">None =&gt; ()</span>
      <span class="s1">}</span>
    <span class="s1">})</span>
  <span class="s1">}</span>
<span class="s1">}))</span>
</pre>

That completes the game with some non-trivial use cases stubbed out.
Time to ship it.

# Deploying with Serverless Framework

To deploy this package I used the [Serverless Framework](https://www.serverless.com/framework/docs).


To install serverless framework I added a `package.json` file.
`node.js` was already installed using [nvm](https://github.com/nvm-sh/nvm).

<pre class="s0">
{
  "name": "test-scala-serverless",
  "version": "1.0.0",
  "devDependencies": {
    "serverless": "^3.30.1",
    "serverless-s3-sync": "^3.1.0"
  }
}
</pre>

Running `npm install` installs the framework in the project.

To configure it, I added a `serverless.yml` file.

<pre>
<span class="s0">service</span><span class="s1">: </span><span class="s2">aws-test-scala-serverless</span>
<span class="s0">frameworkVersion</span><span class="s1">: </span><span class="s3">'3'</span>

<span class="s0">provider</span><span class="s1">:</span>
  <span class="s0">name</span><span class="s1">: </span><span class="s2">aws</span>
  <span class="s0">runtime</span><span class="s1">: </span><span class="s2">java11</span>
  <span class="s0">region</span><span class="s1">: </span><span class="s2">us-west-2</span>



<span class="s0">package</span><span class="s1">:</span>
  <span class="s0">artifact</span><span class="s1">: </span><span class="s2">${file(./backend/target/universal/lambda.json):artifact}</span>

<span class="s0">functions</span><span class="s1">:</span>
  <span class="s0">api</span><span class="s1">:</span>
    <span class="s0">handler</span><span class="s1">: </span><span class="s2">LambdaHandler</span>
    <span class="s0">events</span><span class="s1">:</span>
      <span class="s1">- </span><span class="s0">http</span><span class="s1">: </span><span class="s2">ANY /{paths+}</span>
    <span class="s0">snapStart</span><span class="s1">: </span><span class="s2">true</span>
    <span class="s0">environment</span><span class="s1">:</span>
      <span class="s0">ASSETS_BASE_URL</span><span class="s1">: </span><span class="s2">${file(./deploy-config.${opt:stage, 'dev'}.json):assetsUrlBase}</span>
      <span class="s0">API_BASE_URL</span><span class="s1">: </span><span class="s2">${file(./deploy-config.${opt:stage, 'dev'}.json):apiBaseUrl}</span>

<span class="s0">custom</span><span class="s1">:</span>
  <span class="s0">s3Sync</span><span class="s1">:</span>
    <span class="s1">- </span><span class="s0">bucketName</span><span class="s1">: </span><span class="s2">${file(./deploy-config.${opt:stage, 'dev'}.json):assetsBucketName}</span>
      <span class="s0">bucketPrefix</span><span class="s1">: </span><span class="s2">${file(./deploy-config.${opt:stage, 'dev'}.json):assetsBucketPrefix}</span>
      <span class="s0">localDir</span><span class="s1">: </span><span class="s2">frontend/dist</span>

<span class="s0">plugins</span><span class="s1">:</span>
  <span class="s1">- </span><span class="s2">serverless-s3-sync</span>
</pre>

Back to  `backend/target/universal/lambda.json`.
It had a reference to the `universal` package that sbt built with `.jar` files of all the backend code and dependencies.

<pre class="s0">
{"artifact":"backend/target/universal/backend-0.1.0-SNAPSHOT.zip"}
</pre>

This snippet points serverless to the zip file, and skips the default Serverless Framework packaging step.

<pre>
<span class="s0">package</span><span class="s1">:</span>
  <span class="s0">artifact</span><span class="s1">: </span><span class="s2">${file(./backend/target/universal/lambda.json):artifact}</span>
</pre>

The frontend code is deployed to an [S3 hosted static website](https://docs.aws.amazon.com/AmazonS3/latest/userguide/website-hosting-custom-domain-walkthrough.html).
Serverless Framework needs to know what S3 bucket to upload to and the backend needs to know where the JS bundles are hosted.
This config is set in the `deploy-config.dev.json` file.

*Update:* The S3 site also needs a CORS configuration.
I couldn't find the original source I used for the configuration but [this post](https://www.mslinn.com/blog/2021/03/21/cors-aws.html) seems to have the right steps.
The CloudFront configuration is a pain, but it provides caching and better configuration options vs S3 direct hosting. 

<pre class="s0">
{
  "assetsBucketName":  "YOURBUCKET",
  "assetsBucketPrefix": "testscalaserverless/dev/assets",
  "assetsUrlBase": "https://www.example.com/testscalaserverless/dev/assets",
  "apiBaseUrl": "https://www.example.com/testscalaserverless/dev"
}
</pre>

The `serverless-s3-sync` plugin is used to copy the JS bundle to S3 during the deploy phase.
To be safe it might be better to run the S3 upload before deploying to AWS Lambda.

If you're following along, you would have to [set up AWS credentials with a named profile](https://www.serverless.com/framework/docs/providers/aws/guide/credentials/).

To deploy, run `sbt lambdaPackage` to build the package, and `npx serverless` to deploy to AWS Lambda and S3. 

```shell
sbt lambdaPackage
npx serverless deploy --aws-profile MYAWSPROFILENAME
```

Serverless will create a new REST API Gateway and print out the url on the command line.
[Creating a custom domain](https://www.serverless.com/blog/serverless-api-gateway-domain/) for the API is a whole other process. 

# AWS Lambda SnapStart

Without SnapSnart the cold start response times are around 2 seconds measured by `curl`.
This project doesn't have a lot of dependencies, so most of it is probably extracting `.jar` files and loading classes.
With SnapStart the cold start response times came down to around 1 second.

To implement SnapStart, add the CraC dependency, register the handler with `Core.getGlobalContext.register`, and implement the `afterRestore` and `beforeCheckpoint` methods.

<pre class="s0">
libraryDependencies ++= Seq(
  "io.github.crac" %  "org-crac" %  "0.1.3"
)
</pre>

<pre>
<span class="s0">class </span><span class="s1">LambdaHandler </span><span class="s0">extends </span><span class="s1">RequestHandler[APIGatewayProxyRequestEvent</span><span class="s0">, </span><span class="s1">APIGatewayProxyResponseEvent]</span><span class="s0">, </span><span class="s1">Resource {</span>

  <span class="s1">Core.getGlobalContext.register(</span><span class="s0">this</span><span class="s1">)</span>

  <span class="s0">override def </span><span class="s1">handleRequest(input: APIGatewayProxyRequestEvent</span><span class="s0">, </span><span class="s1">context: Context): APIGatewayProxyResponseEvent = {</span>
    <span class="s0">if </span><span class="s1">(input.getPath == </span><span class="s2">&quot;/game&quot; </span><span class="s1">&amp;&amp; input.getHttpMethod == </span><span class="s2">&quot;GET&quot;</span><span class="s1">) {</span>
      <span class="s0">return </span><span class="s1">gamePage</span>
    <span class="s1">} </span><span class="s0">else if </span><span class="s1">(input.getPath == </span><span class="s2">&quot;/cards/draw&quot; </span><span class="s1">&amp;&amp; input.getHttpMethod == </span><span class="s2">&quot;POST&quot;</span><span class="s1">) {</span>
      <span class="s0">return </span><span class="s1">drawCards()</span>
    <span class="s1">}</span>
    <span class="s1">notFoundPage(input.getPath)</span>
  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">afterRestore(context: org.crac.Context[? &lt;: org.crac.Resource]): Unit = {</span>

  <span class="s1">}</span>

  <span class="s0">def </span><span class="s1">beforeCheckpoint(context: org.crac.Context[? &lt;: org.crac.Resource]): Unit = {</span>
    <span class="s1">gamePage</span>
  <span class="s1">}</span>

<span class="s1">}</span>
</pre>

`beforeCheckpoint` will run when you deploy, saving a memory snapshot that will be used during cold starts.
I wanted to preload most of the libraries that would be used, so I just ran the `gamePage` method.
[Tuning AWS Lambda JVM SnapStart](https://dev.to/aws-builders/measuring-java-11-lambda-cold-starts-with-snapstart-part-5-priming-end-to-end-latency-and-deployment-time-jem) goes in detail into how to do this with different Java frameworks and proper response time measurements.

# Takeaways

I'm pretty happy with the results so far, the main thing I will probably try next is using [Tyrian](https://tyrian.indigoengine.io/) and the [Indigo HTML game engine](https://indigoengine.io/).
Lessons learned:

- A lot can be done on the browser with Scala.js and pure Scala.js libraries
- I wish I had found out about ScalaTags and scala-js-snabbdom sooner
- `given` and `using` clauses can clean up abstractions that would normally be verbose and cumbersome
- Monocle lenses enable interesting possibilities for immutable data and user interfaces 
- AWS Lambda SnapStart makes JVM Scala lambda cold starts quick enough for a lot of use cases
- Being able to pass build targets between sbt sub-projects made it easier to pass JS bundle information around
- Serverless framework is a nice way to deploy Scala lambdas with a bit of configuration