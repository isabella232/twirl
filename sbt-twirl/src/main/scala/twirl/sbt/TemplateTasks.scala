/* Copyright 2012 Johannes Rudolph
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package twirl.sbt

import sbt._
import Keys._

object TemplateTasks {

  class ProblemException(issues: xsbti.Problem*) extends xsbti.CompileFailed {
    def arguments(): Array[String] = Array.empty
    def problems(): Array[xsbti.Problem] = issues.toArray
  }

  def improveErrorMsg[T](result: Result[T], streamManagerR: Result[Streams]): T = result match {
    case Inc(incomplete) =>
      val probs = TemplateProblems.getProblems(incomplete, streamManagerR.toEither.right.get)
      throw new ProblemException(probs: _*)
    case Value(v) => v
  }

  def addProblemReporterTo[T: Manifest](key: TaskKey[T], filter: File => Boolean = _ => true): Setting[_] =
    TwirlPlugin.Twirl.twirlReportErrors.asInstanceOf[TaskKey[T]] in key <<=
      (key, streams).mapR(reportProblems(filter))
                    .triggeredBy(key)

  def reportProblems[T](filter: File => Boolean)(result: Result[T], streams: Result[TaskStreams]): T = result match {
    case Inc(incomplete) =>
      val logger = Utilities.colorLogger(streams.toEither.right.get.log)
      val reporter = new LoggerReporter(10, logger)
      val problems = TemplateProblems.allProblems(incomplete).filter(_.position.sourceFile.exists(filter))
      if (!problems.isEmpty) {
        logger.error("[-YELLOW-]%s problem(s) in Twirl template(s) found" format problems.size)
        problems.foreach { p => reporter.display(p.position, p.message, p.severity) }
      }
      throw incomplete
    case Value(v) => v
  }
}