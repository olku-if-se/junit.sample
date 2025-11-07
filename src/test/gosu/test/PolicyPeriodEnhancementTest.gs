package test

uses entity.BrandConcept_Ext
uses entity.PolicyPeriod
uses entity.ProdCodeBrandConcept_Ext
uses entity.ProducerCode
uses org.junit.Assert
uses org.junit.Test

class PolicyPeriodEnhancementTest {

  // ===== Test Case 1: FirstPeriodInTermCreateTime_Ext =====

  @Test
  function testFirstPeriodCreateTime_WithNullFirstPeriod() {
    // Branch: this.FirstPeriodInTerm == null
    var period = new PolicyPeriod()
    var result = period.FirstPeriodInTermCreateTime_Ext
    Assert.assertNull("Should return null when FirstPeriodInTerm is null", result)
  }

  @Test
  function testFirstPeriodCreateTime_WithNullCreateTime() {
    // Branch: FirstPeriodInTerm != null, but CreateTime == null
    var firstPeriod = new PolicyPeriod()
    firstPeriod.CreateTime = null
    var period = new PolicyPeriod(firstPeriod)
    var result = period.FirstPeriodInTermCreateTime_Ext
    Assert.assertNull("Should return null when CreateTime is null", result)
  }

  @Test
  function testFirstPeriodCreateTime_WithValidData() {
    // Branch: Both not null
    var firstPeriod = new PolicyPeriod()
    var period = new PolicyPeriod(firstPeriod)
    var result = period.FirstPeriodInTermCreateTime_Ext
    Assert.assertNotNull("Should return CreateTime when both are not null", result)
  }

  // ===== Test Case 2: AvailableBrandConceptsForProdCode =====

  @Test
  function testAvailableBrandConcepts_WithNullProducerCode() {
    // Branch: ProducerCodeOfRecord == null
    var period = new PolicyPeriod()
    var result = period.AvailableBrandConceptsForProdCode
    Assert.assertNull("Should return null when ProducerCodeOfRecord is null", result)
  }

  @Test
  function testAvailableBrandConcepts_WithEmptyBrandConcepts() {
    // Branch: BrandConcepts_Ext is empty (HasElements = false)
    var producerCode = new ProducerCode(new ProdCodeBrandConcept_Ext[0])
    var period = new PolicyPeriod(producerCode)
    var result = period.AvailableBrandConceptsForProdCode
    Assert.assertNull("Should return null when HasElements is false", result)
  }

  @Test
  function testAvailableBrandConcepts_WithValidData() {
    // Branch: HasElements = true
    var brand1 = new BrandConcept_Ext("Brand A")
    var brand2 = new BrandConcept_Ext("Brand B")
    var prodBrands = {
        new ProdCodeBrandConcept_Ext(brand1),
        new ProdCodeBrandConcept_Ext(brand2)
    }.toTypedArray()
    var producerCode = new ProducerCode(prodBrands)
    var period = new PolicyPeriod(producerCode)

    var result = period.AvailableBrandConceptsForProdCode
    Assert.assertNotNull("Should return list when HasElements is true", result)
    Assert.assertEquals("Should have 2 brand concepts", 2, result.size())
  }

  // ===== Test Case 3: Complex chain =====

  @Test
  function testFirstPeriodProducerCodeName_AllNull() {
    var period = new PolicyPeriod()
    var result = period.FirstPeriodProducerCodeName
    Assert.assertNull("Should handle null chain gracefully", result)
  }

  @Test
  function testFirstPeriodProducerCodeName_ValidData() {
    var brand = new BrandConcept_Ext("Premium Brand")
    var prodBrand = new ProdCodeBrandConcept_Ext(brand)
    var producerCode = new ProducerCode({prodBrand})

    var firstPeriod = new PolicyPeriod(producerCode)
    var period = new PolicyPeriod(firstPeriod)

    var result = period.FirstPeriodProducerCodeName
    Assert.assertEquals("Should return brand name", "Premium Brand", result)
  }

  // ===== Test Case 4: Simple null check =====

  @Test
  function testProducerCodeExists_False() {
    var period = new PolicyPeriod()
    var result = period.ProducerCodeExists
    Assert.assertFalse("Should return false when ProducerCode is null", result)
  }

  @Test
  function testProducerCodeExists_True() {
    var producerCode = new ProducerCode()
    var period = new PolicyPeriod(producerCode)
    var result = period.ProducerCodeExists
    Assert.assertTrue("Should return true when ProducerCode exists", result)
  }
}