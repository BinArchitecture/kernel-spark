package com.lppz.spark.scala.kafka

import java.util.HashMap
import scala.collection.mutable.Set
import org.apache.log4j.Logger
import org.apache.spark.sql.DataFrame
import java.util.UUID
import com.lppz.bean.Spark2kafkaBean
import scala.util.control.Breaks
import java.text.SimpleDateFormat
import com.lppz.spark.kafka.SparkExportProducer
import com.lppz.spark.scala.jedis.JedisClientUtil

class SparkKafkaHandler extends Serializable {
  @transient lazy val LOG = Logger.getLogger(getClass.getName)

  object instance extends Serializable {

    def buildColArray(orginColumnArray: Array[String], excludeColumns: String): Array[String] = {
      val excludeCol: Array[String] = excludeColumns.split(",")
      val columnArray: Array[String] = new Array[String](orginColumnArray.length - excludeCol.length)
      var i = 0
      val exCludeset = Set[String]()
      excludeCol.foreach { ex => exCludeset += ex }
      orginColumnArray.foreach { x =>
        {
          if (!exCludeset.contains(x)) {
            columnArray.update(i, x)
            i += 1
          }
        }
      }
      columnArray
    }

    def send2Kafka(df: DataFrame, kafkaBrokerPath: String, jedisClusterYamlPath: String, partionNum: Integer, esIndex: String, esType: String, dateColumnNames: String) {
      val rdd = df.rdd
      var columns = df.columns;
      //      val colArray = buildColArray(df.columns,excludeColumns)
      rdd.repartition(partionNum).foreachPartition { riter =>
        {
          var producer: SparkExportProducer = null;
          if (riter != null && (!riter.isEmpty)) {
            try {
              object InternalProducerClient extends KafkaProducerUtil {
              }
              InternalProducerClient.instance.buildKafkaProducer(kafkaBrokerPath)
              producer = InternalProducerClient.instance.getProducer
              object InternalJedisClusterCliente extends JedisClientUtil {}
              InternalJedisClusterCliente.instance.makeJedisCluster(jedisClusterYamlPath)
              val jedisCluster = InternalJedisClusterCliente.instance.getJedisCluster

              riter.foreach {
                r =>
                  {
                    var dateFormater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    var spark2kafkaBean = new Spark2kafkaBean()
                    var rowMap = new HashMap[String, String]()
                    spark2kafkaBean.setEsIndex(esIndex)
                    spark2kafkaBean.setEsType(esType)
                    //                    rowMap.put(ESConstants.ES_INDEX_KEY, esIndex)
                    //                    rowMap.put(ESConstants.ES_TYPE_KEY, esType)
                    var key = esIndex + "_" + esType + "_"
                    val timeColumns = dateColumnNames.split(",")
                    columns.foreach { c =>
                      {
                        val col: String = c.asInstanceOf[String]
                        val v = r.get(r.fieldIndex(col))
                        var vs: String = null
                        val loop = new Breaks;
                        loop.breakable {
                          timeColumns.foreach { x =>
                            {
                              if (x.equals(col)) {
                                vs = dateFormater.format(v)
                                LOG.debug("-------------------------------------time=" + vs)
                                loop.break
                              }
                            }
                          }
                        }
                        if (vs == null) {
                          vs = String.valueOf(v);
                          if (col.equals("id")) {
                            key += v
                          }
                        }
                        rowMap.put(col, vs);
                      }
                    }
                    LOG.debug("-------------------------------------key=" + key)
                    var uuid = jedisCluster.get(key);
                    LOG.debug("-------------------------------------uuid=" + uuid)
                    if (uuid == null) {
                      uuid = java.lang.Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()), 36);
                      jedisCluster.set(key, uuid);
                    }
                    //                     rowMap.put(ESConstants.ES_ID_KEY, uuid)
                    spark2kafkaBean.setEsId(uuid)
                    spark2kafkaBean.setResultMap(rowMap)
                    LOG.debug("-------------------------------------key=" + key)
                    LOG.debug("-------------------------------------rowmap=" + rowMap)
                    //                    producer.sendMsg(JSONObject.toJSONString(rowMap))
                    producer.sendMsg(spark2kafkaBean)
                  }
              }
            } catch {
              case e: Exception => {
                LOG.error(e.getMessage, e)
              }
            }
            LOG.info("--------------------------------partition over-------------------------------")
            if (producer != null) {
              try {
                producer.destroy()
                //                producer.close()
              } catch {
                case e: Exception => {
                  LOG.error(e.getMessage, e)
                }
              }
            }
          }
        }
      }
    }
  }
}