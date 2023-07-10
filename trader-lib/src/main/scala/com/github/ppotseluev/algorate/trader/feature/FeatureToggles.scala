package com.github.ppotseluev.algorate.trader.feature

import scala.collection.mutable

trait FeatureToggles {
  def list: List[Feature[_]]

  def register[T: Feature.Parse](name: String, value: T): Feature[T]

  final def find(name: String): Option[Feature[_]] =
    list.find(_.name == name)
}

object FeatureToggles {
  def inMemory: FeatureToggles = new FeatureToggles {
    private val lock = new Object()

    private val featureValues = new mutable.HashMap[String, Any]()
    private val features = new mutable.HashMap[String, Feature[_]]()

    override def list: List[Feature[_]] = lock.synchronized {
      features.values.toList
    }

    override def register[T: Feature.Parse](featureName: String, value: T): Feature[T] =
      lock.synchronized {
        features.get(featureName) match {
          case Some(feature) => feature.asInstanceOf[Feature[T]]
          case None =>
            val feature = new Feature[T] {
              override def name: String = featureName

              override def apply(): T = featureValues(name).asInstanceOf[T]

              override def set(value: T): Unit = {
                featureValues(name) = value
              }
            }
            featureValues.put(featureName, value)
            features.put(featureName, feature)
            feature
        }
      }
  }

}
