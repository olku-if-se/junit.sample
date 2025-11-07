package enhancement

uses entity.BrandConcept_Ext
uses entity.PolicyPeriod

enhancement PolicyPeriodEnhancement : PolicyPeriod {

  /**
   * Test Case 1: Simple null-safe property chaining
   * Expected: 4 branches (2 null checks)
   */
  property get FirstPeriodInTermCreateTime_Ext() : Date {
    return this.FirstPeriodInTerm?.CreateTime
  }

  /**
   * Test Case 2: Business logic with HasElements
   * Expected: ~16-18 branches (including business logic)
   */
  property get AvailableBrandConceptsForProdCode() : List<BrandConcept_Ext> {
    var brandConcepts = this.ProducerCodeOfRecord?.BrandConcepts_Ext
    return brandConcepts?.HasElements ? brandConcepts*.BrandConcept.toList() : null
  }

  /**
   * Test Case 3: Multiple null-safe chains
   * Expected: 6 branches (3 null checks)
   */
  property get FirstPeriodProducerCodeName() : String {
    return this.FirstPeriodInTerm?.ProducerCodeOfRecord?.BrandConcepts_Ext?.first()?.BrandConcept?.Name
  }

  /**
   * Test Case 4: Simple single null check
   * Expected: 2 branches (1 null check)
   */
  property get ProducerCodeExists() : boolean {
    return this.ProducerCodeOfRecord != null
  }
}