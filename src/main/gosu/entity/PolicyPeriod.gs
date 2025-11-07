package entity

class PolicyPeriod {
  var _firstPeriodInTerm : PolicyPeriod as FirstPeriodInTerm
  var _createTime : Date as CreateTime
  var _producerCodeOfRecord : ProducerCode as ProducerCodeOfRecord

  construct() {
    _createTime = new Date()
  }

  construct(firstPeriod : PolicyPeriod) {
    this()
    _firstPeriodInTerm = firstPeriod
  }

  construct(producerCode : ProducerCode) {
    this()
    _producerCodeOfRecord = producerCode
  }
}