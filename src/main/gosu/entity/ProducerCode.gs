package entity

class ProducerCode {
  var _brandConcepts : ProdCodeBrandConcept_Ext[]as BrandConcepts_Ext

  construct() {
  }

  construct(brandConcepts : ProdCodeBrandConcept_Ext[]) {
    _brandConcepts = brandConcepts
  }
}