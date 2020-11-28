package com.raphtory.examples.blockchain.routers

import com.raphtory.core.components.Router.GraphBuilder
import com.raphtory.core.model.communication._
import com.raphtory.sources.blockchain.BitcoinTransaction
import spray.json.JsArray

import scala.util.hashing.MurmurHash3


class LitecoinGraphBuilder extends GraphBuilder[BitcoinTransaction]{

  override def parseTuple(tuple:BitcoinTransaction)  = {
    val transaction  = tuple.transaction
    val time         = tuple.time
    val blockID      = tuple.blockID
    val block        = tuple.block
    val timeAsString = time.toString
    val timeAsLong   = (timeAsString.toLong) * 1000

    val txid          = transaction.asJsObject.fields("txid").toString()
    val vins          = transaction.asJsObject.fields("vin")
    val vouts         = transaction.asJsObject.fields("vout")
    val locktime      = transaction.asJsObject.fields("locktime")
    val version       = transaction.asJsObject.fields("version")
    var total: Double = 0
    for (vout <- vouts.asInstanceOf[JsArray].elements) {
      val voutOBJ = vout.asJsObject()
      var value   = voutOBJ.fields("value").toString
      total += value.toDouble
      val n            = voutOBJ.fields("n").toString
      val scriptpubkey = voutOBJ.fields("scriptPubKey").asJsObject()

      var address    = "nulldata"
      val outputType = scriptpubkey.fields("type").toString

      if (scriptpubkey.fields.contains("addresses"))
        address = scriptpubkey.fields("addresses").asInstanceOf[JsArray].elements(0).toString
      else value = "0" //TODO deal with people burning money

      //creates vertex for the receiving wallet
      sendUpdate(
              VertexAddWithProperties(
                      msgTime = timeAsLong,
                      srcID = MurmurHash3.stringHash(address),
                      Properties(
                              StringProperty("type", "address"),
                              StringProperty("address", address),
                              StringProperty("outputType", outputType)
                      )
              )
      )
      //creates edge between the transaction and the wallet
      sendUpdate(
              EdgeAddWithProperties(
                      msgTime = timeAsLong,
                      srcID = MurmurHash3.stringHash(txid),
                      dstID = MurmurHash3.stringHash(address),
                      Properties(StringProperty("n", n), StringProperty("value", value))
              )
      )

    }
    sendUpdate(
            VertexAddWithProperties(
                    msgTime = timeAsLong,
                    srcID = MurmurHash3.stringHash(txid),
                    Properties(
                            StringProperty("type", "transaction"),
                            StringProperty("time", timeAsString),
                            StringProperty("id", txid),
                            StringProperty("total", total.toString),
                            StringProperty("lockTime", locktime.toString),
                            StringProperty("version", version.toString),
                            StringProperty("blockhash", blockID.toString),
                            StringProperty("block", block.toString)
                    )
            )
    )

    if (vins.toString().contains("coinbase")) {
      //creates the coingen node
      sendUpdate(
              VertexAddWithProperties(
                      msgTime = timeAsLong,
                      srcID = MurmurHash3.stringHash("coingen"),
                      Properties(StringProperty("type", "coingen"))
              )
      )

      //creates edge between coingen and the transaction
      sendUpdate(
              EdgeAdd(
                      msgTime = timeAsLong,
                      srcID = MurmurHash3.stringHash("coingen"),
                      dstID = MurmurHash3.stringHash(txid)
              )
      )
    } else {
      for (vin <- vins.asInstanceOf[JsArray].elements) {
        val vinOBJ   = vin.asJsObject()
        val prevVout = vinOBJ.fields("vout").toString
        val prevtxid = vinOBJ.fields("txid").toString
        val sequence = vinOBJ.fields("sequence").toString
        //no need to create node for prevtxid as should already exist
        //creates edge between the prev transaction and current transaction
        sendUpdate(
                EdgeAddWithProperties(
                        msgTime = timeAsLong,
                        srcID = MurmurHash3.stringHash(prevtxid),
                        dstID = MurmurHash3.stringHash(txid),
                        Properties(StringProperty("vout", prevVout), StringProperty("sequence", sequence))
                )
        )
      }

    }
  }

}
