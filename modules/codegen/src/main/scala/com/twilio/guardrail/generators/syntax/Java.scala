package com.twilio.guardrail.generators.syntax

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.`type`.{ ClassOrInterfaceType, Type }
import com.github.javaparser.ast.body.{ ClassOrInterfaceDeclaration, Parameter }
import com.github.javaparser.ast.expr.{ Expression, Name, SimpleName }
import com.github.javaparser.ast.{ CompilationUnit, ImportDeclaration, Node, NodeList }
import com.github.javaparser.printer.PrettyPrinterConfiguration
import com.github.javaparser.printer.PrettyPrinterConfiguration.IndentType
import com.twilio.guardrail.Target
import java.nio.charset.StandardCharsets
import java.util.Optional
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

object Java {
  implicit class RichJavaOptional[T](val o: Optional[T]) extends AnyVal {
    def asScala: Option[T] = if (o.isPresent) Option(o.get) else None
  }

  implicit class RichType(val tpe: Type) extends AnyVal {
    def isOptional: Boolean =
      tpe match {
        case cls: ClassOrInterfaceType =>
          val scope = cls.getScope.asScala
          cls.getNameAsString == "Optional" && (scope.isEmpty || scope.map(_.asString).contains("java.util"))
        case _ => false
      }

    def containedType: Type =
      tpe match {
        case cls: ClassOrInterfaceType => cls.getTypeArguments.asScala.filter(_.size == 1).fold(tpe)(_.get(0))
        case _                         => tpe
      }

    def unbox: Type =
      tpe match {
        case cls: ClassOrInterfaceType if cls.isBoxedType => cls.toUnboxedType
        case _                                            => tpe
      }

    def isNamed(name: String): Boolean =
      tpe match {
        case cls: ClassOrInterfaceType if name.contains(".") =>
          (cls.getScope.asScala.fold("")(_.getName.asString + ".") + cls.getNameAsString) == name
        case cls: ClassOrInterfaceType => cls.getNameAsString == name
        case _                         => false
      }

    def name: Option[String] =
      tpe match {
        case cls: ClassOrInterfaceType =>
          Some(cls.getScope.asScala.fold("")(_.getName.asString + ".") + cls.getNameAsString)
        case _ => None
      }
  }

  implicit class RichListOfNode[T <: Node](val l: List[T]) extends AnyVal {
    def toNodeList: NodeList[T] = new NodeList[T](l: _*)
  }

  private[this] def safeParse[T](log: String)(parser: String => T, s: String)(implicit cls: ClassTag[T]): Target[T] =
    Target.log.function(s"${log}: ${s}") {
      Try(parser(s)) match {
        case Success(value) => Target.pure(value)
        case Failure(t)     => Target.raiseError(s"Unable to parse '${s}' to a ${cls.runtimeClass.getName}: ${t.getMessage}")
      }
    }

  def safeParseCode(s: String): Target[CompilationUnit]  = safeParse("safeParseCode")(JavaParser.parse, s)
  def safeParseSimpleName(s: String): Target[SimpleName] = safeParse("safeParseSimpleName")(JavaParser.parseSimpleName, s)
  def safeParseName(s: String): Target[Name]             = safeParse("safeParseName")(JavaParser.parseName, s)
  def safeParseType(s: String): Target[Type]             = safeParse("safeParseType")(JavaParser.parseType, s)
  def safeParseClassOrInterfaceType(s: String): Target[ClassOrInterfaceType] =
    safeParse("safeParseClassOrInterfaceType")(JavaParser.parseClassOrInterfaceType, s)
  def safeParseExpression[T <: Expression](s: String)(implicit cls: ClassTag[T]): Target[T] =
    safeParse[T]("safeParseExpression")(JavaParser.parseExpression[T], s)
  def safeParseParameter(s: String): Target[Parameter]               = safeParse("safeParseParameter")(JavaParser.parseParameter, s)
  def safeParseImport(s: String): Target[ImportDeclaration]          = safeParse("safeParseImport")(JavaParser.parseImport, s)
  def safeParseRawImport(s: String): Target[ImportDeclaration]       = safeParse("safeParseRawImport")(JavaParser.parseImport, s"import ${s};")
  def safeParseRawStaticImport(s: String): Target[ImportDeclaration] = safeParse("safeParseStaticImport")(JavaParser.parseImport, s"import static ${s};")

  def completionStageType(of: Type): ClassOrInterfaceType     = JavaParser.parseClassOrInterfaceType("CompletionStage").setTypeArguments(of)
  def optionalType(of: Type): ClassOrInterfaceType            = JavaParser.parseClassOrInterfaceType("Optional").setTypeArguments(of)
  def functionType(in: Type, out: Type): ClassOrInterfaceType = JavaParser.parseClassOrInterfaceType("Function").setTypeArguments(in, out)
  def supplierType(of: Type): ClassOrInterfaceType            = JavaParser.parseClassOrInterfaceType("Supplier").setTypeArguments(of)

  val OBJECT_TYPE: ClassOrInterfaceType          = JavaParser.parseClassOrInterfaceType("Object")
  val STRING_TYPE: ClassOrInterfaceType          = JavaParser.parseClassOrInterfaceType("String")
  val THROWABLE_TYPE: ClassOrInterfaceType       = JavaParser.parseClassOrInterfaceType("Throwable")
  val ASSERTION_ERROR_TYPE: ClassOrInterfaceType = JavaParser.parseClassOrInterfaceType("AssertionError")

  val printer: PrettyPrinterConfiguration = new PrettyPrinterConfiguration()
    .setColumnAlignFirstMethodChain(false)
    .setColumnAlignParameters(false)
    .setIndentSize(4)
    .setIndentType(IndentType.SPACES)
    .setOrderImports(true)
    .setPrintComments(true)
    .setPrintJavadoc(true)
    .setTabWidth(4)

  // from https://en.wikipedia.org/wiki/List_of_Java_keywords
  private val reservedWords = Set(
    "abstract",
    "assert",
    "boolean",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "class",
    "const",
    "continue",
    "default",
    "do",
    "double",
    "else",
    "enum",
    "exports",
    "extends",
    "false",
    "final",
    "finally",
    "float",
    "for",
    "goto",
    "if",
    "implements",
    "import",
    "instanceof",
    "int",
    "interface",
    "long",
    "module",
    "native",
    "new",
    "null",
    "package",
    "private",
    "protected",
    "public",
    "requires",
    "return",
    "short",
    "static",
    "strictfp",
    "super",
    "switch",
    "synchronized",
    "this",
    "throw",
    "throws",
    "transient",
    "true",
    "try",
    "var",
    "void",
    "volatile",
    "while"
  )

  implicit class RichJavaString(val s: String) extends AnyVal {
    def escapeReservedWord: String = if (reservedWords.contains(s)) s + "_" else s
    def unescapeReservedWord: String =
      if (s.endsWith("_")) {
        val prefix = s.substring(0, s.length - 1)
        if (reservedWords.contains(prefix)) {
          prefix
        } else {
          s
        }
      } else {
        s
      }
  }

  lazy val SHOWER_CLASS_DEF: ClassOrInterfaceDeclaration = {
    JavaParser
      .parseResource(getClass.getClassLoader, "java/Shower.java", StandardCharsets.UTF_8)
      .getClassByName("Shower")
      .asScala
      .getOrElse(
        throw new AssertionError("Shower.java in class resources is not valid")
      )
  }
}
