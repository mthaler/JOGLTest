import sbt._
import Keys._
import sbtassembly.Plugin.AssemblyKeys._
import net.virtualvoid.sbt.graph.Plugin.graphSettings
import sbt.Package.ManifestAttributes
import sbtassembly.Plugin._
import sbtassembly.AssemblyUtils._

object Version {

  val akka = "2.3.5"

  val scala = "2.11.1"
}

object Dependencies {
  val gluegen_rt = "org.jogamp.gluegen" % "gluegen-rt-main" % "2.2.1"

  val jogl_all = "org.jogamp.jogl" % "jogl-all-main" % "2.2.1" 
}

object BuildSettings {
  import sbtassembly.Plugin.assemblySettings
  val buildOrganization = "heavens-above"
  val buildVersion      = "1.0.0"
  val buildScalaVersion = Version.scala

  /**
   * Default build settings
   */
  val buildSettings = Defaults.defaultSettings ++ graphSettings ++ Seq (
    organization := buildOrganization,
    version      := buildVersion,
    scalaVersion := buildScalaVersion,
    fork := true,
    javacOptions ++= Seq("-Xlint:unchecked"),
    resolvers += "spray repo" at "http://repo.spray.io",
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
  )

  /**
   * Build settings for projects that require a fat .jar
   */
  lazy val jarBuildSettings = buildSettings ++ assemblySettings

  /**
   * Build settings for projects that require a fat jar, but no scala library
   */
  lazy val javaJarBuildSettings = jarBuildSettings ++ Seq(autoScalaLibrary := false)
}

object Build extends sbt.Build {
  import Dependencies._
  import BuildSettings._

  lazy val jogltest = Project(
    id = "jogltest",
    base = file("."),
    settings = jarBuildSettings ++ Seq(
      jarName := "jogltest.jar",
      libraryDependencies ++= Seq(gluegen_rt, jogl_all),
      mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
	{
	  case s if s.endsWith(".so") => custom
	  case s if s.endsWith(".dll") => custom
	  case s if s.startsWith("jogamp/nativetag") => custom
	  case x => old(x)
	}
      }
    )
  )
  
  val custom: MergeStrategy = new MergeStrategy {
    val name = "custom"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
      Right(files flatMap { f =>
	if(!f.exists) Seq.empty
	else if(f.isDirectory && (f ** "*.class").get.nonEmpty) Seq(f -> path)
	else sourceOfFileForMerge(tempDir, f) match {
	  case (_, _, _, false) => Seq(f -> path)
	  case (jar, base, p, true) =>
	    val filenames = files.map {
	      case f =>
		val (source, _, _, _) = sourceOfFileForMerge(tempDir, f) 
		source.getName
	    }
	    val filtered = filenames.filter(name => name.startsWith("jogl-all") && name.contains("natives"))
	    if (filtered.isEmpty) {
	      Seq(f -> path)
	    } else {
	      val sourceJarName = jar.getName
	      val index0 = sourceJarName.indexOf("natives") + 8
	      val index1 = sourceJarName.lastIndexOf(".")
	      val os = sourceJarName.substring(index0, index1)
	      val dest = new File(f.getParent, appendJarName(f.getName, jar))
	      val dest2 = new File(f.getParent, "natives/" + os + "/" + path)
	      println("dest: " + dest + " dest2: " + dest2)
	      //IO.move(f, dest)
	      //val result = Seq(dest -> appendJarName(path, jar))
	      IO.move(f, dest2)
	      val result = Seq(dest2 -> ("natives/" + os + "/" + path))
	      if (dest2.isDirectory) ((dest2 ** (-DirectoryFilter))) x relativeTo(base)
	      else result
	   }
      }
  })

  def appendJarName(source: String, jar: File): String =
    FileExtension.replaceFirstIn(source, "") + "_" + FileExtension.replaceFirstIn(jar.getName, "") + FileExtension.findFirstIn(source).getOrElse("")
  override def notifyThreshold = 1
  }
  
  private val FileExtension = """([.]\w+)$""".r
}