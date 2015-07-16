package de.frosner.ddq

import org.apache.spark.sql.{Column, DataFrame}
import Constraint.ConstraintFunction
import org.apache.spark.storage.StorageLevel
import Check._

case class Check(dataFrame: DataFrame,
                 cacheMethod: Option[StorageLevel] = Option(StorageLevel.MEMORY_ONLY),
                 constraints: Iterable[Constraint] = Iterable.empty) {
  
  private def addConstraint(cf: ConstraintFunction): Check = Check(dataFrame, cacheMethod, constraints ++ List(Constraint(cf)))
  
  def hasKey(columnName: String, columnNames: String*): Check = addConstraint { 
    df => {
      val columnsString = (columnName :: columnNames.toList).mkString(",")
      val nonUniqueRows = df.groupBy(columnName, columnNames:_*).count.filter(new Column("count") > 1).count
      if (nonUniqueRows == 0)
        success(s"""Columns $columnsString are a key""")
      else
        failure(s"""Columns $columnsString are not a key""")
    }
  }
  
  def satisfies(constraint: String): Check = addConstraint {
    df => {
      val succeedingRows = df.filter(constraint).count
      val count = df.count
      if (succeedingRows == count)
        success(s"Constraint $constraint is satisfied")
      else
        failure(s"${count - succeedingRows} rows did not satisfy constraint $constraint")
    }
  }

  def isNeverNull(columnName: String) = addConstraint {
    df => {
      val nullCount = df.filter(new Column(columnName).isNull).count
      if (nullCount == 0) {
        success(s"Column $columnName is not null")
      } else {
        failure(s"Column $columnName has $nullCount null rows although it should not be null")
      }
    }
  }

  def isAlwaysNull(columnName: String) = addConstraint {
    df => {
      val notNullCount = df.filter(new Column(columnName).isNotNull).count
      if (notNullCount == 0) {
        success(s"Column $columnName is null")
      } else {
        failure(s"Column $columnName has $notNullCount non-null rows although it should be null")
      }
    }
  }
  
  def hasNumRowsEqualTo(expected: Long): Check = addConstraint {
    df => {
      val count = df.count
      if (count == expected)
        success(s"The number of rows is equal to $count")
      else
        failure(s"The actual number of rows $count is not equal to the expected $expected")
    }
  }
  
  def run: Boolean = {
    hint(s"Checking $dataFrame")
    val potentiallyPersistedDf = cacheMethod.map(dataFrame.persist(_)).getOrElse(dataFrame)
    if (!constraints.isEmpty)
      constraints.map(c => c.fun(potentiallyPersistedDf)).reduce(_ && _)
    else
      hint("- Nothing to check!")
  }
      
}

object Check {

  def success(message: String): Boolean = {
    println(Console.GREEN + "- " + message + Console.RESET)
    true
  }

  def failure(message: String): Boolean = {
    println(Console.RED + "- " + message + Console.RESET)
    false
  }

  def hint(message: String): Boolean = {
    println(Console.BLUE + message + Console.RESET)
    true
  }

}
