package entity

class BrandConcept_Ext {
  var _name : String as Name

  construct(name : String) {
    _name = name
  }

  override function toString() : String {
    return "BrandConcept(${_name})"
  }
}