/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.griffin.measure.data.connector.batch

import org.apache.griffin.measure.config.params.user.DataConnectorParam
import org.apache.griffin.measure.process.engine.DqEngines
import org.apache.griffin.measure.utils.HdfsUtil
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.griffin.measure.utils.ParamUtil._

// data connector for avro file
case class TextDirBatchDataConnector(sqlContext: SQLContext, dqEngines: DqEngines, dcParam: DataConnectorParam
                                    ) extends BatchDataConnector {

  val config = dcParam.config

  val DirPath = "dir.path"
  val DataDirDepth = "data.dir.depth"
  val SuccessFile = "success.file"
  val DoneFile = "done.file"

  val dirPath = config.getString(DirPath, "")
  val dataDirDepth = config.getInt(DataDirDepth, 0)
  val successFile = config.getString(SuccessFile, "_SUCCESS")
  val doneFile = config.getString(DoneFile, "_DONE")

  val ignoreFilePrefix = "_"

  private def dirExist(): Boolean = {
    HdfsUtil.existPath(dirPath)
  }

  def data(ms: Long): Option[DataFrame] = {
    try {
      val dataDirs = listSubDirs(dirPath :: Nil, dataDirDepth, readable)
      // touch done file for read dirs
      dataDirs.foreach(dir => touchDone(dir))

      val validDataDirs = dataDirs.filter(dir => !emptyDir(dir))

      if (validDataDirs.size > 0) {
        val df = sqlContext.read.text(validDataDirs:  _*)
        val dfOpt = Some(df)
        val preDfOpt = preProcess(dfOpt, ms)
        preDfOpt
      } else {
        None
      }
    } catch {
      case e: Throwable => {
        error(s"load text dir ${dirPath} fails: ${e.getMessage}")
        None
      }
    }
  }

  private def listSubDirs(paths: Seq[String], depth: Int, filteFunc: (String) => Boolean): Seq[String] = {
    val subDirs = paths.flatMap { path => HdfsUtil.listSubPathsByType(path, "dir", true) }
    if (depth <= 0) {
      subDirs.filter(filteFunc)
    } else {
      listSubDirs(subDirs, depth, filteFunc)
    }
  }

  private def readable(dir: String): Boolean = isSuccess(dir) && !isDone(dir)
  private def isDone(dir: String): Boolean = HdfsUtil.existFileInDir(dir, doneFile)
  private def isSuccess(dir: String): Boolean = HdfsUtil.existFileInDir(dir, successFile)

  private def touchDone(dir: String): Unit = HdfsUtil.createEmptyFile(HdfsUtil.getHdfsFilePath(dir, doneFile))

  private def emptyDir(dir: String): Boolean = {
    HdfsUtil.listSubPathsByType(dir, "file").filter(!_.startsWith(ignoreFilePrefix)).size == 0
  }

//  def available(): Boolean = {
//    (!concreteFileFullPath.isEmpty) && fileExist
//  }

//  def init(): Unit = {}

//  def metaData(): Try[Iterable[(String, String)]] = {
//    Try {
//      val st = sqlContext.read.format("com.databricks.spark.avro").load(concreteFileFullPath).schema
//      st.fields.map(f => (f.name, f.dataType.typeName))
//    }
//  }

//  def data(): Try[RDD[(Product, (Map[String, Any], Map[String, Any]))]] = {
//    Try {
//      loadDataFile.flatMap { row =>
//        // generate cache data
//        val cacheExprValueMaps = ExprValueUtil.genExprValueMaps(Some(row), ruleExprs.cacheExprs, constFinalExprValueMap)
//        val finalExprValueMaps = ExprValueUtil.updateExprValueMaps(ruleExprs.finalCacheExprs, cacheExprValueMaps)
//
//        // data info
//        val dataInfoMap: Map[String, Any] = DataInfo.cacheInfoList.map { info =>
//          try {
//            (info.key -> row.getAs[info.T](info.key))
//          } catch {
//            case e: Throwable => info.defWrap
//          }
//        }.toMap
//
//        finalExprValueMaps.flatMap { finalExprValueMap =>
//          val groupbyData: Seq[AnyRef] = ruleExprs.groupbyExprs.flatMap { expr =>
//            expr.calculate(finalExprValueMap) match {
//              case Some(v) => Some(v.asInstanceOf[AnyRef])
//              case _ => None
//            }
//          }
//          val key = toTuple(groupbyData)
//
//          Some((key, (finalExprValueMap, dataInfoMap)))
//        }
//      }
//    }
//  }

}
